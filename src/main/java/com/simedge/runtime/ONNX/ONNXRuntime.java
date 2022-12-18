package com.simedge.runtime.ONNX;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.OrtSession.Result;
import org.apache.commons.io.FileUtils;

public class ONNXRuntime {

    public OrtEnvironment env = OrtEnvironment.getEnvironment();
    public LinkedList<OrtSession> sessions = new LinkedList<OrtSession>();

    public ONNXRuntime(String modelPath) throws OrtException {
        sessions.add(env.createSession(modelPath, new OrtSession.SessionOptions()));
    }

    public ONNXRuntime(byte[] model) throws OrtException {
        sessions.add(env.createSession(model, new OrtSession.SessionOptions()));
    }

    /**
     * 
     * @param models takes a queue of models as byte arrays and adds them to the
     *               sessions that will be executed after each other
     * @throws OrtException
     */
    public ONNXRuntime(Queue<byte[]> models) throws OrtException {
        for (byte[] model : models) {
            sessions.add(env.createSession(model, new OrtSession.SessionOptions()));
        }
    }

    /**
     * 
     * @param dense_input input that gets used by model to do inference
     * @return returns a Map of OnnxTensors which have a type and the values.
     *         Returns null if something went wrong.
     * @throws OrtException
     */
    Map<String, OnnxTensor> execute(Map<String, OnnxTensor> dense_input, OrtSession session) throws OrtException {

        long start = System.currentTimeMillis();
        try (Result results = session.run(dense_input)) {
            System.out.println("Onnx took: " + (System.currentTimeMillis() - start) + "ms");
            Map<String, OnnxTensor> dense_output = new HashMap<String, OnnxTensor>();

            // put the results in a reusable map of OnnxTensors.
            for (var result : results) {
                System.out.println(((OnnxTensor) result.getValue()).getInfo());
                System.out.println(session.getInputInfo());
                if (sessions.lastIndexOf(session) == sessions.size() - 1) {
                    dense_output.put(result.getKey(), ((OnnxTensor) result.getValue()));
                } else {
                    // TODO get right input name
                    dense_output.put("input_1", OnnxTensor.createTensor(env,
                            ((OnnxTensor) result.getValue()).getFloatBuffer(), new long[] { 1, 5 }));

                }

            }
            return dense_output;
        }

    }

    Map<String, OnnxTensor> executeAll(Map<String, OnnxTensor> dense_input) throws OrtException {
        for (OrtSession session : sessions) {
            dense_input = execute(dense_input, session);
        }
        return dense_input;

    }

    public static void main(String[] args) {
        try {
            LinkedList<byte[]> models = new LinkedList<byte[]>();
            models.add(FileUtils.readFileToByteArray(new File("machineLearning/move_to_acts.onnx")));
            models.add(FileUtils.readFileToByteArray(new File("machineLearning/acts_to_coords.onnx")));
            ONNXRuntime runtime = new ONNXRuntime(models);

            float[] values = new float[] { 3.895135498046875000e+01f,
                    3.025833129882812500e+02f, 3.746795654296875000e+02f, 0f };

            float[] acts = new float[] { -3.2441564f,
                    1.484456E24f,
                    -2.2189876E-25f,
                    -4.82704999E14f,
                    9.4420028E16f };
            FloatBuffer data = floatArrayToBuffer(values);

            OnnxTensor input_tensor = OnnxTensor.createTensor(runtime.env, data, new long[] { 1, 4 });
            Map<String, OnnxTensor> dense_input = Map.of("dense_input", input_tensor);

            // reduction index index * 3 + xyz
            int indices[] = new int[] { 15, 52, 339, 434, 570, 730, 868, 938, 976, 1086, 1107,
                    1198, 1230, 1254, 1314, 1361, 1409, 1424, 1452, 1507, 1590, 1660,
                    1742, 2139, 2227, 2487, 2514, 2547, 2586, 2619, 2824, 2861, 3148,
                    3243, 3379, 3539, 3677, 3747, 3785, 3895, 3916, 4007, 4039, 4063,
                    4123, 4170, 4218, 4233, 4261, 4316, 4399, 4469, 4551, 4948, 5036,
                    5296, 5323, 5356, 5395, 5428, 2824, 2861, 3148, 3243, 3379, 3539,
                    3677, 3747, 3785, 3895, 3916, 4007, 4039, 4063, 4123, 4170, 4218,
                    4233, 4261, 4316, 4399, 4469, 4551, 4948, 5036, 5296, 5323, 5356,
                    5395, 5428 };
            printFloatBuffer(reduceResults(indices, getResultsFromMap(runtime.executeAll(dense_input))));

        } catch (OrtException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    // utils

    static FloatBuffer getResultsFromMap(Map<String, OnnxTensor> results) {

        int resultSize = 0;
        for (OnnxTensor result : results.values()) {
            resultSize = result.getByteBuffer().capacity();
        }
        FloatBuffer fb = FloatBuffer.allocate(resultSize);

        for (OnnxTensor result : results.values()) {
            fb.put(result.getByteBuffer().asFloatBuffer());
        }
        return fb;
    }

    public static FloatBuffer floatArrayToBuffer(float[] floatArray) {
        ByteBuffer byteBuffer = ByteBuffer
                .allocateDirect(floatArray.length * 4);
        byteBuffer.order(ByteOrder.nativeOrder());
        FloatBuffer floatBuffer = byteBuffer.asFloatBuffer();
        floatBuffer.put(floatArray);
        floatBuffer.position(0);
        return floatBuffer;
    }

    public static FloatBuffer reduceResults(int[] indices, FloatBuffer results) {

        FloatBuffer output = FloatBuffer.allocate(indices.length);
        for (int index : indices) {
            output.put(results.get(index));
        }

        return output;
    }

    public static void printFloatBuffer(FloatBuffer buffer) {
        System.out.println("Resut Array with size: " + buffer.limit());
        System.out.print("{");
        for (int i = 0; i < buffer.capacity(); i++) {
            System.out.print(buffer.get(i) + ", ");
        }
        System.out.print("}");
        System.out.println("");
    }
}

// 2809 punkte geben das dreifache an werten
// iterierend xyz array [2809, 3] 3> xyz
// array([ 15., 52., 339., 434., 570., 730., 868., 938., 976.,
// 1086., 1107., 1198., 1230., 1254., 1314., 1361., 1409., 1424.,
// 1452., 1507., 1590., 1660., 1742., 2139., 2227., 2487., 2514.,
// 2547., 2586., 2619.])