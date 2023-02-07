package com.simedge.scheduling;

import java.util.ArrayList;

public class LocalScheduler {

    private ArrayList<Double> probabilities = new ArrayList<Double>();
    private ArrayList<Double> RTT = new ArrayList<Double>();
    private ArrayList<String> addresses = new ArrayList<String>();

    public void addResource(String address, double latencyPrediction) {
        synchronized (addresses) {
            // add resource to list
            addresses.add(address);
            RTT.add(latencyPrediction);
            updateProbability();
        }
    }

    public void removeResource(String address) {
        synchronized (addresses) {
            // 1. remove resource from List
            int index = addresses.indexOf(address);
            RTT.remove(index);
            probabilities.remove(index);
            // 2. deregister resource from broker with updated RTT
            // TODO
            updateProbability();
        }
    }

    public String scheduleResource() {
        synchronized (addresses) {
            double random = Math.random();
            double cumulativeProbability = 0.0;
            for (String address : addresses) {
                cumulativeProbability += probabilities.get(addresses.indexOf(address));
                if (random <= cumulativeProbability) {
                    return address;
                }
            }
            // select client based on probability
            return null;
        }
    }

    private void updateProbability() {
        synchronized (addresses) {
            double sum = RTT.stream().mapToDouble(Double::doubleValue).sum();
            probabilities.clear();
            double sumBalance = 0;
            for (Double rtt : RTT) {
                sumBalance += sum / rtt;
            }

            for (Double rtt : RTT) {
                probabilities.add((sum / rtt) / sumBalance);
            }
        }
        System.out.println(probabilities.toString());
    }

    public void updateRTTAvarage(String address, int rtt) {
        synchronized (addresses) {
            int index = addresses.indexOf(address);
            RTT.set(index, RTT.get(index) * 0.9 + rtt * 0.1);
            updateProbability();
        }
    }

    public boolean isEmpty() {
        return addresses.isEmpty();
    }

}
