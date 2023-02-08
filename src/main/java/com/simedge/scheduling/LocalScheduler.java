package com.simedge.scheduling;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

import org.drasyl.identity.DrasylAddress;

import com.simedge.peer.ConnectionPool;
import com.simedge.protocols.PeerMessage;

public class LocalScheduler {

    private ConcurrentHashMap<String, Long> peerLastUsed = new ConcurrentHashMap<String, Long>();
    private ConcurrentHashMap<String, ConcurrentHashMap<Long, Long>> messageControllers = new ConcurrentHashMap<String, ConcurrentHashMap<Long, Long>>();
    // private ConcurrentHashMap<Long, Long> messageController = new
    // ConcurrentHashMap<Long, Long>();
    private static final int MAX_MESSAGES = 1;
    private static final int TIMEOUT = 100;

    private ArrayList<Double> probabilities = new ArrayList<Double>();
    private ConcurrentHashMap<String, Double> RTT = new ConcurrentHashMap<String, Double>();
    private ArrayList<String> addresses = new ArrayList<String>();

    public LocalScheduler() {
        messageControllers.put(ConnectionPool.node.identity().getAddress().toString(),
                new ConcurrentHashMap<Long, Long>());
        peerLastUsed.put(ConnectionPool.node.identity().getAddress().toString(), -1L);
    }

    public void addResource(String address, double latencyPrediction) {
        synchronized (addresses) {
            // add resource to list
            RTT.put(address, latencyPrediction);
            pingResource(address);
            /*
             * Add adress and update probability only when ping is received
             * addresses.add(address);
             * updateProbability();
             */

        }
    }

    public void removeResource(String address) {
        synchronized (addresses) {
            // 1. remove resource from List
            int index = addresses.indexOf(address);
            RTT.remove(address);
            probabilities.remove(index);
            // 2. deregister resource from broker with updated RTT
            // TODO
            updateProbability();
        }
    }

    public String scheduleResource() {
        synchronized (addresses) {
            // Return local node address if waiting for resources or nothing availible
            if (addresses.isEmpty()) {
                if (fullMessageController(ConnectionPool.node.identity().getAddress().toString())) {
                    return null;
                } else {
                    return ConnectionPool.node.identity().getAddress().toString();
                }
            }

            // If any resource selected based on probability
            double random = Math.random();
            double cumulativeProbability = 0.0;
            for (String address : addresses) {
                cumulativeProbability += probabilities.get(addresses.indexOf(address));
                if (random <= cumulativeProbability) {
                    // only return address if peer is availible
                    if (peerAvailible(address) && !fullMessageController(address)) {
                        // if space free and avail return address selection
                        return address;
                    } else if (!peerAvailible(address) && peerLastUsed.get(address) != -1) {
                        // if peer timed out remove peer.
                        removeResource(address);
                    }
                    /*
                     * else if (peerAvailible(address) && fullMessageController(address)) {
                     * // if message controller full reschedule
                     * return scheduleResource();
                     * } else if (!peerAvailible(address) && peerLastUsed.get(address) == -1) {
                     * // if waiting for ping message but some peer availible reschedule
                     * return scheduleResource();
                     * }
                     */

                }
            }

        }
        // if all fails return local node address
        System.err.println("Local Node address used as backup. Check Local Scheduler!!!s");
        return ConnectionPool.node.identity().getAddress().toString();
    }

    private void updateProbability() {
        synchronized (addresses) {
            double sum = RTT.values().stream().mapToDouble(Double::doubleValue).sum();
            probabilities.clear();
            double sumBalance = 0;
            for (Double rtt : RTT.values()) {
                sumBalance += sum / rtt;
            }

            for (Double rtt : RTT.values()) {
                probabilities.add((sum / rtt) / sumBalance);
            }
        }
        System.out.println(probabilities.toString());
    }

    public void updateRTTAvarage(String address, int rtt) {
        synchronized (addresses) {
            RTT.put(address, RTT.get(address) * 0.9 + rtt * 0.1);
            updateProbability();
        }
    }

    // Section for message controller

    private boolean fullMessageController(String hash) {
        if (messageControllers.get(hash).size() >= MAX_MESSAGES) {
            cleanUpMessages(hash);
            return true;
        } else {
            return false;
        }

    }

    public void addToMessageController(String hash, long messageNumber) {
        messageControllers.get(hash).put(messageNumber, System.currentTimeMillis());
    }

    private boolean peerAvailible(String peer) {
        Long lastPeerMessageTime = peerLastUsed.get(peer);
        return (System.currentTimeMillis() - lastPeerMessageTime) < TIMEOUT;

    }

    private synchronized void cleanUpMessages(String hash) {
        if (peerLastUsed.get(hash) != -1) {
            // only clean message controller if not waiting on first message from resource
            long time = System.currentTimeMillis();
            for (var v : messageControllers.get(hash).entrySet()) {
                if (v.getValue() + TIMEOUT < time) {
                    System.out.println("CLEANING EXPIRED MESSAGE: " + v.getKey());
                    messageControllers.get(hash).remove(v.getKey());
                }
            }
        }

    }

    /**
     * When resource is added to scheduler then ping that resource with message
     * number -1.
     * 
     * @param address
     */
    private void pingResource(String address) {
        System.out.println("Sending first Message to Peer: " + address);

        peerLastUsed.put(address, -1L);
        messageControllers.put(address, new ConcurrentHashMap<Long, Long>());
        messageControllers.get(address).put(-1L, System.currentTimeMillis());
        ConnectionPool.node.sendMessage(address, new PeerMessage(-1));
    }

    private void updatePeerLastUsed(String peer) {
        peerLastUsed.put(peer, System.currentTimeMillis());
    }

    public void updateMessageController(DrasylAddress source, PeerMessage peerMessage) {
        if (peerMessage.messageNumber == -1 && !source.equals(ConnectionPool.node.identity().getAddress())) {
            addresses.add(source.toString());
            updateProbability();

        }
        System.out
                .println("Entire execution cost: "
                        + (System.currentTimeMillis()
                                - messageControllers.get(source.toString()).get(peerMessage.messageNumber))
                        + "ms\t\t"
                        + peerMessage.messageNumber);

        messageControllers.get(source.toString()).remove(peerMessage.messageNumber);
        updatePeerLastUsed(source.toString());

    }

}
