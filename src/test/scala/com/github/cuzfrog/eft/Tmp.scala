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

  val receiver = system.actorOf(Props[Receiver], "tmp-receiver")
  try {
    val mockTcpFlow = Flow[Int].map(_ + 1)

    val source = Source.single(0)

    source.via(mockTcpFlow).map(receiver ! _).to(Sink.ignore).run()

  } finally {
    Thread.sleep(2000)
    system.terminate()
  }


  private def flowWithExtraSource[S, T](sourceFuture: Future[Source[S, T]]) =
    Flow.fromGraph(GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._
      val source = Source.fromFutureSource(sourceFuture)
      val merge = builder.add(Merge[S](2))
      source ~> merge.in(1)
      FlowShape(merge.in(0), merge.out)
    })

}

private class Receiver extends Actor {
  override def receive: Receive = {
    case msg => println(msg)
  }
}