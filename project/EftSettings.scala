import MyTasks._
import sbt._
import sbt.Keys._
import sbtassembly.AssemblyKeys.assembly
import scala.sys.process.Process

object EftSettings {
  val settings = Seq(
    test in assembly := {},
    generateSh := {
      val file = crossTarget.value / "eft.bat"
      val batWdir = """%~dp0\"""
      val contents = s"@echo off\r\njava -jar ${batWdir}eft-assembly-${version.value}.jar %*"
      IO.write(file, contents)
      val fileSh = crossTarget.value / "eft"
      val shWdir = """$(dirname "$0")/"""
      val contentsSh = s"#!/bin/bash\njava -jar ${shWdir}eft-assembly-${version.value}.jar " + "\"$@\""
      IO.write(fileSh, contentsSh)
      fileSh.setExecutable(true)

      val log = streams.value.log
      val files = crossTarget.value.listFiles()
        .filter(f => f.isFile && (!f.name.endsWith(".zip")))
        .map(_.getName)
      log.info("compress release files.")
      val cmd =s"""zip ${name.value}.zip ${files.mkString(" ")}""".mkString
      Process(cmd, crossTarget.value) ! log
      Process(s"cp ${name.value}.zip /tmp/", crossTarget.value) ! log
    },
    generateSh := (generateSh triggeredBy assembly).value
  )
}
