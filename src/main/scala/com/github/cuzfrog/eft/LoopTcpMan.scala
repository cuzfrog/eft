package com.github.cuzfrog.eft

import java.io.IOException
import java.net.InetAddress
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference

import akka.actor.ActorSystem
import akka.event.Logging
import akka.stream._
import akka.stream.scaladsl._
import akka.util.ByteString
import akka.{Done, NotUsed}

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.language.postfixOps

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
    val pushFlow = LoopTcpMan.constructSymmetricFlow(
      nodeType = Node.Push,
      getPath = () => file,
      shutdownCallback = () => system.terminate(),
      echoOther = true,
      chunkSize = config.pushChunkSize,
      receiveTestMsgConsumeF = Some(msg => debug(s"Push <~~ $msg")),
      sendTestMsgConsumeF = Some(msg => debug(s"Push ~~> $msg"))
    )
    server.runForeach { connection =>
      connection.handleWith(pushFlow.join(LoopTcpMan.FramingBidi.reversed))
    }
    RemoteInfo(NetworkUtil.getLocalIpAddress, port)
  }

  override def push(codeInfo: RemoteInfo, file: Path): Future[Option[String]] = {
    val donePromise = Promise[Option[String]]
    val remoteIp = codeInfo.availableIpWithHead
    val tcpFlow = Tcp().outgoingConnection(remoteIp, codeInfo.port)
    val pushFlow = LoopTcpMan.constructSymmetricFlow(
      nodeType = Node.Push,
      getPath = () => file,
      shutdownCallback = () => {
        system.terminate()
        debug(s"System[${system.name}] to terminate.")
        donePromise.success(None)
      },
      chunkSize = config.pushChunkSize,
      otherConsumeF = Some((otherV: Array[Byte]) =>
        println(s"From $remoteIp: " + ByteString(otherV).utf8String)
      ),
      receiveTestMsgConsumeF = Some(msg => debug(s"Push <~~ $msg")),
      sendTestMsgConsumeF = Some(msg => debug(s"Push ~~> $msg"))
    )
    tcpFlow.join(LoopTcpMan.FramingBidi).join(pushFlow).run()
    donePromise.future
  }

  private def getDest(destDir: Path): Path = {
    val fn = filenameRef.get()
    debug(s"Pull: get file name[$fn] from ref.")
    destDir.resolve(fn)
  }

  override def setPull(destDir: Path): RemoteInfo = {
    val pullFlow = LoopTcpMan.constructSymmetricFlow(
      nodeType = Node.Pull,
      getPath = () => getDest(destDir),
      saveFilenameF = (fn: String) => filenameRef.set(fn),
      shutdownCallback = () => system.terminate(),
      echoOther = true,
      receiveTestMsgConsumeF = Some(msg => debug(s"Pull <~~ $msg")),
      sendTestMsgConsumeF = Some(msg => debug(s"Pull ~~> $msg"))
    )
    server.runForeach { connection =>
      connection.handleWith(pullFlow.join(LoopTcpMan.FramingBidi.reversed))
    }
    RemoteInfo(NetworkUtil.getLocalIpAddress, port)
  }

  override def pull(codeInfo: RemoteInfo, destDir: Path): Future[Option[String]] = {
    val remoteIp = codeInfo.availableIpWithHead
    val tcpFlow = Tcp().outgoingConnection(remoteIp, codeInfo.port)
    val pullFlow = LoopTcpMan.constructSymmetricFlow(
      nodeType = Node.Pull,
      getPath = () => getDest(destDir),
      saveFilenameF = (fn: String) => filenameRef.set(fn),
      shutdownCallback = () => system.terminate(),
      otherConsumeF = Some((otherV: Array[Byte]) =>
        println(s"From $remoteIp: " + ByteString(otherV).utf8String)
      ),
      receiveTestMsgConsumeF = Some(msg => debug(s"Pull <~~ $msg")),
      sendTestMsgConsumeF = Some(msg => debug(s"Pull ~~> $msg"))
    )
    val result = tcpFlow
      .join(LoopTcpMan.FramingBidi)
      .joinMat(pullFlow)(Keep.right).run().flatten
    result.map { ioResult =>
      Future {
        Thread.sleep(300)
        system.terminate()
      }
      ioResult.status.failed.toOption.map(e => s"Failed with msg:${e.getMessage}")
    }
  }

  override def close(): Unit = system.terminate()
  override def isClosed: Boolean = system.whenTerminated.isCompleted

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
    * Construct a symmetric flow.
    *
    * @param getPath                function to get destination path.
    * @param saveFilenameF          function to store filename.
    * @param otherConsumeF          function to consume Other msg.
    * @param echoOther              whether to echo Other msg.
    * @param receiveTestMsgConsumeF function to consume a fork of msg(for testing).
    * @param executionContext       implicit ExecutionContext
    * @return a flow containing pull logic, materializing Future of IOResult.
    */
  def constructSymmetricFlow(nodeType: Node,
                             getPath: () => Path,
                             saveFilenameF: String => Unit = _ => (),
                             shutdownCallback: () => Unit = () => (),
                             otherConsumeF: Option[Array[Byte] => Unit] = None,
                             chunkSize: Int = 8192,
                             echoOther: Boolean = false,
                             receiveTestMsgConsumeF: Option[Msg => Unit] = None,
                             sendTestMsgConsumeF: Option[Msg => Unit] = None)
                            (implicit executionContext: ExecutionContext)
  : Flow[ByteString, ByteString, Future[Future[IOResult]]] = {

    // ------------- Component --------------
    val fileDonePromise = Promise[Msg]
    val fileSink = {
      val lazyFileSink = Sink.lazyInit[ByteString, Future[IOResult]](
        (_) => Future(FileIO.toPath(getPath())),
        () => Future(
          IOResult.createFailed(0, new IOException("No data received and thus no data written."))
        )
      )
      Flow[Msg].collect { case Msg.Payload(v) => ByteString(v) }.toMat(lazyFileSink)(Keep.right)
    }

    val CmdFlow: Flow[Msg, Msg, NotUsed] = Flow[Msg].flatMapConcat {
      case Msg.Ask =>
        Source.single(Msg.Filename(getPath().getFileName.toString))

      case Msg.Filename(v) =>
        saveFilenameF(v)
        Source.single(Msg.Acknowledge)

      case _: Msg.Payload => Source.empty

      case Msg.PayLoadEnd =>
        fileDonePromise.success(Msg.Empty)
        Source.empty

      case Msg.Acknowledge =>
        FileIO.fromPath(getPath(), chunkSize)
          .map(chunk => Msg.Payload(chunk.toArray)).concat(Source.single(Msg.PayLoadEnd))

      case Msg.Bye(n) =>
        shutdownCallback()
        if (n < 3) Source.single(Msg.Bye(n + 1)) else Source.empty

      case other: Msg.Other => Source.single(other) //echo

      case noReaction =>
        throw new IllegalArgumentException(
          s"Unknow how to react aginst:${noReaction.getClass.getSimpleName}")

    }.filterNot(_ == Msg.Empty)
      .filterNot(m => !echoOther && m.isInstanceOf[Msg.Other])

    val OtherSink = otherConsumeF.toSinkWithFlow(Flow[Msg].collect { case Msg.Other(v) => v })
    // ------------- Graph construction --------------
    Flow.fromGraph(GraphDSL.create(fileSink) { implicit builder =>
      FileSink =>
        import GraphDSL.Implicits._

        val DoneSignal = builder.materializedValue.map(_.flatten.map(_ => Msg.Bye(0)))
          .flatMapConcat(Source.fromFuture).outlet
        /** Command router broadcast */
        val CRB = builder.add(Broadcast[Msg](3))
        /** Command merge. */
        val CM = builder.add(MergePreferred[Msg](2))

        /** Translation layer */
        val TL = builder.add(
          MsgTranslationBidi(
            inTestSink = receiveTestMsgConsumeF.toSink,
            outTestSink = sendTestMsgConsumeF.toSink
          )
        )
        /** File merge for signaling complete. */
        val FM = builder.add(MergePreferred[Msg](1, eagerComplete = true))
        /** File complete signal */
        val CompleteSignal = Source.fromFuture(fileDonePromise.future)

        val InitSignal = nodeType match {
          case Node.Push => Source.empty
          case Node.Pull => Source.single(Msg.Ask)
        }
        //@formatter:off
        TL.out1 ~> CRB ~> FM.preferred
        CompleteSignal ~> FM ~> FileSink
                   CRB ~> OtherSink
                   CRB ~> CmdFlow ~> CM.preferred
        TL.in2 <~ CM <~ InitSignal
                  CM <~ DoneSignal
        //@formatter:on
        FlowShape(TL.in1, TL.out2)
    })
  }

  // ----------------- Protocol layers: -----------------

  /** Msg Translation layer Bidi (Msg <===> ByteString) */
  private def MsgTranslationBidi
  (inTestSink: Sink[Msg, Future[Done]] = Sink.ignore,
   outTestSink: Sink[Msg, Future[Done]] = Sink.ignore) =
    BidiFlow.fromGraph(GraphDSL.create() { b =>
      val bs2msg = Flow[ByteString].map(Msg.fromByteString)
      val msg2bs = Flow[Msg].map(_.toByteString)

      val top = b.add(bs2msg.alsoTo(inTestSink))
      val bottom = b.add(Flow[Msg].alsoTo(outTestSink).via(msg2bs))
      BidiShape.fromFlows(top, bottom)
    })

  //@formatter:off
  /**
    * Add 16bit length field at the beginning of element for framing.
    * See Akka issue #23325
    *
    *     +----------------------------+
    *     | Resulting BidiFlow         |
    *     |                            |
    *     |  +----------------------+  |
    * I1 ~~> |       Framing        | ~~> O1
    *     |  +----------------------+  |
    *     |                            |
    *     |  +----------------------+  |
    * O2 <~~ |   Add length field    | <~~ I2
    *     |  +----------------------+  |
    *     +----------------------------+
    */
  //@formatter:on
  val FramingBidi = {
    def short2ByteString(sh: Short): ByteString = {
      val b0 = (sh >> 8).toByte
      val b1 = sh.toByte
      ByteString(Array(b1, b0)) //LITTLE_ENDIAN
    }
    BidiFlow.fromFlows(
      Framing.lengthField(fieldLength = 2, maximumFrameLength = Short.MaxValue).map(_.drop(2)),
      Flow[ByteString].map(bs => short2ByteString(bs.length.toShort) ++ bs)
    )
  }

  // ----------------- Helpers ------------------
  private implicit class ConsumeFOps[T](in: Option[T => Unit]) {
    def toSink: Sink[T, Future[akka.Done]] = in match {
      case None => Sink.ignore
      case Some(f) => Sink.foreach[T](f)
    }

    def toSinkWithFlow[I, M](flow: Flow[I, T, M]): Sink[I, Future[Done]] = in match {
      case None => Sink.ignore
      case Some(f) => flow.toMat(Sink.foreach[T](f))(Keep.right)
    }
  }
}
