package com.simedge.runtime.ONNX;

import java.io.BufferedReader;
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

/**
 * Onnx Runtime environment
 */
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
     * Initalize ONNX runtime
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
     * Execute ONNX model using runtime initialization
     * 
     * @param dense_input input that gets used by model to do inference
     * @return returns a Map of OnnxTensors which have a type and the values.
     *         Returns null if something went wrong.
     * @throws OrtException
     */
    public ByteBuffer execute(Map<String, OnnxTensor> dense_input)
            throws OrtException {

        try (Result results = session.run(dense_input)) {
            Map<String, ByteBuffer> dense_output = new HashMap<String, ByteBuffer>();

            // put the results in a reusable map of OnnxTensors.

            for (var result : results) {
                var outputTensor = (OnnxTensor) result.getValue();

                dense_output.put(result.getKey(), outputTensor.getByteBuffer());

            }

            return reduceResults(this.indicies, dense_output, this.dataTypeSize);
        }

    }

    // utils

    /**
     * Util method to reduce result with indicies
     * 
     * @param indicies     Indicies which defines subresult to be return
     * @param results      Reults of ONNX execution
     * @param dataTypeSize Data type size for extracting from byte buffer
     * @return Returns byte buffer of reduced results
     */
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

    /**
     * Experimental method trying to getting GPU device by ID
     * 
     * @return
     */
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

    /**
     * Util method to reduce single result
     * 
     * @param indices      indicies
     * @param results      byte buffer results
     * @param dataTypeSize data type size in bytes to extract from byte buffer
     * @return Return reduction results as byte buffer
     * @throws IndexOutOfBoundsException
     */
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

    /**
     * Util method for printing float buffer for debugging
     * 
     * @param buffer Float buffer to print
     */
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
