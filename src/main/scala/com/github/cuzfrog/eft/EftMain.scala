package com.github.cuzfrog.eft

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

  def run(): Unit = {
    if (args.isEmpty) printHelp()
    else {
      args.head.toLowerCase match {
        case "help" | "-help" => printHelp()
        case "push" =>
        case "pull" =>
      }
    }
  }
}
