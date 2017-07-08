package com.github.cuzfrog.eft

import java.nio.ByteBuffer

import akka.util.ByteString
import boopickle.Default._

import scala.util.Try

private case class RemoteInfo(ips: Seq[String], port: Int) extends Msg

private sealed trait Msg {
  /** Serialize into ByteString with head. */
  val toByteString: ByteString = {
    ByteString(Msg.HEAD.toArray) ++ ByteString(Pickle.intoBytes(this))
  }
}

private object Msg {

  case object Ask extends Msg
  case object Acknowledge extends Msg
  case object Done extends Msg
  case object Empty extends Msg {
    override val toByteString: ByteString = ByteString.empty
  }
  case class Payload(v: Array[Byte]) extends Msg
  case class Filename(v: String) extends Msg
  case class Other(v: Array[Byte]) extends Msg {
    override val toByteString: ByteString = ByteString(v)
  }

  val HEAD = "[eft-msg]".getBytes.to[collection.immutable.Seq]
  val PAYLOAD = "[eft-payload]".getBytes.to[collection.immutable.Seq]
  val BadMsg = Other("[eft]Bad msg stream, which cannot be parsed.".getBytes)

  private def fromByteBuffer(bb: ByteBuffer): Option[Msg] =
    Try(Unpickle[Msg].fromBytes(bb)).toOption

  /** Deserialize ByteString which contains head. */
  def fromByteString(bs: ByteString): Msg = {
    if (bs.startsWith(HEAD)) this.fromByteBuffer(bs.drop(Msg.HEAD.length).toByteBuffer).getOrElse(BadMsg)
    else if (bs.isEmpty) Empty else Other(bs.toArray)
  }

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