package com.github.cuzfrog.eft

import java.nio.file.Paths

private object PushTest extends App {
  private val remoteInfo = TcpMan.push(Paths.get("/tmp/f1"))
  println(s"Pushed:$remoteInfo")
}

private object PullTest extends App {
  val cmdPort = scala.io.StdIn.readLine("port? >").toInt
  TcpMan.pull(RemoteInfo(Seq("127.0.0.1"), cmdPort), Paths.get("/tmp/d1"))
}