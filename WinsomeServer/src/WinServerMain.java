import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.rmi.AlreadyBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Iterator;
import java.util.Scanner;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.*;

public class WinServerMain {

    private static int TCPport;
    private static int RMIport;
    private static int UDPport;
    private static int rewardTime;
    private static String multicastAddress;
    private static WinServerStorage serverStorage;

    private ThreadPoolExecutor threadPool;
    
    // Informazioni per il calcolo periodico delle ricompense
    private WinRewardCalculator rewardCalculator;
    private Thread calcThread;
    
    private static WinServerMain winServer;
    
    private ConcurrentHashMap<String, WinUser> onlineUsers;
    
    private NotificationServiceServerImpl followersRMI;

    public void configServer () {

        File file = new File(".\\src\\files\\ServerConfigFile.txt");

        Scanner sc = null;
        try {
            sc = new Scanner(file);
        } catch (FileNotFoundException e) {

            System.err.println("ERROR: configuration file not found");
            e.printStackTrace();
        }

        while (sc.hasNextLine()) {
            if(sc.findInLine("#") != null) sc.nextLine();
            else {
                StringTokenizer st = new StringTokenizer(sc.nextLine());
                while (st.hasMoreTokens()) {
                    switch(st.nextToken()) {
                        case "TCPPORT":
                            TCPport = Integer.parseInt(st.nextToken());
                            break;
                        case "RMIPORTREGISTER":
                        	RMIport = Integer.parseInt(st.nextToken());
                        	break;
                        case "UDPPORT":
                        	UDPport = Integer.parseInt(st.nextToken());
                        	break;
                        case "MULTICASTADD":
                            multicastAddress = st.nextToken();
                            break;
                        case "REWARDTIME":
                        	rewardTime = Integer.parseInt(st.nextToken());
                        	break;
                    }

                }
            }
        }

    }
    
    private void startThreadPool() {
        //rejection handler
        //la lista?
        //threadfactory?
        threadPool = new ThreadPoolExecutor(0,100, 60, TimeUnit.SECONDS, new ArrayBlockingQueue<>(10));
        System.out.println("Threadpool started");
    }
    
    public void registrationServiceRegister() {

        try {
            
            RegistrationServiceImpl registerRMI = new RegistrationServiceImpl(serverStorage);
			RegistrationService stub1 = (RegistrationService) UnicastRemoteObject.exportObject(registerRMI, 0);
			// creo un registry sulla porta dedicata all'RMI
			LocateRegistry.createRegistry(RMIport);
			Registry r = LocateRegistry.getRegistry(RMIport);
			// pubblico lo stub nel registry
			r.rebind("REGISTER-SERVER", stub1);
		} catch (RemoteException e) {
			System.err.println("ERROR: Communication, " + e.toString());
			e.printStackTrace();
		}
    }
    
