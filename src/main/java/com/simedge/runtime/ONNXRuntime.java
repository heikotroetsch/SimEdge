package com.simedge.runtime;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Map;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.OrtSession.Result;

public class ONNXRuntime {

    public OrtEnvironment env;
    OrtSession session;

    public ONNXRuntime(String modelPath) throws OrtException {
        env = OrtEnvironment.getEnvironment();
        session = env.createSession(modelPath, new OrtSession.SessionOptions());
    }

    public ONNXRuntime(byte[] model) throws OrtException {
        env = OrtEnvironment.getEnvironment();
        session = env.createSession(model, new OrtSession.SessionOptions());
    }

    void execute(Map<String, OnnxTensor> dense_input) throws OrtException {
        // 3.895135498046875000e+01 3.025833129882812500e+02 3.746795654296875000e+02
        // new String[]{"3.895135498046875000e+01", "3.025833129882812500e+02",
        // "3.746795654296875000e+02"}

        long start = System.currentTimeMillis();
        try (Result results = session.run(dense_input)) {
            System.out.println(System.currentTimeMillis() - start);
            // manipulate the results
            for (var result : results) {
                FloatBuffer fb = ((OnnxTensor) result.getValue()).getByteBuffer().asFloatBuffer();
                while (fb.hasRemaining()) {
                    System.out.println(fb.get());
                }
            }

        }

    }

    public static void main(String[] args) {
        try {
            ONNXRuntime runtime = new ONNXRuntime("machineLearning/move_to_acts.onnx");

            float[] values = new float[] { 3.895135498046875000e+01f,
                    3.025833129882812500e+02f, 3.746795654296875000e+02f, 0f };
            FloatBuffer data = floatArrayToBuffer(values);

            OnnxTensor input_tensor = OnnxTensor.createTensor(runtime.env, data, new long[] { 1, 4 });
            Map<String, OnnxTensor> dense_input = Map.of("dense_input", input_tensor);
            runtime.execute(dense_input);
        } catch (OrtException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    // utils

    public static FloatBuffer floatArrayToBuffer(float[] floatArray) {
        ByteBuffer byteBuffer = ByteBuffer
                .allocateDirect(floatArray.length * 4);
        byteBuffer.order(ByteOrder.nativeOrder());
        FloatBuffer floatBuffer = byteBuffer.asFloatBuffer();
        floatBuffer.put(floatArray);/* ww w . jav a 2s . c o m */
        floatBuffer.position(0);
        return floatBuffer;
    }
}

// 2809 punkte geben das dreifache an werten
// iterierend xyz array [2809, 3] 3> xyz
// array([ 15., 52., 339., 434., 570., 730., 868., 938., 976.,
// 1086., 1107., 1198., 1230., 1254., 1314., 1361., 1409., 1424.,
// 1452., 1507., 1590., 1660., 1742., 2139., 2227., 2487., 2514.,
// 2547., 2586., 2619.])