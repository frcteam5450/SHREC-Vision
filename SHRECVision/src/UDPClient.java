import java.io.*;
import java.net.*;

public class UDPClient implements Runnable {

	/**
	 * 0: Incidence Angle
	 */
	private double angle;

	public enum VisionState {
		Boiler,
		Gear,
		Idle,
		Disabled
	}
	
	private VisionState state = VisionState.Idle;
	
	private DatagramSocket clientSocket;
	private InetAddress IPAddress;
	private byte[] sendData;
	private byte[] receiveData;
	
	private void startSocket() {
		// Start the UDP socket and open a connection to the server
		try {
			clientSocket = new DatagramSocket();
		} catch (SocketException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		try {
			IPAddress = InetAddress.getByName("10.54.50.2");
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		angle = 0.0;
		
		if (isConnected()) {
			System.out.println("Successfully connected to UDP Server");
			setVisionState(VisionState.Idle);
		} else {
			System.out.println("Failed to connect to UDP Server");
			setVisionState(VisionState.Disabled);
			UDPClient.this.closeSocket();
			
			try {
				Thread.sleep(1000);
			} catch(InterruptedException e) {
				e.printStackTrace();
			}
			UDPClient.this.startSocket();
		}
	}
	
	private void updateSocket() {
		sendData = new byte[1024];
		receiveData = new byte[1024];
	
		// Generate a message to send
		String request = generateRequest();
		sendData = request.getBytes();
	
		System.out.println("Request: " + request);
		// Send the request to the UDP Server
		DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, IPAddress, 5800);
		try {
			clientSocket.send(sendPacket);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		System.out.println("Awaiting Server");
		try {
			clientSocket.setSoTimeout(500);
		} catch (SocketException e) {
			e.printStackTrace();
		}
		
		// Receive a response from the server
		String response = "";
		DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
		try {
			clientSocket.receive(receivePacket);
			response = new String(receivePacket.getData());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			response = "1";
		}
		
		System.out.println("Response: " + response);
		if (response.substring(0, 1).equals("3")) {
			setVisionState(VisionState.Boiler);
		} else if (response.substring(0, 1).equals("2")) {
			setVisionState(VisionState.Gear);
		} else if (response.substring(0, 1).equals("1")) {
			setVisionState(VisionState.Idle);
		} else if (response.substring(0, 1).equals("0")) {
			setVisionState(VisionState.Disabled);
		}

		try {
			Thread.sleep(500);
		} catch(InterruptedException e) {
			e.printStackTrace();
		}
	}
	
	private void closeSocket() {
		clientSocket.close();
		System.out.println("Shutting down socket");
	}
	
	private String generateRequest() {
		double a = getAngle();
		String request = a + "!";
		return request;
	}
	
	public synchronized void setVisionState(VisionState s) {
		state = s;
	}
	
	public synchronized VisionState getVisionState() {
		return state;
	}
	
	public synchronized void setAngle(double _a) {
		angle = _a;
	}
	
	public synchronized double getAngle() {
		return angle;
	}
	
	public boolean isConnected() {
		return IPAddress != null && clientSocket != null;
	}

	@Override
	public void run() {
		// TODO Auto-generated method stub
		UDPClient.this.startSocket();
		while (getVisionState() != VisionState.Disabled) {
			UDPClient.this.updateSocket();
		}
		UDPClient.this.closeSocket();
	}
}
