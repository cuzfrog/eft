package com.github.cuzfrog.eft

import scala.concurrent.duration._
import scala.language.postfixOps

private case class Configuration(isDebug: Boolean,
                                 networkTimeout: Duration,
                                 port: Option[Int] = None,
                                 name: String = "eft",
                                 pushChunkSize: Int = 8192)
