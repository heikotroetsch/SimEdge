package com.simedge;

import org.drasyl.annotation.NonNull;
import org.drasyl.node.DrasylConfig;
import org.drasyl.node.DrasylException;
import org.drasyl.node.DrasylNode;
import org.drasyl.node.event.Event;
import org.drasyl.node.event.MessageEvent;
import org.drasyl.node.event.NodeOnlineEvent;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

import static org.drasyl.util.RandomUtil.randomString;

/**
 * Starts a {@link DrasylNode} which sends one message to given address and echoes back any received message to
 * the sender. Based on the <a href="https://tools.ietf.org/html/rfc862">Echo Protocol</a>.
 *
 * @see EchoServerNode
 */
@SuppressWarnings({ "java:S106", "java:S112", "java:S125", "java:S126", "java:S2096" })
public class EchoClient extends DrasylNode {
    private static final String IDENTITY = System.getProperty("identity", "echo-client.identity");
    private static final int SIZE = Integer.parseInt(System.getProperty("size", "256"));
    private final CompletableFuture<Void> online = new CompletableFuture<>();

    protected EchoClient(final DrasylConfig config) throws DrasylException {
        super(config);
    }

    @Override
    public void onEvent(final @NonNull Event event) {
        // hier keine Blokierenden sachen weil während dessn empfängt man keine Messages
        if (event instanceof NodeOnlineEvent) {
            online.complete(null);
        }
        else if (event instanceof MessageEvent) {
            System.out.println("Got `" + ((MessageEvent) event).getPayload() + "` from `" + ((MessageEvent) event).getSender() + "`");
            send(((MessageEvent) event).getSender(), ((MessageEvent) event).getPayload()).exceptionally(e -> {
                throw new RuntimeException("Unable to process message.", e);
            });
        }
    }

    public static void main(final String[] args) throws DrasylException {
        String[] argss = new String[1];
        argss[0] = "6af6878315ed980ea3929aee61933b7e4285376bd2a0699df98d7906ac87c007";
        if (argss.length != 1) {
            System.err.println("Please provide EchoServer address as first argument.");
            System.exit(1);
        }
        final String recipient = argss[0];

        final DrasylConfig config = DrasylConfig.newBuilder()
                .identityPath(Path.of(IDENTITY))
                //.remoteMessageArmApplicationEnabled(false) 
                //reliable
                .remoteMessageArqEnabled(false)
                .build();
        final EchoClient node = new EchoClient(config);

        node.start().toCompletableFuture().join();
        node.online.join();
        System.out.println("EchoClient started");

        final String payload = randomString(SIZE);

        System.out.println("Send `" + payload + "` to `" + recipient + "`");
        node.send(recipient, payload).exceptionally(e -> {
            throw new RuntimeException("Unable to process message.", e);
        });

        while(true){

        }
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
 *   intra-vm-discovery.enabled = false
 *
 *   remote {
 *     bind-port = 22527
 *     expose.enabled = false
 *     super-peer.enabled = false
 *     local-host-discovery.enabled = false
 *     local-network-discovery.enabled = false
 *   }
 *}
*/