import java.io.*;
import java.net.*;

public class UDPServer implements Runnable {

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
	
	private DatagramSocket serverSocket;
	private byte[] sendData;
	private byte[] receiveData;
	
	private void startSocket() {
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
		angle = 0.0;
		
		if (isConnected()) {
			System.out.println("Successfully started UDP Server");
			setVisionState(VisionState.Idle);
		} else {
			System.out.println("Failed to start UDP Server");
			setVisionState(VisionState.Disabled);
			
			try {
				Thread.sleep(100);
			} catch(InterruptedException e) {
				e.printStackTrace();
			}
			
			UDPServer.this.startSocket();
		}
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
        if (getVisionState() == VisionState.Boiler) {
        	response = "3";
        } else if (getVisionState() == VisionState.Gear) {
        	response = "2";
        } else if (getVisionState() == VisionState.Idle) {
        	response = "1";
        } else if (getVisionState() == VisionState.Disabled) {
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
		int index = request.indexOf('!');
		String buffer = request.substring(0, index);
		double a = 0.0;
		try {
			a = Double.parseDouble(buffer);
		} catch(NumberFormatException e){
			e.printStackTrace();
			a = getAngle();
		}
		setAngle(a);
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
		return serverSocket != null;
	}

	@Override
	public void run() {
		// TODO Auto-generated method stub
		UDPServer.this.startSocket();
		while (getVisionState() != VisionState.Disabled) {
			UDPServer.this.updateSocket();
		}
		UDPServer.this.closeSocket();
	}
}
