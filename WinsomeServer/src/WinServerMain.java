import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.channels.*;
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
    private static int UDPport;

    // Channel Multiplexing con NIO
    private ServerSocketChannel serverChannel;
    private Selector selector;
    private int selectTimeout;

    // Informazioni per il calcolo periodico delle ricompense
    private static int rewardTime;
    private static String multicastAddress;

    // Struttura dati per tenere traccia degli utenti attualmente connessi
    private static ConcurrentHashMap<SelectableChannel, String> activeUsers;

    // Informazioni per la persistenza nel file system
    private static WinServerStorage serverStorage;
    private WinServerStoragePersistenceManager serverStorageKeeper;
    private Thread keeperThread;
    private int saveTime;

    // Informazioni per il calcolo periodico delle ricompense
    private WinRewardCalculator rewardCalculator;
    private Thread calcThread;
    private int authorPercentage;
    private ThreadPoolExecutor threadPool;
    private static WinServerMain winServer;

    // RMI
    private static int RMIportregister;
    private static int RMIportfollowers;
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
                            RMIportregister = Integer.parseInt(st.nextToken());
                            break;
                        case "RMIPORTFOLLOWERS":
                            RMIportfollowers = Integer.parseInt(st.nextToken());
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
                        case "AUTHOR_PERCENTAGE":
                        	authorPercentage = Integer.parseInt(st.nextToken());
                        	break;
                        case "SAVETIME":
                        	saveTime = Integer.parseInt(st.nextToken());
                        	break;
                        case "SELECTTIMEOUT":
                            selectTimeout = Integer.parseInt(st.nextToken());
                            break;
                    }

                }
            }
        }

    }

    private void startThreadPool() {
        threadPool = new ThreadPoolExecutor(0,1, 60, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(1), new ThreadPoolExecutor.AbortPolicy());
        System.out.println("Threadpool started");
    }

    public void stopThreadPool() {
        threadPool.shutdown();
        try {
            if(threadPool.awaitTermination(5000, TimeUnit.MILLISECONDS))
                threadPool.shutdownNow();
        } catch (InterruptedException e) {
            threadPool.shutdownNow();
            System.err.println("ERROR: threadpool termination interrupted" + e.getMessage());
            e.printStackTrace();
        }
        if(threadPool.isTerminated()) System.out.println("Threadpool terminated");
    }

    public void registrationServiceRegister() {

        try {

            RegistrationServiceImpl registerRMI = new RegistrationServiceImpl(serverStorage);
            RegistrationService stub1 = (RegistrationService) UnicastRemoteObject.exportObject(registerRMI, 0);
            // creo un registry sulla porta dedicata all'RMI
            LocateRegistry.createRegistry(RMIportregister);
            Registry r = LocateRegistry.getRegistry(RMIportregister);
            // pubblico lo stub nel registry
            r.rebind("REGISTER-SERVER", stub1);
        } catch (RemoteException e) {
            System.err.println("ERROR: Communication, " + e.toString());
            e.printStackTrace();
        }
    }

    public void notificationServiceRegister() {

        try {
            followersRMI = new NotificationServiceServerImpl();
            NotificationServiceServer stub2 = (NotificationServiceServer) UnicastRemoteObject.exportObject(followersRMI, 39000);
            LocateRegistry.createRegistry(RMIportfollowers);
            Registry registry = LocateRegistry.getRegistry(RMIportfollowers);
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

        rewardCalculator = new WinRewardCalculator(serverStorage, rewardTime, multicastAddress, UDPport, authorPercentage);
        calcThread = new Thread(rewardCalculator);
        calcThread.setDaemon(true);
        calcThread.start();
    }

    public void stopMulticast() {
        if(calcThread != null) {
            rewardCalculator.stop();
            calcThread.interrupt();
            System.out.println("Reward thread terminated");
        }
    }

    public void startStorageKeeper() {
        serverStorageKeeper = new WinServerStoragePersistenceManager(serverStorage, saveTime);
        keeperThread = new Thread(serverStorageKeeper);
        keeperThread.setDaemon(true);
        keeperThread.start();
    }

    public void stopStorageKeeper() {
        if(keeperThread != null) {
            serverStorageKeeper.stop();
            keeperThread.interrupt();
            System.out.println("Persistence thread terminated");
        }
    }
    public void closeConnections() {
        System.out.println("Closing all connections with clients...");
        try {
            selector.select(selectTimeout);
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        // Vado a vedere quali delle chiavi sono pronte
        Set<SelectionKey> readyKeys = selector.selectedKeys();
        Iterator<SelectionKey> iterator = readyKeys.iterator();

        while(iterator.hasNext()) {

            SelectionKey key = iterator.next();
            iterator.remove();

            try {
                if (key.isWritable() && key.isValid()) {
                    System.out.println("writable");
                    SocketChannel client = (SocketChannel) key.channel();

                    if(key.attachment() == null) {
                        JsonObject response = new JsonObject();
                        response.addProperty("result", -1);
                        response.addProperty("result-msg", "Your request was not processed by server, retry");
                        WinUtils.send(response.toString(), client);
                    }

                    JsonObject response = new Gson().fromJson(key.attachment().toString(), JsonObject.class);

                    if(response.has("login-ok")) {
                        System.out.println("user logged in");
                        activeUsers.put(key.channel(), response.get("user").getAsString());
                    }
                    if(response.has("logout-ok")) {
                        System.out.println("user logged out");
                        activeUsers.remove(key.channel(), response.get("user").getAsString());
                    }

                    WinUtils.send(response.toString(), client);
                }
            } catch (IOException ex) {
                System.err.println("ERROR: lost connection with client, disconnecting user...");
                // In caso di errore disconnetto l'utente
                if(activeUsers.get(key.channel()) != null){
                    followersRMI.emergencyUnregister(activeUsers.get(key.channel()));
                    serverStorage.removeOnlineUser(activeUsers.get(key.channel()));
                }
                key.cancel();
            }
            key.cancel();
        }
    }
    private void connect() {

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
                selector.select(selectTimeout);
            } catch (IOException ex) {
                ex.printStackTrace();
                break;
            }

            // Vado a vedere quali delle chiavi sono pronte
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
                        System.out.println("Accepted connection from client");
                        client.configureBlocking(false);
                        //registro il socket che mi collega a quel selettore con l'op write o read
                        client.register(selector, SelectionKey.OP_READ);

                    }
                    else if (key.isReadable() && key.isValid()) {
                        System.out.println("reading");
                        SocketChannel clientRead = (SocketChannel) key.channel();

                        String operation = WinUtils.receive(clientRead);

                        if(operation.equals("")) {
                            if(activeUsers.get(key.channel()) != null){
                                followersRMI.emergencyUnregister(activeUsers.get(key.channel()));
                                serverStorage.removeOnlineUser(activeUsers.get(key.channel()));
                            }
                            key.cancel();
                            System.out.println("Closed connection with client");
                            continue;
                        }

                        try{
                            threadPool.execute(new WinServerWorker(operation, key, serverStorage, followersRMI, multicastAddress, UDPport));
                        } catch(RejectedExecutionException e) {
                            System.err.println("ERROR: task rejected");
                            key.interestOps(SelectionKey.OP_WRITE);
                        }

                    }
                    else if (key.isWritable() && key.isValid()) {
                        System.out.println("writable");
                        //scrittura disponibile
                        SocketChannel client = (SocketChannel) key.channel();

                        if(key.attachment() == null) {
                            JsonObject response = new JsonObject();
                            response.addProperty("result", -1);
                            response.addProperty("result-msg", "Your request was not processed by server, retry");
                            WinUtils.send(response.toString(), client);
                            key.interestOps(SelectionKey.OP_READ);
                            continue;
                        }

                        JsonObject response = new Gson().fromJson(key.attachment().toString(), JsonObject.class);

                        if(response.has("login-ok")) {
                            System.out.println("user logged in");
                            activeUsers.put(key.channel(), response.get("user").getAsString());
                        }
                        if(response.has("logout-ok")) {
                            System.out.println("user logged out");
                            activeUsers.remove(key.channel(), response.get("user").getAsString());
                        }

                        WinUtils.send(response.toString(), client);
                                                
                        // Dopo aver scritto torno in lettura
                        key.interestOps(SelectionKey.OP_READ);
                    }
                } catch (IOException ex) {
                    System.err.println("ERROR: lost connection with client, disconnecting user...");
                    // In caso di errore disconneto l'utente
                    if(activeUsers.get(key.channel()) != null){
                        followersRMI.emergencyUnregister(activeUsers.get(key.channel()));
                        serverStorage.removeOnlineUser(activeUsers.get(key.channel()));
                    }
                    key.cancel();
                }
            }
        }
    }
    public static void main(String[] args) {

        winServer = new WinServerMain();

        // Parsing del file di configurazione
        winServer.configServer();

        // Inizializzo le strutture dati per la gestione del server
        serverStorage = new WinServerStorage();
        activeUsers = new ConcurrentHashMap<SelectableChannel, String>();

        // Setto lo shutdownhook per la chiusura del server
        Runtime.getRuntime().addShutdownHook(new ServerShutdown(winServer));

        // Inizializzo il pool di thread
        winServer.startThreadPool();

        //RMI per la registrazione e la notifica dei followers
        winServer.registrationServiceRegister();
        winServer.notificationServiceRegister();

        // Faccio partire il thread per il calcolo delle ricompense
        winServer.startMulticast();

        // Faccio partire il sistema per mantenere i dati del serverStorage
        winServer.startStorageKeeper();

        // Metto il server in ascolto per le connessioni
        winServer.connect();

        System.out.println("Server started... ");
        System.exit(0);
    }

}
