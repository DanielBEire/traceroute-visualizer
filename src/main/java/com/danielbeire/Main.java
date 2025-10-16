package com.danielbeire;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Point;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;

public class Main extends JFrame {

    private final JTextField domainField;
    private final JButton traceButton;
    private final JTextArea outputArea;
    private final MapPanel mapPanel;
    private final JProgressBar progressBar;

    public Main() {
        setTitle("Traceroute Visualizer - by DanielBEire");
        setSize(1000, 800);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // --- Top Panel for Input ---
        JPanel topPanel = new JPanel(new BorderLayout(10, 10));
        topPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel inputPanel = new JPanel();
        domainField = new JTextField(25);
        traceButton = new JButton("Trace Route");

        inputPanel.add(new JLabel("Domain or IP Address:"));
        inputPanel.add(domainField);
        inputPanel.add(traceButton);

        progressBar = new JProgressBar();
        progressBar.setIndeterminate(false);
        progressBar.setStringPainted(true);
        progressBar.setString("");

        topPanel.add(inputPanel, BorderLayout.CENTER);
        topPanel.add(progressBar, BorderLayout.SOUTH);

        // --- Center Panel for the Map ---
        mapPanel = new MapPanel();
        mapPanel.setBorder(BorderFactory.createEtchedBorder());

        // --- Bottom Panel for Text Output ---
        outputArea = new JTextArea();
        outputArea.setEditable(false);
        outputArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(outputArea);
        scrollPane.setPreferredSize(new Dimension(800, 200));
        scrollPane.setBorder(BorderFactory.createTitledBorder("Traceroute Output"));

        add(topPanel, BorderLayout.NORTH);
        add(mapPanel, BorderLayout.CENTER);
        add(scrollPane, BorderLayout.SOUTH);

        traceButton.addActionListener(e -> {
            String domain = domainField.getText();
            if (domain != null && !domain.trim().isEmpty()) {
                startTrace(domain);
            } else {
                JOptionPane.showMessageDialog(Main.this, "Please enter a domain name or IP address.", "Input Required", JOptionPane.WARNING_MESSAGE);
            }
        });

        setLocationRelativeTo(null);
    }

    private void startTrace(String domain) {
        traceButton.setEnabled(false);
        domainField.setEditable(false);
        outputArea.setText("Starting traceroute for " + domain + "...\nThis may take a moment.\n\n");
        mapPanel.setTraceCoordinates(null);
        progressBar.setIndeterminate(true);
        progressBar.setString("Running traceroute...");

        SwingWorker<List<Point.Double>, String> worker = new SwingWorker<List<Point.Double>, String>() {
            @Override
            protected List<Point.Double> doInBackground() throws Exception {
                Traceroute traceroute = new Traceroute();
                List<String> ips = traceroute.trace(domain, this::publish);

                publish("\nGeolocating IP addresses...");
                geoIP geoIP = new geoIP();
                List<Point.Double> coords = geoIP.getCoordinates(ips);

                publish("\nDrawing map...");
                return coords;
            }

            @Override
            protected void process(List<String> chunks) {
                for (String line : chunks) {
                    outputArea.append(line + "\n");
                    outputArea.setCaretPosition(outputArea.getDocument().getLength());
                }
            }

            @Override
            protected void done() {
                try {
                    List<Point.Double> coords = get();
                    mapPanel.setTraceCoordinates(coords);
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    outputArea.append("\nError: " + cause.getMessage());
                    JOptionPane.showMessageDialog(Main.this, "An error occurred during the trace:\n" + cause.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                } finally {
                    traceButton.setEnabled(true);
                    domainField.setEditable(true);
                    progressBar.setIndeterminate(false);
                    progressBar.setString("Complete");
                }
            }
        };

        worker.execute();
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }

        SwingUtilities.invokeLater(() -> new Main().setVisible(true));
    }
}
