import org.opencv.core.*;
import org.opencv.videoio.*;
import org.opencv.imgproc.*;
import org.opencv.imgcodecs.*;
import java.lang.*;
import java.util.*;
import java.io.*;

/**
 * 
 * Project: SHREC Vision.
 * 
 * Description: This is a java application that uses OpenCV, a computer vision library,
 * to process images from an Axis IP Camera. This application tracks reflective tape,
 * and calculates an x, y, and z position and velocity. This data is published to a UDP
 * Socket that is connected to a NI RoboRIO on port 5800.
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
 * Date: February 18, 2017.
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
 * Date: February 19, 2017.
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
 * 
 * 
 * Version: 0.1.2.
 * 
 * Date: February 20, 2017.
 * 
 * Name: Switcheable Object Tracking
 * 
 * Description: This version of this project employs a UDP server to communicate between a
 * roborio and a raspberry pi. The client UDP Socket receives a vision state, which directs
 * the raspberry pi which vision target to open and process. This application dynamically loads
 * vision tracking preferences and thresholds from a text file of the raspberry pi.
 * 
 * 
 * 
 * Version: 0.1.3.
 * 
 * Date: March 2, 2017.
 * 
 * Name: Incidence Angle
 * 
 * Description: This version of this project fixes a number of bugs with the UDP Client, and
 * calculates a horizontal incidence angle with the vision target rather than x and y positions.
 * This calculation is used to incrementally aim towards the vision target. This application
 * publishes an angle to the UDP server to be read by a NI RoboRIO device.
 * 
 * 
 * 
 * Version: 0.1.4.
 * 
 * Date: March 6, 2017.
 * 
 * Name: Robust Improvement
 * 
 * Description: This version of this projects removes a number of bugs that existed with the
 * UDP Client and Server. This code also alters the incidence angle calculation into a form that
 * spans between -FOV/2 to +FOV/2 of the IP camera.
 * 
 * 
 * 
 * Version: 0.1.5.
 * 
 * Date: March 9, 2017.
 * 
 * Name: Successful Gear Placement
 * 
 * Description: This version of this project has been successfully tested streaming an incidence angle
 * calculation to the NI RoboRIO via UDP Port 5800. A robot running this application has been
 * successfully tested delivering a gear during autonomous mode.
 * 
 * 
 * 
 * Version: 0.2.0.
 * 
 * Date: March 19, 2017.
 * 
 * Name: Competition Tested
 *
 * Description: This version of this project has been tested at an FRC Competition, and successfully
 * delivers incidence angle calculations to an NI RoboRIO in real time via a UDP Socket connected on port
 * 5800. Note that all network devices have been assigned a static IP address for easy access, including
 * the raspberry pi running this software.  
 * 
 */
class SHRECVision implements Runnable {
	static { System.loadLibrary(Core.NATIVE_LIBRARY_NAME); }
	
