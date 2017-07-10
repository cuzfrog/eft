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

    val flow = Flow[ByteString].flatMapConcat { s =>
      FileIO.fromPath(src, 256)
        .map(chunk => Msg.Payload(chunk.toArray)).concat(Source.single(Msg.PayLoadEnd))
        .map(_.toByteString)
    }

    server.runForeach { con =>
      con.handleWith(flow)
    }

    val tcpFlow = Tcp().outgoingConnection("127.0.0.1", 23888)
      .alsoTo(Sink.foreach(bs => println(s"tcp ~~> ${Msg.fromByteString(bs)}")))

    val (pub, tcpCon) = TestSource.probe[ByteString].viaMat(tcpFlow)(Keep.both)
      .viaMat(pullFlow)(Keep.left)
      .map(Msg.fromByteString)
      .toMat(Sink.foreach(msg => info(s" ~~> $msg")))(Keep.left).run

    pub.sendNext(ByteString(1))
    println(tcpCon.isCompleted)

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