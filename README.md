# Middleware Tech. 2020/2021 - Project #2 - Distributed Node-RED flows

To enable cluster capabilities in the Akka project the remote settings have been added, and `cluster` has been used as the `akka.actor.provider`. The `akka.cluster.seed-nodes` have been also be added to `application.conf` file.
The seed nodes are configured contact points which newly started nodes will try to connect with in order to join the cluster. Currently, 2 seed nodes have been configured on ports `25251` and `25252`.

Note that if you are going to start the nodes on different machines you need to specify the ip-addresses or host names of the machines in `application.conf` instead of `127.0.0.1`.

See --- [application.conf](src/main/resources/application.conf)

## System pre-requisites
- Latest [Java-JDK](https://www.oracle.com/it/java/technologies/javase-downloads.html)
- [Node-Red](https://nodered.org/)

Optional, if you want to run the application with Maven:

- [Maven](http://maven.apache.org/download.cgi) 

## Command line arguments
- Role in the Cluster: Can be `Server`, `Seed`, `NodeRedInstallation`.
- Port for the Akka Cluster node: If `0` it will use a random available port.
- Port for the TCP-Out socket. Set to `0` for `Server` and `Seed`. 
- Port for the TCP-In socket. Set to `0` for `Server` and `Seed`.

If none of these arguments are specified, the default behavior is to start a new Akka Cluster node with role `NodeRedInstallation`, random port for the Cluster node, ports `12345` and `56789` for the Sockets.

If #arguments is not equal to 4 or 0, an exception is raised.

## Run instructions using the executable .JAR file

First we need to start the Cluster, and the Seed nodes. Then we can connect a new Node-RED installation by starting a new Akka node. This Akka node will automatically join the Cluster.

### Start the Cluster

Get the JAR file from the Deliverables folder and place it in a place of your choice.

Open a new terminal window. Navigate till the JAR position. Then type:

    java -jar Project2NodeRED.jar Server 25251 0 0

This will start the Cluster, and a Seed node at port `25251`.

Open a second terminal window, navigate till the JAR position, and then execute the following command:

    java -jar Project2NodeRED.jar Seed 25252 0 0

This will start the 2nd Seed node at port `25252`.

Now the Akka CLuster is running and ready to accept new nodes.

### Create a new node and join the Cluster

Open a third terminal window, navigate till the JAR position, and type:

    java -jar Project2NodeRED.jar

This will start a new node that will automatically join the CLuster. Since we do not specify any arguments, the node connect itself to `localhost` on a `random free port`.Then it open 2 TCP-sockets on the `localhost` on the default ports `12345` and `56789`.

Now the node waits for Node-RED to connect on the Sockets.

#### If you want to specify your ports

Check the command-line arguments section. Open a third terminal window, and type:

    java -jar Project2NodeRED.jar NodeRedInstallation port port port

## Run instructions using Maven

First we need to start the Cluster, and the Seed nodes. Then we can connect a new Node-RED installation by starting a new Akka node. This Akka node will automatically join the Cluster.

### Start the Cluster

Get the complete source code of the project and place it in a place of your choice.

Open a new terminal window. Navigate till the project folder position. Then type:

    mvn clean

Then, if the clean was successful:

    mvn compile

Close the terminal window and open a new fresh one. Navigate till the project folder position, and then execute the following command:

    mvn exec:java -Dexec.mainClass="sample.cluster.middlew.ClusterMain" -Dexec.args="Server 25251 0 0"

This will start the Cluster, and a Seed node at port `25251`.

Open a second terminal window, navigate till the project folder position, and then execute the following command:

    mvn exec:java -Dexec.mainClass="sample.cluster.middlew.ClusterMain" -Dexec.args="Seed 25252 0 0"

This will start the 2nd Seed node at port `25252`.

Now the Akka CLuster is running and ready to accept new nodes.

### Create a new node and join the Cluster

Open a third terminal window, navigate till the project folder position, and type:

    mvn exec:java -Dexec.mainClass="sample.cluster.middlew.ClusterMain"

This will start a new node that will automatically join the CLuster. Since we do not specify any arguments, the node connect itself to `localhost` on a `random free port`.Then it open 2 TCP-sockets on the `localhost` on the default ports `12345` and `56789`.

Now the node waits for Node-RED to connect on the Sockets.

#### If you want to specify your ports

Check the command-line arguments section. Open a third terminal window, and type:

    mvn exec:java -Dexec.mainClass="sample.cluster.middlew.ClusterMain" -Dexec.args="NodeRedInstallation port port port"

## Quick-Run instructions for Windows' users using the executable .JAR file

Download the [.bat] file from the Deliverables folder. Place them in the same folder where you place the jar. Double-click on them to start the various nodes

## How to modify your Node-RED flow to be distributed

//TODO
