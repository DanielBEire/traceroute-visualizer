package com.danielbeire;

import com.google.gson.*;
import java.awt.Point;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

public class GeoIP {

    private final Gson gson = new Gson();

    public List<Point.Double> getCoordinates(List<String> ips) throws Exception {
        if (ips == null || ips.isEmpty()) return Collections.emptyList();

        // ip-api batch: up to 100 queries per request (free tier is fine for traceroute)
        URL url = new URL("http://ip-api.com/batch");
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("POST");
        con.setDoOutput(true);
        con.setRequestProperty("Content-Type", "application/json; charset=UTF-8");

        // Build body: [{"query":"1.2.3.4"}, {"query":"5.6.7.8"}, ...]
        List<Map<String, String>> body = ips.stream()
                .map(ip -> Collections.singletonMap("query", ip))
                .collect(Collectors.toList());
        try (OutputStream os = con.getOutputStream();
             OutputStreamWriter w = new OutputStreamWriter(os, StandardCharsets.UTF_8)) {
            w.write(gson.toJson(body));
        }

        List<Point.Double> coords = new ArrayList<>();
        if (con.getResponseCode() == 200) {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8))) {
                JsonArray arr = JsonParser.parseReader(in).getAsJsonArray();
                for (JsonElement el : arr) {
                    JsonObject o = el.getAsJsonObject();
                    if (o.has("status") && "success".equals(o.get("status").getAsString())) {
                        double lat = o.get("lat").getAsDouble();
                        double lon = o.get("lon").getAsDouble();
                        coords.add(new Point.Double(lon, lat));
                    }
                }
            }
        } else {
            System.err.println("GeoIP batch failed: HTTP " + con.getResponseCode());
        }
        return coords;
    }
}
