package com.github.cuzfrog.eft

import java.net.NetworkInterface

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
}