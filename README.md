# eft - a file transfer tool.

Copy file between two computer, using akka streaming.

###Example:
On node where you want to send a file:

    $eft push file1.txt
    Pull code: XXXXXX
        
Then, on node where you want to receive this file:
(code represents ip and port information)

    $eft pull XXXXXX
    
file1.txt will be saved to current dir on pull node.

###Build:

under sbt console:

    >assembly
    
jar and executable sh/bat file will be packed into
 cross target dir.