package com.github.cuzfrog.eft

private sealed trait Msg
private case class RemoteInfo(ips: Seq[String], port: Int,
                              receivePort: Option[Int] = None,
                              filename: Option[String] = None) extends Msg
private case object Ask extends Msg
private case object Hello extends Msg
private case object Done extends Msg

private object Msg {
  def publishCode(info: RemoteInfo): String = {
    val port = "%04X".format(info.port)
    val ips = info.ips.flatMap { ip =>
      ip.split("""\.""").map(section => "%02X".format(section.toInt))
    }.reduce(_ + _)
    port + ips
  }

  def publishAddress(info: RemoteInfo): String = {
    info.ips.map { ip =>
      s"$ip:${info.port}"
    }.mkString("|")
  }

  def fromAddressOrCode(addrOrCode: String): RemoteInfo = {
    if (addrOrCode.contains(":")) fromAddress(addrOrCode) else fromCode(addrOrCode)
  }

  private def fromCode(code: String): RemoteInfo = try {
    val trimmed = code.trim
    val port = Integer.parseInt(trimmed.take(4), 16)
    val ips = trimmed.drop(4).grouped(8).map { section =>
      require(section.length == 8, "Malformed remote code.")
      section.grouped(2).map(Integer.parseInt(_, 16).toString).reduce(_ + "." + _)
    }.toVector
    RemoteInfo(ips, port)
  } catch {
    case e@(_: NumberFormatException | _: IllegalArgumentException) =>
      throw new IllegalArgumentException(s"Bad remote code:$code")
  }

  private def fromAddress(address: String): RemoteInfo = {
    require(address.matches("""\d+\.\d+\.\d+\.\d+:\d+"""), s"Malformed address:$address")
    val Seq(ip, port) = address.split(""":""").toSeq
    RemoteInfo(Seq(ip), port.toInt)
  }
}