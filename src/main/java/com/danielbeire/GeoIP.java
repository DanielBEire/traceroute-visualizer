package com.danielbeire;

import com.google.gson.*;
import java.awt.Point;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class geoIP {

    private static final Gson GSON = new Gson();

    // Small LRU cache so repeated traces are instant
    private static final Map<String, Point.Double> CACHE =
        new LinkedHashMap<String, Point.Double>(512, 0.75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<String, Point.Double> e) {
                return size() > 500;
            }
        };

    public List<Point.Double> getCoordinates(List<String> ips) throws Exception {
        if (ips == null || ips.isEmpty()) return Collections.emptyList();

        // Which IPs do we still need to query?
        List<String> toQuery = new ArrayList<>();
        for (String ip : ips) if (!CACHE.containsKey(ip)) toQuery.add(ip);

        if (!toQuery.isEmpty()) {
            // Batch query to ip-api.com (much faster than per-IP)
            URL url = new URL("http://ip-api.com/batch");
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("POST");
            con.setDoOutput(true);
            con.setRequestProperty("Content-Type", "application/json; charset=UTF-8");

            List<Map<String,String>> body = new ArrayList<>();
            for (String ip : toQuery) body.add(Collections.singletonMap("query", ip));
            try (OutputStream os = con.getOutputStream();
                 OutputStreamWriter w = new OutputStreamWriter(os, StandardCharsets.UTF_8)) {
                w.write(GSON.toJson(body));
            }

            if (con.getResponseCode() == 200) {
                try (BufferedReader in = new BufferedReader(
                        new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8))) {
                    JsonArray arr = JsonParser.parseReader(in).getAsJsonArray();
                    for (int i = 0; i < arr.size(); i++) {
                        JsonObject o = arr.get(i).getAsJsonObject();
                        String ip = toQuery.get(i);
                        if ("success".equals(o.get("status").getAsString())) {
                            double lat = o.get("lat").getAsDouble();
                            double lon = o.get("lon").getAsDouble();
                            CACHE.put(ip, new Point.Double(lon, lat)); // (lon, lat)
                        } else {
                            CACHE.put(ip, null);
                        }
                    }
                }
            } else {
                System.err.println("GeoIP batch failed: HTTP " + con.getResponseCode());
            }
        }

        // Preserve original hop order
        List<Point.Double> out = new ArrayList<>();
        for (String ip : ips) {
            Point.Double p = CACHE.get(ip);
            if (p != null) out.add(p);
        }
        return out;
    }
}
