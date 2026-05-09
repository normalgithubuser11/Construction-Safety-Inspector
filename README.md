### ** AI-POWERED CONSTRUCTION SAFETY INSPECTOR**

> Main Gist: It's a Java Project that utilizes Webcam detection using a pre-trained Yolo26 Model (by yihong1120)  
  
**Classes Detected:**  
0: Hardhat  
1: Mask  
2: NO-Hardhat  
3: NO-Mask  
4: NO-Safety Vest  
5: Person  
6: Safety Cone  
7: Safety Vest  
8: Machinery  
9: Utility Pole  
10: Vehicle  

**Language Used: JAVA**  

Main Features:
1. Safety Inspection via pre-trained YOLO26 model
2. I/O Handling: All detections and violation of safety is detected in a log file (txt file) in the logs folder
3. Control Buttons to turn on and off the webcam

**References:**  
1. Pre-trained model used for the time being by [yihong1120](https://huggingface.co/yihong1120/Construction-Hazard-Detection)
2. Ultralytics Model: [Yolo26](https://github.com/ultralytics)
3. Inspired by a study: _Construction Site Hazards Identification Using Deep Learning and Computer Vision_ [(Alateeq et al., 2023)](https://www.mdpi.com/2071-1050/15/3/2358)
