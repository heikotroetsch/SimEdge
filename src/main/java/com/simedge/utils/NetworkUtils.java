package com.simedge.utils;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.LinkedList;

public class NetworkUtils {
    public static Integer[] getPings() throws IOException {
        BufferedReader br = new BufferedReader(new FileReader("azureZones"));
        String url;
        LinkedList<Integer> results = new LinkedList<Integer>();
        while ((url = br.readLine()) != null) {
            // ping("http://" + url + ".blob.core.windows.net/probe/ping.js");
            // results.add((int) ping("http://" + url +
            // ".blob.core.windows.net/probe/ping.js"));
            results.add(0);
        }
        br.close();
        return results.toArray(new Integer[results.size()]);
    }

    private static long ping(String URL) {

        long time = System.currentTimeMillis();
        try {
            URL url = new URL(URL);
            HttpURLConnection.setFollowRedirects(false);
            HttpURLConnection huc = (HttpURLConnection) url.openConnection();
            time = System.currentTimeMillis();
            huc.connect();
            return System.currentTimeMillis() - time;

        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return 0L;
    }
}
