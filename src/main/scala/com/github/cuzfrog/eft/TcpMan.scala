package com.github.cuzfrog.eft

import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.file.Path

import akka.actor.ActorSystem
import akka.stream._
import akka.stream.scaladsl.Tcp.{IncomingConnection, ServerBinding}
import akka.stream.scaladsl._
import akka.util.ByteString
import me.alexpanov.net.FreePortFinder
import boopickle.Default._

import scala.concurrent.Future
import scala.util.Try


private object TcpMan extends SimpleLogger {
  private implicit val system = ActorSystem("eft")
  private implicit val materializer = ActorMaterializer()
  private implicit val ec = system.dispatcher
  private def randomPort = FreePortFinder.findFreeLocalPort()

  def push(file: Path): RemoteInfo = {
    val cmdPort = randomPort
    val cmdServer = Tcp().bind("0.0.0.0", cmdPort)

    cmdServer.runForeach(_.handleWith(cmdFlow(file)))
    RemoteInfo(NetworkUtil.getLocalIpAddress, cmdPort, Option(file.getFileName.toString))
  }

  def pull(remoteInfo: RemoteInfo): Unit = {
    val receivePort = randomPort
    val receiveServer = Tcp().bind("0.0.0.0", receivePort)
    receiveServer.runForeach { connection =>
      connection.handleWith(receiveFlow(remoteInfo.filename.getOrElse("unnamed")))
    }

    val remoteOpt = remoteInfo.ips.find(InetAddress.getByName(_).isReachable(300))
    remoteOpt match {
      case Some(ip) =>
        val remote = Tcp().outgoingConnection(ip, remoteInfo.port)
        val info = RemoteInfo(NetworkUtil.getLocalIpAddress, receivePort)
        remote.runWith(
          Source.single(ByteString(Pickle.intoBytes(info))),
          Sink.foreach(println)
          //todo: real transfer logic
        )
      case None => err("Cannot reach remote pull. Try again by run pull at remote again.")
    }
  }

  private val AddressExtrator = """ip:([\d\.]+),port:(\d+)""".r
  private def cmdFlow(file: Path) = Flow[ByteString]
    .via(Framing.delimiter(ByteString("\n"), maximumFrameLength = 256, allowTruncation = true))
    .map { bs =>
      val infoOpt = Try(Unpickle[RemoteInfo].fromBytes(bs.asByteBuffer)).toOption
      infoOpt match {
        case Some(info) =>
          transfer(file, info)
          Pickle.intoBytes(Ack)
        case None => ByteBuffer.allocate(0)
      }
    }
    .map(ByteString(_))

  private def receiveFlow(filename: String) = Flow[ByteString]
    .map { bs =>
      println(s"file transfered:\n$bs")
      Pickle.intoBytes(Ack)
    }
    .map(ByteString(_))

  private def transfer(file: Path, remoteInfo: RemoteInfo): Unit = {
    val remoteOpt = remoteInfo.ips.find(InetAddress.getByName(_).isReachable(300))
    remoteOpt match {
      case Some(ip) =>
        val remote = Tcp().outgoingConnection(ip, remoteInfo.port)
        remote.runWith(
          Source.single(ByteString("Some1233453")),
          Sink.foreach(println)
          //todo: real transfer logic
        )
      case None => err("Cannot reach remote pull. Try again by run pull at remote again.")
    }
  }


}
