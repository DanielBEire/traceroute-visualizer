package com.danielbeire;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Traceroute {

    private static final Pattern IP_PATTERN = Pattern.compile("(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})");

    public List<String> trace(String domain, Consumer<String> onOutput) throws Exception {
        List<String> ips = new ArrayList<>();
        String os = System.getProperty("os.name").toLowerCase();
        String command = os.contains("win") ? "tracert " + domain : "traceroute " + domain;

        Process process = Runtime.getRuntime().exec(command);

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                onOutput.accept(line);
                Matcher matcher = IP_PATTERN.matcher(line);
                if (matcher.find()) {
                    String ip = matcher.group(1);
                    if (!ips.contains(ip)) {
                        ips.add(ip);
                    }
                }
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("Traceroute command failed. Make sure it's installed and you have network connectivity.");
        }

        return ips;
    }
}
