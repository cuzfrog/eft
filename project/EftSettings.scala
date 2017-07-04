import MyTasks._
import sbt._
import sbt.Keys._
import sbtassembly.AssemblyKeys.assembly

object EftSettings {
  val settings = Seq(
    assembly := (assembly dependsOn generateSh).value,
    generateSh := {
      val file = crossTarget.value / "eft.bat"
      val contents = s"@echo off\r\njava -jar eft-assembly-${version.value}.jar %*"
      IO.write(file, contents)
      val fileSh = crossTarget.value / "eft"
      val pwd = """$(dirname "$0")/"""
      val contentsSh = s"#!/bin/bash\njava -jar ${pwd}eft-assembly-${version.value}.jar " + "\"$@\""
      IO.write(fileSh, contentsSh)
      fileSh.setExecutable(true)
    }
  )
}
