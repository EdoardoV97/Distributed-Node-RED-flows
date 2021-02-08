package sample.cluster.middlew.ServerSocket;

import java.io.*;
import java.net.*;

public class ServerSocketToNodeRed {

    private ServerSocket serverSocket;
    private Socket clientSocket;
    private ObjectOutputStream out;
    private BufferedReader in;

    public ServerSocketToNodeRed(int port) {
        try {
            serverSocket = new ServerSocket(port);
            System.out.println("Opening a socket on the local host on port: " + port);
            System.out.println("Waiting for connection from NodeRed");
            clientSocket = serverSocket.accept();

            out = new ObjectOutputStream(new DataOutputStream(clientSocket.getOutputStream()));
            in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            System.out.println("Successfully open the Socket with NodeRed");
        } catch (IOException e) {
            System.out.println("Error in opening the Socket!");
        }
    }


    public void stop()  {
        try {
            in.close();
            out.close();
            clientSocket.close();
            serverSocket.close();
            System.out.println("Closing socket...");
        } catch (IOException e) {
            System.out.println("Error in closing the Socket!");
        }
    }

    public ObjectOutputStream getOut() {
        return out;
    }

    public BufferedReader getIn() {
        return in;
    }

}
