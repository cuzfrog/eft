package com.github.cuzfrog.eft

import java.nio.file.Files
import java.util.concurrent.atomic.AtomicBoolean

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl._
import akka.stream.testkit.scaladsl.{TestSink, TestSource}
import akka.util.ByteString
import com.github.cuzfrog.eft.PushFlowTest.mockSystemIndicator
import org.junit._
import org.junit.Assert._
import org.hamcrest.CoreMatchers._

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.language.postfixOps


object PullFlowTest {
  private val system = ActorSystem("tmp")
  private val mockSystemIndicator = new AtomicBoolean(false)

  @BeforeClass
  def setup(): Unit = {
    mockSystemIndicator.set(true)
  }

  @AfterClass
  def teardown(): Unit = {
    system.terminate()
  }
}

class PullFlowTest {

  private val (config, content, file, destDir) = TestFileInitial.init
  private val dest = destDir.resolve(file.getFileName)

  private implicit val system = PullFlowTest.system
  private implicit val materializer = ActorMaterializer()
  private implicit val ec = system.dispatcher

  @volatile private var filename = "unnamed"
  @Before
  def resetFilename(): Unit = {
    filename = "unnamed"
  }

  private val flow = LoopTcpMan.constructPullFlow(destDir.resolve(filename),
    (fn: String) => filename = fn,
    (otherV: Array[Byte]) => println(ByteString(otherV).utf8String),
    msg => println(s"From command broadcast:$msg")
  )
  private val (pub, sub) = TestSource.probe[ByteString].via(flow).toMat(TestSink.probe)(Keep.both).run
  private val fileSrc = FileIO.fromPath(file, 32).map(bs => Payload(bs.toArray).toByteString)

  @Test
  def filenameTest(): Unit = {
    sub.request(1).expectNext(Ask.toByteString)
    pub.sendNext(Filename("some-name").toByteString)
    sub.request(1).expectNext(Acknowledge.toByteString)
    assert(filename == "some-name")
  }

  @Test
  def payloadTest(): Unit = {
    sub.request(1).expectNext(Ask.toByteString)
    val result = fileSrc.to(Sink.foreach(pub.sendNext)).run()
    result.onComplete(_ => pub.sendComplete())

    sub.request(1).expectNext(Done.toByteString)
    Thread.sleep(1000)
    assert(Files.readAllBytes(file) sameElements Files.readAllBytes(destDir.resolve(filename)))
  }
}
