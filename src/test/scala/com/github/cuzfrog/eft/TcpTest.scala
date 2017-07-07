package com.github.cuzfrog.eft

import java.nio.file.Files

import utest._
import utest.asserts.{RetryInterval, RetryMax}

import scala.concurrent.duration._
import scala.language.postfixOps

object TcpTest extends TestSuite {
  private val (config, content, src, destDir) = TestFileInitial.init
  private val dest = destDir.resolve(src.getFileName)

  val tests = this {
    'positive {
      val pushNode = TcpMan(config.copy(name = "push-node"))
      val pullNode = TcpMan(config.copy(name = "pull-node"))

      val codeInfo = pushNode.setPush(src)
      Thread.sleep(100)
      pullNode.pull(codeInfo, destDir)

      implicit val retryMax = RetryMax(1000.millis)
      implicit val retryInterval = RetryInterval(100.millis)
      eventually(Files.exists(dest))
      val destContent = Files.readAllBytes(dest)
      assert(content.getBytes sameElements destContent)
    }
  }
}