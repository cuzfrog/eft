package com.github.cuzfrog.eft

import java.nio.file.{Files, Paths}

import com.typesafe.config.ConfigFactory
import org.apache.commons.io.FileUtils
import utest._
import utest.asserts.{RetryInterval, RetryMax}

import scala.concurrent.duration._
import scala.util.Random

object TcpTest extends TestSuite {
  private val content = Random.alphanumeric.take(512).mkString
  private val src = Paths.get("/tmp/f1")
  Files.write(src, content.getBytes)
  private val destDir = Paths.get("/tmp/d1")
  FileUtils.deleteDirectory(destDir.toFile)
  Files.createDirectories(destDir)
  private val dest = destDir.resolve(src.getFileName)

  private val config = Configuration()

  val tests = this {
    'positive {
      val pushNode = new TcpMan("pushNode", config)
      val pullNode = new TcpMan("pullNode", config)

      val codeInfo = pushNode.push(src)
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