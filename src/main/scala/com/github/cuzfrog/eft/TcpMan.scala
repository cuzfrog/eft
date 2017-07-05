package com.github.cuzfrog.eft

import java.net.{InetAddress, SocketException}
import java.nio.ByteBuffer
import java.nio.file.Path

import akka.actor.ActorSystem
import akka.event.Logging
import akka.stream._
import akka.stream.scaladsl._
import akka.util.ByteString
import boopickle.Default._

import scala.concurrent.Await
import scala.language.postfixOps
import scala.util.Try

/**
  * Stream tcp utility.
  */
private class TcpMan(systemName: String = "eft",
                     config: Configuration) extends SimpleLogger {
  override val loggerLevel = if (config.isDebug) SimpleLogger.Debug else SimpleLogger.Info
  implicit val system = ActorSystem(systemName)
  if (!config.isDebug) system.eventStream.setLogLevel(Logging.ErrorLevel)

  private implicit val materializer = ActorMaterializer()
  private implicit val ec = system.dispatcher
  private def randomPort = NetworkUtil.freeLocalPort

  private final val EmptyByteString = ByteString(ByteBuffer.allocate(0))

  private lazy val cmdPort = config.cmdPort.getOrElse(randomPort)
  private lazy val cmdServer = Tcp().bind("0.0.0.0", cmdPort)

  private lazy val receivePort = randomPort
  private lazy val receiveServer = Tcp().bind("0.0.0.0", receivePort)

  @volatile private var currentPushInfo: RemoteInfo = _
  /**
    * Read file name and setup listening cmd server and publish server connection info.
    *
    * @param file the file to be transferred.
    * @return cmd server connection info(local address).
    */
  def push(file: Path): RemoteInfo = {
    cmdServer.runForeach(_.handleWith(pushCmdFlow(file)))
    debug("File pushed, waiting for client to pull.")
    val pushInfo = RemoteInfo(NetworkUtil.getLocalIpAddress, cmdPort,
      filename = Option(file.getFileName.toString))
    currentPushInfo = pushInfo
    pushInfo
  }

  private def pushCmdFlow(file: Path) = constrCmdFlow { bs =>
    val infoOpt = Try(Unpickle[Msg].fromBytes(bs.asByteBuffer)).toOption
    infoOpt match {
      case Some(info: RemoteInfo) =>
        val remote = Tcp().outgoingConnection(info.availableIP, info.receivePort.get)
        remote.runWith(FileIO.fromPath(file), Sink.ignore)
      case Some(Ask) =>
        ByteString(Pickle.intoBytes(currentPushInfo: Msg))
      case Some(Done) =>
        system.terminate()
        debug("File pulled.")
        info("File pulled.", withTitle = false)
      case Some(Hello) => bs //echo
      case bad => warn(s"Bad cmd:$bad")
    }
  }

  /**
    * Connect push server by remote info, telling it pull server is ready to receive.
    *
    * @param codeInfo the push server's connection info.
    * @param folder   the dir where to save received data.
    */
  def pull(codeInfo: RemoteInfo, folder: Path): Unit = {
    //establish local cmd server.
    cmdServer.runForeach(_.handleWith(pullCmdFlow))

    //checkout remote ips.
    val remoteIP = codeInfo.availableIP
    val remoteInfo = sendCmd(remoteIP, codeInfo.cmdPort, Ask) match {
      case Some(remoteInfo: RemoteInfo) => remoteInfo
      case bad => throw new AssertionError(s"Bad response from push:$bad")
    }

    //establish receive server.
    receiveServer.runForeach { connection =>
      val filename = remoteInfo.filename.getOrElse("unnamed")
      val (_, ioResultF) = connection.flow.runWith(
        Source.maybe,
        FileIO.toPath(folder.resolve(filename))
      )
      ioResultF.map {
        case iOResult if iOResult.wasSuccessful =>
          debug("File pulled.")
          sendCmd(remoteIP, codeInfo.cmdPort, Done)
          system.terminate()
          iOResult
        case failed =>
          if (config.isDebug) failed.getError.printStackTrace()
          err(s"File transfer or save failed with msg:${failed.getError.getMessage}",
            withTitle = false)
          system.terminate()
      }
    }

    //send out receive server's connection info.
    val pullInfo: Msg = RemoteInfo(NetworkUtil.getLocalIpAddress,
      cmdPort, Option(receivePort))
    sendCmd(remoteIP, codeInfo.cmdPort, pullInfo)

  }
  private val pullCmdFlow = constrCmdFlow {
    bs =>
      val infoOpt = Try(Unpickle[Msg].fromBytes(bs.asByteBuffer)).toOption
      infoOpt match {
        case Some(Hello) => bs //echo
        case bad => warn(s"Bad cmd:$bad")
      }
  }

  //================ helpers =================
  private def sendCmd(ip: String, port: Int, msg: Msg): Option[Msg] = {
    val graph = Source.single(ByteString(Pickle.intoBytes(msg)))
      .via(Tcp().outgoingConnection(ip, port))
      .toMat(Sink.headOption)(Keep.right)
    val f = graph.run().map(_.map(bs => Unpickle[Msg].fromBytes(bs.asByteBuffer)))
    Try(Await.result(f, config.networkTimeout)).toOption.flatten
  }

  private implicit class RemoteInfoEx(in: RemoteInfo) {
    def availableIP: String = {


      val ipOpt = in.ips.find { ip =>
        sendCmd(ip, in.cmdPort, Hello).contains(Hello) ||
          InetAddress.getByName(ip).isReachable(config.networkTimeout.toMillis.toInt)
      }
      ipOpt.getOrElse {
        system.terminate()
        err("Cannot reach remote ip.")
        throw new SocketException("Cannot reach remote ip.")
      }
    }
  }

  private def constrCmdFlow[R](block: ByteString => R) = Flow[ByteString]
    .via(Framing.delimiter(ByteString("\n"), maximumFrameLength = 256, allowTruncation = true))
    .map {block}
    .map {
      case bs: ByteString => bs
      case _ => EmptyByteString
    }
}
