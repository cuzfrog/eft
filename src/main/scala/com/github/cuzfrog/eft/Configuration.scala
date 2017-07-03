package com.github.cuzfrog.eft

import com.typesafe.config.{Config, ConfigFactory}

/**
  * Created by cuz on 7/3/17.
  */
private class Configuration(provided: Config = ConfigFactory.load()) {
  private val root = provided.withFallback(ConfigFactory.load())
  private val config = root.getConfig("eft")

  val isDebug: Boolean = config.getBoolean("is-debug")
}
