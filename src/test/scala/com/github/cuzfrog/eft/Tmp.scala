package com.github.cuzfrog.eft

import java.net.InetAddress

import akka.actor.{Actor, ActorSystem, Props}
import java.nio.file.Paths

import akka.NotUsed
import akka.stream._
import akka.stream.scaladsl.Tcp.{IncomingConnection, ServerBinding}
import akka.stream.scaladsl._
import akka.util.ByteString

import scala.concurrent.{Future, Promise}
import arm._

/**
  * Created by cuz on 7/3/17.
  */
object Tmp extends App {
  implicit val system = ActorSystem("tmp")
  implicit val materializer = ActorMaterializer()
  implicit val ec = system.dispatcher

  private val (config, content, src, destDir) = TestFileInitial.init

  try {
    val source = FileIO.fromPath(src, chunkSize = 1)
    source.map(println).to(Sink.ignore).run

  } finally {
    Thread.sleep(2000)
    system.terminate()
  }

  private def circleFlow[I, O, T](flowToCircle: Flow[I, O, T], loopFlow: Flow[O, I, T]) =
    Flow.fromGraph(GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val leftMerge = builder.add(MergePreferred[I](1))
      val rightBcast = builder.add(Broadcast[O](2))

      leftMerge ~> flowToCircle ~> rightBcast
      leftMerge.preferred <~ loopFlow <~ rightBcast


      FlowShape(leftMerge.in(0), rightBcast.out(1))
    })

}

private class Receiver extends Actor {
  override def receive: Receive = {
    case msg => println(msg)
  }
}