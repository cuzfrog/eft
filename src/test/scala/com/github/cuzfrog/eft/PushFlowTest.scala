package com.github.cuzfrog.eft

import java.nio.file.Files
import java.util.concurrent.atomic.AtomicBoolean

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl._
import akka.util.ByteString
import com.github.cuzfrog.eft.PushFlowTest.mockSystemIndicator
import org.junit._
import org.junit.Assert._
import org.hamcrest.CoreMatchers._

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.language.postfixOps

object PushFlowTest {

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

class PushFlowTest {
  private val (config, content, file, destDir) = TestFileInitial.init
  private val dest = destDir.resolve(file.getFileName)

  private implicit val system = PushFlowTest.system
  private implicit val materializer = ActorMaterializer()
  private implicit val ec = system.dispatcher

  private val flow = LoopTcpMan.constructPushFlow(
    file = file,
    () => { //mock
      mockSystemIndicator.set(false)
      println("system terminated.")
    }
  )

  private implicit class TestDsl(in: ByteString) {
    def -->(expectedOut: ByteString): Unit = {
      assert(expectedOut == this.throughStream)
    }

    def throughStream: ByteString = {
      val sink: Sink[ByteString, Future[ByteString]] =
        Sink.fold(ByteString.empty)(_ ++ _)
      val src = Source.single(in)
      val result = src.via(flow).toMat(sink)(Keep.right).run()
      Await.result(result, 3 seconds)
    }
  }

  @Test
  def ask(): Unit = {
    Ask.toByteString --> Filename(file.getFileName.toString).toByteString
  }

  @Test
  def ack(): Unit = {
    //println("r:" + Acknowledge.toByteString.throughStream.utf8String)
    Acknowledge.toByteString --> ByteString(Files.readAllBytes(file))
  }

  @Test
  def done(): Unit = {
    assertEquals(mockSystemIndicator.get(), true)
    Done.toByteString --> ByteString.empty
    assertEquals(mockSystemIndicator.get(), false)
  }
}
