package com.github.cuzfrog.eft

private sealed trait Msg
private case class RemoteInfo(ips: Seq[String], cmdPort: Int,
                              receivePort: Option[Int] = None,
                              filename: Option[String] = None) extends Msg
private case object Ask extends Msg
private case object Hello extends Msg
private case object Done extends Msg

private object Msg {
  def publishCode(info: RemoteInfo): String = {
    val port = "%04X".format(info.cmdPort)
    val ips = info.ips.flatMap { ip =>
      ip.split("""\.""").map(section => "%02X".format(section.toInt))
    }.reduce(_ + _)
    port + ips
  }

  def fromCode(code: String): RemoteInfo = try {
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
}