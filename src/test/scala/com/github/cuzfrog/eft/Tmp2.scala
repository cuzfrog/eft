package com.github.cuzfrog.eft

import akka.util.ByteString
import boopickle.Default._

object Tmp2 extends App{

  def creatBs: ByteString = ByteString.createBuilder
    .putBytes(Msg.HEAD.toArray)
    .putBytes(Pickle.intoBytes(Filename("f1")).array()).result()

  assert(creatBs == creatBs)
}
