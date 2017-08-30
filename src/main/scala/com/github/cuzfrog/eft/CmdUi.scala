package com.github.cuzfrog.eft

import java.io.File
import java.nio.file.Paths

import Scmd._

import scala.concurrent.duration._
import scala.language.postfixOps

object CmdUi extends App {

  private val defs = new EftDef(args).parse

  import scmdValueConverter._

  private val config = Configuration(
    isDebug = defs.debug.value,
    networkTimeout = defs.timeout.value millis,
    port = defs.port.value
  )

  new EftMain(defs, config).run()

}

@ScmdDef
private class EftDef(args: Seq[String]) extends ScmdDefStub[EftDef] {
  appDef(shortDescription = "Effective file transfer tool")
  appDefCustom(
    "Example" ->"""  eft push /path/file1 --port 8888
                  |  eft pull -n 192.168.116.1:8888
                  |""".stripMargin
  )

  val debug = optDef[Boolean](description = "debug mode")
  val timeout = optDef[Long](description = "max network timeout in millisecond").withDefault(500)
  val port = optDef[Int](description = "specify listening port for contact")
  val printCode = optDef[Boolean](description = "print connection address as hex string.")

  val remoteNode = optDef[String](
    description = "remote address or connection code, e.g. 127.0.0.1:8088", abbr = "n")

  val push = cmdDef(description =
    "push a file to pull node | publish a file and wait for pulling from remote")
  val file = paramDef[File](description = "file to send").mandatory

  val pull = cmdDef(description =
    "pull a published file from push node | request pull and wait for pushing from remote")
  val destDir = paramDef[File](description = "dest dir to save pulled file")
    .withDefault(Paths.get(".").toFile)
}