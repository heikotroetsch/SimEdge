package com.simedge.runtime.ONNX;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.OrtSession.Result;
import org.apache.commons.io.FileUtils;

public class ONNXRuntime {

    public enum Error {
        OUTOFBOUNDS("DataTypeSize Wrong or index higher than result array.", (byte) 1);

        private final String errorMessage;
        private final byte message;

        private Error(String errorMessage, byte message) {
            this.errorMessage = errorMessage;
            this.message = message;
        }

        public static String messageOf(byte error) {
            switch (error) {
                case (byte) 1:
                    return ONNXRuntime.Error.OUTOFBOUNDS.errorMessage;
                default:
                    return "Error not specified";
            }
        }
    }

    public OrtEnvironment env = OrtEnvironment.getEnvironment();
    private OrtSession session;
    private int[] indicies;
    private int dataTypeSize;

    /**
     * 
     * @param model        Bytes of the ML ONNX model. Using>
     *                     FileUtils.readFileToByteArray(new File(String Path)),
     * @param indicies     Reduction indicies. From each results only those will be
     *                     returned which position is specified in this indicies
     *                     array. By only returing parts of each model network speed
     *                     can be improved. These results can be interpolated
     *                     afterwards using statistical models. NULL = dont use
     *                     (Return the full results as bytes)
     * @param dataTypeSize The number of bytes for one entry of the used result data
     *                     type
     * @throws OrtException
     */
    public ONNXRuntime(byte[] model, int[] indicies, int dataTypeSize) throws OrtException {
        var sessionOptions = new OrtSession.SessionOptions();
        session = env.createSession(model, sessionOptions);
        this.indicies = indicies;
        this.dataTypeSize = dataTypeSize;
    }

    /**
     * 
     * @param dense_input input that gets used by model to do inference
     * @return returns a Map of OnnxTensors which have a type and the values.
     *         Returns null if something went wrong.
     * @throws OrtException
     */
    public ByteBuffer execute(Map<String, OnnxTensor> dense_input)
            throws OrtException {

        long start = System.currentTimeMillis();
        try (Result results = session.run(dense_input)) {
            System.out.println("Onnx took: " + (System.currentTimeMillis() - start) + "ms");
            Map<String, ByteBuffer> dense_output = new HashMap<String, ByteBuffer>();

            // put the results in a reusable map of OnnxTensors.

            for (var result : results) {
                var outputTensor = (OnnxTensor) result.getValue();

                System.out.println(session.getInputInfo());
                System.out.println(outputTensor.getInfo());

                dense_output.put(result.getKey(), outputTensor.getByteBuffer());

            }
            return reduceResults(this.indicies, dense_output, this.dataTypeSize);
        }

    }

    public static void main(String[] args) {
        try {

            int indices[] = new int[] { 15, 52, 339, 434, 570, 730, 868, 938, 976, 1086, 1107,
                    1198, 1230, 1254, 1314, 1361, 1409, 1424, 1452, 1507, 1590, 1660,
                    1742, 2139, 2227, 2487, 2514,
                    2547, 2586, 2619 };

            ONNXRuntime runtime = new ONNXRuntime(
                    FileUtils.readFileToByteArray(
                            new File("modelCache/baed48a5c57c26eb7efc412cfd26ca6e379e86e3")),
                    indices, 4);

            float[] moves = new float[] { 3.895135498046875000e+01f,
                    3.025833129882812500e+02f, 3.746795654296875000e+02f, 0f };

            var input_tensor = OnnxTensor.createTensor(runtime.env, new float[][] { moves });
            Map<String, OnnxTensor> dense_input = Map.of("dense_input", input_tensor);
            ByteBuffer results;
            results = runtime.execute(dense_input);
            if (results.limit() == 1) {
                System.out.print(ONNXRuntime.Error.messageOf(results.get()));
            } else {
                printFloatBuffer(results.asFloatBuffer());
            }

        } catch (OrtException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }

    // utils

    private static ByteBuffer reduceResults(int[] indicies, Map<String, ByteBuffer> results, int dataTypeSize) {

        if (indicies != null) {
            // reduce by indice
            ByteBuffer output = ByteBuffer.allocate(results.size() * indicies.length * dataTypeSize);

            for (var result : results.entrySet()) {
                try {
                    output.put(reduceOneResults(indicies, result.getValue(), dataTypeSize));
                } catch (IndexOutOfBoundsException e) {
                    output = ByteBuffer.allocate(1);
                    output.put(ONNXRuntime.Error.OUTOFBOUNDS.message);
                }
            }
            output.position(0);
            return output;
        } else {
            // no indice specified so return results as bytes
            int size = 0;
            for (var result : results.entrySet()) {
                size += result.getValue().limit();
            }
            ByteBuffer output = ByteBuffer.allocate(size);
            for (var result : results.entrySet()) {
                output.put(result.getValue());
            }
            output.position(0);
            return output;
        }
    }

    public static int getGPUid() {
        try {
            if (System.getProperty("os.name").contains("Windows")) {
                String line;

                Process p = Runtime.getRuntime().exec("powershell.exe (Get-WmiObject Win32_VideoController).Name");
                BufferedReader input = new BufferedReader(new InputStreamReader(p.getInputStream()));
                int id = 0;
                while ((line = input.readLine()) != null) {
                    if (line.contains("NVIDIA")) {
                        System.out.println("NVIDIA GPU found");
                        System.out.println(line);
                        return id;
                    } else if (line.contains("AMD")) {
                        System.out.println("AMD GPU found");
                        System.out.println(line);
                        return id;
                    }
                    id++;
                }
                input.close();
            } else if (System.getProperty("os.name").contains("Linux")) {
                String line;

                Process p = Runtime.getRuntime().exec("lspci | grep VGA");
                BufferedReader input = new BufferedReader(new InputStreamReader(p.getInputStream()));
                int id = 0;
                while ((line = input.readLine()) != null) {
                    if (line.contains("NVIDIA")) {
                        System.out.println("NVIDIA GPU found");
                        System.out.println(line);
                        return id;
                    } else if (line.contains("AMD")) {
                        System.out.println("AMD GPU found");
                        System.out.println(line);
                        return id;
                    }
                    id++;
                }
                input.close();
            }
        } catch (Exception e) {
            System.err.println("Could not determine GPU");
        }
        System.out.println("No GPU found");
        return -1;
    }

    public static ByteBuffer reduceOneResults(int[] indices, ByteBuffer results, int dataTypeSize)
            throws IndexOutOfBoundsException {

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