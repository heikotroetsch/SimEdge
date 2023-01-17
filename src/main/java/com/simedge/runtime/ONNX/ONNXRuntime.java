package com.simedge.runtime.ONNX;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.OrtSession.Result;
import org.apache.commons.io.FileUtils;

public class ONNXRuntime {

    public OrtEnvironment env = OrtEnvironment.getEnvironment();
    public OrtSession session;

    public ONNXRuntime(String modelPath) throws OrtException {
        session = env.createSession(modelPath, new OrtSession.SessionOptions());
    }

    public ONNXRuntime(byte[] model) throws OrtException {
        session = env.createSession(model, new OrtSession.SessionOptions());
    }

    /**
     * 
     * @param dense_input input that gets used by model to do inference
     * @return returns a Map of OnnxTensors which have a type and the values.
     *         Returns null if something went wrong.
     * @throws OrtException
     */
    public Map<String, ByteBuffer> execute(Map<String, OnnxTensor> dense_input)
            throws OrtException {

        long start = System.currentTimeMillis();
        try (Result results = session.run(dense_input)) {
            System.out.println("Onnx took: " + (System.currentTimeMillis() - start) + "ms");
            Map<String, ByteBuffer> dense_output = new HashMap<String, ByteBuffer>();

            // put the results in a reusable map of OnnxTensors.

            for (var result : results) {
                var outputTensor = (OnnxTensor) result.getValue();
                // TODO return results as array of byte arrays or buffer

                System.out.println(session.getInputInfo());
                System.out.println(outputTensor.getInfo());

                dense_output.put(result.getKey(), outputTensor.getByteBuffer());

            }
            return dense_output;
        }

    }

    public static void main(String[] args) {

        try {
            ONNXRuntime runtime = new ONNXRuntime(
                    FileUtils.readFileToByteArray(new File("machineLearning/move_to_acts.onnx")));

            float[] moves = new float[] { 3.895135498046875000e+01f,
                    3.025833129882812500e+02f, 3.746795654296875000e+02f, 0f };

            var input_tensor = OnnxTensor.createTensor(runtime.env, new float[][] { moves });
            Map<String, OnnxTensor> dense_input = Map.of("dense_input", input_tensor);

            // reduction index
            int indices[] = new int[] { 15, 52, 339, 434, 570, 730, 868, 938, 976, 1086, 1107,
                    1198, 1230, 1254, 1314, 1361, 1409, 1424, 1452, 1507, 1590, 1660,
                    1742, 2139, 2227, 2487, 2514, 2547, 2586, 2619, 2824, 2861, 3148,
                    3243, 3379, 3539, 3677, 3747, 3785, 3895, 3916, 4007, 4039, 4063,
                    4123, 4170, 4218, 4233, 4261, 4316, 4399, 4469, 4551, 4948, 5036,
                    5296, 5323, 5356, 5395, 5428, 2824, 2861, 3148, 3243, 3379, 3539,
                    3677, 3747, 3785, 3895, 3916, 4007, 4039, 4063, 4123, 4170, 4218,
                    4233, 4261, 4316, 4399, 4469, 4551, 4948, 5036, 5296, 5323, 5356,
                    5395, 5428 };

            var results = runtime.execute(dense_input);
            var byteResults = results.get(results.keySet().toArray()[0]);
            indices = new int[] { 4 }; // should be 1.484456E24
            var fb = reduceResults(indices, byteResults, 4);
            printFloatBuffer(fb.asFloatBuffer());

            // var fbsmall = reduceResults(indices, fb);
            // printFloatBuffer(fb);

        } catch (OrtException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    // utils

    public static ByteBuffer reduceResults(int[] indices, ByteBuffer results, int dataTypeSize) {

        ByteBuffer output = ByteBuffer.allocate(indices.length * dataTypeSize);
        for (int index : indices) {

            for (int i = 0; i < dataTypeSize; i++) {
                output.put(results.get(index * dataTypeSize + i));
            }
        }
        output.position(0);
        return output;
    }

    public static void printFloatBuffer(FloatBuffer buffer) {
        System.out.println("Resut Array with size: " + buffer.limit());
        System.out.println("Resut Buffer with allocation: " + buffer.capacity());
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