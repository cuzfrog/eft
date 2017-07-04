package com.github.cuzfrog.eft

import java.io.IOException
import java.net.{InetAddress, SocketException}
import java.nio.ByteBuffer
import java.nio.file.Path

import akka.actor.ActorSystem
import akka.stream._
import akka.stream.scaladsl._
import akka.util.ByteString
import boopickle.Default._
import me.alexpanov.net.FreePortFinder

import scala.util.{Success, Try}

/**
  * Stream tcp utility.
  */
private object TcpMan extends SimpleLogger{
  override val loggerLevel = SimpleLogger.Debug
  private implicit val system = ActorSystem("eft")
  private implicit val materializer = ActorMaterializer()
  private implicit val ec = system.dispatcher
  private def randomPort = FreePortFinder.findFreeLocalPort()

  private final val EmptyByteString = ByteString(ByteBuffer.allocate(0))

  private lazy val cmdPort = randomPort
  private lazy val cmdServer = Tcp().bind("0.0.0.0", cmdPort)

  private lazy val receivePort = randomPort
  private lazy val receiveServer = Tcp().bind("0.0.0.0", receivePort)

  /**
    * Read file name and setup listening cmd server and publish server connection info.
    *
    * @param file the file to be transferred.
    * @return cmd server connection info(local address).
    */
  def push(file: Path): RemoteInfo = {
    cmdServer.runForeach(_.handleWith(cmdFlow(file)))
    debug("File pushed, waiting for client to pull.")
    RemoteInfo(NetworkUtil.getLocalIpAddress, cmdPort, None, Option(file.getFileName.toString))
  }
  private def cmdFlow(file: Path) = Flow[ByteString]
    .via(Framing.delimiter(ByteString("\n"), maximumFrameLength = 256, allowTruncation = true))
    .map { bs =>
      val infoOpt = Try(Unpickle[Msg].fromBytes(bs.asByteBuffer)).toOption
      infoOpt match {
        case Some(info: RemoteInfo) =>
          val remote = Tcp().outgoingConnection(info.availableIP, info.receivePort.get)
          remote.runWith(FileIO.fromPath(file), Sink.ignore)
        case Some(Done) =>
          system.terminate()
          debug("File pulled.")
        case bad => warn(s"Bad cmd:$bad")
      }
    }
    .map(_ => EmptyByteString)

  /**
    * Connect push server by remote info, telling it pull server is ready to receive.
    *
    * @param remoteInfo the push server's connection info.
    * @param folder     the dir where to save received data.
    */
  def pull(remoteInfo: RemoteInfo, folder: Path): Unit = {
    //checkout remote ips.
    val remoteIP = remoteInfo.availableIP

    //establish receive server.
    receiveServer.runForeach { connection =>
      val filename = remoteInfo.filename.getOrElse("unnamed")
      val (_, ioResult) = connection.flow.runWith(
        Source.maybe,
        FileIO.toPath(folder.resolve(filename))
      )
      ioResult.onComplete {
        case Success(s) if s.status.isSuccess =>
          debug("File pulled.")
          sendCmdDone(remoteIP, remoteInfo.cmdPort)
          system.terminate()
        case _ => throw new IOException("file transfer or save failed.")
      }
    }

    //send out receive server's connection info.
    val remote = Tcp().outgoingConnection(remoteIP, remoteInfo.cmdPort)
    val pullInfo = {
      val info: Msg = RemoteInfo(NetworkUtil.getLocalIpAddress, cmdPort, Option(receivePort))
      Source.single(ByteString(Pickle.intoBytes(info)))
    }
    remote.runWith(pullInfo, Sink.ignore)
  }

  private def sendCmdDone(ip: String, port: Int): Unit = {
    Tcp().outgoingConnection(ip, port).runWith(
      Source.single(ByteString(Pickle.intoBytes(Done: Msg))),
      Sink.ignore
    )
  }

  private implicit class RemoteInfoEx(in: RemoteInfo) {
    def availableIP: String = in.ips.find(InetAddress.getByName(_).isReachable(300))
      .getOrElse(throw new SocketException("Cannot reach remote ip."))
  }
}
