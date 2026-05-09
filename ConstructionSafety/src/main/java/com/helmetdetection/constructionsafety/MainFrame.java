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
 * Matches the provided UI mockup with webcam panel, detections panel, and control buttons.
 * 
 * Addresses Issue 1 (Camera Stuttering):
 * - Separate threads for capture and detection
 * - Detection runs at lower frequency than display
 * - Double buffering for smooth rendering
 */
public class MainFrame extends JFrame {

    private final WebcamCapture webcamCapture;
    private final YoloDetector detector;

    private JPanel webcamPanel;
    private JTextArea detectionsTextArea;
    private JButton openButton;
    private JButton closeButton;

    private BufferedImage currentFrame;
    private List<Detection> currentDetections = new ArrayList<>();

    private Thread captureThread;
    private Thread detectionThread;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean detecting = new AtomicBoolean(false);

    // Shared frame for detection thread
    private volatile Mat latestFrame = null;
    private final Object frameLock = new Object();

    // Frame skip counter for detection (Issue 1 - run detection every N frames)
    private static final int DETECTION_INTERVAL_MS = 100; // Run detection every 100ms

    public MainFrame(YoloDetector detector) {
        this.webcamCapture = new WebcamCapture();
        this.detector = detector;

        initUI();
    }

    private void initUI() {
        setTitle("Helmet Detection System");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(5, 5));
        getContentPane().setBackground(new Color(210, 215, 225));

        // Main content panel
        JPanel mainPanel = new JPanel(new BorderLayout(5, 5));
        mainPanel.setBackground(new Color(210, 215, 225));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        // === LEFT SIDE: Webcam + Controls ===
        JPanel leftPanel = new JPanel(new BorderLayout(5, 5));
        leftPanel.setBackground(new Color(210, 215, 225));

        // Webcam title
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
                    // Calculate scaling to fit panel while maintaining aspect ratio (Issue 3)
                    int panelW = getWidth();
                    int panelH = getHeight();
                    int imgW = currentFrame.getWidth();
                    int imgH = currentFrame.getHeight();

                    double scale = Math.min((double) panelW / imgW, (double) panelH / imgH);
                    int scaledW = (int) (imgW * scale);
                    int scaledH = (int) (imgH * scale);
                    int offsetX = (panelW - scaledW) / 2;
                    int offsetY = (panelH - scaledH) / 2;

                    // Draw frame
                    g2d.drawImage(currentFrame, offsetX, offsetY, scaledW, scaledH, null);

