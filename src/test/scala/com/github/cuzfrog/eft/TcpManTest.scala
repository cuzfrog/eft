package com.github.cuzfrog.eft

import java.nio.file.Files

import org.apache.commons.io.FileUtils

import scala.concurrent.duration._
import scala.language.postfixOps
import org.junit._
import org.junit.Assert._
import org.hamcrest.CoreMatchers._

import scala.concurrent.Await

class TcpManTest {
  private val (config, content, src, destDir) = TestFileInitial.init
  private val dest = destDir.resolve(src.getFileName)

  @Before
  def setup(): Unit = {
    FileUtils.cleanDirectory(destDir.toFile)
  }

  @Test
  def setPush(): Unit = {
    val pushNode = TcpMan(config.copy(name = "push-node1"))
    val pullNode = TcpMan(config.copy(name = "pull-node1"))

    val codeInfo = pushNode.setPush(src)
    Thread.sleep(100)
    val result = pullNode.pull(codeInfo, destDir)

    Await.ready(result, 3 seconds)
    assert(Files.exists(dest))
    val destContent = Files.readAllBytes(dest)
    assert(content.getBytes sameElements destContent)
  }

  @Test
  def setPull(): Unit = {
    val pushNode = TcpMan(config.copy(name = "push-node2"))
    val pullNode = TcpMan(config.copy(name = "pull-node2"))

    val codeInfo = pullNode.setPull(destDir)
    val result = pushNode.push(codeInfo, src)
    Await.ready(result, 3 seconds)
    assert(Files.exists(dest))
    val destContent = Files.readAllBytes(dest)

    println(content)
    println("--------------------------------------")
    println(new String(destContent))

    assert(content.getBytes sameElements destContent)
  }
}