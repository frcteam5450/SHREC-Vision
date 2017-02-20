import java.io.*;
import java.net.*;

public class UDPServer implements Runnable {

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
	
	private VisionState state;
	
	private DatagramSocket serverSocket;
	private byte[] sendData;
	private byte[] receiveData;
	
	private void startSocket() {
		state = VisionState.Idle;
		
		// Start the UDP socket and open a connection to the server
		try {
			// Bind the socket to this port on roboRIO-5450-FRC.local 5800-5810
			serverSocket = new DatagramSocket(5800);
		} catch (SocketException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		receiveData = new byte[1024];
		sendData = new byte[1024];
		coordinates = new double[6];
	}
	
	private void updateSocket() {
		// Receive a packet of bytes from a client
		DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
       
		try {
			serverSocket.receive(receivePacket);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		// Extract text from the byte packet
        String request = new String( receivePacket.getData());
        handleRequest(request);
        
        // Send a message back to the client
        String response = "";
        if (state == VisionState.Boiler) {
        	response = "3";
        } else if (state == VisionState.Gear) {
        	response = "2";
        } else if (state == VisionState.Idle) {
        	response = "1";
        } else if (state == VisionState.Disabled) {
        	response = "0";
        }
        sendData = response.getBytes();
        
        // Open a connection to an ip address
        InetAddress IPAddress = receivePacket.getAddress();
        int port = receivePacket.getPort();
        
        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, IPAddress, port);
        
        try {
			serverSocket.send(sendPacket);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	private void closeSocket() {
		serverSocket.close();
	}
	
	private void handleRequest(String request) {
		// Update internal state variables depending on the request
		String[] buffer = request.split(",");
		for (int i = 0; i < coordinates.length; i++) {
			coordinates[i] = Double.parseDouble(buffer[i]);
		}
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

	@Override
	public void run() {
		// TODO Auto-generated method stub
		UDPServer.this.startSocket();
		while (state != VisionState.Disabled) {
			UDPServer.this.updateSocket();
		}
		UDPServer.this.closeSocket();
	}
}
