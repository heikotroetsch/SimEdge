package com.simedge.utils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

import org.apache.commons.io.FileUtils;
import org.apache.commons.net.ftp.FTPSClient;

import com.simedge.peer.ConnectionPool;
import com.simedge.protocols.BrokerProtocol;
import com.simedge.runtime.ONNX.ONNXRuntime;

public class LRUCache {
    long MAX_MEMORY;
    long USED_MEMORY = 0L;
    ConcurrentHashMap<ByteBuffer, byte[]> models = new ConcurrentHashMap<ByteBuffer, byte[]>();
    ConcurrentHashMap<ByteBuffer, ONNXRuntime> onnxRuntimes = new ConcurrentHashMap<ByteBuffer, ONNXRuntime>();

    ConcurrentLinkedDeque<ByteBuffer> LRU = new ConcurrentLinkedDeque<ByteBuffer>();
    private ConcurrentHashMap<ByteBuffer, Boolean> downloadingModel = new ConcurrentHashMap<ByteBuffer, Boolean>();

    public LRUCache(long MAX_MEMORY) {
        this.MAX_MEMORY = MAX_MEMORY;
        try {
            loadPersistantLRUCache();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public void putONNXRuntime(ByteBuffer modelHash, ONNXRuntime runtime) {
        onnxRuntimes.put(modelHash, runtime);
    }

    public ONNXRuntime getONNXRuntime(ByteBuffer modelHash) {
        return onnxRuntimes.get(modelHash);
    }

    /**
     * This method will add a new model to the cache and evict the least recently
     * used models in case there is notenough space left in the cache.
     * 
     * @param hash  the sha1 hash of the model
     * @param model the model data
     * @return a ByteBuffer array with all hashes of the models that were evicted
     */
    public ByteBuffer[] put(ByteBuffer hash, byte[] model) {
        System.out.println("PUT Function called \tModel Size: " + models.size() + "\t LRU Size: " + LRU.size());
        // if max memory is no enough for model update it to model size
        if (model.length > MAX_MEMORY) {
            MAX_MEMORY = model.length;
            return put(hash, model);
        }

        // if there is space
        if (MAX_MEMORY - USED_MEMORY >= model.length) {
            if (models.put(hash, model) == null) {
                // add model if absent
                LRU.addFirst(hash);
                USED_MEMORY += model.length;
                return null;
            } else {
                // if present push on top of LRUCache
                LRU.remove(hash);
                LRU.add(hash);
                return null;
            }

        } else {
            // if not enough space evict LRU until there is space

            ArrayList<ByteBuffer> removed = new ArrayList<ByteBuffer>();

            // remove last until memory free
            while (MAX_MEMORY - USED_MEMORY < model.length) {
                ByteBuffer lruHASH = LRU.removeLast();
                // tell broker model is no longer present on client
                ConnectionPool.brokerConnection.brokerProtocol.MODEL_EXPIRED(lruHASH.array());
                removed.add(lruHASH);
                // free used memeory
                USED_MEMORY -= models.get(lruHASH).length;
                byte[] fileData = models.remove(lruHASH);
                onnxRuntimes.remove(lruHASH);

                // try writing the model to disk
                try {
                    Files.write(new File("modelCache/" + ConnectionPool.bytesToHex(lruHASH.array())).toPath(),
                            fileData);
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }

            }

            // now with enough space add the model and return the removed models
            models.put(hash, model);
            LRU.addFirst(hash);
            USED_MEMORY += model.length;

            ByteBuffer[] removedArray = new ByteBuffer[removed.size()];
            for (int i = 0; i < removed.size(); i++) {
                removedArray[i] = removed.get(i);
            }

            return removedArray;
        }
    }

    /**
     * Gets model bytes as array from cache and updates LRU position. If model is
     * not in cache it will load the file from disk.
     * 
     * @param hash hash of the model
     * @return returns the bytes of the model. Null if model is not in cache or on
     *         disk.
     */
    public byte[] get(ByteBuffer hash) {
        System.out.println("GET Function called \tModel Size: " + models.size() + "\t LRU Size: " + LRU.size());

        byte[] data;
        // if model is present in cache than return bytes
        if ((data = models.get(hash)) != null) {
            // if present push on top of LRUCache
            LRU.remove(hash);
            LRU.add(hash);
            return data;
        } else {
            // if not in cache load from disk
            try {
                var file = new File("modelCache/" + ConnectionPool.bytesToHex(hash.array()));
                this.put(hash, FileUtils
                        .readFileToByteArray(file));
            } catch (IOException e) {
                // if not on disk then get from server
                if (downloadingModel.get(hash) != null && downloadingModel.get(hash)) {
                    return null;
                }
                downloadingModel.put(hash, true);

                // TODO do in thread so not blocking

                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        BrokerProtocol.downloadModel(ConnectionPool.bytesToHex(hash.array()));
                        downloadingModel.remove(hash);
                    }
                }).start();

                return null;
                // throw away message after download since it took way too long
            }
        }
        return models.get(hash);
    }

    public void saveModelChacheToDisk() throws IOException {
        BufferedWriter bw = new BufferedWriter(new FileWriter("Persistant_LRUCache", true));
        while (LRU.size() > 0) {
            ByteBuffer toRemove = LRU.removeLast();
            bw.write(ConnectionPool.bytesToHex(toRemove.array()));
            bw.newLine();
            System.out.println("SAVING: " + ConnectionPool.bytesToHex(toRemove.array()));
            Files.write(Path.of("modelCache/" + ConnectionPool.bytesToHex(toRemove.array())),
                    models.remove(toRemove));
        }

        bw.close();

    }

    private void loadPersistantLRUCache() throws IOException {
        BufferedReader br = new BufferedReader(new FileReader("Persistant_LRUCache"));
        String modelHash;
        while ((modelHash = br.readLine()) != null) {
            this.put(ByteBuffer.wrap(ConnectionPool.hexToBytes(modelHash)),
                    FileUtils.readFileToByteArray(new File("modelCache/" + modelHash)));
        }
        br.close();

        new File("Persistant_LRUCache").delete();
    }

}