                    // Draw detection boxes (Issue 3 - properly scaled)
                    for (Detection det : currentDetections) {
                        Rectangle box = det.getBoundingBox();

                        // Scale bounding box from image coords to panel coords
                        int bx = offsetX + (int) (box.x * scale);
                        int by = offsetY + (int) (box.y * scale);
                        int bw = (int) (box.width * scale);
                        int bh = (int) (box.height * scale);

                        // Clamp to panel bounds (Issue 3)
                        bx = Math.max(offsetX, Math.min(bx, offsetX + scaledW));
                        by = Math.max(offsetY, Math.min(by, offsetY + scaledH));
                        bw = Math.min(bw, offsetX + scaledW - bx);
                        bh = Math.min(bh, offsetY + scaledH - by);

                        // Draw bounding box
                        g2d.setColor(det.getColor());
                        g2d.setStroke(new BasicStroke(3));
                        g2d.drawRect(bx, by, bw, bh);

                        // Draw label background
                        String label = det.toString();
                        FontMetrics fm = g2d.getFontMetrics();
                        int labelW = fm.stringWidth(label) + 8;
                        int labelH = fm.getHeight() + 4;

                        g2d.setColor(det.getColor());
                        g2d.fillRect(bx, by - labelH, labelW, labelH);

                        // Draw label text
                        g2d.setColor(Color.WHITE);
                        g2d.setFont(new Font("SansSerif", Font.BOLD, 12));
                        g2d.drawString(label, bx + 4, by - 4);
                    }
                } else {
                    // Black background when no frame
                    g2d.setColor(Color.BLACK);
                    g2d.fillRect(0, 0, getWidth(), getHeight());
                }
            }
        };
        webcamPanel.setBackground(Color.BLACK);
        webcamPanel.setPreferredSize(new Dimension(800, 600));
        leftPanel.add(webcamPanel, BorderLayout.CENTER);

        // Control buttons
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 50, 15));
        controlPanel.setBackground(new Color(210, 215, 225));

        openButton = new JButton("Open Webcam");
        openButton.setPreferredSize(new Dimension(140, 30));
        openButton.addActionListener(e -> startWebcam());

        closeButton = new JButton("Close Webcam");
        closeButton.setPreferredSize(new Dimension(140, 30));
        closeButton.setEnabled(false);
        closeButton.addActionListener(e -> stopWebcam());

        controlPanel.add(openButton);
        controlPanel.add(closeButton);
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

        // Window settings
        setSize(1100, 750);
        setLocationRelativeTo(null);
        setMinimumSize(new Dimension(900, 600));

        // Cleanup on close
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                stopWebcam();
                detector.close();
            }
        });
    }

    /**
     * Start webcam capture and detection in separate threads.
     * Using separate threads addresses Issue 1 (Camera Stuttering) by:
     * - Capture thread runs at full speed for smooth display
     * - Detection thread runs at reduced frequency to avoid blocking
     */
    private void startWebcam() {
        if (running.get()) return;

        if (!webcamCapture.open()) {
            JOptionPane.showMessageDialog(this,
                    "Failed to open webcam. Please check permissions.\n" +
                            "On macOS, ensure camera access is granted in System Preferences > Privacy & Security.",
                    "Webcam Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        running.set(true);
        openButton.setEnabled(false);
        closeButton.setEnabled(true);

        // Capture thread - runs continuously for smooth video (Issue 1)
        captureThread = new Thread(() -> {
            while (running.get()) {
                Mat frame = webcamCapture.readFrame();
                if (frame != null) {
                    // Update display frame
                    currentFrame = matToBufferedImage(frame);

                    // Provide frame to detection thread
                    synchronized (frameLock) {
                        if (latestFrame != null) {
                            latestFrame.release();
                        }
                        latestFrame = frame.clone();
                        frameLock.notify();
                    }

                    frame.release();

                    // Repaint at ~30fps
                    SwingUtilities.invokeLater(() -> webcamPanel.repaint());
                }

                // Small sleep to prevent CPU overload (Issue 1)
                try {
                    Thread.sleep(33); // ~30 fps display
                } catch (InterruptedException e) {
                    break;
                }
            }
        }, "Capture-Thread");
        captureThread.setDaemon(true);
        captureThread.start();

        // Detection thread - runs at lower frequency (Issue 1)
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

                    // Update detections (thread-safe)
                    currentDetections = detections;

                    // Update text area
                    SwingUtilities.invokeLater(() -> {
                        StringBuilder sb = new StringBuilder();
                        if (detections.isEmpty()) {
                            sb.append("No detections\n");
                        } else {
                            for (Detection det : detections) {
                                sb.append("• ").append(det.toString()).append("\n");
                            }
                            sb.append("\n--- Total: ").append(detections.size()).append(" ---\n");
                        }
                        detectionsTextArea.setText(sb.toString());
                    });

                    frameToDetect.release();
                }

                // Detection interval (Issue 1 - don't run detection every frame)
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
     * Stop webcam and detection.
     */
    private void stopWebcam() {
        running.set(false);

        // Wait for threads to finish
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
     * Efficient conversion without unnecessary copying.
     */
    private BufferedImage matToBufferedImage(Mat mat) {
        int type = BufferedImage.TYPE_3BYTE_BGR;
        if (mat.channels() == 1) {
            type = BufferedImage.TYPE_BYTE_GRAY;
        }

        int width = mat.cols();
        int height = mat.rows();
        int channels = mat.channels();

        BufferedImage image = new BufferedImage(width, height, type);
        byte[] targetPixels = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
        mat.get(0, 0, targetPixels);

        return image;
    }
}
