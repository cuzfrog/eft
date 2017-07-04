package com.github.cuzfrog.eft

sealed trait Msg
private case class RemoteInfo(ips: Seq[String], cmdPort: Int,
                              receivePort: Option[Int] = None,
                              filename: Option[String] = None) extends Msg
//private case object Ack extends Msg
private case object Done extends Msg