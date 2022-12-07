package com.simedge.broker.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.simedge.protocols.BrokerProtocol;

public class ClientThread extends Thread {
    String hostname = "localhost";
    int port = 12345;
    BufferedReader reader;
    PrintWriter writer;
    Socket socket;
    boolean stop = false;
    public String peerIdentity;

    public ConcurrentLinkedQueue<String> messageQueue = new ConcurrentLinkedQueue<String>();
    public BrokerProtocol brokerProtocol = new BrokerProtocol(this);

    public ClientThread(String peerIdentiy) {
        this.peerIdentity = peerIdentiy;
        this.initThread();
    }

    public void initThread() {

        try {
            socket = new Socket(hostname, port);
            writer = new PrintWriter(socket.getOutputStream(), true);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

        } catch (UnknownHostException ex) {

            System.out.println("Server not found: " + ex.getMessage());

        } catch (IOException e) {

            System.out.println("I/O error: " + e.getMessage());
        }
    }

    public void run() {
        try {
            do {
                if (stop) {
                    break;
                }

                if (reader.ready()) {
                    // reads message type and content. Content is nu ll if empty.
                    int messageType = reader.read() - 48; // 48 is the char number for 0
                    System.out.println(messageType);
                    String content = reader.readLine();
                    System.out.println(content);
                    System.out.println("message type: " + messageType + " content: " + content);
                    // handle message
                    handleMessage(messageType, content);

                }

                // if write queue is filled write message
                if (!messageQueue.isEmpty()) {
                    System.out.println("message in queue");
                    String message = messageQueue.poll();
                    writer.write(message);
                    writer.flush();
                    System.out.println("message sent: " + message);
                }

            } while (!stop);

            socket.close();
        } catch (IOException e) {
            System.out.println("Server exception: " + e.getMessage());
            shutdown();
        } catch (NullPointerException e) {
            System.out.println("Shutting down broker Connection");
            shutdown();
        }
    }

    public void shutdown() {
        this.stop = true;
    }

    private void handleMessage(int messageType, String content) {

        switch (messageType) {
            case BrokerProtocol.FAILURE:
                brokerProtocol.process_FAILURE(content);
                break;

            case BrokerProtocol.HELLO:
                brokerProtocol.process_HELLO(content);
                break;

            case BrokerProtocol.GET_RESOURCE:
                brokerProtocol.process_GET_RESOURCE(content);
                break;

            case BrokerProtocol.SET_PING:
                brokerProtocol.process_SET_PING(content);
                break;

            case BrokerProtocol.BYE:
                shutdown();
                break;
        }

    }

}
