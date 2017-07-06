package com.github.cuzfrog.eft

import java.net.{InetSocketAddress, NetworkInterface, ServerSocket, Socket}
import java.io.IOException

/**
  * Created by cuz on 7/3/17.
  */
object NetworkUtil {
  def getLocalIpAddress: Seq[String] = {
    import scala.collection.JavaConverters._
    val enumeration = NetworkInterface.getNetworkInterfaces.asScala.toVector

    val ipAddresses = enumeration.flatMap(p =>
      p.getInetAddresses.asScala.toSeq
    )

    val address = ipAddresses.filter { address =>
      val host = address.getHostAddress
      host.contains(".") && !address.isLoopbackAddress
    }.toList
    address.map(_.getHostAddress)
  }

  def freeLocalPort: Int = try {
    val serverSocket = new ServerSocket(0)
    val port = serverSocket.getLocalPort
    serverSocket.close()
    port
  } catch {
    case e: IOException =>
      throw new IllegalStateException(e)
  }

  def checkPortReachable(ip: String, port: Int, timeoutInMilliSec: Int = 500): Boolean = {
    val s = new Socket()
    try {
      s.setReuseAddress(true)
      val sa = new InetSocketAddress(ip, port)
      s.connect(sa, timeoutInMilliSec)
      s.isConnected
    } catch {
      case e: IOException => false
    } finally {
      if (s.isConnected) s.close()
    }
  }
}
