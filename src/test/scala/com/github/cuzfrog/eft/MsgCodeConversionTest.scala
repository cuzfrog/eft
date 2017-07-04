package com.github.cuzfrog.eft

import utest._

object MsgCodeConversionTest extends TestSuite {
  val tests = this {
    'positive {
      val info = RemoteInfo(Seq("127.0.0.1", "192.168.1.81"), 23432)
      val code = Msg.publishCode(info)
      println(code)
      assert(Msg.fromCode(code) == info)
    }
    'negtive {
      val badCode = "5B887F000001C0A80151-Im bad"
      intercept[IllegalArgumentException] {
        Msg.fromCode(badCode)
      }
    }
  }
}