package com.github.cuzfrog.eft

import java.net.InetAddress

import akka.actor.{Actor, ActorSystem, Props}
import java.nio.file.Paths

import akka.NotUsed
import akka.io.Inet
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
object Tmp extends App with SimpleLogger {
  implicit val system = ActorSystem("tmp")
  implicit val materializer = ActorMaterializer()
  implicit val ec = system.dispatcher

  private val (config, content, src, destDir) = TestFileInitial.init
  private val pullFlow = LoopTcpMan.constructPullFlow(
    () => {
      val fn = destDir.resolve("f1")
      println(s"get filename [$fn] from ref")
      fn
    },
    (fn: String) => {
      println(s"store filename [$fn] to ref")
    },
    Some { (otherV: Array[Byte]) =>
      println(ByteString(otherV).utf8String)
    }
  )

  private lazy val server = Tcp().bind("0.0.0.0", 23888, halfClose = true)

  try {
    val flow = Flow[ByteString].flatMapConcat(bs =>
        Source(1 to 9).map(i=> ByteString(i.toString))
          .concat(Source.single(ByteString("end")))
    ).map { bs => println(s"server ~~> ${bs.utf8String}"); bs }

    val flow2 = Flow[ByteString].merge(
      Source(1 to 9).map(i=> ByteString(i.toString))
        .concat(Source.single(ByteString("end")))
    ).map { bs => println(s"server ~~> ${bs.utf8String}"); bs }

    server.runForeach { con =>
      con.handleWith(flow2)
    }


    val tcpFlow = Tcp().outgoingConnection("127.0.0.1", 23888)
      //.map { bs => println(s"server ~~> ${bs.utf8String}"); bs }

    Source.single(ByteString.empty).via(tcpFlow).map(_.utf8String)
      .to(Sink.foreach(msg => println(s"client <~~ $msg"))).run

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