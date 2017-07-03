package com.github.cuzfrog.eft

import java.nio.file.Path

import akka.stream._
import akka.stream.scaladsl.Tcp.ServerBinding
import akka.stream.scaladsl._

import scala.concurrent.Future
/**
  * Created by cuz on 7/3/17.
  */
private object Push {
  def run(file: Path): Unit = {
    val binding: Future[ServerBinding] =
      Tcp().bind("127.0.0.1", 8888).to(Sink.ignore).run()

    binding.map { b =>
      b.unbind() onComplete {
        case _ => // ...
      }
    }
  }
}
