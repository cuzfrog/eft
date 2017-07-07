package com.github.cuzfrog.eft

import java.nio.ByteBuffer

import akka.util.ByteString
import boopickle.Default._

import scala.util.Try

private sealed trait Msg {
  /** Serialize into ByteString with head. */
  def toByteString: ByteString = {
    ByteString.createBuilder
      .putBytes(Msg.HEAD.toArray)
      .putBytes(Pickle.intoBytes(this).array()).result()
  }
}
private case class RemoteInfo(ips: Seq[String], port: Int) extends Msg
private case class Filename(v: String) extends Msg
private case object Ask extends Msg
private case object Acknowledge extends Msg
private case object Done extends Msg

private object Msg {
  val HEAD = "[eft-msg]".getBytes.to[collection.immutable.Seq]
  val PAYLOAD = "[eft-payload]".getBytes.to[collection.immutable.Seq]

  private def fromByteBuffer(bb: ByteBuffer): Option[Msg] =
    Try(Unpickle[Msg].fromBytes(bb)).toOption

  /** Deserialize ByteString which contains head. */
  def fromByteString(bs: ByteString): Option[Msg] =
    this.fromByteBuffer(bs.drop(Msg.HEAD.length).toByteBuffer)

  def publishCode(info: RemoteInfo): String = {
    val port = "%04X".format(info.port)
    val ips = info.ips.map { ip =>
      ip.split("""\.""").map(section => "%02X".format(section.toInt)).reduce(_ + _)
    }.reduce(_ + "-" + _)
    port + "-" + ips
  }

  def publishAddress(info: RemoteInfo): String = {
    info.ips.map { ip =>
      s"$ip:${info.port}"
    }.mkString("|")
  }

  def fromAddressOrCode(addrOrCode: String): RemoteInfo = {
    val trimmed = addrOrCode.trim
    if (addrOrCode.contains(":")) fromAddress(trimmed) else fromCode(trimmed)
  }

  private def fromCode(code: String): RemoteInfo = try {
    require(code.matches("""[\d\w]{4}\-[\d\w]{8}(\-[\d\w]{8})?"""))
    val codes = code.split("""\-""").toSeq
    val port = Integer.parseInt(codes.head, 16)
    val ips = codes.tail.map { section =>
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