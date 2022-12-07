package com.simedge.peer;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.drasyl.node.DrasylException;

import com.simedge.broker.client.ClientThread;

public class ConnectionPool {
    public static ConcurrentLinkedQueue<String> availibleResources = new ConcurrentLinkedQueue<String>();
    public static PeerConnection node;
    public static ClientThread brokerConnection;

    public static void initPeer() {
        try {
            node = new PeerConnection();
        } catch (DrasylException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        brokerConnection = new ClientThread(node.identity().getAddress().toString());
        brokerConnection.start();
        brokerConnection.brokerProtocol.REGISTER(4);
        brokerConnection.brokerProtocol.GET_RESOURCE();
    }

    public static void main(final String[] args) throws DrasylException {
        initPeer();

        while (true) {

        }
    }

}
