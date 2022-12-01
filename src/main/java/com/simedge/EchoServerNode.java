package com.simedge;

import org.drasyl.annotation.NonNull;
import org.drasyl.node.DrasylConfig;
import org.drasyl.node.DrasylException;
import org.drasyl.node.DrasylNode;
import org.drasyl.node.event.Event;
import org.drasyl.node.event.MessageEvent;

import java.nio.file.Path;

/**
 * Starts a {@link DrasylNode} that sends all received messages back to the receiver. Based on the <a
 * href="https://tools.ietf.org/html/rfc862">Echo Protocol</a>.
 *
 * @see EchoClient
 * @see EchoServerBootstrap
 */
@SuppressWarnings({ "java:S106", "java:S125", "java:S112", "java:S2096" })
public class EchoServerNode extends DrasylNode {
    private static final String IDENTITY = System.getProperty("identity", "echo-server.identity");

    protected EchoServerNode(final DrasylConfig config) throws DrasylException {
        super(config);
    }

    @Override
    public void onEvent(final @NonNull Event event) {
        if (event instanceof MessageEvent) {
            System.out.println("Got `" + ((MessageEvent) event).getPayload() + "` from `" + ((MessageEvent) event).getSender() + "`");
            send(((MessageEvent) event).getSender(), ((MessageEvent) event).getPayload()).exceptionally(e -> {
                throw new RuntimeException("Unable to process message.", e);
            });
        }
    }

    public static void main(final String[] args) throws DrasylException {
        final DrasylConfig config = DrasylConfig.newBuilder()
                .identityPath(Path.of(IDENTITY))
                .remoteMessageArqEnabled(false)
                .build();
        final EchoServerNode node = new EchoServerNode(config);

        node.start().toCompletableFuture().join();
        System.out.println("EchoServer listening on address " + node.identity().getAddress());
        while(true){

        }
    }
}