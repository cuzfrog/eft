package com.github.cuzfrog.eft

import scala.language.reflectiveCalls

object arm {
  type Closeable = {
    def close(): Unit
  }

  type ManagedResource[A <: Closeable] = Traversable[A]

  implicit class CloseableOps[A <: Closeable](resource: => A) {
    def autoClosed: ManagedResource[A] = new Traversable[A] {
      override def foreach[U](f: A => U): Unit = try {
        f(resource)
      } finally {
        resource.close()
      }
    }

    def withAutoClose[R](f: A => R): R = try {
      f(resource)
    } finally {
      resource.close()
    }
  }
}
