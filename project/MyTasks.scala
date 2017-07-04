import sbt.TaskKey

object MyTasks {
  val generateSh = TaskKey[Unit]("generate-sh", "Generate shell file for shell command.")
  val copyApp: TaskKey[Unit] = TaskKey[Unit]("copy-app", "Copy app files to target.")
  val cleanAll: TaskKey[Unit] = TaskKey[Unit]("clean-all", "Clean all files in target folders.")
  val versionReadme: TaskKey[Unit] = TaskKey[Unit]("version-readme", "Update version in README.MD")
}