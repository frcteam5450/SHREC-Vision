import org.opencv.core.*;
import org.opencv.videoio.*;
import org.opencv.imgproc.*;
import org.opencv.imgcodecs.*;
import java.lang.*;
import java.util.*;

/**
 * 
 * Project: SHREC Vision.
 * 
 * Description: This is a java application that uses OpenCV, a computer vision library,
 * to process images from an Axis IP Camera. This application tracks reflective tape,
 * and calculates an x, y, and z position and velocity. This data is published to a UDP
 * Socket that is connected to a NI RoboRIO (10.54.50.2) on port 5800.
 * 
 * Contributors: Brandon Trabucco - Programming Lead.
 * 
 * References: OpenCV - http://www.opencv.org,
 * Oracle - https://docs.oracle.com,
 * FRC Team 5687 - https://github.com/frc5687/2017-pi-tracker,
 * UDP Socket - https://systembash.com/a-simple-java-udp-server-and-udp-client/.
 * 
 * 
 * 
 * Version: 0.1.0.
 * 
 * Date: February 19, 2017.
 * 
 * Name: USB Camera Stream.
 * 
 * Description: This version of this project implements a simple USB Webcam stream.
 * At a rate of twenty times per second, a frame is retrieved, and processed using OpenCV
 * filters. This program will be expended in the future to calculate x, y, and z
 * positions and velocities, as well as grab frames from an Axis IP Camera.
 * 
 * 
 * 
 * Version: 0.1.1.
 * 
 * Date: February 20, 2017.
 * 
 * Name: Real-time Computer Vision.
 * 
 * Description: This version of this project opens a camera stream from an Axis m1013 IP
 * Camera with a static IP Address connected to a raspberry pi over wi-fi. A frame is
 * pulled from the camera 15 times per second, and the frame is processed using OpenCV
 * filters. A VisionState is communicated between the raspberry pi and roborio that are
 * both also connected via wi-fi. This Vision State is used by the pi for distance measurement
 * purposes.
 * 
 */
class SHRECVision implements Runnable {
	static { System.loadLibrary(Core.NATIVE_LIBRARY_NAME); }
	
	/**
	 * The UDP Socket to post the x, y, and z positions and velocity to
	 */
	private static final UDPClient client = new UDPClient();
	
	/**
	 * These thresholds are clipping points for the Core.inRange() function.
	 * Any color within this range will show as white in the filtered image.
	 */
	private static final Scalar thd_color_low = new Scalar(0, 0, 0);
	private static final Scalar thd_color_high = new Scalar(255, 255, 255);
	
	/**
	 * Thresholds for contour area. These ensure that a countour that is too small
	 * or too large is rejected.
	 */
	private static final double min_area = 0.0;
	private static final double max_area = 1000000.0;
	
	/**
	 * The physical size of the boiler reflective tape
	 */
	private static final double boiler_width = 15.0;
	private static final double boiler_height_top = 4.0;
	private static final double boiler_height_bottom = 2.0;
	private static final double boiler_height_difference = 10.0;
	private static final double boiler_fudge_factor = 1.0;
	
	/**
	 * The physical size of the gear hook reflective tape
	 */
	private static final double gear_width = 2.0;
	private static final double gear_width_difference = 10.25;
	private static final double gear_height = 5.0;
	private static final double gear_fudge_factor = 1.0;
	
	/**
	 * The field of view of the camera
	 */
	private static final double camera_horizontal_fov = 67.0;
	private static final double camera_width = 320.0;
	private static final double camera_height = 240.0;
	
