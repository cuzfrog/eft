package com.github.cuzfrog.eft

import java.io.File
import java.nio.file.{Path, Paths}

import org.backuity.clist._

import scala.concurrent.duration._
import scala.language.postfixOps

object CmdUi extends App {
  Cli.parse(args)
    .version(getClass.getPackage.getImplementationVersion)
    .withProgramName("eft")
    .withCommands(new Push, new Pull).foreach { cmd =>

    val config = Configuration(
      isDebug = cmd.debug,
      networkTimeout = cmd.networkTimeout millis
    )

    new EftMain(cmd, config).run()
  }
}

private sealed trait CommonOpt {
  self: Command =>
  var debug = opt[Boolean](description = "is debug mode")
  var networkTimeout = opt[Long](abbrev = "nt", description = "max network timeout in millisecond")
}

private class Push extends Command(
  description = "publish a file and wait for pulling from client")
  with CommonOpt {
  var file = arg[File](required = true, description = "file to send")
}

private class Pull extends Command(
  description = "pull a published file from push node")
  with CommonOpt {
  var code = arg[String](required = true, description = "pull code")
  var destDir =
    arg[File](default = Paths.get(".").toFile, description = "dest dir to save pulled file")
}