package com.simedge.protocols;

public class PeerProtocol {

    public static void handleMessage(Object payload) {
        System.out.println("Peer-Message: " + payload);
    }

}
