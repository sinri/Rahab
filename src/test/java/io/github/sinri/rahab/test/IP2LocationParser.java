package io.github.sinri.rahab.test;

import io.vertx.core.json.JsonArray;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class IP2LocationParser {
    public static void main(String[] args) {
        String csvFile = "/Users/leqee/code/Rahab/debug/IP2LOCATION-LITE-DB1.CSV/china.csv";

        Pattern pattern = Pattern.compile("^\"(\\d+)\",\"(\\d+)\",\"CN\",\"China\"$");
        try (var br = new BufferedReader(new FileReader(csvFile))) {
            JsonArray array = new JsonArray();

            while (true) {
                String line = br.readLine();
                if (line == null) {
                    break;
                }
                Matcher matcher = pattern.matcher(line);
                if (matcher.matches()) {
                    long start = Long.parseLong(matcher.group(1));
                    long end = Long.parseLong(matcher.group(2));

                    String startIP = convertNumberToIPv4(start);
                    String endIP = convertNumberToIPv4(end);

                    //System.out.println("From "+start+" ["+startIP+"] to "+end+" ["+endIP+"]");
                    //System.out.println("list.add("+start+"L); // start from "+startIP);
                    //System.out.println("list.add("+end+"L); // end to "+endIP);

                    array.add(start).add(end);
                }
            }

            System.out.println(array);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    private static Integer convertIPv4ToNumber(String ipv4) {
        //Converts a String that represents an IP to an int.
        try {
            InetAddress i = InetAddress.getByName(ipv4);
            return ByteBuffer.wrap(i.getAddress()).getInt();
        } catch (UnknownHostException e) {
            return null;
        }
    }

    private static String convertNumberToIPv4(long number) {
        //This converts an int representation of ip back to String
        try {
            InetAddress i = InetAddress.getByName(String.valueOf(number));
            return i.getHostAddress();
        } catch (UnknownHostException e) {
            return null;
        }
    }
}
