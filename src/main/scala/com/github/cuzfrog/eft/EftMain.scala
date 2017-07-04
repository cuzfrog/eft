package com.github.cuzfrog.eft

import java.nio.file.{Files, Paths}
import java.util.concurrent.atomic.AtomicReference

/**
  * Created by cuz on 7/3/17.
  */
private class EftMain(args: Array[String]) extends SimpleLogger {
  override val loggerAgent: String = "eft"
  implicit private val configuration: Configuration = try {
    new Configuration()
  } catch {
    case e: Exception =>
      err(e.getMessage)
      System.exit(1)
      throw new AssertionError
  }
  override val loggerLevel = if (configuration.isDebug) SimpleLogger.Debug else SimpleLogger.Info
  private lazy val version: String = getClass.getPackage.getImplementationVersion
  private def printHelp(): Unit = {
    println(s"eft. version: $version")
    val text = scala.io.Source.fromInputStream(getClass.getResourceAsStream("/helps")).mkString
    println(text)
  }

  private lazy val tcpMan = new TcpMan(config = configuration)
  private val tcpManRef = new AtomicReference[Option[TcpMan]](None)
  private val cmdArgs = args.filter(!_.startsWith("-"))
  private val params = args.filter(_.startsWith("-"))

  def run(): Unit = try {
    if (cmdArgs.isEmpty) printHelp()
    else {
      cmdArgs.head.toLowerCase match {
        case "push" =>
          cmdArgs.tail.headOption match {
            case None => err("Must provide path arg.")
            case Some(path) =>
              val file = Paths.get(path)
              if (Files.exists(file)) {
                tcpManRef.set(Option(tcpMan))
                val code = Msg.publishCode(tcpMan.push(Paths.get(path)))
                info(s"Pull code: $code waiting to pull...", withTitle = false)
              }
              else err(s"File $path does not exist.")
          }
        case "pull" =>
          cmdArgs.tail.headOption match {
            case None => err("Must provide pull code.")
            case Some(code) =>
              val destDir = Paths.get(cmdArgs.drop(2).headOption.getOrElse("."))
              tcpManRef.set(Option(tcpMan))
              tcpMan.pull(Msg.fromCode(code), destDir)
          }
        case _ => printHelp()
      }
    }
  } catch {
    case e: Throwable =>
      err(s"Failed with msg:${e.getMessage}")
      if (configuration.isDebug) e.printStackTrace()
      tcpManRef.get().map(_.system.terminate())
      System.exit(1)
  }
}
