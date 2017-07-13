package com.github.cuzfrog.eft

import java.nio.file.{Files, Path, Paths}

import org.apache.commons.io.FileUtils

import scala.util.Random
import scala.concurrent.duration._
import scala.language.postfixOps

/**
  * Created by cuz on 7/7/17.
  */
private object TestFileInitial {
  def init: (Configuration, String, Path, Path) = {
    val content = Random.alphanumeric.take(512).mkString
    val src = Paths.get("/tmp/f1")
    Files.write(src, content.getBytes)
    val destDir = Paths.get("/tmp/d1")
    FileUtils.deleteDirectory(destDir.toFile)
    Files.createDirectories(destDir)

    val config = Configuration(
      isDebug = true,
      networkTimeout = 500 millis,
      pushChunkSize = 2048,
      fileSinkEagerComplete = true
    )
    (config, content, src, destDir)
  }
}
