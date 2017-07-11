package com.github.cuzfrog.eft

import java.io.File
import java.nio.file.Paths

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
      networkTimeout = cmd.timeout millis,
      port = cmd.port
    )

    new EftMain(cmd, config).run()
  }
}

private sealed trait CommonOpt {
  self: Command =>
  var debug = opt[Boolean](description = "debug mode")
  var timeout = opt[Long](default = 500, description = "max network timeout in millisecond")
  var port = opt[Option[Int]](description = "specify listening port for contact")
  var printCode = opt[Boolean](description = "print connection address as hex string.")
}

private class Push extends Command(
  description = "push a file to pull node | publish a file and wait for pulling from remote")
  with CommonOpt {
  var file = arg[File](required = true, description = "file to send")
  var pullNode = opt[Option[String]](description = "address or connection code, e.g. 127.0.0.1:8088")
}

private class Pull extends Command(
  description = "pull a published file from push node | request pull and wait for pushing from remote")
  with CommonOpt {
  var destDir =
    arg[File](required = false, default = Paths.get(".").toFile,
      description = "dest dir to save pulled file")
  var pushNode = opt[Option[String]](description = "address or connection code, e.g. 127.0.0.1:8088")
}