    public void notificationServiceRegister() {
    	
    	try {
    		followersRMI = new NotificationServiceServerImpl(onlineUsers);
			NotificationServiceServer stub2 = (NotificationServiceServer) UnicastRemoteObject.exportObject(followersRMI, 39000);
			LocateRegistry.createRegistry(5556);
			Registry registry = LocateRegistry.getRegistry(5556);
			registry.bind("FOLLOWERS-SERVER", stub2);
		} catch (RemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (AlreadyBoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }
    
    public void startMulticast() {
        // Faccio partire il thread per il calcolo periodico delle risorse
        // Gestisce la comunicazione UDP multicast
		
        rewardCalculator = new WinRewardCalculator(serverStorage, rewardTime, multicastAddress, UDPport);
        calcThread = new Thread(rewardCalculator);
        calcThread.start();
        return;
    }
    
    public void stopMulticast() {
    	rewardCalculator.disconnect();
    	calcThread.interrupt();
    }
    
    public void connect() {
        // Channel Multiplexing con NIO

        ServerSocketChannel serverChannel;
        Selector selector;

        //apro il SSC collegandolo alla porta e poi all'indirizzo
        try {
            serverChannel = ServerSocketChannel.open();
            ServerSocket ss = serverChannel.socket();
            InetSocketAddress address = new InetSocketAddress(TCPport);
            ss.bind(address);

            serverChannel.configureBlocking(false);

            //registro il canale con un selettore sull op di accept
            selector = Selector.open();
            serverChannel.register(selector, SelectionKey.OP_ACCEPT);

        } catch (IOException ex) {
            ex.printStackTrace();
            return;
        }

        while(true) {
            try {
                // ATTENZIONE bloccante fino a che non c'è una richeista di connessione
                //System.out.println("piantato sulla select");
                selector.select(1000);
                //System.out.println("non più piantato sulla select");
            } catch (IOException ex) {
                ex.printStackTrace();
                break;
            }

            // vado a vedere quali delle chiavi sono pronte
            Set<SelectionKey> readyKeys = selector.selectedKeys();
            Iterator<SelectionKey> iterator = readyKeys.iterator();

            while(iterator.hasNext()) {

                SelectionKey key = iterator.next();
                iterator.remove();

                try {

                    if(key.isAcceptable() && key.isValid()) {
                        System.out.println("accetable");
                        //c'è una richiesta di connessione
                        ServerSocketChannel server = (ServerSocketChannel) key.channel();
                        SocketChannel client = server.accept();
                        System.out.println("Accepted connection from " + client);
                        client.configureBlocking(false);
                        //registro il socket che mi collega a quel selettore con l'op write o read
                        //altra key???
                        SelectionKey keyClient = client.register(selector, SelectionKey.OP_READ);
                        
                    }
                    else if (key.isReadable() && key.isValid()) {
                        System.out.println("reading");
                        SocketChannel clientRead = (SocketChannel) key.channel();

                        String operation = WinUtils.receive(clientRead);

                        System.out.println(operation);
                        
                        if(operation.contains("logout")) {
                        	followersRMI.follow("honker");
                        }

                        threadPool.execute(new WinServerWorker(operation, key, serverStorage));

                    }
                    else if (key.isWritable() && key.isValid()) {
                        System.out.println("writable");
                        //scrittura disponibile
                        SocketChannel client = (SocketChannel) key.channel();              

                        //ATTENZIONE controllare che l'attachment ci sia?
                        //ATTENZIONE casting a stringa brutto?
                        WinUtils.send((String)key.attachment(), client);
                        
                        // Se l'utente ha appena effettuato il login con successo
                        if(key.attachment().equals("LOGIN-OK")) {
                        	// Attivo il thread per comunicare gli aggiornamenti sul portafoglio
                        	winServer.startMulticast();
                        }
                        
                        // Se l'utente ha appena effettuato il logout con successo
                        if(key.attachment().equals("LOGOUT-OK")) {
                        	// Fermo il calcolo periodico delle ricompense
                        	winServer.stopMulticast();
                        }
                        
                        //dopo aver scritto torno in lettura
                        key.interestOps(SelectionKey.OP_READ);
                    }
                } catch (IOException ex) {
                    key.cancel();
                    try {
                        key.channel().close();
                    } catch (IOException cex) {
                        cex.printStackTrace();
                    }
                }
            }
        }
    }

    public static void main(String[] args) {

        //configserver statica?
        winServer = new WinServerMain();
        
        // Parsing del file di configurazione
        winServer.configServer();
        //controllare se era presente una precende sessione
        //se c'era implementare anche come ricaricare tutta la roba
        serverStorage = new WinServerStorage();
        
        // inizializzo il pool di thread e il thread per le ricompense
        winServer.startThreadPool();

        System.out.println("Listening on port: " + TCPport);
        
        //RMI? punto adatto?

        winServer.registrationServiceRegister();
        winServer.notificationServiceRegister();
        
        // Metto il server in ascolto per le connessioni
        winServer.connect();
        

        
        // Esporto l'oggetto
        

        //shutdown thread?

    }

}
