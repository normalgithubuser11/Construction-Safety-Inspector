/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.helmetdetection.constructionsafety;

/**
 *
 * @author miraclerizaldysuthelie
 */


import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;

import java.awt.Rectangle;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

import ai.onnxruntime.*;

/**
 * YOLO26 detector using ONNX Runtime.
 * 
 * YOLO26 uses end-to-end detection with output shape (1, 300, 6).
 * Each detection is [x1, y1, x2, y2, confidence, class_id] in xyxy format.
 * NMS is built into the model — only confidence filtering is needed.
 * 
 * Reference: Ultralytics YOLO26 End-to-End Detection documentation.
 */
public class YoloDetector {

    private OrtEnvironment env;
    private OrtSession session;
    private int modelInputWidth = 640;
    private int modelInputHeight = 640;
    private float confidenceThreshold = 0.25f;
    private String[] classNames;
    private boolean modelLoaded = false;

    public YoloDetector() {
        this.classNames = new String[]{"helmet", "head", "person"};
    }

    /**
     * Load the ONNX model from the specified path.
     */
    public boolean loadModel(String modelPath) {
        try {
            env = OrtEnvironment.getEnvironment();
            OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
            opts.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
            opts.setIntraOpNumThreads(2);

            session = env.createSession(modelPath, opts);
            modelLoaded = true;

            System.out.println("Model loaded successfully from: " + modelPath);
            System.out.println("Input info: " + session.getInputInfo());
            System.out.println("Output info: " + session.getOutputInfo());
            return true;

        } catch (Exception e) {
            System.err.println("Error loading model: " + e.getMessage());
            e.printStackTrace();
            modelLoaded = false;
            return false;
        }
    }

    public void setClassNames(String[] classNames) {
        this.classNames = classNames;
    }

    public void setConfidenceThreshold(float threshold) {
        this.confidenceThreshold = threshold;
    }

    // NMS threshold not needed for YOLO26 end-to-end, but keep setter for compatibility
    public void setNmsThreshold(float threshold) {
        // Not used — YOLO26 handles NMS internally
    }

    public boolean isModelLoaded() {
        return modelLoaded;
    }

