import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.SocketChannel;
import java.rmi.NotBoundException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class WinClientMain {

    // Porta TCP del server
    private static int TCPserverport;
    // Indirizzo del Server
    private static String hostAddress;
    // Socket del Client
    private static SocketChannel clientSocket;
    // Timeout del socket
    private int socketTimeout;

    // RMI
    // Porta per l'RMI di registrazione
    private int RMIregisterport;
    // Porta per la notifica dei followers con callback
    private int RMIfollowersport;
    private static RegistrationService serverRegister;
    private static NotificationServiceServer server;
    private static NotificationServiceClient stub2;

    // Dati dell'utente
    private String currentUser = null;
    private List<String> listFollowers;

    // Thread per la ricezione degli aggiornamenti sul portafoglio
    private WinClientWallUp WalletUpdate;
    private Thread WallUp;

    // Impedisce che altri thread stampino mentre l'utente sta inserendo l'input
    public AtomicBoolean print = new AtomicBoolean(true);

    /**
     * Fa il parsing del file di configurazione settando le variabili opportune
     * @throws FileNotFoundException Se il file di configurazione non viene trovato nel path specificato
     */
    public void configClient () {

        File file = new File(".\\src\\files\\ClientConfigFile.txt");

        Scanner sc;
        try {
            sc = new Scanner(file);
        } catch (FileNotFoundException e) {
            System.err.println("ERROR: configuration file not found");
            e.printStackTrace();
            return;
        }

        while (sc.hasNextLine()) {
            if(sc.findInLine("#") != null) sc.nextLine();
            else {
                StringTokenizer st = new StringTokenizer(sc.nextLine());
                while (st.hasMoreTokens()) {
                    switch(st.nextToken()) {
                        case "TCPSERVERPORT":
                            TCPserverport = Integer.parseInt(st.nextToken());
                            break;
                        case "RMIPORTREGISTER":
                            RMIregisterport = Integer.parseInt(st.nextToken());
                            break;
                        case "RMIPORTFOLLOWERS":
                            RMIfollowersport = Integer.parseInt(st.nextToken());
                            break;
                        case "SERVERNAME":
                            hostAddress = st.nextToken();
                            break;
                        case "SOCKETTIMEOUT":
                            socketTimeout = Integer.parseInt(st.nextToken());
                            break;
                    }
                }
            }
        }
    }

    /**
     * Instaura una connessioni TCP con il server
     * @throws IOException
     */
    private void connect() {
        // Preparo l'indirizzo
        SocketAddress address = new InetSocketAddress(hostAddress, TCPserverport);

        try {
            // Apro la connessione e setto un timeout sul socket
            clientSocket = SocketChannel.open(address);
            clientSocket.socket().setSoTimeout(socketTimeout);
            // Aspetto che la connessione sia stabilita prima di fare altro
            while(!clientSocket.finishConnect()) {}
        } catch (IOException e) {
            System.err.println("ERROR: connection: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Accede all'oggetto remoto sul server che poi permettera' di invocare il metodo per la registrazione dell'utente
     * @throws RemoteException Se e' impossibile creare un riferimento al registry
     * @throws NotBoundException Se non c'e' un binding corrispondente al nome
     */
    private void registrationRegister() {
        try {
            // Accedo al registry tramite la porta
            Registry registry1 = LocateRegistry.getRegistry(RMIregisterport);
            // Cerco il nome del registry
            Remote remoteRegister = registry1.lookup("REGISTER-SERVER");
            // Casting dell'oggetto remoto
            serverRegister = (RegistrationService) remoteRegister;
        } catch (Exception e) {
            System.err.println("ERROR: invoking object method " + e.toString() + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Si registra al servizio di notifica del server preparando l'oggetto remoto del client
     * di cui il server invochera' i metodi
     * @throws RemoteException
     * @throws NotBoundException Se non c'e' un binding corrispondente al nome
     */
    private void callbackRegister() {
        try {
            // Cerco il registry del server
            Registry registry2 = LocateRegistry.getRegistry(RMIfollowersport);
            // Creo lo stub del server
            server = (NotificationServiceServer) registry2.lookup("FOLLOWERS-SERVER");
            // Alloco l'oggetto remoto del client che provvedera' a notificare l'utente
            NotificationServiceClient followCallback = new NotificationServiceClientImpl(listFollowers);
            stub2 = (NotificationServiceClient) UnicastRemoteObject.exportObject(followCallback, 0);
            // Passo al server un riferimento al metodo del client che il server usera' per mandare le notifiche
            server.registerForCallback(stub2, currentUser);
        } catch (Exception e) {
            System.err.println("ERROR: callback register " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Si rimuove dal servizio di notifica cancellando la registrazione per la callback
     * @throws RemoteException
     */
    private void callbackUnregister() {
        try {
            server.unregisterForCallback(stub2, currentUser);
        } catch (RemoteException e) {
        	System.err.println("ERROR: callback unregister " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Avvia un thread che si mettera' in attesa degli aggiornamenti sul wallet dell'utente
     * Il thread stampera' solo quando l'utente non sta scrivendo
     * @param multicastAddress L'indirizzo su cui attendere le notifiche sul portafoglio
     * @param UDPserverport La porta su cui attendere le notifiche sul portafoglio
     */
    private void connectMulticast(String multicastAddress, int UDPserverport) {
        WalletUpdate = new WinClientWallUp(UDPserverport, multicastAddress, print);
        WallUp = new Thread(WalletUpdate);
        WallUp.setDaemon(true);
        WallUp.start();
    }

    /**
     * Ferma il thread che attende la notifica sul calcolo delle ricompense
     */
    private void disconnectMulticast() {
        WalletUpdate.stopWallUp();
        WallUp.interrupt();
        WallUp = null;
    }

    /**
     * Controlla che non ci sia un'utente gia' online, prepara la lista dei tag, fa l'hashing della password
     * e poi chiama il metodo remoto per la registrazione
     * @param command array di stringhe contenente le informazioni sull'utente
     * @throws RemoteException
     * @throws NoSuchAlgorithmException
     */
    private void registerUser(String[] command) throws RemoteException {
    	// Controllo che non ci sia una sessione gia' attiva
    	if(currentUser != null) {
    		System.err.println("ERROR: You are already logged in");
        	return;
        }
    	// Creo la lista di tag da inviare al server
    	// Tutti i tag vengono convertiti in minuscolo
        // Nel caso di un tag ripetuto viene ignorato e non inserito
        List<String> tagList = new ArrayList<String>();

        for(int i = 3; i < command.length; i++) {
            if(!tagList.contains(command[i].toLowerCase()))
                tagList.add(command[i].toLowerCase());
        }
        // Faccio l'hashing della password per non salvarla in chiaro
        MessageDigest md = null;
        BigInteger number = null;
        try {
            md = MessageDigest.getInstance("SHA-256");
            md.reset();
            md.update(command[2].getBytes());
            number = new BigInteger(1, md.digest());
        } catch (NoSuchAlgorithmException e) {
            System.err.println("ERROR: encrypting password " + e.getMessage());
            e.printStackTrace();
        }

        // Controllo se la registrazione e' andata a buon fine
        if(serverRegister.registerUser(command[1], number.toString(16), tagList) == 0)
            System.out.println("< User " + command[1] + " has been registred! You can now log in...");
        else System.err.println("ERROR: Username already in use, try logging in or choose another username");
    }

    /**
     * Controlla che non ci sia un'utente gia' online, manda un messaggio al server e se l'operazione lato server
     * e' andata a buon fine alloca le risorse di sessione
     * @param loginUser lo username dell'utente che effettua il login
     * @param loginPassword la password dell'utente
     * @throws IOException
     * @throws NoSuchAlgorithmException
     */
    private void loginUser(String loginUser, String loginPassword) throws IOException {
    	// Controlla che non ci sia gia' una sessione attiva
        if(currentUser != null) {
        	System.err.println("ERROR: You are already logged in");
        	return;
        }
        // Mando l'hash per confrontarla con quella salvata nel server
        MessageDigest md = null;
        BigInteger number = null;
        try {
            md = MessageDigest.getInstance("SHA-256");
            md.reset();
            md.update(loginPassword.getBytes());
            number = new BigInteger(1, md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        // Preparo il messaggio da mandare al server
        JsonObject message = new JsonObject();
        message.addProperty("operation", "login");
        message.addProperty("user", loginUser);
        message.addProperty("password", number.toString(16));
        WinUtils.send(message.toString(), clientSocket);
        // Ricevo la risposta
        String loginResponse = WinUtils.receive(clientSocket);
        // Converto la risposta
        JsonObject loginJson = new Gson().fromJson(loginResponse, JsonObject.class);                                       
       
       // Il login e' andato a buon fine
        if(loginJson.get("result").getAsInt() == 0) {
        	System.out.println("< " + loginJson.get("result-msg").getAsString());
        	// Setto l'utente della sessione corrente
            currentUser = loginUser;
            // Recupero dalla risposta la lista dei follower gia' esistenti
            listFollowers = new Gson().fromJson(loginJson.get("followers-list").getAsString(), new TypeToken<Vector<String>>(){}.getType());
            // Registro l'utente corrente per la callback sulla lista dei followers
            callbackRegister();
            // Mi connetto al gruppo di multicast
            connectMulticast(loginJson.get("multicast").getAsString(), loginJson.get("UDPport").getAsInt());
        } else {
            // Se c'e' stato un errore stampo il messaggio
        	System.err.println(loginJson.get("result-msg").getAsString());
        }
    }

    /**
     * Controlla se c'e' un utente online e poi procede a richiedere il logout al server, se va a buon fine dealloca
     * le risorse associate alla sua sessione
     * @throws IOException In caso di errori di comunicazione con il server
     */
    private void logoutUser() throws IOException {

        // Nessun utente online
        if(currentUser == null) {
            System.err.println("ERROR: No user logged in, cannot log out");
            return;
        }
        // Preparo il messaggio
        JsonObject message = new JsonObject();
        message.addProperty("operation", "logout");
        message.addProperty("user", currentUser);
        WinUtils.send(message.toString(), clientSocket);
        // Ricevo la risposta
        String logoutResponse = WinUtils.receive(clientSocket);
        // Converto la risposta
        JsonObject logoutJson = new Gson().fromJson(logoutResponse, JsonObject.class);                     

        // Se il logout e' andato a buon fine resetto la sessione e mi disconnetto da multicast e callback
        if(logoutJson.get("result").getAsInt() == 0) {
        	System.out.println("< " + logoutJson.get("result-msg").getAsString());
            // Resetto tutte le risorse associate all'utente corrente
        	callbackUnregister();
            currentUser = null;
            listFollowers.clear();
            disconnectMulticast();
        } else {
            // Se c'e' stato un errore stampo il messaggio
        	System.err.println(logoutJson.get("result-msg").getAsString());
        }
    }

    /**
     * Richiede la lista degli utenti al server, se va a buon fine stampa le informazioni ricevute
     * @throws IOException In caso di errori di comunicazione con il server
     */
    private void listUsers() throws IOException {
        // Nessun utente online
        if(currentUser == null) {
            System.err.println("ERROR: No user logged in");
            return;
        }
        // Preparo il messaggio
        JsonObject message = new JsonObject();
        message.addProperty("operation", "list users");
        message.addProperty("user", currentUser);
        WinUtils.send(message.toString(), clientSocket);
        // Ricevo la risposta
        String listResponse = WinUtils.receive(clientSocket);
        // Converto la risposta
        JsonObject usersJson = new Gson().fromJson(listResponse, JsonObject.class);
        
        if(usersJson.get("result").getAsInt() == 0) {
        	System.out.println("< " + usersJson.get("result-msg").getAsString());
        	// Se la lista e' vuota mi fermo
        	if(usersJson.get("users-list") == null) return;
            // Converto la lista ricevuta
        	List<String> users = new Gson().fromJson(usersJson.get("users-list").getAsString(), new TypeToken<List<String>>(){}.getType());
        	// Parso e stampo le informazioni
            TableList tl = new TableList(2, "USER", "Tags in Common").withUnicode(false);
            for (String info : users) {
                String[] userTag = info.split("/");
                String tags = "";
                for (int i = 1; i < userTag.length; i++) {
                    if(i < userTag.length - 1) tags = tags.concat(userTag[i] +", ");
                    else tags = tags.concat(userTag[i]);
                }
                tl.addRow(userTag[0], tags);
            }
        	tl.print();
        } else {
        	System.err.println(usersJson.get("result-msg").getAsString());
        }
    }

    /**
     * Operazione eseguita lato client. Stampa la lista degli utenti che seguono l'utente corrente
     */
    private void listFollowers() {
        // Nessun utente online
        if(currentUser == null) {
            System.err.println("ERROR: No user logged in");
            return;
        }
        // Stampo le informazioni sulla lista dei followers
        if(listFollowers.isEmpty()) {
        	System.out.println("< You have no followers");
        	return;
        }
        TableList tl = new TableList(1,
                "You have " + listFollowers.size() + " followers").withUnicode(false);
        listFollowers.forEach(user -> tl.addRow(user));
        tl.print();
    }

    /**
     * Richiede la lista degli utenti che l'utente corrente segue al server,
     * se va a buon fine stampa le informazioni ricevute
     * @throws IOException In caso di errori di comunicazione con il server
     */
    private void listFollowing() throws IOException {
    	// Nessun utente online
        if(currentUser == null) {
            System.err.println("ERROR: No user logged in");
            return;
        }
        // Preparo il messaggio
        JsonObject message = new JsonObject();
        message.addProperty("operation", "list following");
        message.addProperty("user", currentUser);
        WinUtils.send(message.toString(), clientSocket);
        // Ricevo la risposta
        String listResponse = WinUtils.receive(clientSocket);
        // Converto la risposta
        JsonObject followingJson = new Gson().fromJson(listResponse, JsonObject.class);
        
        if(followingJson.get("result").getAsInt() == 0) {
            // Se la lista e' vuota mi fermo
        	if(followingJson.get("following-list") == null) {
                System.out.println("< " + followingJson.get("result-msg").getAsString());
                return;
            }
        	// Converto la lista ricevuta
        	List<String> users = new Gson().fromJson(followingJson.get("following-list").getAsString(), new TypeToken<List<String>>(){}.getType());
            TableList tl = new TableList(1,followingJson.get("result-msg").getAsString()).withUnicode(false);
            users.forEach(user -> tl.addRow(user));
            tl.print();
        } else {
        	System.err.println(followingJson.get("result-msg").getAsString());
        }
    }

    /**
     * Manda una richiesta al server per seguire un altro utente
     * @param userToFollow Lo username dell'utente da seguire
     * @throws IOException In caso di errori di comunicazione con il server
     */
    private void followUser(String userToFollow) throws IOException {
    	// Nessun utente online
    	if(currentUser == null) {
            System.err.println("ERROR: No user logged in");
            return;
        }
        // Preparo il messaggio
        JsonObject message = new JsonObject();
        message.addProperty("operation", "follow");
        message.addProperty("user", currentUser);
        message.addProperty("user-to-follow", userToFollow);
        WinUtils.send(message.toString(), clientSocket);
        // Ricevo la risposta
        String followResponse = WinUtils.receive(clientSocket);
        // Converto la risposta
        JsonObject followJson = new Gson().fromJson(followResponse, JsonObject.class);
        // Stampo l'esito
        if(followJson.get("result").getAsInt() == 0) {
        	System.out.println("< " + followJson.get("result-msg").getAsString());
        } else {
        	System.err.println(followJson.get("result-msg").getAsString());
        }  
    }

    /**
     * Manda una richiesta al server per smettere di seguire un altro utente
     * @param userToUnfollow Lo username dell'utente da smettere di seguire
     * @throws IOException In caso di errori di comunicazione con il server
     */
    private void unfollowUser(String userToUnfollow) throws IOException {
    	// Nessun utente online
        if(currentUser == null) {
        	System.err.println("ERROR: No user logged in");
            return;
        }
        // Preparo il messaggio e lo invio
        JsonObject message = new JsonObject();
        message.addProperty("operation", "unfollow");
        message.addProperty("user", currentUser);
        message.addProperty("user-to-unfollow", userToUnfollow);
        WinUtils.send(message.toString(), clientSocket);
        // Ricevo la risposta
        String unfollowResponse = WinUtils.receive(clientSocket);
        // Converto la risposta
        JsonObject unfollowJson = new Gson().fromJson(unfollowResponse, JsonObject.class);
        // Stampo il risultato
        if(unfollowJson.get("result").getAsInt() == 0) {
        	System.out.println("< " + unfollowJson.get("result-msg").getAsString());
        } else {
        	System.err.println(unfollowJson.get("result-msg").getAsString());
        }
    }

    /**
     * Richiede il blog dell'utente, se va a buon fine stampa le informazioni
     * @throws IOException
     */
    private void viewBlog() throws IOException {
        // Nessun utente online
    	if(currentUser == null) {
    		System.err.println("ERROR: No user logged in");
            return;
        }
        // Preparo il messaggio
        JsonObject message = new JsonObject();
        message.addProperty("operation", "blog");
        message.addProperty("user", currentUser);
        WinUtils.send(message.toString(), clientSocket);
        // Ricevo la risposta
        String blogResponse = WinUtils.receive(clientSocket);
        // Converto la risposta
        JsonObject blogJson = new Gson().fromJson(blogResponse, JsonObject.class);
        
        if(blogJson.get("result").getAsInt() == 0) {
        	System.out.println("< " + blogJson.get("result-msg").getAsString());
        	// Se il blog e' vuoto mi fermo
        	if(blogJson.get("blog") == null) return;
            // Parso le informazioni e le stampo
            List<String> blog = new Gson().fromJson(blogJson.get("blog").getAsString(), new TypeToken<List<String>>(){}.getType());
            TableList tl = new TableList(3, "POST", "Author", "Title").withUnicode(false);

            for (String info : blog) {
            	String[] blogEntry = info.split("/");
                tl.addRow(blogEntry[0], blogEntry[1], blogEntry[2]);
            }
            tl.print();
        } else {
        	System.err.println(blogJson.get("result-msg").getAsString());
        }       
    }

    /**
     * Richiede la creazione di un post mandando una richiesta al server, se va a buon fine stampa un messaggio di
     * successo contente l'ID del post, altrimenti un messaggio di errore
     * @param title Il titolo del post
     * @param text Il testo del post
     * @throws IOException
     */
    private void createPost(String title, String text) throws IOException {
         // Nessun utente online
      	 if(currentUser == null) {
             System.err.println("ERROR: No user logged in");
             return;
         }
        // Preparo il messaggio
        JsonObject message = new JsonObject();
        message.addProperty("operation", "post");
        message.addProperty("user", currentUser);
        message.addProperty("title", title);
        message.addProperty("content", text);
        WinUtils.send(message.toString(), clientSocket);
        // Ricevo la risposta
        String postResponse = WinUtils.receive(clientSocket);
        // Converto la risposta
        JsonObject postJson = new Gson().fromJson(postResponse, JsonObject.class);
        // Stampo il risultato
        if(postJson.get("result").getAsInt() == 0) {
        	System.out.println(postJson.get("result-msg").getAsString());
        } else {
        	System.err.println(postJson.get("result-msg").getAsString());
        }
    }

    /**
     * Richiede il feed dell'utente, se la richiesta va a buon fine lo stampa
     * @throws IOException
     */
    private void showFeed() throws IOException {
    	//Nessun utente online
     	if(currentUser == null) {
            System.err.println("ERROR: User not logged in");
            return;
        }
        // Preparo il messaggio
        JsonObject message = new JsonObject();
        message.addProperty("operation", "show feed");
        message.addProperty("user", currentUser);
        WinUtils.send(message.toString(), clientSocket);
        // Ricevo la risposta
        String feedResponse = WinUtils.receive(clientSocket);
        // Converto la risposta
        JsonObject feedJson = new Gson().fromJson(feedResponse, JsonObject.class);
        
        if(feedJson.get("result").getAsInt() == 0) {
        	System.out.println("< " + feedJson.get("result-msg").getAsString());
        	// Se il feed e' vuoto mi fermo
        	if(feedJson.get("feed") == null) return;
        	// Converto la lista ricevuta
        	List<String> feed = new Gson().fromJson(feedJson.get("feed").getAsString(), new TypeToken<List<String>>(){}.getType());
            // Stampo la risposta
            TableList tl = new TableList(3, "POST", "Author", "Title").withUnicode(false);
        	for (String info : feed) {
                String[] feedEntry = info.split("/");
                tl.addRow(feedEntry[0], feedEntry[1], feedEntry[2]);
            }
            tl.print();
        } else {
        	System.err.println(feedJson.get("result-msg").getAsString());
        } 
    }

    /**
     * Richiede un post dato un certo ID, se la richiesta va a buon fine stampa il post
     * @param postID L'ID del post che l'utente vuole ricevere
     * @throws IOException
     */
    private void showPost(String postID) throws IOException {
    	// Nessun utente online
    	if(currentUser == null) {
            System.err.println("ERROR: User not logged in");
            return;
        }
        // Prepara il messaggio
        JsonObject message = new JsonObject();
        message.addProperty("operation", "show post");
        message.addProperty("user", currentUser);
        message.addProperty("post-id", postID);
        WinUtils.send(message.toString(), clientSocket);
        // Invia il messaggio
        String showpostResponse = WinUtils.receive(clientSocket);
        // Converte la risposta
        JsonObject postJson = new Gson().fromJson(showpostResponse, JsonObject.class);

        if(postJson.get("result").getAsInt() == 0) {
            // Stampa le informazioni
            System.out.println("< " + postJson.get("result-msg").getAsString());
            TableList tl = new TableList(1, "POST").withUnicode(false);
        	tl.addRow(("'" + postJson.get("title").getAsString() + "'"));
            tl.addRow("'" + postJson.get("content").getAsString() + "'");
            tl.addRow("By: " + postJson.get("author").getAsString());
            tl.addRow("ID: " + postJson.get("id").getAsString());
            tl.addRow("Upvotes " + postJson.get("upvote").getAsInt() + "    Dowvotes " + postJson.get("downvote").getAsInt());
        	List<String> comments = new Gson().fromJson(postJson.get("comments").getAsString(), new TypeToken<List<String>>(){}.getType());
        	for(String comment : comments) {
        		tl.addRow(comment);
        	}
        	tl.print();
        } else {
        	System.err.println(postJson.get("result-msg").getAsString());
        } 
    }

    /**
     * Richiede la cancellazione di un post e stampa il risultato dell'operazione
     * @param postID L'ID del post da cancellare
     * @throws IOException
     */
    private void deletePost(String postID) throws IOException {
   		//Nessun utente online
    	if(currentUser == null) {
            System.err.println("ERROR: User not logged in");
            return;
        }
        // Preparo il messaggio
        JsonObject message = new JsonObject();
        message.addProperty("operation", "delete");
        message.addProperty("user", currentUser);
        message.addProperty("post-id", postID);
        WinUtils.send(message.toString(), clientSocket);
        String deleteResponse = WinUtils.receive(clientSocket);
        // Ricevo la risposta
        JsonObject deleteJson = new Gson().fromJson(deleteResponse, JsonObject.class);
        // Stampo il risultato
        if(deleteJson.get("result").getAsInt() == 0) {
        	System.out.println("< " + deleteJson.get("result-msg").getAsString());
        } else {
        	System.err.println(deleteJson.get("result-msg").getAsString());
        }
    }

    /**
     * Manda una richiesta al server per fare il rewin di un post e ne stampa l'esito
     * @param postID L'id del post di cui fare il rewin
     * @throws IOException
     */
    private void rewinPost(String postID) throws IOException {
    	// Nessun utente online
    	if(currentUser == null) {
    		System.err.println("ERROR: User not logged in");
            return;
        }
        // Creo il messaggio
        JsonObject message = new JsonObject();
        message.addProperty("operation", "rewin");
        message.addProperty("user", currentUser);
        message.addProperty("post-id", postID);
        WinUtils.send(message.toString(), clientSocket);
        // Ricevo la risposta
        String rewinResponse = WinUtils.receive(clientSocket);
        // Converto la risposta
        JsonObject rewinJson = new Gson().fromJson(rewinResponse, JsonObject.class);
        // Stampo la risposta
        if(rewinJson.get("result").getAsInt() == 0) {     
        	System.out.println("< " + rewinJson.get("result-msg").getAsString());
        } else {
        	System.err.println(rewinJson.get("result-msg").getAsString());
        }
    }

    /**
     * Manda una richiesta per aggiungere un voto ad un post
     * @param postID L'id del post da votare
     * @param rate Il voto, puo' essere +1 o -1
     * @throws IOException
     */
    private void ratePost(String postID, String rate) throws IOException {
 		// Nessun utente online
    	if(currentUser == null) {
    		System.err.println("ERROR: User not logged in");
            return;
        }
        // Preparo il messaggio
        JsonObject message = new JsonObject();
        message.addProperty("operation", "rate");
        message.addProperty("user", currentUser);
        message.addProperty("post-id", postID);
        message.addProperty("rate", rate);
        WinUtils.send(message.toString(), clientSocket);
        // Ricevo la risposta
        String rateResponse = WinUtils.receive(clientSocket);
        // Converto la risposta
        JsonObject rateJson = new Gson().fromJson(rateResponse, JsonObject.class);
        // Stampo il risultato
        if(rateJson.get("result").getAsInt() == 0) {
        	System.out.println("< " + rateJson.get("result-msg").getAsString());
        } else {
        	System.err.println(rateJson.get("result-msg").getAsString());
        }
    }

    /**
     * Manda una richiesta al server per aggiungere un commento
     * @param postID L'ID del post da commentare
     * @param comment Il commento da aggiungere al post
     * @throws IOException
     */
    private void addComment(String postID, String comment) throws IOException {
		// Nessun utente online
    	if(currentUser == null) {
    		System.err.println("ERROR: User not logged in");
            return;
        }
        // Preparo il messaggio
        JsonObject message = new JsonObject();
        message.addProperty("operation", "comment");
        message.addProperty("user", currentUser);
        message.addProperty("post-id", postID);
        message.addProperty("comment", comment);
        WinUtils.send(message.toString(), clientSocket);
        // Ricevo la risposta
        String commentResponse = WinUtils.receive(clientSocket);
        // Converto la risposta
        JsonObject commentJson = new Gson().fromJson(commentResponse, JsonObject.class);
        // Stampo il risultato
        if(commentJson.get("result").getAsInt() == 0) {
        	System.out.println("< " + commentJson.get("result-msg").getAsString());
        } else {
        	System.err.println(commentJson.get("result-msg").getAsString());
        }
    }

    /**
     * Richiede al server il portafoglio dell'utente
     * @throws IOException
     */
    private void getWallet() throws IOException {
        // Nessun utente online
        if(currentUser == null) {
            System.err.println("ERROR: No user logged in");
            return;
        }
        // Preparo il messaggio
        JsonObject message = new JsonObject();
        message.addProperty("operation", "wallet");
        message.addProperty("user", currentUser);
        WinUtils.send(message.toString(), clientSocket);
        // Ricevo la risposta
        String walletResponse = WinUtils.receive(clientSocket);
        // Converto la risposta
        JsonObject walletJson = new Gson().fromJson(walletResponse, JsonObject.class);

        if(walletJson.get("result").getAsInt() == 0) {
            System.out.println("< " + walletJson.get("result-msg").getAsString());
            // Se il portafoglio e' vuoto mi fermo
            if (walletJson.get("transaction-list") == null) return;
            // Altrimenti stampo
            TableList tl = new TableList(2, "WINCOINS", "").withUnicode(false);
            List<String> transactionList = new Gson().fromJson(walletJson.get("transaction-list").getAsString(),
                    new TypeToken<List<String>>(){}.getType());

            for(String transaction : transactionList) {
                String[] values = transaction.split("/");
                DecimalFormat df = new DecimalFormat("0.00");
                double value = Double.parseDouble(values[0]);
                tl.addRow(df.format(value), values[1]);
            }
            tl.print();
            System.out.printf("< TOTAL %.2f\n", walletJson.get("wallet-tot").getAsDouble());
        } else System.out.println(walletJson.get("result-msg").getAsString());
    }

    /**
     * Richiede al server il portafoglio dell'utente in bitcoin
     * @throws IOException
     */
    private void getWalletBitcoin() throws IOException {
    	// Nessun utente online
        if(currentUser == null) {
            System.err.println("ERROR: No user logged in");
            return;
        }
        // Preparo il messaggio
        JsonObject message = new JsonObject();
        message.addProperty("operation", "wallet btc");
        message.addProperty("user", currentUser);
        WinUtils.send(message.toString(), clientSocket);
        // Ricevo la risposta
        String walletbtcResponse = WinUtils.receive(clientSocket);
        // Converto la risposta
        JsonObject walletbtcJson = new Gson().fromJson(walletbtcResponse, JsonObject.class);

        if(walletbtcJson.get("result").getAsInt() == 0) {
            System.out.println("< " + walletbtcJson.get("result-msg").getAsString());
            // Se il portafoglio e' vuoto mi fermo
            if (walletbtcJson.get("transaction-list") == null) return;
            // Altrimenti stampo
            TableList tl = new TableList(2, "WINCOINS", "").withUnicode(false);
            List<String> transactionList = new Gson().fromJson(walletbtcJson.get("transaction-list").getAsString(),
                    new TypeToken<List<String>>(){}.getType());

            for(String transaction : transactionList) {
                String[] values = transaction.split("/");
                DecimalFormat df = new DecimalFormat("0.00");
                double value = Double.parseDouble(values[0]);
                tl.addRow(df.format(value), values[1]);
            }
            tl.print();
            System.out.printf("< TOTAL %.2f\n", walletbtcJson.get("wallet-tot").getAsDouble());
        } else System.out.println(walletbtcJson.get("result-msg").getAsString());
    }

    public static void main(String[] args) {

        // Inizializzazione del client e parsing del file di configurazione
        WinClientMain winClient = new WinClientMain();
        winClient.configClient();

        // Inizializzo la lista che verra' utilizzata per salvare i follower dell'utente
        winClient.listFollowers = new ArrayList<String>();
        // RMI per la registrazione
        winClient.registrationRegister();
        // Mi connetto al server
        winClient.connect();

        try {
            Scanner scan = new Scanner(System.in);

            TableList tl = new TableList(1, "WINSOME the reWardINg SOcial Media!").withUnicode(false);
            tl.addRow("WELCOME");
            tl.align(0, TableList.EnumAlignment.CENTER);
            tl.print();
            System.out.println("Login or register to start interacting with other users");
            System.out.println("If you need help type -> help");
            System.out.print("> ");

            // Mi preparo a ricevere l'input dall'utente
            String action;
            // Impedisco alle stampe di altri thread di interferire con l'input utente
            winClient.print.set(false);
            while(scan.hasNextLine() && !((action = scan.nextLine()).equals("exit"))) {
                winClient.print.set(true);
                // Divido la stringa per fare i controlli
                String[] command = action.split(" ");
                // Se l'utente non ha inserito niente richiedo l'input
                if(command.length == 0) {
                    System.out.println("> ");
                    continue;
                }
                
                /*
                 * A seconda del tipo di operazione richiesta dal client controllo che l'input dell'utente sia corretto
                 * per poi chiamare la relativa funzione dove avviene la comunicazione col server
                 */
                switch (command[0]) {
                
                    case "register":
                    	if(command.length > 8 || command.length < 4) {
                    		System.out.print("< ");
                    		System.err.println("ERROR: use -> register <username> <password> <tag1 tag2... tag5>");
                            System.err.println("Tag list must be max 5 tags");
                            break;
                    	}
                    	winClient.registerUser(command);
                        break;
                    case "login":
                        if(command.length != 3) {
                        	System.out.print("< ");
                            System.err.println("ERROR: use -> login <username> <password>");
                            break;
                        }
                    	winClient.loginUser(command[1], command[2]);
                        break;
                    case "logout":
                    	winClient.logoutUser();
                        break;
                    case "list":
                        if(command.length != 2 || (!(command[1].equals("users")) && !(command[1].equals("followers")) && !(command[1].equals("following")))) {
                            System.out.println("ERROR: command 'list' can be used with 'users', 'following' or 'followers'");
                            break;
                        }
                        if(command[1].equals("followers")) winClient.listFollowers();
                        else if (command[1].equals("users")) winClient.listUsers();
                        else winClient.listFollowing();
                        break;
                    case "follow":
                        if(command.length != 2) {
                        	System.out.println("ERROR: use -> follow <username>");
                            break;
                        }
                        winClient.followUser(command[1]);
                        break;
                    case "unfollow":
                        if(command.length != 2) {
                        	System.out.println("ERROR: use -> unfollow <username>");
                            break;
                        }
                        winClient.unfollowUser(command[1]);
                        break;
                    case "blog":
                        winClient.viewBlog();
                        break;
                    case "post":
                        //catturo i dati del post
                        String[] elements = action.split("\"");
                        if(elements.length != 4) {
                            System.out.println("ERROR: use -> post \"<title>\" \"<content>\"");
                            break;
                        }
                        if((elements[1].length() > 20) || (elements[3].length() > 500)) {
                            System.out.println("ERROR: format title must be under 20 characters content under 500");
                            break;
                        }
                    	winClient.createPost(elements[1], elements[3]);
                        break;
                    case "show":
                        if((!(command[1].equals("feed")) && !(command[1].equals("post")))) {
                            System.err.println("ERROR: use -> show feed OR show post");
                            break;
                        }
                        if(command[1].equals("feed")) {
                        	if(command.length != 2) {
                        		System.err.println("ERROR: use -> show feed");
                        		break;
                        	}
                        	winClient.showFeed();
                        }
                        else {
                        	if(command.length != 3) {
                        		System.err.println("ERROR: use -> show post <post id>");
                        		break;
                        	}
                        	winClient.showPost(command[2]);
                        }
                        break;
                    case "rewin":
                    	if(command.length != 2) {
                    		System.out.println("ERROR: use -> rewin <idPost>");
                    		break;
                    	}
                    	winClient.rewinPost(command[1]);
                    	break;
                    case "wallet":
                    	if(command.length == 1) {
                    		winClient.getWallet();
                            break;
                    	} else if(command[1].equals("btc")) {
                    		winClient.getWalletBitcoin();
                            break;
                    	} else {
                    		System.err.println("ERROR:");
                    		System.err.println("use ->");
                            System.err.println("'wallet' to see your wallet");
                    		System.err.println("'wallet btc' to see your wallet in bitcoin");
                    		break;
                    	}
                    case "delete":
                    	if(command.length != 2) {
                			System.err.println("ERROR: use -> delete <idPost>");
                			break;
                		}
                    	winClient.deletePost(command[1]);
                        break;
                    case "comment":
                    	String[] comment = action.split("\"");
                		if(comment.length != 2) {
                			System.err.println("ERROR: use -> comment <idPost> \"<comment>\"");
                			break;
                		}
                		winClient.addComment(command[1], comment[1]);
                        break;
                    case "rate":
                    	if(command.length != 3) {
                			System.err.println("ERROR: use -> rate <idPost> <vote>");
                			break;
                		}
                		if(!command[2].equals("+1") && !command[2].equals("-1")) {
                			System.out.println("<vote> can be <+1> or <-1>");
                			break;
                		}
                		winClient.ratePost(command[1], command[2]);
                        break;
                    case "help":
                        System.out.println("Type one of these commands:\n");
                        System.out.println("register <username> <password> <tag1 tag2... tag5>");
                        System.out.println("For registering a new user, you can specify max 5 tags to let other users"
                        + " know your interests, your username must be unique\n");
                        System.out.println("login <username> <password>");
                        System.out.println("To login with you username and password after you registered\n");
                        System.out.println("logout");
                        System.out.println("To logout, if you want to end your sessione use -> exit\n");
                        System.out.println("list users");
                        System.out.println("To see other users who share your interests\n");
                        System.out.println("list followers");
                        System.out.println("To see who is following you\n");
                        System.out.println("list following");
                        System.out.println("To see the users you are following\n");
                        System.out.println("follow <username>");
                        System.out.println("To follow another user\n");
                        System.out.println("unfollow <username>");
                        System.out.println("To stop following another user\n");
                        System.out.println("blog");
                        System.out.println("To view all the posts in your blog\n");
                        System.out.println("post \"<title>\" \"<content>\"");
                        System.out.println("To create a new post, the title can be max 20 characters and the content " +
                                "can be max 500 characters long\n");
                        System.out.println("show feed");
                        System.out.println("To see all the posts in your feed\n");
                        System.out.println("show post <id>");
                        System.out.println("To see the post related to a certain id\n");
                        System.out.println("delete <id>");
                        System.out.println("To delete the post related to the id, if you are not the author of the post" +
                                "you cannot delete the post.\n" +
                                "If you rewinned the post, the post will be only delete from your blog\n");
                        System.out.println("rewin <id>");
                        System.out.println("To rewin a post, that is publishing another author post in your blog\n");
                        System.out.println("rate <id> <vote>");
                        System.out.println("To rate a post, the vote must be <+1> or <-1>, you cannot vote a post " +
                                " with the same value more than once.\n" +
                                "You can change your opinion and vote again with an opposite value\n" +
                                "You also cannot rate your own post or posts that are not in your feed\n");
                        System.out.println("comment");
                        System.out.println("To add a comment on a post. You cannot add comment on your posts or posts" +
                                "that are not in your feed\n");
                        System.out.println("wallet");
                        System.out.println("To get your wallet\n");
                        System.out.println("wallet btc");
                        System.out.println("To get your wallet in bitcoins\n");
                        System.out.println("exit");
                        System.out.println("To end your sessions." +
                                " Attention! you will be automatically logged out if you exit while logged in");

                    	break;
				default:
                        System.out.println("Command " + command[0] + " not recognized");
                        System.out.println("If you need help type -> help");
                        break;
                }
                System.out.print("> ");
                winClient.print.set(false);
            }
            System.out.println("Closing session...");
            scan.close();

            // Se l'utente richiede di uscire senza fare il logout verra' fatto in automatico
            // Se il socket e' connesso mi disconnetto
            if(clientSocket != null && clientSocket.isConnected()) {
                try {
                    if(winClient.currentUser != null) {
                        winClient.logoutUser();
                    }
                    clientSocket.close();
                } catch (IOException e) {
                    System.err.println("ERROR: cannot disconnect client " + e.getMessage());
                }
            }
        } catch (IOException e1) {
            System.err.println("ERROR: problem communicating with server: " + e1.getMessage());
            System.exit(-1);
        }
        System.exit(0);
    }
}
