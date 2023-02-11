package com.simedge.protocols;

import java.nio.ByteBuffer;
import java.util.Map;

import org.drasyl.identity.DrasylAddress;
import org.drasyl.node.event.Peer;

import com.simedge.api.SimEdgeAPI;
import com.simedge.logger.Logger;
import com.simedge.peer.ConnectionPool;
import com.simedge.peer.PeerConnection;
import com.simedge.runtime.ONNX.ONNXRuntime;
import com.simedge.scheduling.LocalScheduler;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtException;

public class PeerProtocol {

    public static void handleMessage(PeerMessage peerMessage, DrasylAddress source) {

        if (peerMessage.messageType == PeerMessage.MessageType.EXECUTE) {
            ByteBuffer results = ByteBuffer.allocate(1);
            try {
                ONNXRuntime runtime = null;
                if ((runtime = ConnectionPool.modelCache
                        .getONNXRuntime(ByteBuffer.wrap(peerMessage.modelHash))) == null) {

                    var model = ConnectionPool.modelCache.get(ByteBuffer.wrap(peerMessage.modelHash));
                    if (model == null) {
                        // if model is downloading message is thrown away
                        ConnectionPool.node.sendResultMessage(source.toString(),
                                new PeerMessage(ByteBuffer.allocate(0), 0L, 0L));

                        return;
                    }
                    runtime = new ONNXRuntime(model, peerMessage.indices, peerMessage.dataTye.getDataTypeSize());
                    ConnectionPool.modelCache.putONNXRuntime(ByteBuffer.wrap(peerMessage.modelHash), runtime);
                }
                long start = System.currentTimeMillis();

                OnnxTensor input_tensor;
                switch (peerMessage.dataTye) {
                    case BYTE:

                        input_tensor = OnnxTensor.createTensor(runtime.env, new byte[][] { peerMessage.data.array() });
                        break;
                    case INT:

                        int[] data = new int[peerMessage.data.remaining() / peerMessage.dataTye.getDataTypeSize()];
                        for (int i = 0; i < data.length; i++) {
                            data[i] = peerMessage.data.getInt();
                        }
                        input_tensor = OnnxTensor.createTensor(runtime.env, new int[][] { data });
                        break;
                    case LONG:

                        long[] dataL = new long[peerMessage.data.remaining() / peerMessage.dataTye.getDataTypeSize()];
                        for (int i = 0; i < dataL.length; i++) {
                            dataL[i] = peerMessage.data.getLong();
                        }
                        input_tensor = OnnxTensor.createTensor(runtime.env, new long[][] { dataL });
                        break;
                    case FLOAT:

                        float[] dataF = new float[peerMessage.data.remaining() / peerMessage.dataTye.getDataTypeSize()];
                        for (int i = 0; i < dataF.length; i++) {
                            dataF[i] = peerMessage.data.getFloat();
                        }

                        input_tensor = OnnxTensor.createTensor(runtime.env, new float[][] { dataF });
                        break;
                    case DOUBLE:
                        double[] dataD = new double[peerMessage.data.remaining()
                                / peerMessage.dataTye.getDataTypeSize()];
                        for (int i = 0; i < dataD.length; i++) {
                            dataD[i] = peerMessage.data.getDouble();
                        }

                        input_tensor = OnnxTensor.createTensor(runtime.env, new double[][] { dataD });
                        break;
                    case CHAR:

                        char[] dataC = new char[peerMessage.data.remaining() / peerMessage.dataTye.getDataTypeSize()];
                        for (int i = 0; i < dataC.length; i++) {
                            dataC[i] = peerMessage.data.getChar();
                        }
                        input_tensor = OnnxTensor.createTensor(runtime.env, new char[][] { dataC });
                        break;
                    default:

                        input_tensor = OnnxTensor.createTensor(runtime.env, peerMessage.data.array());
                        break;
                }

                Map<String, OnnxTensor> dense_input = Map.of(peerMessage.inputName, input_tensor);
                results = runtime.execute(dense_input);
                System.out.println("Sending results  to: " + source.toString());

                if ((System.currentTimeMillis() - start) < LocalScheduler.TIMEOUT) {
                    ConnectionPool.node.sendResultMessage(source.toString(),
                            new PeerMessage(results, peerMessage.messageNumber, (System.currentTimeMillis() - start)));
                }

            } catch (OrtException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

        } else if (peerMessage.messageType == PeerMessage.MessageType.RESULT) {
            // handle result
            // ONNXRuntime.printFloatBuffer(ByteBuffer.wrap(peerMessage.data.array()).asFloatBuffer());
            ConnectionPool.scheduler.updateMessageController(source, peerMessage);

        } else if (peerMessage.messageType == PeerMessage.MessageType.PING) {
            // handle PING by sending back result instantly
            if (!ConnectionPool.modelCache.hasModel(peerMessage.modelHash)) {
                BrokerProtocol.downloadModel(ConnectionPool.bytesToHex(peerMessage.modelHash));
            }
            ConnectionPool.node.sendResultMessage(source.toString(),
                    new PeerMessage(ByteBuffer.allocate(1), peerMessage.messageNumber, 0L));
        } else {
            System.out.println("No Peer message type type");

        }

    }

}