    /**
     * Run detection on a frame.
     * YOLO26 end-to-end output: (1, 300, 6) → [x1, y1, x2, y2, confidence, class_id]
     */
    public List<Detection> detect(Mat frame) {
        if (!modelLoaded || frame.empty()) {
            return new ArrayList<>();
        }

        try {
            int originalWidth = frame.cols();
            int originalHeight = frame.rows();

            // Preprocess: letterbox resize and normalize
            float[] inputData = preprocess(frame);

            // Create ONNX tensor
            long[] shape = {1, 3, modelInputHeight, modelInputWidth};
            OnnxTensor inputTensor = OnnxTensor.createTensor(env,
                    FloatBuffer.wrap(inputData), shape);

            // Run inference
            String inputName = session.getInputNames().iterator().next();
            OrtSession.Result results = session.run(
                    java.util.Collections.singletonMap(inputName, inputTensor));

            // Get output — shape is (1, 300, 6)
            float[][][] output = (float[][][]) results.get(0).getValue();

            // Postprocess — simple confidence filter, no NMS needed
            List<Detection> detections = postprocess(output, originalWidth, originalHeight);

            inputTensor.close();
            results.close();

            return detections;

        } catch (Exception e) {
            System.err.println("Detection error: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Preprocess frame for YOLO input.
     * Letterbox resize to 640x640, BGR→RGB, normalize to [0,1], CHW format.
     */
    private float[] preprocess(Mat frame) {
        Mat resized = letterbox(frame, modelInputWidth, modelInputHeight);

        Mat rgb = new Mat();
        Imgproc.cvtColor(resized, rgb, Imgproc.COLOR_BGR2RGB);

        int channels = 3;
        int height = modelInputHeight;
        int width = modelInputWidth;
        float[] data = new float[channels * height * width];

        byte[] pixels = new byte[height * width * channels];
        rgb.get(0, 0, pixels);

        for (int c = 0; c < channels; c++) {
            for (int h = 0; h < height; h++) {
                for (int w = 0; w < width; w++) {
                    int pixelIdx = (h * width + w) * channels + c;
                    data[c * height * width + h * width + w] =
                            (pixels[pixelIdx] & 0xFF) / 255.0f;
                }
            }
        }

        rgb.release();
        resized.release();

        return data;
    }

    /**
     * Letterbox resize: pads image to target size maintaining aspect ratio.
     */
    private Mat letterbox(Mat src, int targetWidth, int targetHeight) {
        int srcWidth = src.cols();
        int srcHeight = src.rows();

        float scale = Math.min((float) targetWidth / srcWidth,
                (float) targetHeight / srcHeight);

        int newWidth = Math.round(srcWidth * scale);
        int newHeight = Math.round(srcHeight * scale);

        Mat resized = new Mat();
        Imgproc.resize(src, resized, new Size(newWidth, newHeight),
                0, 0, Imgproc.INTER_LINEAR);

        Mat padded = new Mat(targetHeight, targetWidth, src.type(), new Scalar(114, 114, 114));

        int padX = (targetWidth - newWidth) / 2;
        int padY = (targetHeight - newHeight) / 2;

        Mat roi = padded.submat(padY, padY + newHeight, padX, padX + newWidth);
        resized.copyTo(roi);

        resized.release();
        return padded;
    }

    /**
     * Postprocess YOLO26 end-to-end output.
     * 
     * Output shape: (1, 300, 6)
     * Each row: [x1, y1, x2, y2, confidence, class_id]
     * Coordinates are in xyxy format relative to 640x640 input.
     * No NMS needed — model handles it internally.
     */
    private List<Detection> postprocess(float[][][] output, int originalWidth, int originalHeight) {
        List<Detection> detections = new ArrayList<>();

        // Calculate letterbox scaling parameters
        float scaleX = (float) modelInputWidth / originalWidth;
        float scaleY = (float) modelInputHeight / originalHeight;
        float scale = Math.min(scaleX, scaleY);
        float padX = (modelInputWidth - originalWidth * scale) / 2.0f;
        float padY = (modelInputHeight - originalHeight * scale) / 2.0f;

        // output[0] has shape (300, 6)
        int numDetections = output[0].length;  // 300

        for (int i = 0; i < numDetections; i++) {
            float x1 = output[0][i][0];  // top-left x (in 640x640 space)
            float y1 = output[0][i][1];  // top-left y (in 640x640 space)
            float x2 = output[0][i][2];  // bottom-right x (in 640x640 space)
            float y2 = output[0][i][3];  // bottom-right y (in 640x640 space)
            float confidence = output[0][i][4];
            int classId = Math.round(output[0][i][5]);

            // Filter by confidence threshold
            if (confidence < confidenceThreshold) {
                continue;
            }

            // Scale coordinates from 640x640 letterbox space back to original image
            // Remove padding, then divide by scale
            float origX1 = (x1 - padX) / scale;
            float origY1 = (y1 - padY) / scale;
            float origX2 = (x2 - padX) / scale;
            float origY2 = (y2 - padY) / scale;

            // Clamp to image bounds (Issue 3 - prevent out of bounds)
            origX1 = Math.max(0, Math.min(origX1, originalWidth - 1));
            origY1 = Math.max(0, Math.min(origY1, originalHeight - 1));
            origX2 = Math.max(0, Math.min(origX2, originalWidth - 1));
            origY2 = Math.max(0, Math.min(origY2, originalHeight - 1));

            int bx = Math.round(origX1);
            int by = Math.round(origY1);
            int bw = Math.round(origX2 - origX1);
            int bh = Math.round(origY2 - origY1);

            if (bw > 0 && bh > 0) {
                String label = (classId >= 0 && classId < classNames.length)
                        ? classNames[classId]
                        : "class_" + classId;

                detections.add(new Detection(
                        label,
                        confidence,
                        new Rectangle(bx, by, bw, bh)
                ));
            }
        }

        return detections;
    }

    /**
     * Release resources.
     */
    public void close() {
        try {
            if (session != null) session.close();
            if (env != null) env.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
