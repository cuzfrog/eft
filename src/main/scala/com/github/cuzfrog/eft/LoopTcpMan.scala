package com.github.cuzfrog.eft

import java.io.IOException
import java.net.{InetAddress, SocketException}
import java.nio.file.{Files, Path}
import java.util.concurrent.atomic.AtomicReference

import akka.{NotUsed}
import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.event.Logging
import akka.stream._
import akka.stream.scaladsl._
import akka.util.ByteString
import akka.pattern.ask

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.Try

/**
  * Created by cuz on 7/6/17.
  */
private class LoopTcpMan(config: Configuration) extends TcpMan with SimpleLogger {
  //------------ Initialization ------------
  override val loggerLevel = if (config.isDebug) SimpleLogger.Debug else SimpleLogger.Info
  private implicit val system = ActorSystem(config.name)
  if (!config.isDebug) system.eventStream.setLogLevel(Logging.ErrorLevel)

  private implicit val materializer = ActorMaterializer()
  private implicit val ec = system.dispatcher

  private lazy val port = config.port.getOrElse(NetworkUtil.freeLocalPort)
  private lazy val server = Tcp().bind("0.0.0.0", port)

  private[this] val filenameRef: AtomicReference[String] = new AtomicReference("unnamed")

  //------------ Implementations ------------
  override def setPush(file: Path): RemoteInfo = {
    val pushFlow = LoopTcpMan.constructPushFlow(
      file,
      shutdownCallback = () => system.terminate(),
      echoOther = true
    )
    server.runForeach { connection =>
      connection.handleWith(pushFlow)
    }
    RemoteInfo(NetworkUtil.getLocalIpAddress, port)
  }

  override def push(codeInfo: RemoteInfo, file: Path): Future[Option[String]] = {
    val donePromise = Promise[Option[String]]
    val tcpFlow = Tcp().outgoingConnection(codeInfo.availableIpWithHead, codeInfo.port)
    val pushFlow = LoopTcpMan.constructPushFlow(
      file,
      shutdownCallback = () => {
        system.terminate()
        donePromise.success(None)
      }
    )
    tcpFlow.join(pushFlow).run()
    donePromise.future
  }

  override def setPull(destDir: Path): RemoteInfo = {
    val pullFlow = LoopTcpMan.constructPullFlow(
      getDest = () => destDir.resolve(filenameRef.get()),
      saveFilenameF = (fn: String) => filenameRef.set(fn),
      echoOther = true
    )
    server.runForeach { connection =>
      connection.handleWith(pullFlow)
    }
    RemoteInfo(NetworkUtil.getLocalIpAddress, port)
  }

  override def pull(codeInfo: RemoteInfo, destDir: Path): Future[Option[String]] = {
    val tcpFlow = Tcp().outgoingConnection(codeInfo.availableIpWithHead, codeInfo.port)
    val pullFlow = LoopTcpMan.constructPullFlow(
      () => destDir.resolve(filenameRef.get()),
      (fn: String) => filenameRef.set(fn),
      Some((otherV: Array[Byte]) => println(ByteString(otherV).utf8String))
    )
    val result = tcpFlow.joinMat(pullFlow)(Keep.right).run().flatten
    result.map(_.status.failed.toOption.map(e => s"Failed with msg:${e.getMessage}"))
  }

  override def close(): Unit = system.terminate()

  //------------ Helpers ------------
  private implicit class RemoteInfoEx(in: RemoteInfo) {
    def availableIP: Option[String] = {
      in.ips.find { ip =>
        val icmp = InetAddress.getByName(ip).isReachable(config.networkTimeout.toMillis.toInt)
        lazy val tcp = NetworkUtil.checkPortReachable(ip, in.port)
        icmp || tcp
      }
    }

    def availableIpWithHead: String = {
      availableIP.getOrElse(
        in.ips.headOption.getOrElse(throw new AssertionError("Bad RemoteInfo."))
      )
    }
  }
}

private object LoopTcpMan {

