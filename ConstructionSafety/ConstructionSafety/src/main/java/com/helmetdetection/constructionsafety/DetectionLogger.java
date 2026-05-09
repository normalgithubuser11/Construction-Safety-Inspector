/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.helmetdetection.constructionsafety;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Logs detections to a text file with timestamps.
 * Each detection entry includes: date, time, class label, confidence, and bounding box.
 */
public class DetectionLogger {

    private final String logFilePath;
    private final DateTimeFormatter dateTimeFormatter;
    private final DateTimeFormatter fileNameFormatter;
    private PrintWriter writer;
    private boolean isOpen = false;

    // Avoid duplicate logging — only log if detections changed
    private String lastLoggedEntry = "";

    public DetectionLogger(String logDirectory) {
        // Create log directory if it doesn't exist
        File dir = new File(logDirectory);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        // Create log file with current date in filename
        fileNameFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        String fileName = "detections_" + LocalDateTime.now().format(fileNameFormatter) + ".txt";
        this.logFilePath = logDirectory + File.separator + fileName;
    }

    /**
     * Open the log file for writing. Appends to existing file.
     */
    public boolean open() {
        try {
            writer = new PrintWriter(new BufferedWriter(new FileWriter(logFilePath, true)));
            isOpen = true;

            // Write header if file is new/empty
            File file = new File(logFilePath);
            if (file.length() == 0) {
                writer.println("==========================================================");
                writer.println("   CONSTRUCTION SAFETY - DETECTION LOG");
                writer.println("   Started: " + LocalDateTime.now().format(dateTimeFormatter));
                writer.println("==========================================================");
                writer.println();
                writer.println(String.format("%-22s | %-15s | %-8s | %s",
                        "TIMESTAMP", "LABEL", "CONF(%)", "BOUNDING BOX"));
                writer.println("------------------------------------------------------------");
                writer.flush();
            }

            System.out.println("Detection logger opened: " + logFilePath);
            return true;

        } catch (IOException e) {
            System.err.println("Failed to open log file: " + e.getMessage());
            isOpen = false;
            return false;
        }
    }

    /**
     * Log a list of detections with the current timestamp.
     * Only logs if there are actual detections to avoid flooding the file.
     * Skips duplicate entries (same detections in consecutive frames).
     */
    public void logDetections(List<Detection> detections) {
        if (!isOpen || writer == null || detections.isEmpty()) {
            return;
        }

        // Build a summary of current detections to check for duplicates
        StringBuilder summary = new StringBuilder();
        for (Detection det : detections) {
            summary.append(det.getLabel()).append(",");
        }
        String currentEntry = summary.toString();

        // Skip if same as last logged entry (avoids flooding with duplicate frames)
        if (currentEntry.equals(lastLoggedEntry)) {
            return;
        }
        lastLoggedEntry = currentEntry;

        // Log each detection
        String timestamp = LocalDateTime.now().format(dateTimeFormatter);

        for (Detection det : detections) {
            String line = String.format("%-22s | %-15s | %-8.1f | [x=%d, y=%d, w=%d, h=%d]",
                    timestamp,
                    det.getLabel(),
                    det.getConfidence() * 100,
                    det.getBoundingBox().x,
                    det.getBoundingBox().y,
                    det.getBoundingBox().width,
                    det.getBoundingBox().height);

            writer.println(line);

            // Mark violations clearly
            if (det.isViolation()) {
                writer.println("                        ⚠️  SAFETY VIOLATION DETECTED");
            }
        }

        writer.println();  // Blank line between detection groups
        writer.flush();    // Flush immediately so data isn't lost on crash
    }

    /**
     * Log a custom message (e.g., session start/stop).
     */
    public void logMessage(String message) {
        if (!isOpen || writer == null) {
            return;
        }

        String timestamp = LocalDateTime.now().format(dateTimeFormatter);
        writer.println("[" + timestamp + "] " + message);
        writer.flush();
    }

    /**
     * Close the log file.
     */
    public void close() {
        if (writer != null) {
            writer.println();
            writer.println("------------------------------------------------------------");
            logMessage("Session ended.");
            writer.println("==========================================================");
            writer.flush();
            writer.close();
            isOpen = false;
            System.out.println("Detection logger closed: " + logFilePath);
        }
    }

    public boolean isOpen() {
        return isOpen;
    }

    public String getLogFilePath() {
        return logFilePath;
    }
}