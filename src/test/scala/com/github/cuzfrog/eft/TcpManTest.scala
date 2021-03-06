package com.github.cuzfrog.eft

import java.nio.file.Files
import java.util.concurrent.atomic.AtomicReference

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

  private val pushNodeRef = new AtomicReference[TcpMan]()
  private val pullNodeRef = new AtomicReference[TcpMan]()

  private def pushNode = pushNodeRef.get()
  private def pullNode = pullNodeRef.get()

  @Before
  def setup(): Unit = {
    FileUtils.cleanDirectory(destDir.toFile)
    pushNodeRef.set(TcpMan(config.copy(name = "push-node")))
    pullNodeRef.set(TcpMan(config.copy(name = "pull-node")))
  }

  @After
  def tearDown():Unit = {
    Option(pushNodeRef.get()).foreach(_.close())
    Option(pullNodeRef.get()).foreach(_.close())
    Thread.sleep(2000)
  }

  @Test
  def setPush(): Unit = {
    val codeInfo = pushNode.setPush(src)
    Thread.sleep(100)
    val result = pullNode.pull(codeInfo, destDir)

    Await.ready(result, 3 seconds)
    assert(Files.exists(dest))
    val destContent = Files.readAllBytes(dest)
    assert(content.getBytes sameElements destContent, "content inconsistent")
  }

  @Test
  def setPull(): Unit = {
    val codeInfo = pullNode.setPull(destDir)
    val result = pushNode.push(codeInfo, src)

    Await.ready(result, 3 seconds)
    assert(Files.exists(dest))
    val destContent = Files.readAllBytes(dest)

    println(content)
    println("--------------------------------------")
    println(new String(destContent))

    assert(content.getBytes sameElements destContent, "content inconsistent")
  }
}