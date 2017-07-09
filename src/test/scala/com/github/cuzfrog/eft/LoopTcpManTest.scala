package com.github.cuzfrog.eft

import java.nio.file.Files
import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl._
import akka.stream.testkit.scaladsl.{TestSink, TestSource}
import akka.util.ByteString
import org.apache.commons.io.FileUtils
import org.junit._
import org.junit.Assert._
import org.hamcrest.CoreMatchers._

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.language.postfixOps
import Msg._

object LoopTcpManTest {

  private val system = ActorSystem("tmp")

  @BeforeClass
  def setup(): Unit = {

  }

  @AfterClass
  def teardown(): Unit = {
    system.terminate()
  }
}

class LoopTcpManTest {

  //----------- provision ------------
  private val (config, content, file, destDir) = TestFileInitial.init
  private val dest = destDir.resolve(file.getFileName)

  private implicit val system = LoopTcpManTest.system
  private implicit val materializer = ActorMaterializer()
  private implicit val ec = system.dispatcher


  //----------- push flow ------------
  private val mockSystemIndicator = new AtomicBoolean(false)
  private val pushFlow = LoopTcpMan.constructPushFlow(
    file = file,
    () => { //mock
      mockSystemIndicator.set(false)
      println("system terminated(mock).")
    },
    chunkSize = 128
  )

  private implicit class TestDsl(in: ByteString) {
    def -->(expectedOut: ByteString): Unit = {
      val result = this.throughStream
      if (expectedOut != result) println(Msg.fromByteString(result))

      assert(expectedOut == result)
    }

    def throughStream: ByteString = {
      val sink = Sink.fold[ByteString, ByteString](ByteString.empty)(_ ++ _)
      val src = Source.single(in)
      val extractPayload = Flow[ByteString].map { bs: ByteString =>
        Msg.fromByteString(bs) match {
          case Payload(v) => ByteString(v)
          case _ => bs
        }
      }

      val result = src.via(pushFlow).via(extractPayload).toMat(sink)(Keep.right).run()
      Await.result(result, 3 seconds)
    }
  }

  //----------- pull flow ------------
  private val filenameRef = new AtomicReference("unnamed")
  @Before
  def setup(): Unit = {
    mockSystemIndicator.set(true)
    filenameRef.set("unnamed")
    FileUtils.cleanDirectory(destDir.toFile)
  }

  private val pullFlow = LoopTcpMan.constructPullFlow(
    () => {
      val fn = destDir.resolve(filenameRef.get)
      println(s"get filename [$fn] from ref")
      fn
    },
    (fn: String) => {
      filenameRef.set(fn)
      println(s"store filename [$fn] to ref")
    },
    (otherV: Array[Byte]) => println(ByteString(otherV).utf8String),
    Option(msg => ()) //println(s"From command broadcast:$msg")
  )
  private def newPullPubSub = TestSource.probe[ByteString].via(pullFlow).toMat(TestSink.probe)(Keep.both).run
  private val fileSrc = FileIO.fromPath(file, 64).map(bs => Payload(bs.toArray).toByteString)

  //----------- tests ------------
  @Test
  def askToPush(): Unit = {
    Ask.toByteString --> Filename(file.getFileName.toString).toByteString
  }

  @Test
  def ackToPush(): Unit = {
    Acknowledge.toByteString --> ByteString(Files.readAllBytes(file))
  }

  @Test
  def doneToPush(): Unit = {
    assertEquals(true, mockSystemIndicator.get())
    Done.toByteString --> ByteString.empty
    assertEquals(false, mockSystemIndicator.get())
  }

  @Test
  def filenameToPull(): Unit = {
    val (pub, sub) = newPullPubSub
    sub.request(1).expectNext(Ask.toByteString)
    pub.sendNext(Filename("some-name").toByteString)
    sub.request(1).expectNext(Acknowledge.toByteString)
    assert(filenameRef.get() == "some-name")
  }

  @Test
  def payloadTest(): Unit = {
    val (_, sub) = fileSrc.via(pullFlow).toMat(TestSink.probe)(Keep.both).run
    sub.request(1).expectNext(Ask.toByteString)
    sub.request(1).expectNext(Done.toByteString)
    //    println(content)
    //    val destContent = new String(Files.readAllBytes(destDir.resolve(filename)))
    //    println("--------------------------------------")
    //    println(destContent)
    assert(Files.readAllBytes(file) sameElements Files.readAllBytes(destDir.resolve(filenameRef.get())))
  }


  @Test
  def integrationTest(): Unit = {
    def printFlow(pr: String) = Flow[ByteString].map(Msg.fromByteString)
      .alsoTo(Sink.foreach(msg => println(s"$pr$msg"))).map(_.toByteString)
    val result = printFlow("push-in - ").via(pushFlow).join(printFlow("pull-in - ").via(pullFlow)).run()

    Thread.sleep(3000)
    val destContent = Files.readAllBytes(dest)
    assert(content.getBytes sameElements destContent)
  }
}
