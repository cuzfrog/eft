# akka stream tcp problems

This page illustrates problems discovered in akka stream tcp.

### 1 - Inconsistent element-boundary in tcp flow:

Elements through a `Tcp().outgoingConnection` will be de-framed(concatenated) unintentionally.
See akka [issue#23325](https://github.com/akka/akka/issues/23325)

```scala
private lazy val server = Tcp().bind("0.0.0.0", 23888, halfClose = true)
val flow2 = Flow[ByteString].merge(
  Source(1 to 9).map(i=> ByteString(i.toString))
    .concat(Source.single(ByteString("end")))
).map { bs => println(s"server ~~> ${bs.utf8String}"); bs }

server.runForeach { con =>
  con.handleWith(flow2)
}

val tcpFlow = Tcp().outgoingConnection("127.0.0.1", 23888)
      //.map { bs => println(s"server ~~> ${bs.utf8String}"); bs }

Source.single(ByteString.empty).via(tcpFlow).map(_.utf8String)
      .to(Sink.foreach(msg => println(s"client <~~ $msg"))).run
```

output(different from each run):
```
/*
server ~~> 1
server ~~> 2
client <~~ 1
server ~~> 3
server ~~> 4
server ~~> 5
server ~~> 6
client <~~ 2
server ~~> 7
server ~~> 8
client <~~ 34567 //concatenated
server ~~> 9
client <~~ 8
server ~~> end
client <~~ 9
client <~~ end
*/
```

`Tcp().outgoingConnection` is not able to preserve elements barriers. 
**Explicit `Framing` should be used here.**

#### solution:

A field length field framing layer could be used to solve this problem:
```scala
/**
* Add 16bit length field at the beginning of element for framing.
* See Akka issue #23325
*
*     +----------------------------+
*     | Framing BidiFlow         |
*     |                            |
*     |  +----------------------+  |
* I1 ~~> |       Framing        | ~~> O1
*     |  +----------------------+  |
*     |                            |
*     |  +----------------------+  |
* O2 <~~ |   Add length field    | <~~ I2
*     |  +----------------------+  |
*     +----------------------------+
*/
val FramingBidi = {
    def short2ByteString(sh: Short): ByteString = {
      val b0 = (sh >> 8).toByte
      val b1 = sh.toByte
      ByteString(Array(b1, b0)) //LITTLE_ENDIAN
    }
    BidiFlow.fromFlows(
      Framing.lengthField(fieldLength = 2, maximumFrameLength = Short.MaxValue).map(_.drop(2)),
      Flow[ByteString].map(bs => short2ByteString(bs.length.toShort) ++ bs)
    )
}
```

### 2 - Inconsistent complete signal in tcp flow:

When run eft remotely or locally, akka stream behaves differently that
 a complete signal is emit or not, respectively.

#### why in scope?

Because eft works within a single loop, there is no complete signal ever emit,
eft uses a `Merge` to control the complete signal to file sink (`FileIO.toPath`):
```text
                  +--------+
u ~>  Payload ~> =I        |
                  | Merge  O= ~> FileSink
  CompleteSig ~> =I        |
                  +--------+
```
Once `FileIO.fromPath` on `Push` node complete, a `PayloadEnd` msg ensues, which triggers a
complete signal on `Pull` node, as the graph showed above. So the `Merge` should be set
`eagerComplete = true`

If a complete would come from `u`, it would trigger `FileSink` to complete unintentionally.

#### workaround:

For now I can only use a config flag to control `eagerComplete`.
And did not know where the complete signal comes from or or why it does.
