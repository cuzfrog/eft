import sbt.Keys._
import Settings._

shellPrompt in ThisBuild := { state => Project.extract(state).currentRef.project + "> " }
onLoad in Global := (onLoad in Global).value andThen (Command.process(s"", _))

val root = (project in file("."))
  .settings(commonSettings, EftSettings.settings)
  .settings(
    name := "eft",
    version := "0.2.0",
    libraryDependencies ++= Seq(
      "io.suzaku" %% "boopickle" % "1.2.6",
      "commons-io" % "commons-io" % "2.5" % Test,
      "com.typesafe.akka" %% "akka-stream" % "2.5.3",
      "com.typesafe.akka" %% "akka-stream-testkit" % "2.5.3" % Test,
      "org.backuity.clist" %% "clist-core"   % "3.2.2",
      "org.backuity.clist" %% "clist-macros" % "3.2.2" % Provided
    )
  )

//val portTool =(project in file("."))
//  .settings(commonSettings, EftSettings.settings)
//  .settings(
//    name := "port-tool",
//    version := "0.1.0",
//    libraryDependencies ++= Seq(
//    )
//  )