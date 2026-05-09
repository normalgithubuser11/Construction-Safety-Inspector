package com.helmetdetection.constructionsafety;

/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

/**
 *
 * @author miraclerizaldysuthelie
 */


import javax.swing.*;
import java.io.File;

public class App {

    // Path to the downloaded yolo26n.onnx model
    private static final String MODEL_PATH = "models/yolo26n.onnx";

    // 11 classes from yihong1120/Construction-Hazard-Detection model
    private static final String[] CLASS_NAMES = {
        "Hardhat",          // 0
        "Mask",             // 1
        "NO-Hardhat",       // 2
        "NO-Mask",          // 3
        "NO-Safety Vest",   // 4
        "Person",           // 5
        "Safety Cone",      // 6
        "Safety Vest",      // 7
        "Machinery",        // 8
        "Utility Pole",     // 9
        "Vehicle"           // 10
    };

    public static void main(String[] args) {
        // Load OpenCV native library using OpenPnP's built-in loader
        // Automatically extracts correct native lib for macOS (Intel/Apple Silicon)
        nu.pattern.OpenCV.loadLocally();

        System.setProperty("apple.awt.application.name", "Helmet Detection");
        System.setProperty("apple.laf.useScreenMenuBar", "true");

        // Initialize detector
        YoloDetector detector = new YoloDetector();
        detector.setClassNames(CLASS_NAMES);
        detector.setConfidenceThreshold(0.25f);  // Model recommends conf=0.25
        detector.setNmsThreshold(0.5f);

        // Load model
        File modelFile = new File(MODEL_PATH);
        if (modelFile.exists()) {
            boolean loaded = detector.loadModel(MODEL_PATH);
            if (loaded) {
                System.out.println("Model loaded successfully.");
                System.out.println("Classes: " + CLASS_NAMES.length);
            } else {
                System.err.println("Failed to load model.");
            }
        } else {
            System.err.println("Model file not found at: " + modelFile.getAbsolutePath());
            System.err.println("Please download yolo26n.onnx from:");
            System.err.println("https://huggingface.co/yihong1120/Construction-Hazard-Detection");
            System.err.println("And place it in the models/ directory.");
        }

        // Launch GUI
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                // ignore
            }

            MainFrame frame = new MainFrame(detector);
            frame.setVisible(true);

            if (!detector.isModelLoaded()) {
                JOptionPane.showMessageDialog(frame,
                        "No model loaded!\n\n" +
                                "Download yolo26n.onnx from:\n" +
                                "https://huggingface.co/yihong1120/Construction-Hazard-Detection\n\n" +
                                "Place it at:\n  " + modelFile.getAbsolutePath(),
                        "Model Not Found", JOptionPane.WARNING_MESSAGE);
            }
        });
    }
}