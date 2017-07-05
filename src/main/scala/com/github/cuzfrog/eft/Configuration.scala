package com.github.cuzfrog.eft

import scala.concurrent.duration._
import scala.language.postfixOps

private case class Configuration(isDebug: Boolean = false,
                                 networkTimeout: Duration = 500 millis)
