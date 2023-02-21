package com.simedge.logger;

import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ConcurrentLinkedQueue;

public class Logger extends Thread {

    public ConcurrentLinkedQueue<String> toWrite = new ConcurrentLinkedQueue<String>();
    private FileWriter fw;
    private PrintWriter pw;
    private static final long start = System.currentTimeMillis();
    DatagramSocket ds = null;

    public Logger(boolean enableUDPEnegeryMessages) throws IOException {
        var file = Files.createFile(Paths.get("logs/log_" + getCurrentTimeStamp() + ".csv"));
        fw = new FileWriter(file.toFile());
        pw = new PrintWriter(fw, true);
        if (enableUDPEnegeryMessages) {
            ds = new DatagramSocket();
        }
    }

    public void run() {
        while (true) {
            if (!toWrite.isEmpty()) {
                pw.println(toWrite.poll());
                pw.flush();
            } else {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        }
    }

    private static String getCurrentTimeStamp() {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH_mm_ss");
        Date now = new Date();
        String result = formatter.format(now);
        return result;
    }

    public void logMessageNumber(int messageNumber) throws IOException {
        if (this.ds != null) {
            InetAddress ip = InetAddress.getByName("192.168.0.92");

            // convert the String input into the byte array.

            var buf = ByteBuffer.allocate(8);
            buf.putInt(messageNumber);
            buf.putInt((int) (System.currentTimeMillis() - start));
            var bufArray = buf.array();

            // Step 2 : Create the datagramPacket for sending
            // the data.
            DatagramPacket DpSend = new DatagramPacket(bufArray, bufArray.length, ip, 47474);

            // Step 3 : invoke the send call to actually send
            // the data.
            ds.send(DpSend);
        }

    }

}
