package com.simedge.api;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

import javax.swing.plaf.synth.SynthOptionPaneUI;

import org.apache.commons.io.FileUtils;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPSClient;
import org.drasyl.node.event.Peer;

import com.google.protobuf.Message;
import com.simedge.peer.ConnectionPool;
import com.simedge.protocols.PeerMessage;

public class SimEdgeAPI {

    public static MessageDigest md;

    private static ConcurrentHashMap<ByteBuffer, Boolean[]> commitedModels = new ConcurrentHashMap<ByteBuffer, Boolean[]>();

    public SimEdgeAPI(int resources, int MAX_MEMORY_MB) {
        ConnectionPool.initPeer(resources, MAX_MEMORY_MB * 1000000, commitedModels);
        try {

            md = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public void executeONNX(byte[] modelHash, String dataInputName, byte[] inputData, PeerMessage.DataType dType,
            int[] indicies) {
        if (!ConnectionPool.node.fullMessageController()) {
            // Only send if message controller has space
            if (ConnectionPool.availibleResources.isEmpty()) {
                if (ConnectionPool.node.peerAvailible(ConnectionPool.node.identity().getAddress().toString())) {
                    PeerMessage message = new PeerMessage(PeerMessage.MessageType.EXECUTE, dType, inputData, modelHash,
                            dataInputName, indicies);
                    ConnectionPool.node.sendMessage(ConnectionPool.node.identity().getAddress().toString(), message);
                }
            } else {
                // TODO fix resource selection with peek. This must smartly get resources by
                // best execution result

                String LRU = ConnectionPool.availibleResources.peek();
                if (ConnectionPool.node.peerAvailible(LRU)) {
                    ConnectionPool.availibleResources.add(LRU);
                    ConnectionPool.availibleResources.remove();

                    PeerMessage message = new PeerMessage(PeerMessage.MessageType.EXECUTE, dType, inputData, modelHash,
                            dataInputName, indicies);

                    ConnectionPool.node.sendMessage(LRU, message);
                }

            }
        }

    }

    public byte[] commitModel(byte[] modelFile) throws NoSuchAlgorithmException {
        ByteBuffer hash = ByteBuffer.wrap(md.digest(modelFile));
        ConnectionPool.modelCache.put(hash, modelFile);
        commitedModels.put(hash, new Boolean[] { false, false });

        ConnectionPool.brokerConnection.brokerProtocol.CHECK_MODEL(hash.array());

        // Wait while broker is checking if model is present
        while (!commitedModels.get(hash)[0]) {
            System.out.println(Arrays.toString(commitedModels.get(hash)));
            System.out.println(commitedModels.size());
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        // If model is not present than send the model to the broker
        if (!commitedModels.get(hash)[1]) {
            System.out.println("Uploading the Model " + ConnectionPool.bytesToHex(hash.array()));
            uploadFile(ConnectionPool.bytesToHex(hash.array()), modelFile);
            commitModel(modelFile);
        } else {
            // get resources with model
            System.out.println("Broker has the Model " + ConnectionPool.bytesToHex(hash.array()));
        }

        ConnectionPool.brokerConnection.brokerProtocol.GET_RESOURCE();
        return hash.array();

    }

    private static boolean uploadFile(String hash, byte[] file) {
        boolean finished = false;

        System.out.println("UPLOADING HASH: " + ConnectionPool.bytesToHex(SimEdgeAPI.md.digest(file)));
        try {
            FTPSClient ftpClient = new FTPSClient();
            ftpClient.connect("134.155.108.108", 2021);
            System.out.print(ftpClient.getReplyString());
            ftpClient.enterLocalPassiveMode();

            ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
            System.out.print(ftpClient.getReplyString());

            ftpClient.login("simedge", "hte^W9k$DaZ@ep^q3%b1^A9h6g");
            System.out.print(ftpClient.getReplyString());

            ftpClient.changeWorkingDirectory("modelCache");
            System.out.print(ftpClient.getReplyString());

            long start = System.currentTimeMillis();
            var stream = new ByteArrayInputStream(file);

            finished = ftpClient.storeFile(hash,
                    stream);
            stream.close();
            System.out.println(ftpClient.getReplyString());
            System.out
                    .println("Upload " + finished + ": " + ((System.currentTimeMillis() - start) / 1000) + " seconds");
            ftpClient.disconnect();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        return finished;
    }

    public static void mainold(String[] args) {

        int indices[] = new int[] { 15, 52, 339, 434, 570, 730, 868, 938, 976, 1086, 1107,
                1198, 1230, 1254, 1314, 1361, 1409, 1424, 1452, 1507, 1590, 1660,
                1742, 2139, 2227, 2487, 2514,
                2547, 2586, 2619 };

        float[] moves = new float[] { 3.895135498046875000e+01f,
                3.025833129882812500e+02f, 3.746795654296875000e+02f, 0f };
        ByteBuffer movesbf = ByteBuffer.allocate(moves.length * 4);
        for (var f : moves) {
            movesbf.putFloat(f);
        }

        SimEdgeAPI api = new SimEdgeAPI(4, 1024);
        try {
            byte[] model = FileUtils.readFileToByteArray(new File("ML_Models/net_combined_moves2coords.onnx"));

            byte[] modelHash = api.commitModel(model);
            while (true) {
                api.executeONNX(modelHash, "dense_input", movesbf.array(), PeerMessage.DataType.FLOAT, indices);
            }

        } catch (NoSuchAlgorithmException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }

    public static void main(String[] args) {
        SimEdgeAPI api = new SimEdgeAPI(4, 1024);

    }

}
