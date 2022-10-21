import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.UnknownHostException;
import java.util.concurrent.atomic.AtomicBoolean;

public class WinClientWallUp implements Runnable{

    private int UDPport;
    private String multicastAdd;

    private volatile boolean stop;

    private AtomicBoolean print;

    public WinClientWallUp(int UDPport, String multicastAdd, AtomicBoolean print) {
        this.UDPport = UDPport;
        this.multicastAdd = multicastAdd;
        this.stop = false;
        this.print = print;
    }

    @SuppressWarnings("deprecation")
    @Override
    public void run() {

        while (!stop) {

            byte[] message = new byte[50];

            InetAddress udpAdd = null;
            try {
                udpAdd = InetAddress.getByName(multicastAdd);
            } catch (UnknownHostException e) {
                System.err.println("ERROR: unknown host multicast " + e.getMessage());
                e.printStackTrace();
            }

            // Mi connetto al gruppo di multicast
            DatagramPacket dp = new DatagramPacket (message, message.length);
            try (MulticastSocket ms = new MulticastSocket(UDPport)) {
                ms.joinGroup(udpAdd);
                ms.setReuseAddress(true);
                ms.receive(dp);
            } catch (IOException e) {
                System.err.println("ERROR: multicast " + e.getMessage());
                e.printStackTrace();
            }
            String message2 = new String(dp.getData(), dp.getOffset(), dp.getLength());
            while (!print.get()) Thread.yield();
            System.out.println(message2);
        }
    }

    public void closeMulticast() {
        stop = true;
        return;
    }

}
