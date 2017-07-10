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
    Some { (otherV: Array[Byte]) =>
      println(ByteString(otherV).utf8String)
    }
  )
  private def newPubSub[M](flow: Flow[ByteString, ByteString, M]) =
    TestSource.probe[ByteString].via(flow).toMat(TestSink.probe)(Keep.both).run
  private val fileSrc = FileIO.fromPath(file, 64).map(bs => Payload(bs.toArray).toByteString)

  //----------- tests ------------
  @Test
  def askToPush(): Unit = {
    val (pub, sub) = newPubSub(pushFlow)
    pub.sendNext(Ask.toByteString)
    sub.request(1).expectNext(Filename(file.getFileName.toString).toByteString)
  }

  @Test
  def ackToPush(): Unit = {
    val (pub, sub) = newPubSub(pushFlow)
    pub.sendNext(Acknowledge.toByteString)
    sub.request(Long.MaxValue)
    val receivedSeq = sub.receiveWithin(max = 3 seconds).map(Msg.fromByteString)
    assert(receivedSeq.last == PayLoadEnd)
    val receivedContent = receivedSeq.collect { case Payload(v) => v }.reduce((p1, p2) => p1 ++ p2)
    assertArrayEquals(Files.readAllBytes(file), receivedContent)
  }

  @Test
  def byeToPush(): Unit = {
    assertEquals(true, mockSystemIndicator.get())
    val (pub, sub) = newPubSub(pushFlow)
    pub.sendNext(Bye.toByteString)
    sub.request(1).expectNoMsg()
    assertEquals(false, mockSystemIndicator.get())
  }

  @Test
  def filenameToPull(): Unit = {
    val (pub, sub) = newPubSub(pullFlow)
    sub.request(1).expectNext(Ask.toByteString)
    pub.sendNext(Filename("some-name").toByteString)
    sub.request(1).expectNext(Acknowledge.toByteString)
    assert(filenameRef.get() == "some-name")
  }

  @Test
  def payloadToPull(): Unit = {
    val (ioResult, sub) = fileSrc.viaMat(pullFlow)(Keep.right)
      .toMat(TestSink.probe)(Keep.both).run //send a complete after reading file.
    sub.request(1).expectNext(Ask.toByteString) //init msg
    sub.request(1).expectNext(Bye.toByteString)
    //    println(content)
    //    val destContent = new String(Files.readAllBytes(destDir.resolve(filename)))
    //    println("--------------------------------------")
    //    println(destContent)
    Await.ready(ioResult.flatten, 3 seconds)
    assert(
      Files.readAllBytes(file) sameElements Files.readAllBytes(destDir.resolve(filenameRef.get()))
    )
  }

  @Test
  def integrationTest(): Unit = {
    def printFlow(pr: String) = Flow[ByteString].map(Msg.fromByteString)
      .alsoTo(Sink.foreach(msg => println(s"$pr$msg"))).map(_.toByteString)
    val result = printFlow("push-in - ").via(pushFlow)
      .joinMat(printFlow("pull-in - ").viaMat(pullFlow)(Keep.right))(Keep.right).run()

    Await.ready(result.flatten, 3 seconds)
    val destContent = Files.readAllBytes(dest)
    assert(content.getBytes sameElements destContent)
  }
}
