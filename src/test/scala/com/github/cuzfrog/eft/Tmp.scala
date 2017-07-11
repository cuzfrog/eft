package com.github.cuzfrog.eft

import java.net.InetAddress
import java.nio.{ByteBuffer, ByteOrder}

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
  private val pullFlow = LoopTcpMan.constructSymmetricFlow(
    nodeType = Node.Pull,
    getPath = () => {
      val fn = destDir.resolve("f1")
      println(s"get filename [$fn] from ref")
      fn
    },
    saveFilenameF = (fn: String) => {
      println(s"store filename [$fn] to ref")
    }
  )

  def short2Bytes(sh: Short): Array[Byte] = {
    val b0 = (sh >> 8).toByte
    val b1 = sh.toByte
    Array(b1, b0)
  }

  private val FramingBidi = BidiFlow.fromFlows(
    Framing.lengthField(fieldLength = 2, maximumFrameLength = Short.MaxValue).map(_.drop(2)),
    Flow[ByteString].map(bs => ByteString(short2Bytes(bs.length.toShort)) ++ bs)
  )
  private lazy val server = Tcp().bind("0.0.0.0", 23888, halfClose = true)

  try {
    val flow2 = Flow[ByteString].merge(
      FileIO.fromPath(src, 2048)
        .concat(Source.single(ByteString("end")))
    )
      .map { bs => println(s"server ~~> ${bs.utf8String}"); bs }
      .join(FramingBidi.reversed)

    server.runForeach { con =>
      con.handleWith(flow2)
    }


    val tcpFlow = Tcp().outgoingConnection("127.0.0.1", 23888)
      .map { bs => println(s"tcp-h size ${bs.size}"); bs }
      .join(FramingBidi)
      .map { bs => println(s"tcp-d size ${bs.size}"); bs }
    //

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