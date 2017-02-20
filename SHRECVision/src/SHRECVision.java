import org.opencv.core.*;
import org.opencv.videoio.*;
import org.opencv.imgproc.*;
import java.lang.*;

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
 */
class SHRECVision implements Runnable {
	
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
	 * This is the entry point of this applicaton
	 */
	public static void main(String[] args) {
		System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
		new Thread(new SHRECVision()).start();
	}
	
	/**
	 * This class depends on a run function to begin processing
	 */
	@Override
	public void run() {
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
				if(capture.read(frame) && client.getVisionState() != UDPClient.VisionState.Idle) {
					/**
					 * Frame read successfully
					 */
					System.out.println("Frame read successfully, processing enabled");
					
					/**
					 * Begin processing the frame
					 */
					process(frame);
				} else if(capture.read(frame) && client.getVisionState() == UDPClient.VisionState.Idle) {
					/**
					 * The vision state is idle, no processing necessary
					 */
					System.out.println("Frame read successfully, processing disabled");
				} else {
					/**
					 * Error reading frame
					 */
					System.out.println("Error reading frame");
				}
				
				/**
				 * Set a refresh delay for stability
				 */
				wait(50);
			}
		} else {
			/**
			 * Error opening video stream
			 */
			System.out.println("Error opening video stream");
			
			/**
			 * Exit the appliction
			 */
			return;
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
		
		/**
		 * Second, apply an HSV color threshold
		 */
		Core.inRange(frame, thd_color_low, thd_color_high, frame);
		
		/**
		 * Calculate the x, y, and z position and velocity of the target shape
		 */
		double[] coordinates = new double[6];
		
		/**
		 * Locate the reflective tape
		 */
		if (client.getVisionState() == UDPClient.VisionState.Boiler) {
			/**
			 * The robot is facing the boiler
			 */
			coordinates = new double[] {0, 0, 0, 0, 0, 0};
		} else if (client.getVisionState() == UDPClient.VisionState.Gear) {
			/**
			 * The robot is facing the gear hook
			 */
			coordinates = new double[] {0, 0, 0, 0, 0, 0};
		}
		
		/**
		 * Send the coordinates to the UDP Socket
		 */
		client.setCoords(coordinates);
	}
	
}
