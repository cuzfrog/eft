package com.github.cuzfrog.eft

import java.nio.file.Files

import arm._

/**
  * Created by cuz on 7/3/17.
  */
private class EftMain(cmd: CommonOpt, config: Configuration) extends SimpleLogger {
  override val loggerAgent: String = "eft"
  override val loggerLevel = if (config.isDebug) SimpleLogger.Debug else SimpleLogger.Info

  private lazy val tcpMan: TcpMan = TcpMan(config = config)


  def run(): Unit = try {
    cmd match {
      case push: Push =>
        val file = push.file.toPath
        if (Files.exists(file)) {
          push.address match {
            case Some(addrOrCode) =>
              val cInfo = RemoteInfo.fromAddressOrCode(addrOrCode)
              tcpMan.autoClosed.foreach(_.push(cInfo, file))
            case None => tcpMan.autoClosed.foreach { tcpman =>
              val cInfo = tcpman.setPush(file)
              printConnectionInfo(cmd, cInfo)
            }
          }
        }
        else err(s"File $file does not exist.")
      case pull: Pull =>
        val destDir = pull.destDir.toPath
        pull.address match {
          case Some(addrOrCode) =>
            tcpMan.autoClosed.foreach(_.pull(RemoteInfo.fromAddressOrCode(addrOrCode), destDir))
          case None => tcpMan.autoClosed.foreach { tcpman =>
            val cInfo = tcpman.setPull(destDir)
            printConnectionInfo(cmd, cInfo)
          }
        }
    }
  } catch {
    case e: Throwable =>
      err(s"Failed with msg:${e.getMessage}")
      if (config.isDebug) e.printStackTrace()
  }

  private def printConnectionInfo(cmd: CommonOpt, cInfo: RemoteInfo): Unit = {
    val code =
      if (cmd.printCode) RemoteInfo.publishCode(cInfo) else RemoteInfo.publishAddress(cInfo)
    info(s"Connection code: $code listening...", withTitle = false)
  }
}