	/**
	 * Load preferences from a file
	 */
	private static void loadPrefs() {
		BufferedReader br = null;
        
		int[] min = new int[3];
		int[] max = new int[3];
		double[] area = new double[2];
           
		try {
			br = new BufferedReader(new FileReader("/home/pi/Documents/Java_Projects/SHRECVision/prefs.txt"));
			String line;
				
            for (int i = 0; (line = br.readLine()) != null; i++) {
                if (i == 0) {
					min[0] = Integer.parseInt(line);
				} else if (i == 1) {
					min[1] = Integer.parseInt(line);
				} else if (i == 2) {
					min[2] = Integer.parseInt(line);
				} else if (i == 3) {
					max[0] = Integer.parseInt(line);
				} else if (i == 4) {
					max[1] = Integer.parseInt(line);
				} else if (i == 5) {
					max[2] = Integer.parseInt(line);
				} else if (i == 6) {
					area[0] = Double.parseDouble(line);
				} else if (i == 7) {
					area[1] = Double.parseDouble(line);
				}
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (br != null) {
                    br.close();
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
        
        thd_color_low = new Scalar(min[0], min[1], min[2]);
		thd_color_high = new Scalar(max[0], max[1], max[2]);
		min_area = area[0];
		max_area = area[1];
	}
	
	/**
	 * The UDP Socket to post the x, y, and z positions and velocities to
	 */
	private static final UDPClient client = new UDPClient();
	
	/**
	 * These thresholds are clipping points for the Core.inRange() function.
	 * Any color within this range will show as white in the filtered image.
	 */
	private static Scalar thd_color_low = new Scalar(0, 0, 0);
	private static Scalar thd_color_high = new Scalar(255, 255, 255);
	
	/**
	 * Thresholds for contour area. These ensure that a countour that is too small
	 * or too large is rejected.
	 */
	private static double min_area = 0.0;
	private static double max_area = 1000000.0;
	
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
	private static final double camera_horizontal_fov = 67.0/* / 180.0 * Math.PI*/;
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
		/**
		 * Start the UDP Socket thread
		 */
		new Thread(client).start();
		
		new SHRECVision().run();
	}
	
	/**
	 * This class depends on a run function to begin processing
	 */
	@Override
	public void run() {
		 
		/**
		 * Open a video capture stream
		 */
		VideoCapture captureGear = new VideoCapture("http://FRC:FRC@10.54.50.3:80/mjpg/video.mjpg");
		VideoCapture captureBoiler = new VideoCapture("http://FRC:FRC@10.54.50.5:80/mjpg/video.mjpg");
		Mat frameGear = new Mat();
		Mat frameBoiler = new Mat();
		
		/**
		 * Check the state of the video stream
		 */
		if (captureGear.isOpened() && captureBoiler.isOpened()) {
			/**
			 * Video stream opened sucessfully
			 */
			System.out.println("Stream opened successfully");

			
			/**
			 * Begin video processing loop
			 */
			while (client.getVisionState() != UDPClient.VisionState.Disabled) {
				/**
				 * Load preferences from an external text file
				 */
				loadPrefs();
				
				/**
				 * Socket opened successfully
				 */
				UDPClient.VisionState state = client.getVisionState();
				 
				/**
				 * Obtain a video frame
				 */
				boolean frame_boiler_opened = captureBoiler.read(frameBoiler);
				boolean frame_gear_opened = captureGear.read(frameGear);
				
				/**
				 * Check if frame was read correctly
				 */
				if(frame_boiler_opened && frame_gear_opened) {
					/**
					 * Frame read successfully
					 */
					//System.out.println("Frame read successfully, processing enabled | " + SHRECVision.mRate + " frames/sec");
					
					if(state == UDPClient.VisionState.Boiler) {
						/**
						 * The vision state is idle, no processing necessary
						 */
						//System.out.println("Processing for Boiler");
						
						/**
						 * Begin processing the frame
						 */
						process(frameBoiler, state);
					} else if(state == UDPClient.VisionState.Gear) {
						/**
						 * The vision state is idle, no processing necessary
						 */
						//System.out.println("Processing for Gear");
						
						/**
						 * Begin processing the frame
						 */
						process(frameGear, state);
					} else if(state == UDPClient.VisionState.Idle) {
						/**
						 * The vision state is idle, no processing necessary
						 */
						//System.out.println("Processing disabled");
					} else if (state == UDPClient.VisionState.Disabled) {
						/**
						 * Vision is disabled, likely because of a communication error
						 */
						System.out.println("Vision disabled");
					}
				}  else {
					/**
					 * Error reading frame
					 */
					System.out.println("Error reading frame, reopening stream");

					/**
					 * Check the camera streams and reconnect
					 */
					if (!frame_boiler_opened) {
						captureBoiler.release();
						captureBoiler = new VideoCapture("http://FRC:FRC@10.54.50.5:80/mjpg/video.mjpg");
					} if (!frame_gear_opened) {
						captureGear.release();
						captureGear = new VideoCapture("http://FRC:FRC@10.54.50.3:80/mjpg/video.mjpg");
					}
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
			captureBoiler.release();
			captureGear.release();
			wait(1000);
			SHRECVision.this.run();
		}
	}
	
	/**
	 * This processes the incoming frame from the video stream
	 */
	private void process(Mat frame, UDPClient.VisionState state) {
		Mat original = frame.clone();
		/**
		 * First, convert the image to the HSV color space
		 */
		Imgproc.cvtColor(frame, frame, Imgproc.COLOR_BGR2HSV);
		
		/**
		 * Second, apply an HSV color threshold
		 */
		Core.inRange(frame, thd_color_low, thd_color_high, frame);
		
		/**
		 * Remove noise from the frame
		 */
		Imgproc.morphologyEx(frame, frame, Imgproc.MORPH_OPEN, element);
		Imgcodecs.imwrite("/home/pi/Desktop/image.png", frame);
    	
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
    	 * Check if a matching coutour has been found
    	 */
    	if (contours.size() > 0) {
			/**
			 * Select the largest two contours
			 */
			double referenced_concavity1 = 0;
			double referenced_concavity2 = 0;
			double referenced_area1 = 0;
			double referenced_area2 = 0;
			int index1 = -1;
			int index2 = -1;
			for (int i = 0; i < contours.size(); i++) {
				Rect boundary = Imgproc.boundingRect(contours.get(i));
				double area = Imgproc.contourArea(contours.get(i));
				double concavity = area / boundary.area();
				if ((concavity > referenced_concavity1) && (area > referenced_area1) && (area > min_area) && (area < max_area)) {
					referenced_concavity2 = referenced_concavity1;
					referenced_concavity1 = concavity;
					referenced_area2 = referenced_area1;
					referenced_area1 = area;
					index2 = index1;
					index1 = i;
				} else if ((concavity > referenced_concavity2) && (area > referenced_area2) && (area > min_area) && (area < max_area)) {
					referenced_concavity2 = concavity;
					referenced_area2 = area;
					index2 = i;
				}
			}
    	
			if (index1 != -1 && index2 != -1) {
				/**
				 * Obtain the bounding box of the largest shape
				 */
				Rect boundary1 = Imgproc.boundingRect(contours.get(index1));
				Rect boundary2 = Imgproc.boundingRect(contours.get(index2));
				
				/**
				 * Calculate the incidence angle of the target shape
				 */
				double angle = 0.0;
				
				/**
				 * Locate the reflective tape
				 */
				if (state == UDPClient.VisionState.Boiler) {
					/**
					 * The robot is facing the boiler
					 * Update the horizontal incedence angle
					 * Calculating a running average velocity
					 */
					
					angle = camera_horizontal_fov * (((((double)(boundary1.tl().x + boundary1.br().x) / 2.0) +
						((double)(boundary2.tl().x + boundary2.br().x) / 2.0)) / (camera_width)) - 1.0) / 2.0;
					
					//System.out.println("Angle: " + angle);
				} else if (state == UDPClient.VisionState.Gear) {
					/**
					 * The robot is facing the gear hook
					 * Update the horizontal incedence angle
					 * Calculating a running average velocity
					 */
					angle = camera_horizontal_fov * (((((double)(boundary1.tl().x + boundary1.br().x) / 2.0) +
						((double)(boundary2.tl().x + boundary2.br().x) / 2.0)) / (camera_width)) - 1.0) / 2.0;
					
					//System.out.println("Angle: " + angle);
				}
				
				/**
				 * Send the angle to the UDP Socket
				 */
				client.setAngle(angle);
			} else {
				System.out.println("No contours matched");
			}
		} else {
			System.out.println("No contours found");
		}
	}
	
}
