package com.github.cuzfrog.eft

/**
  * Created by cuz on 7/3/17.
  */
private case class RemoteInfo(ips: Seq[String], port: Int, filename: Option[String] = None)
private case object Ack