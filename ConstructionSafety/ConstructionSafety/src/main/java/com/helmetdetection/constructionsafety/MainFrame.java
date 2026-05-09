package com.helmetdetection.constructionsafety;

/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

/**
 *
 * @author miraclerizaldysuthelie
 */

import com.helmetdetection.constructionsafety.WebcamCapture;
import com.helmetdetection.constructionsafety.Detection;
import com.helmetdetection.constructionsafety.YoloDetector;

import org.opencv.core.Mat;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Main GUI frame for the helmet detection application.
 * Includes detection logging to a text file.
 */
public class MainFrame extends JFrame {

    private final WebcamCapture webcamCapture;
    private final YoloDetector detector;
    private final DetectionLogger logger;

    private JPanel webcamPanel;
    private JTextArea detectionsTextArea;
    private JButton openButton;
    private JButton closeButton;
    private JLabel logStatusLabel;

    private BufferedImage currentFrame;
    private List<Detection> currentDetections = new ArrayList<>();

    private Thread captureThread;
    private Thread detectionThread;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean detecting = new AtomicBoolean(false);

    private volatile Mat latestFrame = null;
    private final Object frameLock = new Object();

    private static final int DETECTION_INTERVAL_MS = 100;

    public MainFrame(YoloDetector detector) {
        this.webcamCapture = new WebcamCapture();
        this.detector = detector;
        this.logger = new DetectionLogger("logs");

        initUI();
    }

    private void initUI() {
        setTitle("Helmet Detection System");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(5, 5));
        getContentPane().setBackground(new Color(210, 215, 225));

        JPanel mainPanel = new JPanel(new BorderLayout(5, 5));
        mainPanel.setBackground(new Color(210, 215, 225));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        // === LEFT SIDE: Webcam + Controls ===
        JPanel leftPanel = new JPanel(new BorderLayout(5, 5));
        leftPanel.setBackground(new Color(210, 215, 225));

        JLabel webcamTitle = new JLabel("Webcam", SwingConstants.CENTER);
        webcamTitle.setFont(new Font("SansSerif", Font.BOLD, 16));
        leftPanel.add(webcamTitle, BorderLayout.NORTH);

