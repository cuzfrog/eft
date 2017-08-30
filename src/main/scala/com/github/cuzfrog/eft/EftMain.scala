package com.github.cuzfrog.eft

import java.nio.file.Files

import arm._

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps

/**
  * Created by cuz on 7/3/17.
  */
private class EftMain(argDef: EftDef, config: Configuration) extends SimpleLogger {
  override val loggerAgent: String = "eft"
  override val loggerLevel: SimpleLogger.Level =
    if (config.isDebug) SimpleLogger.Debug else SimpleLogger.Info

  private lazy val tcpMan: TcpMan = TcpMan(config = config)

  import Scmd.scmdValueConverter._

  def run(): Unit = try {
    if (argDef.push.met) {
      val file = argDef.file.value.toPath
      if (Files.exists(file)) {
        argDef.remoteNode.value match {
          case Some(addrOrCode) =>
            val cInfo = RemoteInfo.fromAddressOrCode(addrOrCode)
            tcpMan.autoClosed.foreach { tm =>
              val result = Await.result(tm.push(cInfo, file), Duration.Inf)
              println(result.getOrElse("Done."))
            }
          case None => tcpMan.autoClosed.foreach { tcpman =>
            val cInfo = tcpman.setPush(file)
            printConnectionInfo(argDef, cInfo)
            await(() => !tcpman.isClosed)
          }
        }
      }
      else err(s"File $file does not exist.")
    }
    else if (argDef.pull.met) {
      val destDir = argDef.destDir.value.toPath
      argDef.remoteNode.value match {
        case Some(addrOrCode) =>
          tcpMan.autoClosed.foreach { tm =>
            val result = Await.result(tm.pull(RemoteInfo.fromAddressOrCode(addrOrCode), destDir), Duration.Inf)
            println(result.getOrElse("Done."))
          }
        case None => tcpMan.autoClosed.foreach { tcpman =>
          val cInfo = tcpman.setPull(destDir)
          printConnectionInfo(argDef, cInfo)
          await(() => !tcpman.isClosed)
        }
      }
    }
  } catch {
    case e: Throwable =>
      err(s"Failed with msg:${e.getMessage}")
      if (config.isDebug) e.printStackTrace()
  }

  private def await(continueToWait: () => Boolean, checkIntervalInMilli: Int = 100): Unit = {
    while (continueToWait()) Thread.sleep(checkIntervalInMilli)
    println("Complete.")
  }

  private def printConnectionInfo(argDef: EftDef, cInfo: RemoteInfo): Unit = {
    val code =
      if (argDef.printCode.value) RemoteInfo.publishCode(cInfo) else RemoteInfo.publishAddress(cInfo)
    info(s"Connection info: $code listening...", withTitle = false)
  }
}
