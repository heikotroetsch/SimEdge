package com.simedge.protocols;

import java.nio.ByteBuffer;
import java.util.Map;

import org.drasyl.identity.DrasylAddress;

import com.simedge.peer.ConnectionPool;
import com.simedge.runtime.ONNX.ONNXRuntime;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtException;

public class PeerProtocol {

    public static void handleMessage(Object payload, DrasylAddress source) {

        PeerMessage peerMessage = new PeerMessage((byte[]) payload);
        if (peerMessage.messageType == PeerMessage.MessageType.EXECUTE) {
            ByteBuffer results = ByteBuffer.allocate(1);
            try {
                ONNXRuntime runtime = null;
                if ((runtime = ConnectionPool.modelCache
                        .getONNXRuntime(ByteBuffer.wrap(peerMessage.modelHash))) == null) {
                    runtime = new ONNXRuntime(
                            ConnectionPool.modelCache.get(ByteBuffer.wrap(peerMessage.modelHash)),
                            peerMessage.indices, peerMessage.dataTye.getDataTypeSize());
                    ConnectionPool.modelCache.putONNXRuntime(ByteBuffer.wrap(peerMessage.modelHash), runtime);
                }

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

                ConnectionPool.node.send(source.toString(), new PeerMessage(results).getMessageBytes());

            } catch (OrtException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

        } else if (peerMessage.messageType == PeerMessage.MessageType.RESULT) {
            // handle result
            System.out.println("Result type");
            ONNXRuntime.printFloatBuffer(ByteBuffer.wrap((byte[]) payload).asFloatBuffer());
        } else {
            System.out.println("No Peer message type type");

        }

    }

}
