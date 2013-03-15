Building:
Navigate to $PublisherServer/src/ directory and execute: sh bulid.sh. Make sure $JAVA_HOME is set.

Run:
Server
1. Start RMI Registry: 
rmiregistry <port> &

2. Start Server:
sh startserver <localhost ip> <rmiregistry port> <registryserverhost> <registryserverport>

3. Stop Server
shutdown

Client
1. Start Server

2. Start Client:
sh startclient.sh

3. Stop Client:
stopclient

4. joinserver:
joinserver <group server host> <udp client port> <rmiregistry port>

5. leaveserver:
leaveserver

6. subscribe:
subscribe Cat1;Cat2;Cat3;

7. unsubscribe:
unsubscribe Cat1;Cat2;Cat3;

8. publish:
publish Cat1;Cat2;Cat3;content



Config files:
config.properties:
encoding: encoding type in which data is send over UDP. Default UTF-8 is used
serverListUpdateInterval: Interval to getList in millis
serverUdpPort: Port on which goup server UDP client to bind for getList calls
serverPingPort: Port on which server will listen to pings
freePortList: A list of free ports for sending data to different clients

server.policy
security policy for java. Needs the codebase location to be updated where you are running from.

log4j.properties
properties file for logging. Contains location of the files where it generates the logs. In order to change it. Change the following two lines:
log4j.appender.fileDebug.File=D:\\debug.log
log4j.appender.fileInfo.File=D:\\info.log

In case you want to change the log level change the following
log4j.rootLogger=debug, stdoutDebug, fileDebug
to 
log4j.rootLogger= info , stdout, fileInfo