        // Webcam display panel
        webcamPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g;
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                if (currentFrame != null) {
                    int panelW = getWidth();
                    int panelH = getHeight();
                    int imgW = currentFrame.getWidth();
                    int imgH = currentFrame.getHeight();

                    double scale = Math.min((double) panelW / imgW, (double) panelH / imgH);
                    int scaledW = (int) (imgW * scale);
                    int scaledH = (int) (imgH * scale);
                    int offsetX = (panelW - scaledW) / 2;
                    int offsetY = (panelH - scaledH) / 2;

                    g2d.drawImage(currentFrame, offsetX, offsetY, scaledW, scaledH, null);

                    for (Detection det : currentDetections) {
                        Rectangle box = det.getBoundingBox();

                        int bx = offsetX + (int) (box.x * scale);
                        int by = offsetY + (int) (box.y * scale);
                        int bw = (int) (box.width * scale);
                        int bh = (int) (box.height * scale);

                        bx = Math.max(offsetX, Math.min(bx, offsetX + scaledW));
                        by = Math.max(offsetY, Math.min(by, offsetY + scaledH));
                        bw = Math.min(bw, offsetX + scaledW - bx);
                        bh = Math.min(bh, offsetY + scaledH - by);

                        g2d.setColor(det.getColor());
                        g2d.setStroke(new BasicStroke(3));
                        g2d.drawRect(bx, by, bw, bh);

                        String label = det.toString();
                        FontMetrics fm = g2d.getFontMetrics();
                        int labelW = fm.stringWidth(label) + 8;
                        int labelH = fm.getHeight() + 4;

                        g2d.setColor(det.getColor());
                        g2d.fillRect(bx, by - labelH, labelW, labelH);

                        g2d.setColor(Color.WHITE);
                        g2d.setFont(new Font("SansSerif", Font.BOLD, 12));
                        g2d.drawString(label, bx + 4, by - 4);
                    }
                } else {
                    g2d.setColor(Color.BLACK);
                    g2d.fillRect(0, 0, getWidth(), getHeight());
                }
            }
        };
        webcamPanel.setBackground(Color.BLACK);
        webcamPanel.setPreferredSize(new Dimension(800, 600));
        leftPanel.add(webcamPanel, BorderLayout.CENTER);

        // Control buttons + log status
        JPanel controlPanel = new JPanel(new BorderLayout());
        controlPanel.setBackground(new Color(210, 215, 225));

        JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 50, 15));
        buttonsPanel.setBackground(new Color(210, 215, 225));

        openButton = new JButton("Open Webcam");
        openButton.setPreferredSize(new Dimension(140, 30));
        openButton.addActionListener(e -> startWebcam());

        closeButton = new JButton("Close Webcam");
        closeButton.setPreferredSize(new Dimension(140, 30));
        closeButton.setEnabled(false);
        closeButton.addActionListener(e -> stopWebcam());

        buttonsPanel.add(openButton);
        buttonsPanel.add(closeButton);

        // Log status label
        logStatusLabel = new JLabel("Log: Not recording", SwingConstants.CENTER);
        logStatusLabel.setFont(new Font("SansSerif", Font.ITALIC, 11));
        logStatusLabel.setForeground(Color.GRAY);

        controlPanel.add(buttonsPanel, BorderLayout.CENTER);
        controlPanel.add(logStatusLabel, BorderLayout.SOUTH);

        leftPanel.add(controlPanel, BorderLayout.SOUTH);

        mainPanel.add(leftPanel, BorderLayout.CENTER);

        // === RIGHT SIDE: Detections Panel ===
        JPanel rightPanel = new JPanel(new BorderLayout(5, 5));
        rightPanel.setBackground(new Color(210, 215, 225));
        rightPanel.setPreferredSize(new Dimension(250, 0));

        JLabel detectionsTitle = new JLabel("Detections", SwingConstants.CENTER);
        detectionsTitle.setFont(new Font("SansSerif", Font.BOLD, 18));
        rightPanel.add(detectionsTitle, BorderLayout.NORTH);

        detectionsTextArea = new JTextArea();
        detectionsTextArea.setEditable(false);
        detectionsTextArea.setFont(new Font("Monospaced", Font.PLAIN, 13));
        detectionsTextArea.setLineWrap(true);
        detectionsTextArea.setWrapStyleWord(true);
        JScrollPane scrollPane = new JScrollPane(detectionsTextArea);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        rightPanel.add(scrollPane, BorderLayout.CENTER);

        mainPanel.add(rightPanel, BorderLayout.EAST);

        add(mainPanel, BorderLayout.CENTER);

        setSize(1100, 750);
        setLocationRelativeTo(null);
        setMinimumSize(new Dimension(900, 600));

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                stopWebcam();
                detector.close();
                logger.close();
            }
        });
    }

    /**
     * Start webcam capture, detection, and logging.
     */
    private void startWebcam() {
        if (running.get()) return;

        if (!webcamCapture.open()) {
            JOptionPane.showMessageDialog(this,
                    "Failed to open webcam. Please check permissions.\n" +
                            "On macOS: System Settings > Privacy & Security > Camera",
                    "Webcam Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Start the detection logger
        if (logger.open()) {
            logger.logMessage("Webcam session started. Detection active.");
            SwingUtilities.invokeLater(() -> {
                logStatusLabel.setText("Log: Recording → " + logger.getLogFilePath());
                logStatusLabel.setForeground(new Color(0, 150, 0));
            });
        }

        running.set(true);
        openButton.setEnabled(false);
        closeButton.setEnabled(true);

        // Capture thread — runs continuously for smooth video
        captureThread = new Thread(() -> {
            while (running.get()) {
                Mat frame = webcamCapture.readFrame();
                if (frame != null) {
                    currentFrame = matToBufferedImage(frame);

                    synchronized (frameLock) {
                        if (latestFrame != null) {
                            latestFrame.release();
                        }
                        latestFrame = frame.clone();
                        frameLock.notify();
                    }

                    frame.release();

                    SwingUtilities.invokeLater(() -> webcamPanel.repaint());
                }

                try {
                    Thread.sleep(33); // ~30 fps display
                } catch (InterruptedException e) {
                    break;
                }
            }
        }, "Capture-Thread");
        captureThread.setDaemon(true);
        captureThread.start();

        // Detection thread — runs at lower frequency
        detectionThread = new Thread(() -> {
            while (running.get()) {
                Mat frameToDetect = null;

                synchronized (frameLock) {
                    if (latestFrame != null) {
                        frameToDetect = latestFrame.clone();
                    }
                }

                if (frameToDetect != null && detector.isModelLoaded()) {
                    detecting.set(true);
                    List<Detection> detections = detector.detect(frameToDetect);
                    detecting.set(false);

                    currentDetections = detections;

                    // Log detections to file
                    logger.logDetections(detections);

                    // Update text area
                    SwingUtilities.invokeLater(() -> {
                        StringBuilder sb = new StringBuilder();
                        if (detections.isEmpty()) {
                            sb.append("No detections\n");
                        } else {
                            int violations = 0;
                            for (Detection det : detections) {
                                String prefix = det.isViolation() ? "⚠️ " : "  ";
                                sb.append(prefix).append(det.toString()).append("\n");
                                if (det.isViolation()) violations++;
                            }
                            sb.append("\n--- Total: ").append(detections.size());
                            if (violations > 0) {
                                sb.append(" | Violations: ").append(violations);
                            }
                            sb.append(" ---\n");
                        }
                        detectionsTextArea.setText(sb.toString());
                    });

                    frameToDetect.release();
                }

                try {
                    Thread.sleep(DETECTION_INTERVAL_MS);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }, "Detection-Thread");
        detectionThread.setDaemon(true);
        detectionThread.start();
    }

    /**
     * Stop webcam, detection, and logging.
     */
    private void stopWebcam() {
        running.set(false);

        try {
            if (captureThread != null) {
                captureThread.interrupt();
                captureThread.join(1000);
            }
            if (detectionThread != null) {
                detectionThread.interrupt();
                detectionThread.join(1000);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        webcamCapture.close();

        // Close the logger
        logger.close();
        SwingUtilities.invokeLater(() -> {
            logStatusLabel.setText("Log: Saved → " + logger.getLogFilePath());
            logStatusLabel.setForeground(Color.GRAY);
        });

        synchronized (frameLock) {
            if (latestFrame != null) {
                latestFrame.release();
                latestFrame = null;
            }
        }

        currentFrame = null;
        currentDetections.clear();
        detectionsTextArea.setText("");
        webcamPanel.repaint();

        openButton.setEnabled(true);
        closeButton.setEnabled(false);
    }

    /**
     * Convert OpenCV Mat to BufferedImage for display.
     */
    private BufferedImage matToBufferedImage(Mat mat) {
        int type = BufferedImage.TYPE_3BYTE_BGR;
        if (mat.channels() == 1) {
            type = BufferedImage.TYPE_BYTE_GRAY;
        }

        int width = mat.cols();
        int height = mat.rows();

        BufferedImage image = new BufferedImage(width, height, type);
        byte[] targetPixels = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
        mat.get(0, 0, targetPixels);

        return image;
    }
}
