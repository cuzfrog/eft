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

  private var filename = "unnamed"

  private val flow = LoopTcpMan.constructPullFlow(destDir.resolve(filename),
    (fn: String) => filename = fn,
    (otherV: Array[Byte]) => println(ByteString(otherV).utf8String)
  )

  
}
