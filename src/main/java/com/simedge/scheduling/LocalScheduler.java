package com.simedge.scheduling;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

import org.drasyl.identity.DrasylAddress;
import org.drasyl.util.EvictingQueue;

import com.simedge.api.SimEdgeAPI;
import com.simedge.peer.ConnectionPool;
import com.simedge.protocols.BrokerProtocol;
import com.simedge.protocols.PeerMessage;
import com.simedge.utils.LRUCache;

public class LocalScheduler {

    private ConcurrentHashMap<String, Long> peerLastUsed = new ConcurrentHashMap<String, Long>();
    private ConcurrentHashMap<String, ConcurrentHashMap<Long, Long>> messageControllers = new ConcurrentHashMap<String, ConcurrentHashMap<Long, Long>>();
    // private ConcurrentHashMap<Long, Long> messageController = new
    // ConcurrentHashMap<Long, Long>();
    ConcurrentHashMap<String, EvictingQueue<Double>> evictionQueue = new ConcurrentHashMap<String, EvictingQueue<Double>>();

    private static final int MAX_MESSAGES = 1;
    public static final int TIMEOUT = 500;
    private static boolean stop = false;

    private ConcurrentHashMap<String, Double> probabilities = new ConcurrentHashMap<String, Double>();
    private ConcurrentHashMap<String, Double> RTT = new ConcurrentHashMap<String, Double>();
    private ConcurrentHashMap<String, Double> executionTimes = new ConcurrentHashMap<String, Double>();

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
            executionTimes.put(address, latencyPrediction);
            for (var model : ConnectionPool.brokerConnection.brokerProtocol.getCommitedModels()) {
                pingResource(address, model.array());
            }
            /*
             * Add adress and update probability only when ping is received
             * addresses.add(address);
             * updateProbability();
             */

        }
    }

    public void returnAllResources() {
        stop = true;
        var localAdresses = new ArrayList<String>();
        for (var address : addresses) {
            localAdresses.add(address);
        }
        for (var address : localAdresses) {
            removeResource(address);
        }
    }

    public void removeResource(String address) {
        synchronized (addresses) {
            // 1. deregister resource from broker with updated RTT
            ConnectionPool.brokerConnection.brokerProtocol.RETURN_RESOURCE(address, RTT.get(address));
            // 2. remove resource from List
            RTT.remove(address);
            executionTimes.remove(address);
            probabilities.remove(address);
            peerLastUsed.remove(address);
            messageControllers.remove(address);
            evictionQueue.remove(address);
            addresses.remove(address);

            updateProbability();
        }
    }

    public String scheduleResource(byte[] modelHash) {
        if (stop) {
            return null;
        }

        synchronized (addresses) {
            // Return local node address if waiting for resources or
            // all downloading
            // all unavailbile

            if (addresses.isEmpty() || ConnectionPool.modelCache.downloadingModel() || allUnavailible()) {
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
                cumulativeProbability += probabilities.get(address);
                if (random <= cumulativeProbability) {
                    // only return address if peer is availible
                    if (peerAvailible(address) && !fullMessageController(address)) {
                        // if space free and avail return address selection
                        // check if peer has too large of a timeout
                        if (evictionQueue.get(address).stream().mapToDouble(a -> a).average().getAsDouble() > TIMEOUT) {
                            removeResource(address);
                        } else {
                            return address;
                        }
                    }

                    // else if (!peerAvailible(address) && peerLastUsed.get(address) != -1) {
                    // if peer timed out remove peer.
                    // removeResource(address);
                    // }
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
        // System.err.println("Local Node address used as backup. Check Local
        // Scheduler!!!s");
        // return ConnectionPool.node.identity().getAddress().toString();
        return null;
    }

    private boolean allUnavailible() {
        for (var key : peerLastUsed.keySet()) {
            if (peerAvailible(key)) {
                return false;
            }
        }
        return true;
    }

    private void updateProbability() {
        double sum = RTT.values().stream().mapToDouble(Double::doubleValue).sum()
                + executionTimes.values().stream().mapToDouble(Double::doubleValue).sum();
        probabilities.clear();
        double sumBalance = 0;
        for (var entry : RTT.entrySet()) {
            sumBalance += sum / (entry.getValue() + executionTimes.get(entry.getKey()));
        }

        for (var rtt : RTT.entrySet()) {
            probabilities.put(rtt.getKey(), (sum / (rtt.getValue() + executionTimes.get(rtt.getKey()))) / sumBalance);
        }

        System.out.println(probabilities.toString());
    }

    public void updateRTTAvarage(String address, double rtt, double onnxExecution) {
        synchronized (addresses) {
            if (!address.equals(ConnectionPool.node.identity().getAddress().toString())) {
                RTT.put(address, RTT.get(address) * 0.9 + rtt * 0.1);
                executionTimes.put(address, executionTimes.get(address) * 0.9 + onnxExecution * 0.1);
                evictionQueue.get(address).add(rtt + onnxExecution);
                updateProbability();
            }

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
        boolean hadToCleanUp = false;
        if (peerLastUsed.get(hash) != -1) {
            // only clean message controller if not waiting on first message from resource
            long time = System.currentTimeMillis();
            for (var v : messageControllers.get(hash).entrySet()) {
                if (v.getValue() + TIMEOUT < time) {
                    hadToCleanUp = true;
                    System.out.println("CLEANING EXPIRED MESSAGE: " + v.getKey());
                    messageControllers.get(hash).remove(v.getKey());
                }
            }

            if (hadToCleanUp) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        evictTimedOutResources();
                    }
                }).start();
            }

        }

    }

    private void evictTimedOutResources() {
        synchronized (addresses) {
            // every 100 messages check and remove resources that avarage more than a
            // timeout which reschedules a resource
            var localAdresses = new ArrayList<String>();
            // first duplicate addresses to prevent concurrency problem
            for (var address : addresses) {
                localAdresses.add(address);
            }
            for (String address : localAdresses) {
                if (peerAvailible(address) && ((RTT.get(address) + executionTimes.get(address)) > TIMEOUT)) {
                    removeResource(address);
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
    private void pingResource(String address, byte[] modelHash) {
        System.out.println("Sending first Message to Peer: " + address);

        peerLastUsed.put(address, -1L);
        messageControllers.put(address, new ConcurrentHashMap<Long, Long>());
        evictionQueue.put(address, new EvictingQueue<Double>(10));
        messageControllers.get(address).put(-1L, System.currentTimeMillis());
        ConnectionPool.node.sendMessage(address, new PeerMessage(-1, modelHash));
    }

    private void updatePeerLastUsed(String peer) {
        peerLastUsed.put(peer, System.currentTimeMillis());
    }

    public void updateMessageController(DrasylAddress source, PeerMessage peerMessage) {
        synchronized (addresses) {
            if (peerMessage.messageNumber == -1 && !source.equals(ConnectionPool.node.identity().getAddress())) {
                addresses.add(source.toString());
                updateProbability();

            }

            // logging
            SimEdgeAPI.logger.toWrite
                    .add(System.currentTimeMillis() + ";" + ConnectionPool.node.identity().getAddress().toString() + ";"
                            + source.toString() + ";"
                            + peerMessage.onnxTime + ";" + (System.currentTimeMillis()
                                    - messageControllers.get(source.toString()).get(peerMessage.messageNumber))
                            + ";" + peerMessage.messageNumber);
            try {
                SimEdgeAPI.logger.logMessageNumber((int) peerMessage.messageNumber);
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            System.out
                    .println("Result Message Number: \t"
                            + peerMessage.messageNumber + "\t\tEntire execution cost: "
                            + (System.currentTimeMillis()
                                    - messageControllers.get(source.toString()).get(peerMessage.messageNumber))
                            + "ms");

            updateRTTAvarage(source.toString(), (System.currentTimeMillis()
                    - messageControllers.get(source.toString()).get(peerMessage.messageNumber)) - peerMessage.onnxTime,
                    peerMessage.onnxTime);

            messageControllers.get(source.toString()).remove(peerMessage.messageNumber);

            updatePeerLastUsed(source.toString());

        }

    }

}
