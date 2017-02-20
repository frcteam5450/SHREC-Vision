# SHREC-Vision
This is a java application that uses OpenCV, a computer vision library, to process images from an Axis IP Camera. This application tracks reflective tape, and calculates an x, y, and z position and velocity. This data is published to a UDP Socket that is connected to a NI RoboRIO (10.54.50.2) on port 5800.
