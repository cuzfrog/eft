package com.github.cuzfrog.eft

import java.net.InetAddress

import akka.actor.{Actor, ActorSystem, Props}
import java.nio.file.Paths

import akka.NotUsed
import akka.stream._
import akka.stream.scaladsl.Tcp.{IncomingConnection, ServerBinding}
import akka.stream.scaladsl._
import akka.stream.testkit.scaladsl.{TestSink, TestSource}
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
    val flow = Flow[String].flatMapConcat { s =>
      FileIO.fromPath(src,32)
        .map(chunk => Msg.Payload(chunk.toArray)).concat(Source.single(Msg.PayLoadEnd))
    }

    val pub = TestSource.probe[String].via(flow).to(Sink.foreach(println)).run

    pub.sendNext("init")
  } finally {
    Thread.sleep(2000)
    system.terminate()
  }

  private def circleFlow[I, O, T](flowToCircle: Flow[I, O, T], loopFlow: Flow[O, I, T], initSrc: Source[I, T]) =
    RunnableGraph.fromGraph(GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val leftMerge = builder.add(MergePreferred[I](1))

      val lf = loopFlow.shape

      initSrc ~> leftMerge ~> flowToCircle ~> lf.in
      leftMerge.preferred <~ lf.out

      ClosedShape
    })

}

private class Receiver extends Actor {
  override def receive: Receive = {
    case msg => println(msg)
  }
}