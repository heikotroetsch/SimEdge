package com.simedge.protocols;

import java.sql.Connection;

import com.simedge.broker.client.ClientThread;
import com.simedge.peer.ConnectionPool;

public class BrokerProtocol {

    ClientThread source;

    // server mesasges
    public static final int FAILURE = 0;
    public static final int HELLO = 1;
    public static final int BYE = 2;
    public static final int GET_RESOURCE = 3;
    public static final int RETURN_RESOURCE = 4;
    public static final int SET_PING = 5;

    public BrokerProtocol(ClientThread source) {
        this.source = source;
    }

    // Sending messages

    public void REGISTER(int numberResources) {
        System.out.println("register device with broker");
        source.messageQueue.add(BrokerProtocol.HELLO + source.peerIdentity + ";" + numberResources
                + System.getProperty("line.separator"));
    }

    public void GET_RESOURCE() {
        source.messageQueue.add("3" + System.getProperty("line.separator"));
    }

    public void RETURN_RESOURCE(String resourceIdentity) {
        source.messageQueue.add("4" + resourceIdentity + System.getProperty("line.separator"));
    }

    public void SET_PING() {
        // TODO
    }

    public void BYE() {
        source.messageQueue.add("2" + System.getProperty("line.separator"));
        source.shutdown();
    }

    // Receiving messages

    public void process_HELLO(String content) {
        System.out.println(content);
    }

    public void process_FAILURE(String content) {
        System.err.println(content);
    }

    public void process_GET_RESOURCE(String content) {
        ConnectionPool.availibleResources.add(content);
        ConnectionPool.node.sendMessage(content, "Hello");
    }

    public void process_SET_PING(String content) {
        // TODO
    }

}
