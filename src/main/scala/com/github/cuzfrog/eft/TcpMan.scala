package com.github.cuzfrog.eft

import java.io.IOException
import java.net.SocketException
import java.nio.ByteBuffer
import java.nio.file.Path

import akka.actor.ActorSystem
import akka.stream._
import akka.stream.scaladsl._
import akka.util.ByteString
import boopickle.Default._
import me.alexpanov.net.FreePortFinder

import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success, Try}
import scala.concurrent.duration._
import scala.language.postfixOps

/**
  * Stream tcp utility.
  */
private class TcpMan(systemName: String = "eft") extends SimpleLogger {
  override val loggerLevel = SimpleLogger.Debug
  implicit val system = ActorSystem(systemName)
  private implicit val materializer = ActorMaterializer()
  private implicit val ec = system.dispatcher
  private def randomPort = FreePortFinder.findFreeLocalPort()

  private final val EmptyByteString = ByteString(ByteBuffer.allocate(0))

  private lazy val cmdPort = randomPort
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
    val remoteFuture = sendCmd(remoteIP, codeInfo.cmdPort, Ask)
    remoteFuture.map {
      case Some(remoteInfo: RemoteInfo) =>
        val filename = remoteInfo.filename.getOrElse("unnamed")
        //establish receive server.
        receiveServer.runForeach { connection =>
          val (_, ioResult) = connection.flow.runWith(
            Source.maybe,
            FileIO.toPath(folder.resolve(filename))
          )
          ioResult.onComplete {
            case Success(s) if s.status.isSuccess =>
              debug("File pulled.")
              sendCmd(remoteIP, codeInfo.cmdPort, Done)
              system.terminate()
            case _ => throw new IOException("file transfer or save failed.")
          }
        }
      case bad => throw new AssertionError(s"Bad response from push:$bad")
    }

    remoteFuture.onComplete {
      case Success(_) =>
        //send out receive server's connection info.
        val pullInfo: Msg = RemoteInfo(NetworkUtil.getLocalIpAddress,
          cmdPort, Option(receivePort))
        sendCmd(remoteIP, codeInfo.cmdPort, pullInfo)
      case Failure(e) => throw new IOException(s"Cannot ask push with error msg:${e.getMessage}")
    }
  }
  private val pullCmdFlow = constrCmdFlow { bs =>
    val infoOpt = Try(Unpickle[Msg].fromBytes(bs.asByteBuffer)).toOption
    infoOpt match {
      case Some(Hello) => bs //echo
      case bad => warn(s"Bad cmd:$bad")
    }
  }

  //================ helpers =================
  private def sendCmd(ip: String, port: Int, msg: Msg): Future[Option[Msg]] = {
    val graph = Source.single(ByteString(Pickle.intoBytes(msg)))
      .via(Tcp().outgoingConnection(ip, port))
      .toMat(Sink.headOption)(Keep.right)
    graph.run().map(_.map(bs => Unpickle[Msg].fromBytes(bs.asByteBuffer)))
  }

  private implicit class RemoteInfoEx(in: RemoteInfo) {
    def availableIP: String = {
      val ipOpt = in.ips.find { ip =>
        val f = sendCmd(ip, in.cmdPort, Hello)
        Try(Await.result(f, 0.5 seconds)).toOption.flatten.contains(Hello)
      }
      ipOpt.getOrElse(throw new SocketException("Cannot reach remote ip."))
    }
  }

  private def constrCmdFlow[R](block: ByteString => R) = Flow[ByteString]
    .via(Framing.delimiter(ByteString("\n"), maximumFrameLength = 256, allowTruncation = true))
    .map(block)
    .map {
      case bs: ByteString => bs
      case _ => EmptyByteString
    }
}
