import java.io.IOException;
import java.net.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Thread che rimane in ascolto per le notifiche sul portafoglio sull'indirizzo multicast e stampa il messaggio
 * ricevuto dal server
 */
public class WinClientWallUp implements Runnable{
    // Indirizzo e porta multicast
    private int UDPport;
    private String multicastAdd;
    // Variabile che permette alle stampe di non interferire con l'input utente
    private AtomicBoolean print;
    // Il multicast socket per ricevere messaggi dal server
    MulticastSocket ms;
    // L'indirizzo multicast
    InetAddress udpAdd;
    // Variabile per fermare il thread
    private volatile static boolean stop;

    /**
     * Costruttore, setta l'indirizzo e si unisce al gruppo di multicast
     * @param UDPport
     * @param multicastAdd
     * @param print
     */
    @SuppressWarnings("deprecation")
    public WinClientWallUp(int UDPport, String multicastAdd, AtomicBoolean print) {
        this.UDPport = UDPport;
        this.multicastAdd = multicastAdd;
        this.print = print;
        this.stop = false;
        // Preparo l'indirizzo
        try {
            this.udpAdd = InetAddress.getByName(multicastAdd);
        } catch (UnknownHostException e) {
            System.err.println("ERROR: unknown host multicast " + e.getMessage());
            e.printStackTrace();
        }
        // Mi unisco al gurppo di multicast
        try {
            this.ms = new MulticastSocket(UDPport);
            ms.joinGroup(udpAdd);
            ms.setReuseAddress(true);
        } catch (IOException e) {
            System.err.println("ERROR: multicast " + e.getMessage());
            e.printStackTrace();
        }
    }


    @Override
    public void run() {

        while (!stop) {
            // Preparo il byte array per ricever il messaggio
            byte[] message = new byte[50];

            // Mi connetto al gruppo di multicast
            DatagramPacket dp = new DatagramPacket (message, message.length);
            try {
                ms.receive(dp);
            } catch (IOException e) {
                System.out.println("Wallet updates are turned off");
                System.out.print("> ");
            }
            String message2 = new String(dp.getData(), dp.getOffset(), dp.getLength());
            // Se non posso stampare aspetto
            while (!print.get() && !stop) {}
            // Stampo il risultato
            if(message2.contains("wallet")) System.out.println(message2);
        }
    }

    public void stopWallUp(){
        stop = true;
        try {
            ms.leaveGroup(udpAdd);
            ms.close();
        } catch (IOException e) {
            System.err.println("ERROR: multicast " + e.getMessage());
            e.printStackTrace();
        }
    }
}
