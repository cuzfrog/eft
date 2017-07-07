package com.github.cuzfrog.eft

import java.nio.file.Path

import scala.language.postfixOps

/**
  * Stream tcp utility interface.
  */
private trait TcpMan {

  /** Setup push listening and return push node connection info. */
  def setPush(file: Path): RemoteInfo

  /** Connect pull node and push file. */
  def push(codeInfo: RemoteInfo, file: Path): Unit

  /** Setup pull listening and return pull node connection info. */
  def setPull(folder: Path): RemoteInfo

  /** Connect push node and pull file. */
  def pull(codeInfo: RemoteInfo, folder: Path): Unit

  /** Terminate system and shutdown service.*/
  def close(): Unit
}

private object TcpMan {
  def apply(config: Configuration): TcpMan = new LoopTcpMan(config)
}