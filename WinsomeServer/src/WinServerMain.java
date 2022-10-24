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
import java.util.concurrent.atomic.AtomicBoolean;

public class WinServerMain {

    // Porte
    // Porta utilizzata per la comunicazione TCP
    private int TCPport;

    // Informazioni per il Channel Multiplexing con NIO
    // Socket del server
    private ServerSocketChannel serverChannel;
    // Selettore per il multiplexing dei canali
    private Selector selector;
    // Timout della select
    private int selectTimeout;

    // Informazioni per il calcolo periodico delle ricompense
    // Porta utilizzata per la comunicazione multicast
    private int UDPport;
    // Indirizzo multicast
    private String multicastAddress;
    // Thread per il calcolo delle ricompense
    private WinRewardCalculator rewardCalculator;
    private Thread calcThread;
    // Percentuale destinata all'autore
    private int authorPercentage;
    // Tempo che trascorre tra un calcolo di una ricompensa e un'altro
    private int rewardTime;

    // Struttura dati per tenere traccia degli utenti attualmente connessi
    private static ConcurrentHashMap<SelectableChannel, String> activeUsers;

    // Informazioni per la persistenza nel file system
    private static WinServerStorage serverStorage;
    // Thread che periodicamente salva il server e lo aggiorna all'avvio
    private WinServerStoragePersistenceManager serverStorageKeeper;
    private Thread keeperThread;
    // Tempo tra un salvataggio e l'altro
    private int saveTime;

    // RMI
    // Porta per l'RMI di registrazione
    private int RMIportregister;
    // Porta per la notifica dei followers con callback
    private int RMIportfollowers;
    // Implementazione dei metodi dell'interfaccia remota
    private NotificationServiceServerImpl followersRMI;

    // Threadpool per la gestione delle richieste
    private ThreadPoolExecutor threadPool;
    private static WinServerMain winServer;
    // Per la chiusura del server
    private AtomicBoolean shutdown = new AtomicBoolean(false);

    /**
     * Fa il parsing del file di configurazione settando le variabili opportune
     * @exception FileNotFoundException Se il file di configurazione non viene trovato nel path specificato
     */
    private void configServer () {

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
        System.out.println("Server configuration completed");
    }

    /**
     * Inizializza il pool di thread
     */
    private void initThreadPool() {
        threadPool = new ThreadPoolExecutor(10,50, 60, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(100), new DenyPolicy());
        System.out.println("Threadpool ready");
    }

    /**
     * Interrompe il pool di thread, allo scadere del timeout il threadpool viene terminato forzatamente
     * @exception InterruptedException
     */
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

