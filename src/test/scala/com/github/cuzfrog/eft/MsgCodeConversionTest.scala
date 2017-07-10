package com.github.cuzfrog.eft

import utest._

object MsgCodeConversionTest extends TestSuite {
  val tests = this {
    'positive1 {
      val info = RemoteInfo(Seq("127.0.0.1", "192.168.1.81"), 23432)
      val code = RemoteInfo.publishCode(info)
      println(code)
      assert(RemoteInfo.fromAddressOrCode(code) == info)
    }
    'positive2 {
      val ip = "192.168.2.15"
      val port = 33236
      val address = s"$ip:$port"
      val info = RemoteInfo.fromAddressOrCode(address)
      assert(info.ips.contains(ip))
      assert(info.port == port)
    }
    'negtive {
      val badCode1 = "5B887F000001C0A80151-Im bad"
      intercept[IllegalArgumentException] {
        RemoteInfo.fromAddressOrCode(badCode1)
      }
    }
  }
}