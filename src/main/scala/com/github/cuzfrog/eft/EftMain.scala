package com.github.cuzfrog.eft

import java.nio.file.{Files, Paths}

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

  private val tcpMan = new TcpMan()
  private val cmdArgs = args.filter(!_.startsWith("-"))
  private val params = args.filter(_.startsWith("-"))

  def run(): Unit = {
    if (cmdArgs.isEmpty) printHelp()
    else {
      cmdArgs.head.toLowerCase match {
        case "push" =>
          cmdArgs.tail.headOption match {
            case None => err("Must provide path arg.")
            case Some(path) =>
              val file = Paths.get(path)
              if (Files.exists(file)) {
                val code = tcpMan.push(Paths.get(path))
                info(s"Pull code: $code")
              }
              else err(s"File $path does not exist.")
          }
        case "pull" =>
          cmdArgs.tail.headOption match {
            case None => err("Must provide pull code.")
            case Some(code) =>
              val destDir = Paths.get(cmdArgs.tail.headOption.getOrElse("."))
              tcpMan.pull(Msg.fromCode(code), destDir)
          }
        case _ => printHelp()
      }
    }
  }
}
