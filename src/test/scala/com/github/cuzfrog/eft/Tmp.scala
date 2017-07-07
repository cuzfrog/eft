package com.github.cuzfrog.eft

import java.net.InetAddress

import akka.actor.ActorSystem
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

  try {
    val promise = Promise[Source[Int, NotUsed]]

    val src = Source.single(-1).filter(_ > 0)
    val flow = {
      flowWithExtraSource(promise.future)
    }
    val sink = Sink.foreach(println)
    val result = src.via(flow).to(sink).run()

    promise.success(Source.single(2))
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
