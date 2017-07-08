package com.github.cuzfrog.eft

import java.net.{InetAddress, SocketException}
import java.nio.file.{Files, Path}

import akka.NotUsed
import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.event.Logging
import akka.stream._
import akka.stream.scaladsl._
import akka.util.ByteString
import akka.pattern.ask

import scala.concurrent.{Future, Promise}
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

  //------------ Implementations ------------
  override def setPush(file: Path): RemoteInfo = {
    val flow = LoopTcpMan.constructPushFlow(file, () => system.terminate())
    server.runForeach { connection =>
      connection.handleWith(flow)
    }
    RemoteInfo(NetworkUtil.getLocalIpAddress, port)
  }

  override def push(codeInfo: RemoteInfo, file: Path): Unit = ???

  override def setPull(folder: Path): RemoteInfo = ???

  override def pull(codeInfo: RemoteInfo, folder: Path): Unit = {

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
                        shutdownCallback: () => Unit): Flow[ByteString, ByteString, NotUsed] = {
    Flow[ByteString].flatMapConcat { bs =>
      Msg.fromByteString(bs) match {
        case Ask =>
          Source.single(Filename(file.getFileName.toString).toByteString)

        case Acknowledge =>
          FileIO.fromPath(file).map(chunk => Payload(chunk.toArray).toByteString)

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

  def constructPullFlow(dest: => Path,
                        saveFilename: String => Unit,
                        otherConsumeF: Array[Byte] => Unit) = {

    val fileSink = Flow[Msg].collect { case Payload(v) => ByteString(v) }.toMat(FileIO.toPath(dest))(Keep.right)
    val otherSink = Flow[Msg].collect { case Other(v) => otherConsumeF(v) }.to(Sink.ignore)
    val cmdFlow = Flow[Msg].collect {
      case Filename(v) => saveFilename(v); Acknowledge
    }

    Flow.fromGraph(GraphDSL.create(fileSink) { implicit builder => _ =>
        import GraphDSL.Implicits._

        val doneSigal = builder.materializedValue.map(_.map(_ => Done)).flatMapConcat(Source.fromFuture).outlet

        /** Command router broadcast */
        val CRB = builder.add(Broadcast[Msg](3))

        /** Command merge. */
        val CM = builder.add(MergePreferred[Msg](2))

        TL.out1 ~> CRB ~> fileSink
                   CRB ~> otherSink
                   CRB ~> cmdFlow ~> CM.preferred
        TL.in2  <~ CM <~ Source.single(Ask) //init singal
                   CM <~ doneSigal
        FlowShape(TL.in1,TL.out2)
    })
  }

  // ----------------- Shapes -----------------

  /** Tranlation layer Bidi */
  private val TL = BidiShape.fromFlows(
    top = Flow[ByteString].map(Msg.fromByteString).shape,
    bottom = Flow[Msg].map(_.toByteString).shape
  )
}
