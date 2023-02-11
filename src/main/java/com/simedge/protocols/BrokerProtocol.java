package com.simedge.protocols;

import java.io.File;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.net.ftp.FTPSClient;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import com.simedge.api.SimEdgeAPI;
import com.simedge.broker.client.BrokerThread;
import com.simedge.peer.ConnectionPool;
import com.simedge.utils.NetworkUtils;

public class BrokerProtocol {

    BrokerThread source;
    ConcurrentHashMap<ByteBuffer, Boolean[]> commitedModels;

    // server mesasges
    public static final int FAILURE = 0;
    public static final int HELLO = 1;
    public static final int BYE = 2;
    public static final int GET_RESOURCE = 3;
    public static final int RETURN_RESOURCE = 4;
    public static final int SET_PING = 5;
    public static final int CHECK_MODEL = 6;
    public static final int MODEL_CACHED = 7;
    public static final int MODEL_EXPIRED = 8;
    public static final int LOAD_MODEL = 9;

    public BrokerProtocol(BrokerThread source, ConcurrentHashMap<ByteBuffer, Boolean[]> commitedModels) {
        this.source = source;
        this.commitedModels = commitedModels;
    }

    // Sending messages

    public void REGISTER(int numberResources) {
        Integer[] pings = new Integer[0];
        try {
            pings = NetworkUtils.getPings();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        System.out.println("register device with broker");
        StringBuffer ping = new StringBuffer();
        for (int i : pings) {
            ping.append(i + ";");
        }
        source.messageQueue.add(HELLO + source.peerIdentity + ";" + numberResources + ";"
                + ping.toString() + System.getProperty("line.separator"));
    }

    public void GET_RESOURCE(int numberResources) {
        source.messageQueue.add(GET_RESOURCE + "" + numberResources + ";" + System.getProperty("line.separator"));
    }

    public void RETURN_RESOURCE(String resourceIdentity, double rtt) {
        source.messageQueue
                .add(RETURN_RESOURCE + resourceIdentity + ";" + rtt + ";" + System.getProperty("line.separator"));
        System.out.println("Scheduler: Returned resource: " + resourceIdentity + "\t" + rtt);

    }

    public void CHECK_MODEL(byte[] modelHash) {
        System.out.println("Hashlength: " + modelHash.length);
        source.messageQueue
                .add(CHECK_MODEL + ConnectionPool.bytesToHex(modelHash) + ";" + System.getProperty("line.separator"));
    }

    public void MODEL_CACHED(byte[] modelHash) {
        System.out.println("Hashlength: " + modelHash.length);
        source.messageQueue
                .add(MODEL_CACHED + ConnectionPool.bytesToHex(modelHash) + ";" + System.getProperty("line.separator"));
    }

    public void MODEL_EXPIRED(byte[] modelHash) {
        System.out.println("Hashlength: " + modelHash.length);
        source.messageQueue
                .add(MODEL_EXPIRED + ConnectionPool.bytesToHex(modelHash) + ";" + System.getProperty("line.separator"));
    }

    public void SET_PING() {
        // TODO
    }

    public void BYE() {
        source.messageQueue.add(BYE + System.getProperty("line.separator"));
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

        String hash = content.split(";")[0];
        double latencyPrediction = Double.parseDouble(content.split(";")[1]);

        System.out.println("Adding Resource");
        ConnectionPool.scheduler.addResource(hash, latencyPrediction);
        System.out.println("Added Resource");
    }

    public void process_RETURN_RESOURCE(String content) {
        // broker notification that resource has left.
        // Removes resource from scheduler so it is no longer sent to.
        ConnectionPool.scheduler.removeResource(content);
    }

    public void process_CHECK_MODEL(String content) {
        String[] contents = content.split(";");
        byte[] hash = ConnectionPool.hexToBytes(contents[0]);

        // System.out.print("new Value" +
        // SimEdgeAPI.commitedModels.computeIfPresent(hash,
        // (k, v) -> new Boolean[] { true, contents[1].equalsIgnoreCase("1") ? true :
        // false }));
        this.commitedModels.put(ByteBuffer.wrap(hash),
                new Boolean[] { true, contents[1].equals("0") ? false : true });
    }

    public void process_SET_PING(String content) {
        // TODO
    }

    public void process_LOAD_MODEL(String content) {
        String hash = content.split(";")[0];
        boolean finished = downloadModel(hash);

        if (finished) {
            MODEL_CACHED(ConnectionPool.hexToBytes(hash));
        }

    }

    public static boolean downloadModel(String hash) {
        System.out.println("Downloading Model: " + hash);
        boolean finished = false;
        try {
            FTPSClient ftpClient = new FTPSClient();
            ftpClient.connect("134.155.108.108", 2021);
            ftpClient.enterLocalPassiveMode();

            ftpClient.login("simedge", "hte^W9k$DaZ@ep^q3%b1^A9h6g");
            System.out.print(ftpClient.getReplyString());

            ftpClient.changeWorkingDirectory("modelCache");
            System.out.print(ftpClient.getReplyString());

            System.out.println(Arrays.toString(ftpClient.listFiles()));

            System.out.print(ftpClient.getReplyString());

            long start = System.currentTimeMillis();
            ByteArrayOutputStream file = new ByteArrayOutputStream();

            finished = ftpClient.retrieveFile(hash, file);
            System.out.print(ftpClient.getReplyString());

            byte[] fileArray = file.toByteArray();
            System.out.println(fileArray.length);
            System.out.println(ConnectionPool.bytesToHex(SimEdgeAPI.md.digest(fileArray)));

            if (finished && ConnectionPool.bytesToHex(SimEdgeAPI.md.digest(fileArray)).equalsIgnoreCase(hash)) {
                ConnectionPool.modelCache.put(ByteBuffer.wrap(ConnectionPool.hexToBytes(hash)), fileArray);
                System.out.println(ftpClient.getReplyString());
                System.out.println("Download Took: " + ((System.currentTimeMillis() - start) / 1000) + " seconds");
                ftpClient.disconnect();
            } else {
                System.out.println("Download failed!!! Hash: " + hash + " != "
                        + ConnectionPool.bytesToHex(SimEdgeAPI.md.digest(fileArray)));
                ftpClient.disconnect();

            }

            // TODO add logic that throws out cached model if not enough memory

        } catch (

        IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return finished;
    }

}
