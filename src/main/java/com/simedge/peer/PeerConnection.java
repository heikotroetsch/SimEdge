package com.simedge.peer;

import org.drasyl.handler.PeersRttHandler;
import org.drasyl.handler.PeersRttHandler.PeerRtt;
import org.drasyl.handler.PeersRttHandler.PeersRttReport;
import org.drasyl.handler.PeersRttHandler.PeerRtt.Role;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.node.DrasylConfig;
import org.drasyl.node.DrasylException;
import org.drasyl.node.DrasylNode;
import org.drasyl.node.event.Event;
import org.drasyl.node.event.MessageEvent;
import org.drasyl.node.event.NodeOnlineEvent;

import com.simedge.protocols.PeerMessage;
import com.simedge.protocols.PeerProtocol;

import io.netty.channel.ChannelHandlerContext;

import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Starts a {@link DrasylNode} which sends one message to given address and
 * echoes back any received message to
 * the sender. Based on the <a href="https://tools.ietf.org/html/rfc862">Echo
 * Protocol</a>.
 *
 * @see EchoServerNode
 */
@SuppressWarnings({ "java:S106", "java:S112", "java:S125", "java:S126", "java:S2096" })
public class PeerConnection extends DrasylNode {
    // private static final int SIZE = Integer.parseInt(System.getProperty("size",
    // "256"));
    private ConcurrentHashMap<String, Long> peerLastUsed = new ConcurrentHashMap<String, Long>();
    private ConcurrentHashMap<Long, Long> messageController = new ConcurrentHashMap<Long, Long>();
    public static final int MAX_MESSAGES = 10;
    public static final int TIMEOUT = 1000;
    private static final String IDENTITY = System.getProperty("identity", "client-b.identity");
    final static DrasylConfig config = DrasylConfig.newBuilder()
            .identityPath(Path.of(IDENTITY))
            // .remoteMessageArmApplicationEnabled(false)
            // reliable
            .remoteMessageArqEnabled(false)

            .build();
    final CompletableFuture<Void> online = new CompletableFuture<>();

    protected PeerConnection() throws DrasylException {
        super(config);
        this.initialize();
    }

    void initialize() throws DrasylException {

        this.start().toCompletableFuture().join();

        // CLIENT CONNECTIONS
        this.online.join();
        System.out.println("PeerConnection started");
        System.out.println("PeerConnection listening on address " + this.identity().getAddress());

    }

    @Override
    public void onEvent(final @org.drasyl.util.internal.NonNull Event event) {
        // hier keine Blokierenden sachen weil während dessn empfängt man keine Messages
        if (event instanceof NodeOnlineEvent) {
            online.complete(null);
        } else if (event instanceof MessageEvent) {

            PeerMessage peerMessage = new PeerMessage((byte[]) ((MessageEvent) event).getPayload());

            PeerProtocol.handleMessage(peerMessage,
                    ((MessageEvent) event).getSender());

            if (peerMessage.messageType == PeerMessage.MessageType.RESULT) {

                messageController.remove(peerMessage.messageNumber);
                System.out
                        .println("remove from message Controller! \tFree:" + (MAX_MESSAGES - messageController.size()));
            }

            /**
             * System.out.println("Got `" + ((MessageEvent) event).getPayload() + "` from `"
             * + ((MessageEvent) event).getSender() + "`");
             * send(((MessageEvent) event).getSender(), ((MessageEvent)
             * event).getPayload()).exceptionally(e -> {
             * throw new RuntimeException("Unable to process message.", e);
             * });
             */
        }
    }

    public boolean sendMessage(String recipient_identity, PeerMessage peerMessage) {
        // Prime connection to target before using full message speed.
        Long lastPeerMessageTime = peerLastUsed.get(recipient_identity);
        if (lastPeerMessageTime == null
                || ((System.currentTimeMillis() - lastPeerMessageTime) >= TIMEOUT) && lastPeerMessageTime != -1) {
            // if no message has been sent to the peer or not recently (longer than Timeout)
            // then send one message and put -1 in the peerLastUsed hashmap. No Message will
            // be sent until the first message returns.
            System.out.println("Sending first Message to Peer: " + recipient_identity);
            peerLastUsed.put(recipient_identity, -1L);
            this.send(recipient_identity, peerMessage.getMessageBytes()).exceptionally(e -> {
                throw new RuntimeException("Unable to process message.", e);
            });
            return false;
        } else if (lastPeerMessageTime == -1) {
            // return if waiting for first response
            System.out.println("waiting for first response from peer: " + recipient_identity);
            return false;
        }

        // if controller has space left send message (Because we checked that the last
        // usage of the peer was not longer than our TIMEOUT a return message is
        // expected quikckly)
        if (messageController.size() <= MAX_MESSAGES) {
            System.out.println("Sending to " + recipient_identity + "\t" + peerMessage.messageNumber);
            messageController.put(peerMessage.messageNumber, System.currentTimeMillis());
            this.send(recipient_identity, peerMessage.getMessageBytes()).exceptionally(e -> {
                throw new RuntimeException("Unable to process message.", e);
            });

            return true;
        } else {
            // if there is no space left in the controller we will clen up all messages
            // older than Timeout
            cleanUpMessages();
            return false;
        }

    }

    /**
     * Own method fo sending results. Sending results is always possible even if
     * Message controller is full. Ensures instant sendback.
     * 
     * @param recipient_identity String Adress of node to send to
     * @param peerMessage        Message to be sent
     */
    public void sendResultMessage(String recipient_identity, PeerMessage peerMessage) {
        this.send(recipient_identity, peerMessage.getMessageBytes()).exceptionally(e -> {
            throw new RuntimeException("Unable to process message.", e);
        });

    }

    private synchronized void cleanUpMessages() {
        long time = System.currentTimeMillis();
        for (var v : messageController.entrySet()) {
            if (v.getValue() + TIMEOUT < time) {
                System.out.println("Removed Message Number : " + v.getKey());
                messageController.remove(v.getKey());
            }
        }
    }

    public boolean fullMessageController() {
        if (messageController.size() >= MAX_MESSAGES) {
            cleanUpMessages();
            return true;
        } else {
            return false;
        }

    }

    public boolean peerAvailible(String peer) {
        Long lastPeerMessageTime = peerLastUsed.get(peer);
        if (lastPeerMessageTime == null) {
            return true;
        } else {
            return (System.currentTimeMillis() - lastPeerMessageTime) < TIMEOUT;
        }
    }

    public void updatePeerLastUsed(String peer) {
        peerLastUsed.put(peer, System.currentTimeMillis());
    }

}

/*
 * https://github.com/drasyl/drasyl/releases/download/v0.9.0/drasyl-0.9.0.zip
 * brew install drasyl/tap/drasyl
 * choco install drasyl
 * .\drasyl.bat node --config ./drasyl.conf --verbose INFO
 * ./drasyl node ...
 * 
 * drasyl.conf
 * drasyl {
 * intra-vm-discovery.enabled = false
 *
 * remote {
 * bind-port = 22527
 * expose.enabled = false
 * super-peer.enabled = false
 * local-host-discovery.enabled = false
 * local-network-discovery.enabled = false
 * }
 * }
 */