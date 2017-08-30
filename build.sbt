import sbt.Keys._
import Settings._

shellPrompt in ThisBuild := { state => Project.extract(state).currentRef.project + "> " }
version in ThisBuild := "0.3.0"
scalaVersion := "2.12.3"

val macroAnnotationSettings = Seq(
  addCompilerPlugin("org.scalameta" % "paradise" % "3.0.0-M10" cross CrossVersion.full),
  scalacOptions += "-Xplugin-require:macroparadise",
  scalacOptions in(Compile, console) ~= (_ filterNot (_ contains "paradise")),
  libraryDependencies += "org.scalameta" %% "scalameta" % "1.8.0" % Provided
)

val root = (project in file("."))
  .settings(commonSettings, EftSettings.settings, macroAnnotationSettings)
  .settings(
    name := "eft",
    libraryDependencies ++= Seq(
      "io.suzaku" %% "boopickle" % "1.2.6",
      "commons-io" % "commons-io" % "2.5" % Test,
      "com.typesafe.akka" %% "akka-stream" % "2.5.3",
      "com.typesafe.akka" %% "akka-stream-testkit" % "2.5.3" % Test,
      "com.github.cuzfrog" %% "scmd" % "0.1.2"
    )
  )

//val e2eTests = (project in file("./tests"))
//  .settings(commonSettings, EftSettings.settings)
//  .settings(
//    name := "eft-e2e-tests",
//    libraryDependencies ++= Seq(
//    )
//  )