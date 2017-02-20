import java.io.*;
import java.net.*;

public class UDPClient implements Runnable {

	/**
	 * 0: X Position
	 * 1: Y Position
	 * 2: Z Position
	 * 3: X Velocity
	 * 4: Y Velocity
	 * 5: Z Velocity
	 */
	private double[] coordinates;

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
			//e.printStackTrace();
		}
		
		try {
			IPAddress = InetAddress.getByName("roboRIO-5450-FRC.local");
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			//e.printStackTrace();
		}
		
		sendData = new byte[1024];
		receiveData = new byte[1024];
		coordinates = new double[6];
		
		if (isConnected()) {
			System.out.println("Successfully connected to UDP Server");
			setVisionState(VisionState.Idle);
		} else {
			System.out.println("Failed to connect to UDP Server");
			setVisionState(VisionState.Disabled);
		}
	}
	
	private void updateSocket() {
		
		// Generate a message to send
		String request = generateRequest();
		sendData = request.getBytes();
		
		// Send the request to the UDP Server
		DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, IPAddress, 5800);
		try {
			clientSocket.send(sendPacket);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			//e.printStackTrace();
		}
		
		// Receive a response from the server
		DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
		try {
			clientSocket.receive(receivePacket);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			//e.printStackTrace();
		}
		
		String response = new String(receivePacket.getData());
		if (response.equals("3")) {
			setVisionState(VisionState.Boiler);
		} else if (response.equals("2")) {
			setVisionState(VisionState.Gear);
		} else if (response.equals("1")) {
			setVisionState(VisionState.Idle);
		} else if (response.equals("0")) {
			setVisionState(VisionState.Disabled);
		}
	}
	
	private void closeSocket() {
		clientSocket.close();
	}
	
	private String generateRequest() {
		String request = "";
		for (int i = 0; i < getCoords().length; i++) {
			request += getCoords()[i];
			if (i < getCoords().length - 1) {
				request += ",";
			}
		}
		return request;
	}
	
	public synchronized void setVisionState(VisionState s) {
		state = s;
	}
	
	public synchronized VisionState getVisionState() {
		return state;
	}
	
	public synchronized void setCoords(double[] _c) {
		coordinates = _c;
	}
	
	public synchronized double[] getCoords() {
		return coordinates;
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
