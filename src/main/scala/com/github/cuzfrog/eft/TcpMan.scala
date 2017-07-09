package com.github.cuzfrog.eft

import java.nio.file.Path

import scala.language.postfixOps
import Msg._

import scala.concurrent.Future

/**
  * Stream tcp utility interface.
  */
private trait TcpMan {

  /** Setup push listening and return push node connection info. */
  def setPush(file: Path): RemoteInfo

  /** Connect pull node and push file. If failed return an Option of failing msg.*/
  def push(codeInfo: RemoteInfo, file: Path): Future[Option[String]]

  /** Setup pull listening and return pull node connection info. */
  def setPull(destDir: Path): RemoteInfo

  /** Connect push node and pull file. If failed return an Option of failing msg. */
  def pull(codeInfo: RemoteInfo, destDir: Path): Future[Option[String]]

  /** Terminate system and shutdown service. */
  def close(): Unit
}

private object TcpMan {
  def apply(config: Configuration): TcpMan = new LoopTcpMan(config)
}