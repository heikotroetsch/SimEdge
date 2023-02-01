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

import org.apache.commons.io.FileUtils;
import org.apache.commons.net.ftp.FTPSClient;
import com.simedge.peer.ConnectionPool;
import com.simedge.protocols.PeerMessage;

public class SimEdgeAPI {

    private static ConcurrentHashMap<ByteBuffer, Boolean[]> commitedModels = new ConcurrentHashMap<ByteBuffer, Boolean[]>();

    public SimEdgeAPI(int resources, int MAX_MEMORY_MB) {
        ConnectionPool.initPeer(resources, MAX_MEMORY_MB * 1000000, commitedModels);
    }

    public void executeONNX(byte[] modelFile, String dataInputName, byte[] inputData, PeerMessage.DataType dType,
            int[] indicies) {

        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-1");
            byte[] message = new PeerMessage(PeerMessage.MessageType.EXECUTE, dType, inputData, md.digest(modelFile),
                    dataInputName, indicies).getMessageBytes();
            // TODO fix resource selection with peek. This must smartly get resources and
            // rotate through them
            ConnectionPool.node.send(ConnectionPool.availibleResources.peek(), message);

        } catch (NoSuchAlgorithmException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }

    public void commitModel(byte[] modelFile) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
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

    }

    private static boolean uploadFile(String hash, byte[] file) {
        boolean finished = false;
        try {
            FTPSClient ftpClient = new FTPSClient();
            ftpClient.connect("134.155.108.108", 2021);
            ftpClient.enterLocalPassiveMode();

            ftpClient.login("simedge", "hte^W9k$DaZ@ep^q3%b1^A9h6g");
            System.out.print(ftpClient.getReplyString());

            ftpClient.changeWorkingDirectory("modelCache");
            System.out.print(ftpClient.getReplyString());

            long start = System.currentTimeMillis();
            finished = ftpClient.storeFile(hash,
                    new ByteArrayInputStream(file));
            System.out.println(ftpClient.getReplyString());
            System.out.println("Upload took: " + ((System.currentTimeMillis() - start) / 1000) + " seconds");

        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        return finished;
    }

    public static void main(String[] args) {

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
            api.commitModel(model);
            while (true) {

                api.executeONNX(model, "dense_input", movesbf.array(), PeerMessage.DataType.FLOAT, indices);
            }

        } catch (NoSuchAlgorithmException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }

}
