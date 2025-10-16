package com.danielbeire;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Point2D;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class MapPanel extends JPanel {

    // Countries stored as precomputed unit vectors [x,y,z] for speed
    private final List<List<double[]>> countryVecs = new ArrayList<>();
    private List<Point.Double> traceCoordinates;

    // View state
    private double rotationX = Math.toRadians(20);
    private double rotationY = Math.toRadians(-30);
    private double zoom = 1.0;
    private Point lastMousePosition;
    private boolean dragging = false;

    // Styling
    private static final Color BG = new Color(10, 18, 34);
    private static final Color OCEAN_BASE = new Color(36, 72, 140);
    private static final Color OCEAN_HL = new Color(60, 100, 180);
    private static final Color RIM = new Color(180, 210, 255, 60);
    private static final Color COUNTRY_STROKE = new Color(145, 185, 230, 170);
    private static final Color GRID = new Color(180, 200, 230, 70);
    private static final Stroke COUNTRY_STROKE_WIDTH = new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    private static final Stroke GRID_STROKE = new BasicStroke(1.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);

    private final Timer rotationTimer;

    public MapPanel() {
        setBackground(BG);
        setDoubleBuffered(true);
        loadCountryData();

        // gentle auto-rotation (~25fps to reduce CPU)
        rotationTimer = new Timer(40, e -> {
            rotationY += 0.002;
            repaint();
        });
        rotationTimer.start();

        MouseAdapter mouse = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                lastMousePosition = e.getPoint();
                dragging = true;
            }
            @Override public void mouseReleased(MouseEvent e) {
                dragging = false;
            }
            @Override public void mouseDragged(MouseEvent e) {
                int dx = e.getX() - lastMousePosition.x;
                int dy = e.getY() - lastMousePosition.y;
                rotationY += dx * 0.01;
                rotationX -= dy * 0.01;
                rotationX = Math.max(-Math.PI / 2, Math.min(Math.PI / 2, rotationX));
                lastMousePosition = e.getPoint();
                repaint();
            }
            @Override public void mouseWheelMoved(MouseWheelEvent e) {
                zoom -= e.getPreciseWheelRotation() * 0.1;
                zoom = Math.max(0.6, Math.min(3.0, zoom));
                repaint();
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addMouseWheelListener(mouse);
    }

    private void loadCountryData() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("countries.txt")) {
            if (is == null) {
                System.err.println("countries.txt not found on classpath (place in src/main/resources).");
                return;
            }
            try (BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
                List<double[]> current = new ArrayList<>();
                String line;
                while ((line = br.readLine()) != null) {
                    String t = line.trim();
                    if (t.isEmpty()) {
                        if (!current.isEmpty()) {
                            countryVecs.add(new ArrayList<>(current));
                            current.clear();
                        }
                    } else {
                        String[] parts = t.split(",");
                        if (parts.length >= 2) {
                            double lon = Double.parseDouble(parts[0]);
                            double lat = Double.parseDouble(parts[1]);

                            // Precompute unit vector on unit sphere
                            double latRad = Math.toRadians(lat);
                            double lonRad = Math.toRadians(lon);
                            double x = Math.cos(latRad) * Math.cos(lonRad);
                            double y = Math.sin(latRad);
                            double z = Math.cos(latRad) * Math.sin(lonRad);
                            current.add(new double[]{x, y, z});
                        }
                    }
                }
                if (!current.isEmpty()) countryVecs.add(current);
            }
        } catch (Exception e) {
            System.err.println("Failed to load countries.txt");
            e.printStackTrace();
        }
    }

    // Public API
    public void setTraceCoordinates(List<Point.Double> traceCoordinates) {
        this.traceCoordinates = traceCoordinates;
        repaint();
    }
    public void setCoordinates(List<Point.Double> coords) { setTraceCoordinates(coords); }
    public void setCoordinatesFrom2D(List<Point2D.Double> coords2d) {
        if (coords2d == null) { setTraceCoordinates(null); return; }
        List<Point.Double> out = new ArrayList<>(coords2d.size());
        for (Point2D.Double p : coords2d) out.add(new Point.Double(p.x, p.y));
        setTraceCoordinates(out);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        final int w = getWidth(), h = getHeight();
        final int r = (int) (Math.min(w, h) / 2.2 * zoom);
        final int cx = w / 2, cy = h / 2;

        // cache rotation trig once per frame
        final double cosY = Math.cos(rotationY), sinY = Math.sin(rotationY);
        final double cosX = Math.cos(rotationX), sinX = Math.sin(rotationX);

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);

        // background
        g2.setColor(BG);
        g2.fillRect(0, 0, w, h);

        // shadow
        g2.setComposite(AlphaComposite.SrcOver.derive(0.30f));
        g2.setColor(Color.black);
        g2.fillOval(cx - r + 12, cy - r + 16, (r * 2), (r * 2));
        g2.setComposite(AlphaComposite.SrcOver);

        // ocean
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        RadialGradientPaint ocean = new RadialGradientPaint(
                new Point2D.Float(cx - r * 0.25f, cy - r * 0.25f),
                r * 1.05f,
                new float[]{0f, 1f},
                new Color[]{OCEAN_HL, OCEAN_BASE}
        );
        g2.setPaint(ocean);
        g2.fillOval(cx - r, cy - r, r * 2, r * 2);

        // rim glow
        drawAtmosphere(g2, cx, cy, r);

        // graticule (coarser while dragging)
        g2.setStroke(GRID_STROKE);
        g2.setColor(GRID);
        int step = dragging ? 30 : 15;
        int parts = dragging ? 90 : 160;
        drawGraticule(g2, r, cx, cy, step, parts, cosX, sinX, cosY, sinY);

        // countries
        g2.setStroke(COUNTRY_STROKE_WIDTH);
        g2.setColor(COUNTRY_STROKE);
        for (List<double[]> path : countryVecs) {
            drawPathVec(g2, path, r, cx, cy, cosX, sinX, cosY, sinY);
        }

        // traceroute
        if (traceCoordinates != null && !traceCoordinates.isEmpty()) {
            drawTrace(g2, traceCoordinates, r, cx, cy, cosX, sinX, cosY, sinY);
        }

        g2.dispose();
    }

    private void drawAtmosphere(Graphics2D g2, int cx, int cy, int r) {
        RadialGradientPaint atm = new RadialGradientPaint(
                new Point2D.Float(cx, cy),
                r * 1.08f,
                new float[]{0.92f, 1.0f},
                new Color[]{new Color(160, 200, 255, 0), new Color(160, 200, 255, 70)}
        );
        g2.setPaint(atm);
        g2.fillOval(cx - (int)(r * 1.08), cy - (int)(r * 1.08), (int)(r * 2.16), (int)(r * 2.16));
        g2.setColor(RIM);
        g2.setStroke(new BasicStroke(1.2f));
        g2.drawOval(cx - r, cy - r, r * 2, r * 2);
    }

    // project a precomputed unit vector using current rotation
    private Point projectVec(double[] v, int radius, int cx, int cy,
                             double cosX, double sinX, double cosY, double sinY) {
        double x = v[0], y = v[1], z = v[2];
        double tx = x * cosY - z * sinY;
        double tz = x * sinY + z * cosY;
        double ty = y * cosX - tz * sinX;
        tz = y * sinX + tz * cosX;
        if (tz < 0) return null;
        return new Point((int) (cx + tx * radius), (int) (cy - ty * radius));
    }

    private void drawPathVec(Graphics2D g2, List<double[]> path, int radius, int cx, int cy,
                             double cosX, double sinX, double cosY, double sinY) {
        Point last = null;
        for (double[] v : path) {
            Point p = projectVec(v, radius, cx, cy, cosX, sinX, cosY, sinY);
            if (last != null && p != null) {
                g2.drawLine(last.x, last.y, p.x, p.y);
            }
            last = p;
        }
    }

    // lon/lat -> unit vector
    private double[] toVec(double lonDeg, double latDeg) {
        double lon = Math.toRadians(lonDeg);
        double lat = Math.toRadians(latDeg);
        double x = Math.cos(lat) * Math.cos(lon);
        double y = Math.sin(lat);
        double z = Math.cos(lat) * Math.sin(lon);
        return new double[]{x, y, z};
    }

    private void drawGreatCircle(Graphics2D g2, double lon1, double lat1, double lon2, double lat2,
                                 int radius, int cx, int cy,
                                 double cosX, double sinX, double cosY, double sinY) {
        double[] a = toVec(lon1, lat1);
        double[] b = toVec(lon2, lat2);
        double dot = Math.max(-1, Math.min(1, a[0]*b[0] + a[1]*b[1] + a[2]*b[2]));
        double theta = Math.acos(dot);

        int segments = Math.max(8, (int) Math.ceil(theta * 28));
        Point last = null;
        for (int i = 0; i <= segments; i++) {
            double t = (double) i / segments;
            double sinT = Math.sin(theta);
            double s1 = (sinT == 0) ? 1 - t : Math.sin((1 - t) * theta) / sinT;
            double s2 = (sinT == 0) ? t     : Math.sin(t * theta) / sinT;

            double x = s1 * a[0] + s2 * b[0];
            double y = s1 * a[1] + s2 * b[1];
            double z = s1 * a[2] + s2 * b[2];

            double tx = x * cosY - z * sinY;
            double tz = x * sinY + z * cosY;
            double ty = y * cosX - tz * sinX;
            tz = y * sinX + tz * cosX;

            if (tz < 0) { last = null; continue; }
            Point p = new Point((int) (cx + tx * radius), (int) (cy - ty * radius));
            if (last != null) g2.drawLine(last.x, last.y, p.x, p.y);
            last = p;
        }
    }

    private void drawTrace(Graphics2D g2, List<Point.Double> trace, int radius, int cx, int cy,
                           double cosX, double sinX, double cosY, double sinY) {
        for (int i = 0; i < trace.size(); i++) {
            Point.Double curr = trace.get(i);
            if (i > 0) {
                g2.setStroke(new BasicStroke(2.3f));
                g2.setColor(new Color(255, 60, 60));
                Point.Double prev = trace.get(i - 1);
                drawGreatCircle(g2, prev.x, prev.y, curr.x, curr.y, radius, cx, cy, cosX, sinX, cosY, sinY);
            }
            // markers
            double[] v = toVec(curr.x, curr.y);
            Point marker = projectVec(v, radius, cx, cy, cosX, sinX, cosY, sinY);
            if (marker != null) {
                g2.setColor(i == 0 ? Color.GREEN : (i == trace.size() - 1 ? Color.BLUE : Color.ORANGE));
                g2.fillOval(marker.x - 5, marker.y - 5, 10, 10);
                g2.setColor(Color.WHITE);
                g2.drawOval(marker.x - 5, marker.y - 5, 10, 10);
            }
        }
    }

    private void drawGraticule(Graphics2D g2, int radius, int cx, int cy, int stepDeg, int parts,
                               double cosX, double sinX, double cosY, double sinY) {
        for (int lon = -180; lon <= 180; lon += stepDeg) {
            drawGeoLine(g2, lon, -85, lon, 85, radius, cx, cy, parts, cosX, sinX, cosY, sinY);
        }
        for (int lat = -75; lat <= 75; lat += stepDeg) {
            drawGeoLine(g2, -180, lat, 180, lat, radius, cx, cy, parts, cosX, sinX, cosY, sinY);
        }
        g2.setStroke(new BasicStroke(1.2f));
        drawGeoLine(g2, -180, 0, 180, 0, radius, cx, cy, parts, cosX, sinX, cosY, sinY);
        drawGeoLine(g2, 0, -85, 0, 85, radius, cx, cy, parts, cosX, sinX, cosY, sinY);
        g2.setStroke(GRID_STROKE);
    }

    private void drawGeoLine(Graphics2D g2, double lon1, double lat1, double lon2, double lat2,
                             int radius, int cx, int cy, int parts,
                             double cosX, double sinX, double cosY, double sinY) {
        Point last = null;
        for (int i = 0; i <= parts; i++) {
            double t = i / (double) parts;
            double lon = lon1 + (lon2 - lon1) * t;
            double lat = lat1 + (lat2 - lat1) * t;
            double[] v = toVec(lon, lat);
            Point p = projectVec(v, radius, cx, cy, cosX, sinX, cosY, sinY);
            if (last != null && p != null) g2.drawLine(last.x, last.y, p.x, p.y);
            last = p;
        }
    }
}
