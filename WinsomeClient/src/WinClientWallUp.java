import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.UnknownHostException;

public class WinClientWallUp implements Runnable{
	
	private int UDPport;
	private String multicastAdd;
	
	private boolean stop;
	
	public WinClientWallUp(int UDPport, String multicastAdd) {
		this.UDPport = UDPport;
		this.multicastAdd = multicastAdd;
		this.stop = false;
	}

	@SuppressWarnings("deprecation")
	@Override
	public void run() {
				
		while (!stop) {
			
			//Usare una size piu' significativa o mandarla
	        byte[] message = new byte[100];
	        
	        InetAddress udpAdd = null;
			try {
				udpAdd = InetAddress.getByName(multicastAdd);
			} catch (UnknownHostException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			//mi connetto al gruppo di multicast
	        DatagramPacket dp = new DatagramPacket (message, message.length);
	        try (MulticastSocket ms = new MulticastSocket(UDPport)) {
				ms.joinGroup(udpAdd);
		
				ms.setReuseAddress(true);

				ms.receive(dp);
			} catch (IOException e) {
				e.printStackTrace();
			}
	        System.out.println("UDP" + new String(dp.getData()));
		}

	}
	
	public void closeMulticast() {
		stop = true;
		return;
	}

}
