package com.simedge.peer;

import org.drasyl.annotation.NonNull;
import org.drasyl.node.DrasylConfig;
import org.drasyl.node.DrasylException;
import org.drasyl.node.DrasylNode;
import org.drasyl.node.event.Event;
import org.drasyl.node.event.MessageEvent;
import org.drasyl.node.event.NodeOnlineEvent;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

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
    private static final String IDENTITY = System.getProperty("identity", "peer-c.identity");
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

    @Override
    public void onEvent(final @NonNull Event event) {
        // hier keine Blokierenden sachen weil während dessn empfängt man keine Messages
        if (event instanceof NodeOnlineEvent) {
            online.complete(null);
        } else if (event instanceof MessageEvent) {
            System.out.println("Got `" + ((MessageEvent) event).getPayload() + "` from `"
                    + ((MessageEvent) event).getSender() + "`");
            send(((MessageEvent) event).getSender(), ((MessageEvent) event).getPayload()).exceptionally(e -> {
                throw new RuntimeException("Unable to process message.", e);
            });
        }
    }

    void initialize() throws DrasylException {

        this.start().toCompletableFuture().join();

        // CLIENT CONNECTIONS
        this.online.join();
        System.out.println("PeerConnection started");
        System.out.println(
                "PeerConnection listening on address " + this.identity().getAddress());

    }

    public void sendMessage(String recipient_identity, String payload) {
        System.out.println("Send `" + payload + "` to `" + recipient_identity + "`");
        this.send(recipient_identity, payload).exceptionally(e -> {
            throw new RuntimeException("Unable to process message.", e);
        });
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