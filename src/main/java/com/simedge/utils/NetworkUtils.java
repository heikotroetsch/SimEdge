package com.simedge.utils;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.LinkedList;

/**
 * Utils for network profiling via ping mesasges over HTTP
 */
public class NetworkUtils {

    /**
     * Get pings to each azure zone
     * 
     * @return returns a integer array of ping times
     * @throws IOException
     */
    public static Integer[] getPings() throws IOException {
        BufferedReader br = new BufferedReader(new FileReader("azureZones"));
        String url;
        LinkedList<Integer> results = new LinkedList<Integer>();
        while ((url = br.readLine()) != null) {
            ping("http://" + url + ".blob.core.windows.net/probe/ping.js");
            results.add((int) ping("http://" + url + ".blob.core.windows.net/probe/ping.js"));
        }
        br.close();
        return results.toArray(new Integer[results.size()]);
    }

    /**
     * Pings a URL using HTTP connect
     * 
     * @param URL URL to ping
     * @return Returns ping time
     */
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
