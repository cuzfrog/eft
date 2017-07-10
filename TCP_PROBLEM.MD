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