	/**
	 * A pixel cluster size. Any clusters smaller are considered noise and will be removed.
	 */
	private static final Mat element = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(5, 5));
	
	/**
	 * Wait for an amount of time
	 */
	private static boolean wait(int msec) {
		try {
			Thread.sleep(msec);
		} catch(InterruptedException e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}
	
	/**
	 * Keep a running average of the refresh rate
	 */
	private static long mPreviousTime = System.currentTimeMillis();
	private static long mCurrentTime = 0;
	private static double mRate = 0;
	private static double getRate() {
		mCurrentTime = System.currentTimeMillis();
		mRate = 0.4 * mRate + 0.6 * (1000.0 / ((double)(mCurrentTime - mPreviousTime)));
		mPreviousTime = mCurrentTime;
		return mRate;
	}
	private static int i = 0;
	
	/**
	 * This is the entry point of this applicaton
	 */
	public static void main(String[] args) {
		new Thread(new SHRECVision()).start();
	}
	
	/**
	 * This class depends on a run function to begin processing
	 */
	@Override
	public void run() {
		/**
		 * Allow the roborio and radio to begin transmitting a wireless signal
		 */
		wait(5000);
		
		/**
		 * Start the UDP Socket thread
		 */
		new Thread(client).start();
		wait(1000);
		 
		/**
		 * Open a video capture stream
		 */
		VideoCapture capture = new VideoCapture("http://FRC:FRC@10.54.50.3/mjpg/video.mjpg");
		Mat frame = new Mat();
		wait(1000);
		
		if (capture.isOpened()) {
			/**
			 * Video stream opened sucessfully
			 */
			System.out.println("Stream opened successfully");
			
			/**
			 * Begin video processing loop
			 */
			while (client.getVisionState() != UDPClient.VisionState.Disabled) {
				/**
				 * Obtain a video frame
				 */
				boolean frame_opened = capture.read(frame);
				
				/**
				 * Check if frame was read correctly
				 */
				if(frame_opened && client.getVisionState() != UDPClient.VisionState.Idle) {
					/**
					 * Frame read successfully
					 */
					System.out.println("Frame read successfully, processing enabled | " + SHRECVision.mRate + " frames/sec");
					
					/**
					 * Begin processing the frame
					 */
					process(frame);
				} else if(frame_opened && client.getVisionState() == UDPClient.VisionState.Idle) {
					/**
					 * The vision state is idle, no processing necessary
					 */
					System.out.println("Frame read successfully, processing disabled | " + SHRECVision.mRate + " frames/sec");
				} else {
					/**
					 * Error reading frame
					 */
					System.out.println("Error reading frame");
				}
				
				/**
				 * Calculate a refresh rate
				 */
				getRate();
			}
		} else {
			/**
			 * Error opening video stream
			 */
			System.out.println("Error opening video stream");
			
			/**
			 * Retry opening the camera stream
			 */
			client.setVisionState(UDPClient.VisionState.Disabled);
			SHRECVision.this.run();
		}
	}
	
	/**
	 * This processes the incoming frame from the video stream
	 */
	private void process(Mat frame) {
		/**
		 * First, convert the image to the HSV color space
		 */
		Imgproc.cvtColor(frame, frame, Imgproc.COLOR_RGB2HSV);
    	Imgcodecs.imwrite("/home/pi/Desktop/video.png", frame);
		
		/**
		 * Second, apply an HSV color threshold
		 */
		Core.inRange(frame, thd_color_low, thd_color_high, frame);
		
		/**
		 * Remove noise from the frame
		 */
		Imgproc.morphologyEx(frame, frame, Imgproc.MORPH_OPEN, element);
		
		/**
		 * Calculate the x, y, and z position and velocity of the target shape
		 */
		double[] coordinates = new double[6];
		
		/**
		 * Store contours found in the frame
		 */
		List<MatOfPoint> contours = new ArrayList<MatOfPoint>();
    	Mat hierarchy = new Mat();
    	
    	/**
    	 * List found contours in no specified order
    	 */
    	Imgproc.findContours(frame, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE);
    	
    	/**
    	 * Select the largest two contours
    	 */
    	double referenced_area1 = 0;
    	double referenced_area2 = 0;
    	int index1 = -1;
    	int index2 = -1;
    	for (int i = 0; i < contours.size(); i++) {
			double area = Imgproc.contourArea(contours.get(i));
			if ((area > referenced_area1) && (area > min_area) && (area < max_area)) {
				referenced_area2 = referenced_area1;
				referenced_area1 = area;
				index2 = index1;
				index1 = i;
			} else if ((area > referenced_area2) && (area > min_area) && (area < max_area)) {
				referenced_area2 = area;
				index2 = i;
			}
    	}
    	
    	/**
    	 * Check if a matching coutour has been found
    	 */
    	if (index1 != -1 && index2 != -1) {
			/**
			 * Obtain the bounding box of the largest shape
			 */
			Rect boundary1 = Imgproc.boundingRect(contours.get(index1));
			Rect boundary2 = Imgproc.boundingRect(contours.get(index2));
			
			/**
			 * Locate the reflective tape
			 */
			if (client.getVisionState() == UDPClient.VisionState.Boiler) {
				/**
				 * The robot is facing the boiler
				 * Update the z, y, and z positions
				 * Calculating a running average velocity
				 */
				double[] old_coords = coordinates;
				coordinates = new double[] {
					((double)(boundary1.tl().x + boundary2.br().x) / 2.0),
					((double)(boundary1.tl().y + boundary2.br().y) / 2.0),
					gear_fudge_factor * gear_width_difference / (Math.tan(camera_horizontal_fov * (Math.abs(((double)(boundary1.tl().x + boundary1.br().x) / 2.0) - ((double)(boundary2.tl().x + boundary2.br().x) / 2.0)) + (boundary1.width / 2.0) + (boundary2.width / 2.0)) / camera_width)),
					0.4 * old_coords[3] + 0.6 * (coordinates[0] - old_coords[0]),
					0.4 * old_coords[4] + 0.6 * (coordinates[1] - old_coords[1]),
					0.4 * old_coords[5] + 0.6 * (coordinates[2] - old_coords[2]),
				};
			} else if (client.getVisionState() == UDPClient.VisionState.Gear) {
				/**
				 * The robot is facing the gear hook
				 * Update the z, y, and z positions
				 * Calculating a running average velocity
				 */
				double[] old_coords = coordinates;
				coordinates = new double[] {
					((double)(boundary1.tl().x + boundary2.br().x) / 2.0),
					((double)(boundary1.tl().y + boundary2.br().y) / 2.0),
					boiler_fudge_factor * boiler_width / (Math.tan(camera_horizontal_fov * (Math.abs(((double)(boundary1.tl().x + boundary1.br().x) / 2.0) - ((double)(boundary2.tl().x + boundary2.br().x) / 2.0)) + (boundary1.width / 2.0) + (boundary2.width / 2.0)) / camera_width)),
					0.4 * old_coords[3] + 0.6 * (coordinates[0] - old_coords[0]),
					0.4 * old_coords[4] + 0.6 * (coordinates[1] - old_coords[1]),
					0.4 * old_coords[5] + 0.6 * (coordinates[2] - old_coords[2]),
				};
			}
		} else {
			coordinates = new double[] {0, 0, 0, 0, 0, 0};
		}
		
		/**
		 * Send the coordinates to the UDP Socket
		 */
		client.setCoords(coordinates);
	}
	
}
