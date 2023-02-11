package com.simedge.peer;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.swing.plaf.synth.SynthOptionPaneUI;

import org.apache.commons.io.FileUtils;
import org.drasyl.handler.PeersRttHandler;
import org.drasyl.handler.PeersRttHandler.PeerRtt;
import org.drasyl.handler.PeersRttHandler.PeersRttReport;
import org.drasyl.node.DrasylException;

import com.simedge.broker.client.BrokerThread;
import com.simedge.scheduling.LocalScheduler;
import com.simedge.utils.LRUCache;

public class ConnectionPool {
    public static LocalScheduler scheduler;
    public static LRUCache modelCache;
    public static PeerConnection node;
    public static BrokerThread brokerConnection;

    public static void initPeer(int numberOfResources, long MAX_MEMORY,
            ConcurrentHashMap<ByteBuffer, Boolean[]> commitedModels) {
        modelCache = new LRUCache(MAX_MEMORY);
        try {
            System.out.println("Writing cache files to memory");
            fillModelCache();
        } catch (IOException e) {
            System.err.println("File problems during loading files from disk into cache");
        } catch (NoSuchAlgorithmException e) {
            System.err.println("No Such hashing alogrithm");
        }

        try {
            node = new PeerConnection();
        } catch (DrasylException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        brokerConnection = new BrokerThread(node.identity().getAddress().toString(), commitedModels);
        brokerConnection.start();
        brokerConnection.brokerProtocol.REGISTER(numberOfResources);
        // brokerConnection.brokerProtocol.GET_RESOURCE();

        Thread SystemExitHook = new Thread(() -> {
            System.out.println("Shutting down");
            try {
                Thread.sleep(100);
                System.out.println("Scheduler: Schutting down - Returning resources");
                scheduler.returnAllResources();
                System.out.println("Model Cache: Saving cache to disk");
                modelCache.saveModelChacheToDisk();

            } catch (IOException e) {
                System.err.println("File problems during cache save to disk");
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        Runtime.getRuntime().addShutdownHook(SystemExitHook);

        scheduler = new LocalScheduler();

    }

    private static void fillModelCache() throws IOException, NoSuchAlgorithmException {
        // if folder missing make folder
        File[] models = new File("modelCache/").listFiles();
        System.out.println(Arrays.toString(models));
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        for (File file : models) {
            byte[] fileBytes = FileUtils.readFileToByteArray(file);
            System.out.println(bytesToHex(md.digest(fileBytes)));
            modelCache.put(ByteBuffer.wrap(md.digest(fileBytes)), fileBytes);
        }
    }

    public static String bytesToHex(byte[] bytes) {
        final char[] hexArray = { '0', '1', '2', '3', '4', '5', '6', '7', '8',
                '9', 'a', 'b', 'c', 'd', 'e', 'f' };
        char[] hexChars = new char[bytes.length * 2];
        int v;
        for (int j = 0; j < bytes.length; j++) {
            v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    public static byte[] hexToBytes(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i + 1), 16));
        }

        return data;

    }

}
