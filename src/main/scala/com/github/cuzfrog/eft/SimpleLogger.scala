package com.github.cuzfrog.eft


/**
  * Override val loggerAgent:String for logger name display.
  * Created by cuz on 2016-08-17.
  */
trait SimpleLogger {

  import SimpleLogger._

  import Console._

  implicit val loggerAgent: String = this.getClass.getSimpleName
  implicit val loggerLevel: Level = Info

  protected def debug(x: Any, withTitle: Boolean = true) = pl(x, Debug, MAGENTA)(withTitle)
  protected def info(x: Any, withTitle: Boolean = true) = pl(x, Info)(withTitle)
  protected def warn(x: Any, withTitle: Boolean = true) = pl(x, Warn, YELLOW)(withTitle)
  protected def err(x: Any, withTitle: Boolean = true) = pl(x, Error, RED)(withTitle)

  private def pl(x: Any, level: Level, color: String = "")
                (withTitle: Boolean)
                (implicit agent: String, levelThreshold: Level) = {
    val prefix = color + (if (withTitle) s"[$agent][$level]" else "")
    if (level.level >= levelThreshold.level) println(s"$prefix$x$RESET")
  }
}

object SimpleLogger {

  sealed trait Level {val level: Int}
  case object Debug extends Level {val level = -1}
  case object Info extends Level {val level = 0}
  case object Warn extends Level {val level = 1}
  case object Error extends Level {val level = 2}
}

