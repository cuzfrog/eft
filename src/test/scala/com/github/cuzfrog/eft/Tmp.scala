package com.github.cuzfrog.eft

import java.net.InetAddress

import akka.actor.ActorSystem
import java.nio.file.Paths

import akka.stream._
import akka.stream.scaladsl.Tcp.{IncomingConnection, ServerBinding}
import akka.stream.scaladsl._
import akka.util.ByteString

import scala.concurrent.Future

/**
  * Created by cuz on 7/3/17.
  */
object Tmp extends App {
  implicit val system = ActorSystem("tmp")
  implicit val materializer = ActorMaterializer()
  implicit val ec = system.dispatcher

  private val host = "0.0.0.0"
  private val port = 8888

  val server: Source[IncomingConnection, Future[ServerBinding]] = Tcp().bind(host, port)

  server runForeach { connection =>
    println(s"Local address:${connection.localAddress}")
    println(s"New connection from: ${connection.remoteAddress}")
    connection.flow.map(_.utf8String)
      .runWith(Source.maybe, Sink.foreach(println))
  }

  InetAddress.getByName(host).isReachable(300)
}
