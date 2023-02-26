package com.simedge.api;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.io.FileUtils;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPSClient;
import com.simedge.logger.Logger;
import com.simedge.peer.ConnectionPool;
import com.simedge.protocols.PeerMessage;
import com.simedge.protocols.PeerMessage.DataType;

/**
 * SimEdge API class
 */
public class SimEdgeAPI {

    public static MessageDigest md;

    private static ConcurrentHashMap<ByteBuffer, Boolean[]> commitedModels = new ConcurrentHashMap<ByteBuffer, Boolean[]>();
    public static Logger logger = null;

    /**
     * Constructor for the SimEdge system.
     * 
     * @param resources                Number of resources the local system provies
     *                                 to the SimEdge system.
     * @param MAX_MEMORY_MB            Max amount of memory that can by used by the
     *                                 model cache.
     * @param enableUDPEnegeryMessages Enable looging UDP messages to UM25C energy
     *                                 logger.
     */
    public SimEdgeAPI(int resources, int MAX_MEMORY_MB, boolean enableUDPEnegeryMessages) {
        try {
            logger = new Logger(enableUDPEnegeryMessages);
            logger.start();
        } catch (IOException e) {
            e.printStackTrace();
        }

        ConnectionPool.initPeer(resources, MAX_MEMORY_MB * 1000000, commitedModels);
        try {

            md = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
    }

    /**
     * Execute ONNX model on edge computing system
     *
     * @param modelHash     sha1 hash of the onnx model file using md.digest
     * @param dataInputName input name of input tensor
     * @param inputData     input data to run the model on
     * @param dType         data type of the result
     * @param indicies      array of indicies that should be returned. Using this
     *                      returns only a part of the results.
     */
    public boolean executeONNX(byte[] modelHash, String dataInputName, byte[] inputData, PeerMessage.DataType dType,
            int[] indicies) {

        String scheduledResource = ConnectionPool.scheduler.scheduleResource(modelHash);
        if (scheduledResource != null) {
            PeerMessage message = new PeerMessage(PeerMessage.MessageType.EXECUTE, dType, inputData, modelHash,
                    dataInputName, indicies);

            ConnectionPool.node.sendMessage(scheduledResource, message);
            return true;
        }

        return false;

    }

    /**
     * Commits a onnx model to the system. If the model has not been commited to the
     * broker yet it will be uploaded to the repository. Otherwise it will be
     * downloaded by clients who require it.
     *
     * @param modelFile       the model file to be comitted
     * @param numberResources the number of resources that should be prepared to
     *                        execute the model
     * @return returns the has of the model that can be used for the exection
     * @throws NoSuchAlgorithmException
     */
    public byte[] commitModel(byte[] modelFile, int numberResources) throws NoSuchAlgorithmException {
        ByteBuffer hash = ByteBuffer.wrap(md.digest(modelFile));
        ConnectionPool.modelCache.put(hash, modelFile);
        commitedModels.put(hash, new Boolean[] { false, false });

        ConnectionPool.brokerConnection.brokerProtocol.CHECK_MODEL(hash.array());

        // Wait while broker is checking if model is present
        while (!commitedModels.get(hash)[0]) {
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
            commitModel(modelFile, numberResources);
        } else {
            // get resources with model
            System.out.println("Broker has the Model " + ConnectionPool.bytesToHex(hash.array()));
        }

        ConnectionPool.brokerConnection.brokerProtocol.GET_RESOURCE(numberResources);
        return hash.array();

    }

    /**
     * Uploading Model
     * 
     * @param hash model hash
     * @param file file byes
     * @return True if successfull
     */
    private static boolean uploadFile(String hash, byte[] file) {
        boolean finished = false;

        System.out.println("UPLOADING HASH: " + ConnectionPool.bytesToHex(SimEdgeAPI.md.digest(file)));
        try {
            FTPSClient ftpClient = new FTPSClient();
            ftpClient.connect("134.155.108.108", 2021);
            System.out.print(ftpClient.getReplyString());
            ftpClient.enterLocalPassiveMode();

            ftpClient.login("simedge", "hte^W9k$DaZ@ep^q3%b1^A9h6g");
            System.out.print(ftpClient.getReplyString());

            ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
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
            e.printStackTrace();
        }

        return finished;
    }

    public static void main_Execution_Test(String[] args) {

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

        SimEdgeAPI api = new SimEdgeAPI(4, 1024, true);
        try {
            byte[] model = FileUtils.readFileToByteArray(new File("ML_Models/net_combined_moves2coords.onnx"));

            byte[] modelHash = api.commitModel(model, 4);
            String file = FileUtils.readFileToString(new File("machineLearning/motions/fast_100.csv"),
                    Charset.defaultCharset());

            while (true) {
                executeFromCSV(file, api, modelHash, "dense_input", PeerMessage.DataType.FLOAT, indices);
            }

        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    /**
     * Test method for running tasks from CSV input.
     * 
     * @param file      CSV input file
     * @param api       SimEdge API
     * @param modelHash Model hash
     * @param string    Input name string
     * @param f         Data Type
     * @param indices   Indicies
     * @throws FileNotFoundException
     * @throws IOException
     */
    private static void executeFromCSV(String file, SimEdgeAPI api, byte[] modelHash, String string, DataType f,
            int[] indices)
            throws FileNotFoundException, IOException {

        var lineIterator = file.lines().iterator();

        while (lineIterator.hasNext()) {
            var line = lineIterator.next();
            ByteBuffer moves = ByteBuffer.allocate(16);
            String[] values = line.split(" ");
            moves.putFloat(Float.valueOf(values[0]));
            moves.putFloat(Float.valueOf(values[1]));
            moves.putFloat(Float.valueOf(values[2]));
            moves.putFloat(0F);
            boolean finished = false;
            while (!finished) {
                finished = api.executeONNX(modelHash, "dense_input", moves.array(), PeerMessage.DataType.FLOAT,
                        indices);
            }
        }

    }

    /**
     * Idle system Initialization
     * 
     * @param args
     */
    public static void mainold(String[] args) {
        new SimEdgeAPI(4, 1024, false);

    }

}
