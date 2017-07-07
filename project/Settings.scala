import sbt.Keys._
import sbt._
import MyTasks._

object Settings {
  val commonSettings = Seq(
    resolvers ++= Seq(
      Resolver.bintrayRepo("cuzfrog","maven"),
      "Artima Maven Repository" at "http://repo.artima.com/releases",
      "Typesafe Releases" at "http://repo.typesafe.com/typesafe/releases/",
      "spray repo" at "http://repo.spray.io"
    ),
    organization := "com.github.cuzfrog",
    scalaVersion := "2.12.2",
    scalacOptions ++= Seq(
      "-unchecked",
      "-deprecation",
      "-feature"),
    libraryDependencies ++= Seq(
      "com.lihaoyi" %% "utest" % "0.4.7" % "test",
      "junit" % "junit" % "4.12" % "test",
      "com.novocode" % "junit-interface" % "0.11" % "test->default"
    ),
    testOptions += Tests.Argument(TestFrameworks.JUnit, "-v", "-q", "-a"),
    //testFrameworks += new TestFramework("utest.runner.Framework"),
    logBuffered in Test := false,
    parallelExecution in Test := false,
    licenses += ("Apache-2.0", url("https://opensource.org/licenses/Apache-2.0"))
  )

  val publicationSettings = Seq(
    publishTo := Some("My Bintray" at s"https://api.bintray.com/maven/cuzfrog/maven/${name.value }/;publish=1")
  )

  val readmeVersionSettings = Seq(
    (compile in Compile) := ((compile in Compile) dependsOn versionReadme).value,
    versionReadme := {
      val contents = IO.read(file("README.md"))
      val regex =raw"""(?<=libraryDependencies \+= "com\.github\.cuzfrog" %% "${name.value}" % ")[\d\w\-\.]+(?=")"""
      val newContents = contents.replaceAll(regex, version.value)
      IO.write(file("README.md"), newContents)
    }
  )
}