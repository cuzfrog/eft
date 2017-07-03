import sbt.Keys._
import Settings._

shellPrompt in ThisBuild := { state => Project.extract(state).currentRef.project + "> " }
onLoad in Global := (onLoad in Global).value andThen (Command.process(s"", _))


lazy val root = (project in file("."))
  .settings(commonSettings, publicationSettings, readmeVersionSettings)
  .settings(
    name := "eft",
    version := "0.0.1",
    libraryDependencies ++= Seq(
      "com.typesafe" % "config" % "1.3.1",
      "me.alexpanov" % "free-port-finder" % "1.0",
      "io.suzaku" %% "boopickle" % "1.2.6",
      "com.typesafe.akka" %% "akka-stream" % "2.5.3",
      "com.typesafe.akka" %% "akka-stream-testkit" % "2.5.3" % Test
    ),
    reColors := Seq("magenta")
  )
