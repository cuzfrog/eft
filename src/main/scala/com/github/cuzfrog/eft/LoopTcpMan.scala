package com.github.cuzfrog.eft

import java.net.{InetAddress, SocketException}
import java.nio.file.{Files, Path}

import akka.NotUsed
import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.event.Logging
import akka.stream._
import akka.stream.scaladsl._
import akka.util.ByteString

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

  private lazy val commandActor =
    system.actorOf(Props[CommandActor], s"${config.name}-command-actor")
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
    val (ip, port) = (codeInfo.availableIpWithHead, codeInfo.port)
    val flow = Tcp().outgoingConnection(ip, port)
    val source = Source.actorRef[Msg](1, OverflowStrategy.dropNew)
    val forwarder = source.map(_.toByteString).viaMat(flow)(Keep.left)
      .map(commandActor ! _).toMat(Sink.ignore)(Keep.left).run()

    commandActor ! forwarder //pass actorRef in
    commandActor ! folder
    forwarder ! Ask //init command
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
          case Ask => Source.single(
            Filename(file.getFileName.toString).toByteString
          )

          case Acknowledge =>
            FileIO.fromPath(file).map(ByteString(Msg.PAYLOAD.toArray) ++ _)

          case Done =>
            shutdownCallback()
            Source.empty[ByteString]

        }
        respOpt.getOrElse(Source.empty[ByteString])
      } else Source.single(bs) //echo
    }
  }
}

private class CommandActor extends Actor {
  var forwarder: ActorRef = _
  var filename: String = "unnamed"
  var destDir: Path = _

  override def receive: Receive = {
    //provision
    case forwarderRef: ActorRef => forwarder = forwarderRef
    case path: Path => destDir = path
    //commands
    case bs: ByteString if bs.startsWith(Msg.HEAD) => Msg.fromByteString(bs) match {
      case Some(Filename(fn)) =>
        filename = fn
        forwarder ! Acknowledge
    }
    //payload
    case bs: ByteString if bs.startsWith(Msg.PAYLOAD) =>
      //FileIO.toPath(destDir).runWith(Source.single(bs))
      Files.write(destDir.resolve(filename), bs.drop(Msg.PAYLOAD.length).toArray)
      //todo: split IO out of this
      forwarder ! Done
    //print echo
    case bs: ByteString => println("echo:" + bs.utf8String)
  }
}