    /**
     * Crea un'istanza dell'oggetto remoto e la esporta per poi pubblicarla nel registry per permettere
     * al client di invocare il metodo remoto
     * @throws RemoteException
     */
    public void registrationServiceRegister() {
        try {
            RegistrationServiceImpl registerRMI = new RegistrationServiceImpl(serverStorage);
            // Esporto l'oggetto remoto
            RegistrationService stub1 = (RegistrationService) UnicastRemoteObject.exportObject(registerRMI, 0);
            // Creo un registry sulla porta dedicata all'RMI
            LocateRegistry.createRegistry(RMIportregister);
            Registry r = LocateRegistry.getRegistry(RMIportregister);
            // Pubblico lo stub nel registry
            r.rebind("REGISTER-SERVER", stub1);
        } catch (RemoteException e) {
            System.err.println("ERROR: RMI communication " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Crea un'istanza dell'oggetto remoto e la esporta per poi pubblicarla nel registry per
     * poi utilizzarlo per contattare il client via RMI
     * @throws RemoteException
     * @throws AlreadyBoundException Se il nome nel registry ha gia' un binding
     */
    public void notificationServiceRegister() {
        try {
            followersRMI = new NotificationServiceServerImpl();
            NotificationServiceServer stub2 = (NotificationServiceServer) UnicastRemoteObject.exportObject(followersRMI, 39000);
            LocateRegistry.createRegistry(RMIportfollowers);
            Registry registry = LocateRegistry.getRegistry(RMIportfollowers);
            registry.bind("FOLLOWERS-SERVER", stub2);
        } catch (RemoteException e) {
            System.err.println("ERROR: RMI communication " + e.getMessage());
            e.printStackTrace();
        } catch (AlreadyBoundException e) {
            System.err.println("ERROR: RMI communication " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Faccio partire il thread per il calcolo periodico delle risorse che gestisce anche l'invio delle notifiche via
     * multicast al client
     */
    public void startMulticast() {
        rewardCalculator = new WinRewardCalculator(serverStorage, rewardTime, multicastAddress, UDPport, authorPercentage);
        calcThread = new Thread(rewardCalculator);
        calcThread.setDaemon(true);
        calcThread.start();
    }

    /**
     * Ferma il thread per il calcolo periodico delle ricompense e poi lo interrompe
     */
    public void stopMulticast() {
        if(calcThread != null) {
            rewardCalculator.stop();
            calcThread.interrupt();
            System.out.println("Reward thread terminated");
        }
    }

    /**
     * Fa partire il thread per il salvataggio periodico del server
     */
    public void startStorageKeeper() {
        serverStorageKeeper = new WinServerStoragePersistenceManager(serverStorage, saveTime);
        keeperThread = new Thread(serverStorageKeeper);
        keeperThread.setDaemon(true);
        keeperThread.start();
    }

    /**
     * Ferma il thread per il calcolo periodico delle ricompense, se e' stato attivato
     */
    public void stopStorageKeeper() {
        if(keeperThread != null) {
            serverStorageKeeper.stop();
            keeperThread.interrupt();
            System.out.println("Persistence thread terminated");
        }
    }

    /**
     * Chiude tutte le connessioni con i client, inviando le ultime risposte se ce ne sono
     */
    public void closeConnections() {
        System.out.println("Closing all connections with clients...");
        // Guardo se ci sono delle risposte da mandare
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
                // Se la chiave e' in scrittura
                if (key.isWritable() && key.isValid()) {
                    SocketChannel client = (SocketChannel) key.channel();
                    // Converto il messaggio
                    JsonObject response = new Gson().fromJson(key.attachment().toString(), JsonObject.class);
                    // Se l'azione era un login o un logout modifico la struttura degli utenti attivi
                    if(response.has("login-ok")) {
                        System.out.println("user logged in");
                        activeUsers.put(key.channel(), response.get("user").getAsString());
                    }
                    if(response.has("logout-ok")) {
                        System.out.println("user logged out");
                        activeUsers.remove(key.channel(), response.get("user").getAsString());
                    }
                    // Mando il messaggio
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

    /**
     * Setta la viariabile di shutdown, fermando il multiplexing dei canali
     */
    public void setShutdown() {
        shutdown.set(true);
    }

    /**
     * Instuara una connesione TCP con il client e poi fa il multiplexing dei canali con NIO
     */
    private void connect() {
        // Apro il SSC collegandolo alla porta e poi all'indirizzo
        try {
            serverChannel = ServerSocketChannel.open();
            ServerSocket ss = serverChannel.socket();
            InetSocketAddress address = new InetSocketAddress(TCPport);
            ss.bind(address);
            // Configuro come non bloccante
            serverChannel.configureBlocking(false);
            // Registro il canale con un selettore sull'operazione di accept
            selector = Selector.open();
            serverChannel.register(selector, SelectionKey.OP_ACCEPT);
        } catch (IOException ex) {
            System.err.println("ERROR: connecting socket server " + ex.getMessage());
            ex.printStackTrace();
            return;
        }
        // Finche' il server e' attivo
        while(!shutdown.get()) {
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
                    // Se la chiave e' una richiesta di connessione
                    if(key.isAcceptable() && key.isValid()) {
                        ServerSocketChannel server = (ServerSocketChannel) key.channel();
                        SocketChannel client = server.accept();
                        System.out.println("Accepted connection from client");
                        // Configuro come non bloccante
                        client.configureBlocking(false);
                        // Registro il socket che mi collega a quel selettore con l'operazione read
                        client.register(selector, SelectionKey.OP_READ);
                    }
                    // Lettura
                    else if (key.isReadable() && key.isValid()) {
                        // Prendo il canale
                        SocketChannel clientRead = (SocketChannel) key.channel();
                        // Ricevo il messaggio dal client
                        String operation = WinUtils.receive(clientRead);
                        // Se ricevo una stringa vuota vuol dire che il client si e' disconneso
                        if(operation.equals("")) {
                            // Rimuovo l'utente associato a quel canale dalla callback e dagli utenti attivi
                            if(activeUsers.get(key.channel()) != null){
                                followersRMI.emergencyUnregister(activeUsers.get(key.channel()));
                                serverStorage.removeOnlineUser(activeUsers.get(key.channel()));
                            }
                            // Rimuovo la chiave
                            key.cancel();
                            System.out.println("Closed connection with client");
                            continue;
                        }
                        // Faccio eseguire la richiesta a uno dei worker del thread pool
                       threadPool.execute(new WinServerWorker(operation, key, serverStorage, followersRMI,
                               multicastAddress, UDPport));
                    }
                    // Scrittura
                    else if (key.isWritable() && key.isValid()) {
                        // Prendo il canale
                        SocketChannel client = (SocketChannel) key.channel();
                        // Preparo il messaggio
                        JsonObject response = new Gson().fromJson(key.attachment().toString(), JsonObject.class);
                        // Se l'operazione e' stata un login o un logout modifico gli utenti attivi
                        if(response.has("login-ok")) {
                            System.out.println("user logged in");
                            activeUsers.put(key.channel(), response.get("user").getAsString());
                        }
                        if(response.has("logout-ok")) {
                            System.out.println("user logged out");
                            activeUsers.remove(key.channel(), response.get("user").getAsString());
                        }
                        // Mando la risposta
                        WinUtils.send(response.toString(), client);
                        // Dopo aver scritto torno in lettura
                        key.interestOps(SelectionKey.OP_READ);
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
            }
        }
    }

    public static void main(String[] args) {
        // Inizializzo il server
        winServer = new WinServerMain();

        // Parsing del file di configurazione
        winServer.configServer();

        // Inizializzo le strutture dati per la gestione del server
        serverStorage = new WinServerStorage();
        activeUsers = new ConcurrentHashMap<SelectableChannel, String>();

        // Setto lo shutdownhook per la chiusura del server
        Runtime.getRuntime().addShutdownHook(new ServerShutdown(winServer));

        // Inizializzo il pool di thread
        winServer.initThreadPool();

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
