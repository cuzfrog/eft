package com.github.cuzfrog.eft

import java.nio.file.Files
import java.util.concurrent.atomic.AtomicReference

/**
  * Created by cuz on 7/3/17.
  */
private class EftMain(cmd: CommonOpt, config: Configuration) extends SimpleLogger {
  override val loggerAgent: String = "eft"

  override val loggerLevel = if (config.isDebug) SimpleLogger.Debug else SimpleLogger.Info

  private val tcpManRef = new AtomicReference[Option[TcpMan]](None)
  lazy val tcpMan = new TcpMan(config = config)

  def run(): Unit = try {
    cmd match {
      case push: Push =>
        val file = push.file.toPath
        if (Files.exists(file)) {
          tcpManRef.set(Option(tcpMan))
          val code = Msg.publishCode(tcpMan.push(file))
          info(s"Pull code: $code waiting to pull...", withTitle = false)
        }
        else err(s"File $file does not exist.")
      case pull: Pull =>
        val destDir = pull.destDir.toPath
        tcpManRef.set(Option(tcpMan))
        tcpMan.pull(Msg.fromCode(pull.code), destDir)
    }
  } catch {
    case e: Throwable =>
      err(s"Failed with msg:${e.getMessage}")
      if (config.isDebug) e.printStackTrace()
      tcpManRef.get().map(_.system.terminate())
      System.exit(1)
  }
}
