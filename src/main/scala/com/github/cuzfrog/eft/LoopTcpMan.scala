package com.github.cuzfrog.eft

import java.net.{InetAddress, SocketException}
import java.nio.file.Path

import akka.actor.ActorSystem
import akka.event.Logging
import akka.stream._
import akka.stream.scaladsl._
import akka.util.ByteString


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
  override def setPush(file: Path): RemoteInfo = ???
  override def push(codeInfo: RemoteInfo, file: Path): Unit = ???
  override def setPull(folder: Path): RemoteInfo = ???
  override def pull(codeInfo: RemoteInfo, folder: Path): Unit = ???
  override def close(): Unit = ???

  //------------ Stream flows ------------


  //------------ Helpers ------------
  private implicit class RemoteInfoEx(in: RemoteInfo) {
    def availableIP: String = {
      val ipOpt = in.ips.find { ip =>
        val icmp = InetAddress.getByName(ip).isReachable(config.networkTimeout.toMillis.toInt)
        lazy val tcp = NetworkUtil.checkPortReachable(ip, in.port)
        icmp || tcp
      }
      ipOpt.getOrElse {
        system.terminate()
        err("Cannot reach remote ip.")
        throw new SocketException("Cannot reach remote ip.")
      }
    }
  }
}
