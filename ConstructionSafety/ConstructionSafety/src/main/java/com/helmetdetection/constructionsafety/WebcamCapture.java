/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.helmetdetection.constructionsafety;

/**
 *
 * @author miraclerizaldysuthelie
 */

import org.opencv.core.Mat;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;

/**
 * Webcam capture utility using OpenCV.
 * 
 * Addresses Issue 1 (Camera Stuttering):
 * - Uses efficient OpenCV VideoCapture
 * - Sets appropriate resolution (not too high)
 * - Buffer size limited to reduce latency
 */
public class WebcamCapture {

    private VideoCapture capture;
    private boolean isOpen = false;
    private int frameWidth = 640;
    private int frameHeight = 480;

    public WebcamCapture() {
    }

    /**
     * Open the webcam with optimized settings for macOS.
     * Uses AVFoundation backend on macOS for better performance.
     */
    public boolean open() {
        // On macOS, use AVFoundation backend (CAP_AVFOUNDATION = 1200)
        capture = new VideoCapture(0, Videoio.CAP_AVFOUNDATION);

        if (!capture.isOpened()) {
            // Fallback to default backend
            capture = new VideoCapture(0);
        }

        if (capture.isOpened()) {
            // Set resolution - lower resolution = less lag (Issue 1)
            capture.set(Videoio.CAP_PROP_FRAME_WIDTH, frameWidth);
            capture.set(Videoio.CAP_PROP_FRAME_HEIGHT, frameHeight);
            // Set buffer size to 1 to always get the latest frame (reduces lag)
            capture.set(Videoio.CAP_PROP_BUFFERSIZE, 1);
            // Set FPS
            capture.set(Videoio.CAP_PROP_FPS, 30);

            isOpen = true;
            System.out.println("Webcam opened successfully");
            System.out.println("Resolution: " + capture.get(Videoio.CAP_PROP_FRAME_WIDTH)
                    + "x" + capture.get(Videoio.CAP_PROP_FRAME_HEIGHT));
            return true;
        }

        System.err.println("Failed to open webcam");
        return false;
    }

    /**
     * Read a frame from the webcam.
     * Grabs the latest frame to minimize latency (Issue 1).
     */
    public Mat readFrame() {
        if (!isOpen || capture == null) return null;

        Mat frame = new Mat();
        // grab() + retrieve() pattern helps skip buffered frames (Issue 1)
        if (capture.grab()) {
            capture.retrieve(frame);
        }

        if (frame.empty()) {
            frame.release();
            return null;
        }

        return frame;
    }

    /**
     * Close the webcam and release resources.
     */
    public void close() {
        if (capture != null && capture.isOpened()) {
            capture.release();
        }
        isOpen = false;
        System.out.println("Webcam closed");
    }

    public boolean isOpen() {
        return isOpen;
    }

    public int getFrameWidth() {
        return frameWidth;
    }

    public int getFrameHeight() {
        return frameHeight;
    }
}
