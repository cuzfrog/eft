package com.github.cuzfrog.eft

import java.nio.file.Files

import scala.concurrent.duration._
import scala.language.postfixOps

import org.junit._
import org.junit.Assert._
import org.hamcrest.CoreMatchers._

class TcpTest {
  private val (config, content, src, destDir) = TestFileInitial.init
  private val dest = destDir.resolve(src.getFileName)

  @Test
  def positive(): Unit = {
    val pushNode = TcpMan(config.copy(name = "push-node"))
    val pullNode = TcpMan(config.copy(name = "pull-node"))

    val codeInfo = pushNode.setPush(src)
    Thread.sleep(100)
    pullNode.pull(codeInfo, destDir)

    Thread.sleep(5000)
    assert(Files.exists(dest))
    val destContent = Files.readAllBytes(dest)
    assert(content.getBytes sameElements destContent)
  }
}