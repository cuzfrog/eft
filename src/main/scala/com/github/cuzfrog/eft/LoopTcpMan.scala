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
      if (bs.startsWith(Msg.HEAD)) { //msg
        val respOpt = Msg.fromByteString(bs) map {
          case Ask =>
            FileIO.fromPath(file).map(chunk => Payload(file.getFileName.toString, chunk.toArray).toByteString)

          case Done =>
            shutdownCallback()
            Source.empty[ByteString]

        }
        respOpt.getOrElse(Source.empty[ByteString])
      } else Source.single(bs) //echo
    }
  }

  def constructPullSink(ip: String, port: Int, destDir: Path) = {
    //val tcpFlow = Tcp().outgoingConnection(ip, port)

    Sink.fromGraph(GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val CF = Flow[ByteString].map {
        case bs: ByteString if bs.startsWith(Msg.HEAD) => bs
        case bs => println(bs.utf8String)
      }

      val CRB = builder.add(Broadcast[ByteString](3))
      val filenameF = Flow[ByteString]


      //SinkShape(bcast.in)
      ???
    })
  }
}
