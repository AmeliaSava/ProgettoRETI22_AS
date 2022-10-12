import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class WinRewardCalculator implements Runnable {

    private WinServerStorage serverStorage;
    private int time;
    private String multicastAddress;
    private int udpPort;

    private boolean stop;

    public WinRewardCalculator(WinServerStorage serverStorage, int time, String multicastAddress, int udpPort) {
        this.serverStorage = serverStorage;
        this.time = time;
        this.multicastAddress = multicastAddress;
        this.udpPort = udpPort;
        this.stop = false;
    }

    @Override
    public void run() {
        System.out.println("Reward calculator started");

        while(!stop) {
        	
        	calculateReward();

            try (DatagramSocket mc = new DatagramSocket()) {
                System.out.println("Multadd" + multicastAddress);
                InetAddress udpAdd = InetAddress.getByName(multicastAddress);
                byte[] message = "Your wallet has been updated!".getBytes();
                DatagramPacket dp = new DatagramPacket(message, message.length, udpAdd, udpPort);
                mc.send(dp);
            } catch (IOException e1) {
                e1.printStackTrace();
            }

            WinUtils.sleep(time * 1000);
        }
    }
    
    private void calculateReward() {
    	
    	ConcurrentHashMap<UUID, WinPost> postMap = serverStorage.getPostMap();
    	
    	if(postMap == null) { //ATTENZIONE }
    	
    	for(WinPost post : postMap.values()) {
    		
    		int postRevenue = 
    	}
    }

    public void disconnect() {
        stop = true;
        return;
    }

}
