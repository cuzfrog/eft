package com.github.cuzfrog.eft

import java.net.{InetAddress, SocketException}
import java.nio.file.{Files, Path}
import java.util.concurrent.atomic.AtomicReference

import akka.NotUsed
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
    val flow = LoopTcpMan.constructPushFlow(file, () => system.terminate())
    server.runForeach { connection =>
      connection.handleWith(flow)
    }
    RemoteInfo(NetworkUtil.getLocalIpAddress, port)
  }

  override def push(codeInfo: RemoteInfo, file: Path): Unit = ???

  override def setPull(destDir: Path): RemoteInfo = ???

  override def pull(codeInfo: RemoteInfo, destDir: Path): Unit = {
    val tcpFlow = Tcp().outgoingConnection(codeInfo.availableIpWithHead, codeInfo.port)
    val pullFlow = LoopTcpMan.constructPullFlow(
      () => destDir.resolve(filenameRef.get()),
      (fn: String) => filenameRef.set(fn),
      (otherV: Array[Byte]) => println(ByteString(otherV).utf8String)
    )
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

  def constructPushFlow(file: Path,
                        shutdownCallback: () => Unit,
                        chunkSize: Int = 8192): Flow[ByteString, ByteString, NotUsed] = {
    Flow[ByteString].flatMapConcat { bs =>
      Msg.fromByteString(bs) match {
        case Ask =>
          Source.single(Filename(file.getFileName.toString).toByteString)

        case Acknowledge =>
          FileIO.fromPath(file, chunkSize).map(chunk => Payload(chunk.toArray).toByteString)

        case Done =>
          shutdownCallback()
          Source.empty[ByteString]

        case other: Other => Source.single(other.toByteString)

        case noReaction => Source.single(
          Other(s"Unknow how to react aginst:${noReaction.getClass.getSimpleName}".getBytes).toByteString
        )
      }
    }
  }

  def constructPullFlow(getDest: () => Path,
                        saveFilename: String => Unit,
                        otherConsumeF: Array[Byte] => Unit,
                        receiveTestMsgConsumeF: Option[Msg => Unit] = None)
                       (implicit executionContext: ExecutionContext): Flow[ByteString, ByteString, Future[IOResult]] = {

    val fileSink = Flow[Msg].collect { case Payload(v) => ByteString(v) }.toMat(FileIO.toPath(getDest()))(Keep.right)
    val OtherSink = Flow[Msg].collect { case Other(v) => otherConsumeF(v) }.to(Sink.ignore)
    val CmdFlow = Flow[Msg].collect {
      case Filename(v) => saveFilename(v); Acknowledge
    }
    val testSink = receiveTestMsgConsumeF match {
      case None => Sink.ignore
      case Some(f) => Sink.foreach[Msg](f)
    }

    Flow.fromGraph(GraphDSL.create(fileSink) { implicit builder =>
      FileSink =>
        import GraphDSL.Implicits._

        val DoneSigal = builder.materializedValue.map(_.map(_ => Done)).flatMapConcat(Source.fromFuture).outlet
        /** Command router broadcast */
        val CRB = builder.add(Broadcast[Msg](4))
        /** Command merge. */
        val CM = builder.add(MergePreferred[Msg](2))
        /** Translation layer */
        val TL = builder.add(commandTranslationBidiFlow)

        TL.out1 ~> CRB ~> FileSink
        CRB ~> testSink
        CRB ~> OtherSink
        CRB ~> CmdFlow ~> CM.preferred
        TL.in2 <~ CM <~ Source.single(Ask) //init singal
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
}
