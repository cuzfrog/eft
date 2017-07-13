[![Build Status](https://travis-ci.org/cuzfrog/eft.svg?branch=master)](https://travis-ci.org/cuzfrog/eft)
# eft - a file transfer tool.

Copy file between two computers, based on akka stream.

### Usage:
Setup push on node where you want to send a file:

    $eft push file1.txt
    Connection info: XXXXXX
        
Then, on node where you want to receive this file:

    $eft pull XXXXXX
    
file1.txt will be saved to current dir on pull node.

**Tip: Vice versa, one can setup pull first and then push.**

### Download:
Download zip from [Releases](https://github.com/cuzfrog/eft/releases),
 shell/bat files have already been created, running it requires java8 runtime.

### Design:

* Symmetric.
```text
  +----------------------------------------+          +-------
  |NodeA          Translation    Framing   |   Tcp    |NodeB
  |                  Layer        Layer    |          |
  |                                        |          |
  |  +--------+   ~>   I     ~>     I      O=   ~>   =I
  |  |  Flow  |        I            I      |          |
  |  +--------+   <~   I     <~     I      I=   <~   =O
  +----------------------------------------+          +-------
```
 Where `NodeA = NodeB` in topology.

* Reactive.
```text
  ------+      +-----------+
        |      |           O=   ~>   Test/Log  
        |      |           |  
      a O= ~> =I BroadCast O=   ~>   ControlMerge ~>  FileSink
        |      | (Router)  |         
        |      |           |        +-------- +
        |      |         b O=  ~>  =I c       |
  TL    |      +-----------+        |   Msg   |     
  Bidi  |                           | Process |
        |      +--------+           |(Reactor)|
        |      |      e I=    <~   =O d       |
      f I= <~ =O Mearge |           +---------+
        |      |        |        
        |      |        I=  <~ InitialMsg
  ------+      +--------+
```
Where `~> a-b-c-d-e-f ~>` forms a reactive chain.
Which means protocol process completes in a single loop within a single Tcp connection.

`ControlMerge` see problems below.

Implementation detail: [LoopTcpMan](https://github.com/cuzfrog/eft/blob/master/src/main/scala/com/github/cuzfrog/eft/LoopTcpMan.scala)
 
### Tcp problems:
Akka stream tcp [problems](TCP_PROBLEM.MD) encountered

### Build:
under sbt console:

    >assembly
    
jar and executable sh/bat file will be packed into
 cross target dir.