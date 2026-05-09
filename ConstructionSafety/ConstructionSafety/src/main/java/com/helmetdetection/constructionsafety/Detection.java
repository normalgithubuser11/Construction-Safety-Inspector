/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.helmetdetection.constructionsafety;


import java.awt.*;

/**
 * Represents a single detection result from the YOLO model.
 * Color-coded based on the 11 classes from the Construction-Hazard-Detection model.
 */
public class Detection {
    private final String label;
    private final float confidence;
    private final Rectangle boundingBox;
    private final Color color;

    public Detection(String label, float confidence, Rectangle boundingBox) {
        this.label = label;
        this.confidence = confidence;
        this.boundingBox = boundingBox;
        this.color = assignColor(label);
    }

    /**
     * Assign color based on safety status:
     * - GREEN: Safety equipment detected (safe condition)
     * - RED: Missing safety equipment (unsafe/violation)
     * - YELLOW: Person detected
     * - BLUE: Objects (machinery, vehicles, cones, poles)
     */
    private Color assignColor(String label) {
        switch (label) {
            // SAFE - Green: safety equipment worn
            case "Hardhat":
                return new Color(0, 200, 0, 200);
            case "Mask":
                return new Color(0, 180, 50, 200);
            case "Safety Vest":
                return new Color(0, 220, 80, 200);

            // UNSAFE - Red: missing safety equipment (violations)
            case "NO-Hardhat":
                return new Color(220, 0, 0, 200);
            case "NO-Mask":
                return new Color(200, 0, 50, 200);
            case "NO-Safety Vest":
                return new Color(180, 0, 30, 200);

            // PERSON - Yellow/Orange
            case "Person":
                return new Color(220, 180, 0, 200);

            // OBJECTS - Blue shades
            case "Safety Cone":
                return new Color(255, 140, 0, 200);   // Orange for cones
            case "Machinery":
                return new Color(0, 100, 220, 200);
            case "Vehicle":
                return new Color(0, 80, 180, 200);
            case "Utility Pole":
                return new Color(128, 0, 255, 200);   // Purple

            default:
                return new Color(150, 150, 150, 200); // Gray for unknown
        }
    }

    public String getLabel() {
        return label;
    }

    public float getConfidence() {
        return confidence;
    }

    public Rectangle getBoundingBox() {
        return boundingBox;
    }

    public Color getColor() {
        return color;
    }

    /**
     * Returns whether this detection represents a safety violation.
     */
    public boolean isViolation() {
        return label.startsWith("NO-");
    }

    /**
     * Returns whether this detection represents safety equipment being worn.
     */
    public boolean isSafe() {
        return label.equals("Hardhat") || label.equals("Mask") || label.equals("Safety Vest");
    }

    @Override
    public String toString() {
        return String.format("%s (%.1f%%)", label, confidence * 100);
    }
}