  /**
    * Construct a push flow.
    *
    * @param file             the Path of the file about to send.
    * @param shutdownCallback call back to shutdown the system(should use this to terminate ActorSystem).
    * @param echoOther        whether to echo Other msg.
    * @param chunkSize        set chunkSize of FileIO.fromPath
    * @return a flow containing push logic.
    */
  def constructPushFlow(file: Path,
                        shutdownCallback: () => Unit,
                        echoOther: Boolean = false,
                        chunkSize: Int = 8192,
                        receiveTestMsgConsumeF: Option[Msg => Unit] = None): Flow[ByteString, ByteString, NotUsed] = {

    // ------------- Component --------------
    val cmdFlow: Flow[Msg, Msg, NotUsed] = Flow[Msg].flatMapConcat {
      case Msg.Ask =>
        Source.single(Msg.Filename(file.getFileName.toString))

      case Msg.Acknowledge =>
        FileIO.fromPath(file, chunkSize).map(chunk => Msg.Payload(chunk.toArray)).concat(Source.single(Msg.Done))

      case Msg.Done =>
        shutdownCallback()
        Source.empty

      case other: Msg.Other => Source.single(other) //echo

      case noReaction =>
        throw new IllegalArgumentException(s"Unknow how to react aginst:${noReaction.getClass.getSimpleName}")

    }.filterNot(m => !echoOther && m.isInstanceOf[Msg.Other])

    val TestSink = receiveTestMsgConsumeF.toSink

    // ------------- Graph construction --------------
    Flow.fromGraph(GraphDSL.create(cmdFlow) { implicit builder =>
      CmdFlow =>
        import GraphDSL.Implicits._

        /** Translation layer */
        val TL = builder.add(commandTranslationBidiFlow)
        /** Merge for generate complete signal. */
        //val CM = builder.add(MergePreferred[Msg](1))

        /** Command broadcast */
        val CB = builder.add(Broadcast[Msg](2))

        TL.out1 ~> CB ~> CmdFlow
                   CB ~> TestSink
        TL.in2 <~ CmdFlow

        FlowShape(TL.in1, TL.out2)
    })
  }

  /**
    * Construct a pull flow.
    *
    * @param getDest                function to get destination path.
    * @param saveFilenameF          function to store filename.
    * @param otherConsumeF          function to consume Other msg.
    * @param echoOther              whether to echo Other msg.
    * @param receiveTestMsgConsumeF function to consume a fork of msg(for testing).
    * @param executionContext       implicit ExecutionContext
    * @return a flow containing pull logic, materializing Future of IOResult.
    */
  def constructPullFlow(getDest: () => Path,
                        saveFilenameF: String => Unit,
                        otherConsumeF: Option[Array[Byte] => Unit] = None,
                        echoOther: Boolean = false,
                        receiveTestMsgConsumeF: Option[Msg => Unit] = None)
                       (implicit executionContext: ExecutionContext): Flow[ByteString, ByteString, Future[Future[IOResult]]] = {

    // ------------- Component --------------
    val fileDonePromise = Promise[Msg]
    val fileSink = {
      val lazyFileSink = Sink.lazyInit[ByteString, Future[IOResult]](
        (bs) => Future(FileIO.toPath(getDest())),
        () => Future(IOResult.createFailed(0, new IOException("No data received and thus no data written.")))
      )
      Flow[Msg].collect { case Msg.Payload(v) => ByteString(v) }.toMat(lazyFileSink)(Keep.right)
    }
    val OtherSink = otherConsumeF match {
      case Some(f) => Flow[Msg].collect { case Msg.Other(v) => f(v) }.to(Sink.ignore)
      case None => Sink.ignore
    }
    val CmdFlow: Flow[Msg, Msg, NotUsed] = Flow[Msg].collect {
      case Msg.Filename(v) => saveFilenameF(v); Msg.Acknowledge
      case Msg.Done =>
        fileDonePromise.success(Msg.Empty)
        Msg.Empty
      case other: Msg.Other => other
    }.filterNot(_ == Msg.Empty).filterNot(m => !echoOther && m.isInstanceOf[Msg.Other])

    val TestSink = receiveTestMsgConsumeF.toSink

    // ------------- Graph construction --------------
    Flow.fromGraph(GraphDSL.create(fileSink) { implicit builder =>
      FileSink =>
        import GraphDSL.Implicits._

        val DoneSigal = builder.materializedValue.map(_.map(_ => Msg.Done)).flatMapConcat(Source.fromFuture).outlet
        /** Command router broadcast */
        val CRB = builder.add(Broadcast[Msg](4))
        /** Command merge. */
        val CM = builder.add(MergePreferred[Msg](2))
        /** Translation layer */
        val TL = builder.add(commandTranslationBidiFlow)
        /** File merge for signaling complete. */
        val FM = builder.add(MergePreferred[Msg](1, eagerComplete = true))

        TL.out1 ~> CRB ~> FM.preferred
        Source.fromFuture(fileDonePromise.future) ~> FM ~> FileSink
        CRB ~> TestSink
        CRB ~> OtherSink
        CRB ~> CmdFlow ~> CM.preferred
        TL.in2 <~ CM <~ Source.single(Msg.Ask) //init singal
        CM <~ DoneSigal
        FlowShape(TL.in1, TL.out2)
    })
  }

  // ----------------- Shapes -----------------

  /** Tranlation layer Bidi */
  private val commandTranslationBidiFlow = BidiFlow.fromGraph(GraphDSL.create() { b =>
    val top = b.add(Flow[ByteString].map(Msg.fromByteString))
    val bottom = b.add(Flow[Msg].map(_.toByteString))
    BidiShape.fromFlows(top, bottom)
  })

  private implicit class MsgConsumeFOps(in: Option[Msg => Unit]) {
    def toSink: Sink[Msg, Future[akka.Done]] = in match {
      case None => Sink.ignore
      case Some(f) => Sink.foreach[Msg](f)
    }
  }
}
