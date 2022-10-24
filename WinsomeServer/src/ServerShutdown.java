/**
 * Classe che implemeta il thread per lo shudowhook del server
 */
public class ServerShutdown extends Thread {
    // Il server
    private WinServerMain winServer;

    public ServerShutdown(WinServerMain winServer) {
        this.winServer = winServer;
    }

    @Override
    public void run() {
        System.out.println("Closing server...");
        // Smetto di accettare richieste
        winServer.setShutdown();
        // Salvo lo stato del server e interrompo i thread
        winServer.stopStorageKeeper();
        winServer.stopMulticast();
        // Chiudo il pool di thread
        winServer.stopThreadPool();
        // Finisco di mandare le risposte ai client se ce ne sono e poi chiudo tutte le connessioni
        winServer.closeConnections();
    }
}
