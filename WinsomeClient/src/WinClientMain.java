import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.SocketChannel;
import java.rmi.NotBoundException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;

public class WinClientMain {

    private static int TCPserverport;
    private static int UDPserverport;
    private static int RMIregisterport;
    private static int RMIfollowersport;
    private static String hostAddress;
    private static String multicastAddress;

    private static SocketChannel clientSocket;

    private static RegistrationService serverRegister;
    private static Remote RemoteRegister;

    private static NotificationServiceServer server;
    private static NotificationServiceClient stub2;

    // dati dell'utente
    private String currentUser = null;
    private List<String> listFollowers;

    // Thread per la ricezione degli aggiornamenti sul portafoglio
    private WinClientWallUp WalletUpdate;
    private Thread WallUp;

    public void configClient () {

        File file = new File(".\\src\\files\\ClientConfigFile.txt");

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
                        case "TCPSERVERPORT":
                            TCPserverport = Integer.parseInt(st.nextToken());
                            break;
                        case "UDPSERVERPORT":
                            UDPserverport = Integer.parseInt(st.nextToken());
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
                        case "MULTICAST":
                            multicastAddress = st.nextToken();
                            break;
                    }

                }
            }
        }

    }

    private void connect() {
        //mi connetto al server
        SocketAddress address = new InetSocketAddress(hostAddress, TCPserverport);

        try {
            //ATTENZIONE timeout
            clientSocket = SocketChannel.open(address);
            //aspetto che la connessione sia stabilita
            while(!clientSocket.finishConnect()) {}
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private void registrationRegister() {
        try {
            Registry registry1 = LocateRegistry.getRegistry(RMIregisterport);
            RemoteRegister = registry1.lookup("REGISTER-SERVER");
            serverRegister = (RegistrationService) RemoteRegister;
        } catch (Exception e) {
            System.err.println("ERROR: invoking object method " + e.toString() + e.getMessage());
            e.printStackTrace();
        }
    }

    private void callbackRegister() {

        try {
            Registry registry2 = LocateRegistry.getRegistry(RMIfollowersport);
            server = (NotificationServiceServer) registry2.lookup("FOLLOWERS-SERVER");
            NotificationServiceClient followCallback = new NotificationServiceClientImpl(listFollowers);
            stub2 = (NotificationServiceClient) UnicastRemoteObject.exportObject(followCallback, 0);
            server.registerForCallback(stub2, currentUser);
        } catch (RemoteException e) {
            System.err.println("ERROR: callback register " + e.getMessage());
            e.printStackTrace();
        } catch (NotBoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        return;
    }
    
    private void callbackUnregister() {
        try {
            server.unregisterForCallback(stub2, currentUser);
        } catch (RemoteException e) {
        	System.err.println("ERROR: callback unregister " + e.getMessage());
            e.printStackTrace();
        }
    }

    /*
     * Avvia un thread che si mettera' in attesa degli aggiornamenti sul wallet
     */
    private void connectMulticast() {

        WalletUpdate = new WinClientWallUp(UDPserverport, multicastAddress);
        WallUp = new Thread(WalletUpdate);
        WallUp.start();
        return;
    }

    /*
     * Ferma il thread che attende la notifica sul calcolo delle ricompense
     */
    private void disconnectMulticast() {

        WalletUpdate.closeMulticast();
        WallUp.interrupt();
        return;
    }

    private void registerUser(String[] command) throws RemoteException {
    	
    	if(currentUser != null) {
    		System.out.print("< ");
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

        // Controllo se la registrazione e' andata a buon fine
        if(serverRegister.registerUser(command[1], command[2], tagList) == 0)
            System.out.println("< User " + command[1] + " has been registred! You can now log in...");
        else System.err.println("ERROR: Username already in use, try logging in or choose another username");
    }
    
    private void loginUser(String action, String[] command) throws IOException {
    	        
        if(currentUser != null) {
        	System.out.print("< ");
        	System.err.println("ERROR: You are already logged in");
        	return;
        }

        WinUtils.send(action, clientSocket);
        String loginResponse = WinUtils.receive(clientSocket);
        
        Gson gson = new Gson();
        Type type = new TypeToken<List<String>>(){}.getType();
        JsonObject loginJson = new Gson().fromJson(loginResponse, JsonObject.class);                                       
       
       // Il login e' andato a buon fine
        if(loginJson.get("result").getAsInt() == 0) {
        	System.out.println("< " + loginJson.get("result-msg").getAsString());
            
        	// Setto l'utente della sessione corrente
            currentUser = command[1];
            
            // Recupero dalla risposta la lista dei follower gia' esistenti
            listFollowers = gson.fromJson(loginJson.get("followers-list").getAsString(), type);
        
            // Registro l'utente corrent per la callback sulla lista dei followers
            callbackRegister();
            // Mi connetto al gruppo di multicast
            connectMulticast();
            
        } else {
        	System.out.print("< ");
        	System.err.println(loginJson.get("result-msg").getAsString());
        }
    }
    
    private void logoutUser(String[] command) throws IOException {
    	
        // Nessun utente online
        if(currentUser == null) {
        	System.out.print("< ");
            System.err.println("ERROR: No user logged in, cannot log out");
            return;
        }

        // Creo la stringa da inviare al server con "logout <username>"
        String logout = command[0] + " " + currentUser;
        WinUtils.send(logout, clientSocket);
        String logoutResponse = WinUtils.receive(clientSocket);
        
        JsonObject logoutJson = new Gson().fromJson(logoutResponse, JsonObject.class);                     

        // Se il logout e' andato a buon fine resetto la sessione
        // e mi disconnetto da multicast e callback
        if(logoutJson.get("result").getAsInt() == 0) {
        	System.out.println("< " + logoutJson.get("result-msg").getAsString());
            //System.out.println(winClient.currentUser + " logged out");

        	callbackUnregister();

            currentUser = null;
            listFollowers.clear();
            disconnectMulticast();

        } else {
        	System.out.print("< ");
        	System.err.println(logoutJson.get("result-msg").getAsString());
        }
    }
    
    private void listUsers(String action) throws IOException {
    	
        // Nessun utente online
        if(currentUser == null) {
        	System.out.print("< ");
            System.err.println("ERROR: No user logged in");
            return;
        }
                
    	String list = action + " " + currentUser;
        WinUtils.send(list, clientSocket);
        String listResponse = WinUtils.receive(clientSocket);
        
        Gson gson = new Gson();
        Type type = new TypeToken<List<String>>(){}.getType();
        JsonObject usersJson = new Gson().fromJson(listResponse, JsonObject.class);
        
        if(usersJson.get("result").getAsInt() == 0) {
        	
        	System.out.println("< " + usersJson.get("result-msg").getAsString());
        	
        	if(usersJson.get("users-list") == null) return;
        	
        	List<String> users = gson.fromJson(usersJson.get("users-list").getAsString(), type);
        	        		
            for (String info : users) {
                String[] userTag = info.split("/");
                System.out.println("Username: " + userTag[0]);
                System.out.println("You have these tags in common:");
                for (int i = 1; i < userTag.length; i++) System.out.println(userTag[i]);
            }
        	
        } else {
        	System.out.print("< ");
        	System.err.println(usersJson.get("result-msg").getAsString());
        }
    }
    
    private void listFollowers() {
    	
        // Nessun utente online
        if(currentUser == null) {
        	System.out.print("< ");
            System.err.println("ERROR: No user logged in");
            return;
        }

        // Stampo le informazioni sulla lista dei followers

        if(listFollowers.isEmpty()) {
        	System.out.println("< You have no followers");
        	return;
        }

        System.out.println(" < You have " + listFollowers.size() + " followers:");

        for(String user : listFollowers) {
            System.out.println(user);
        }
    }
    
    private void listFollowing(String action) throws IOException {
    	     
        if(currentUser == null) {
            System.err.println("ERROR: No user logged in");
            return;
        }
                               
    	String list = action + " " + currentUser;
        WinUtils.send(list, clientSocket);
        String listResponse = WinUtils.receive(clientSocket);

        Gson gson = new Gson();
        Type type = new TypeToken<List<String>>(){}.getType();
        JsonObject followingJson = new Gson().fromJson(listResponse, JsonObject.class);
        
        if(followingJson.get("result").getAsInt() == 0) {
        	System.out.println("< " + followingJson.get("result-msg").getAsString());
            
        	if(followingJson.get("following-list") == null) return;
        	
        	List<String> users = gson.fromJson(followingJson.get("following-list").getAsString(), type);
        	
        	for (String info : users) {
                System.out.println("Username: " + info);
            }
        	
        } else {
        	System.out.print("< ");
        	System.err.println(followingJson.get("result-msg").getAsString());
        }
    }
    
    private void followUser(String action) throws IOException {
    	
    	// Nessun utente online
    	if(currentUser == null) {
            System.err.println("ERROR: No user logged in");
            return;
        }

        // Creo la stringa da inviare al server con "follow <username da seguire> <username utente corrente>"
        String follow = action + " " + currentUser;
        WinUtils.send(follow, clientSocket);
        String followResponse = WinUtils.receive(clientSocket);
        
        JsonObject followJson = new Gson().fromJson(followResponse, JsonObject.class);

        if(followJson.get("result").getAsInt() == 0) {
        	System.out.println("< " + followJson.get("result-msg").getAsString());
        } else {
        	System.out.print("< ");
        	System.err.println(followJson.get("result-msg").getAsString());
        }  
    }
    
    private void unfollowUser(String action) throws IOException {
    	
        if(currentUser == null) {
        	System.err.println("ERROR: No user logged in");
            return;
        }

        // Creo la stringa da inviare al server con
        // "unfollow <username da non seguire piu'> <username utente corrente>"

        String unfollow = action + " " + currentUser;
        WinUtils.send(unfollow, clientSocket);
        String unfollowResponse = WinUtils.receive(clientSocket);
        
        JsonObject unfollowJson = new Gson().fromJson(unfollowResponse, JsonObject.class);

        if(unfollowJson.get("result").getAsInt() == 0) {
        	System.out.println("< " + unfollowJson.get("result-msg").getAsString());
        } else {
        	System.out.print("< ");
        	System.err.println(unfollowJson.get("result-msg").getAsString());
        }
    }
    
    private void viewBlog(String action) throws IOException {
        
    	if(currentUser == null) {
    		System.err.println("ERROR: No user logged in");
            return;
        }

        String blogMes = action + " " + currentUser;
        WinUtils.send(blogMes, clientSocket);
        String blogResponse = WinUtils.receive(clientSocket);
        
        Gson gson = new Gson();
        Type type = new TypeToken<List<String>>(){}.getType();
        JsonObject blogJson = new Gson().fromJson(blogResponse, JsonObject.class);
        
        if(blogJson.get("result").getAsInt() == 0) {
        	System.out.println("< " + blogJson.get("result-msg").getAsString());
        	
        	if(blogJson.get("blog") == null) return;
        	
        	List<String> blog = gson.fromJson(blogJson.get("blog").getAsString(), type);
        	
            for (String info : blog) {
            	String[] blogEntry = info.split("/");
             	System.out.println("Post ID: " + blogEntry[0]);
             	System.out.println("Author: " + blogEntry[1]);
             	System.out.println("Title: " + blogEntry[2]);
            }
            
        } else {
        	System.err.println(blogJson.get("result-msg").getAsString());
        }       
    }
    
    private void createPost(String action) throws IOException {
    	
      	 if(currentUser == null) {
             System.err.println("ERROR: No user logged in");
             return;
         }

        String post = action.concat(currentUser);
        WinUtils.send(post, clientSocket);
        String postResponse = WinUtils.receive(clientSocket);
        
        JsonObject postJson = new Gson().fromJson(postResponse, JsonObject.class);

        if(postJson.get("result").getAsInt() == 0) {
        	System.out.println(postJson.get("result-msg").getAsString());
        } else {
        	System.err.println(postJson.get("result-msg").getAsString());
        }
    }
    
    private void showFeed(String action) throws IOException {
    	
     	if(currentUser == null) {
            System.err.println("ERROR: User not logged in");
            return;
        }
    	
        String feedMes = action + " " + currentUser;
        WinUtils.send(feedMes, clientSocket);
        String feedResponse = WinUtils.receive(clientSocket);
        
        Gson gson = new Gson();
        Type type = new TypeToken<List<String>>(){}.getType();
        JsonObject feedJson = new Gson().fromJson(feedResponse, JsonObject.class);
        
        if(feedJson.get("result").getAsInt() == 0) {
        	System.out.println("< " + feedJson.get("result-msg").getAsString());
        	
        	if(feedJson.get("feed") == null) return;
        	
        	List<String> feed = gson.fromJson(feedJson.get("feed").getAsString(), type);
        	        	
        	for (String info : feed) {
                String[] feedEntry = info.split("/");
                System.out.println("Post ID: " + feedEntry[0]);
                System.out.println("Author: " + feedEntry[1]);
                System.out.println("Title: " + feedEntry[2]);
            }
        } else {
        	System.err.println(feedJson.get("result-msg").getAsString());
        } 
    }
    
    private void showPost(String action) throws IOException {
    	
    	if(currentUser == null) {
            System.err.println("ERROR: User not logged in");
            return;
        }
    	
    	String postMes = action + " " + currentUser;   
        WinUtils.send(postMes, clientSocket);
        String showpostResponse = WinUtils.receive(clientSocket);
        
        Gson gson = new Gson();
        Type type = new TypeToken<List<String>>(){}.getType();
        JsonObject postJson = new Gson().fromJson(showpostResponse, JsonObject.class);
        
        if(postJson.get("result").getAsInt() == 0) {
        	System.out.println("< " + postJson.get("result-msg").getAsString());
        	System.out.println("'" + postJson.get("title").getAsString() + "'");
        	System.out.println("'" + postJson.get("content").getAsString() + "'");
        	System.out.println("By: " + postJson.get("author").getAsString() + " ID: " + postJson.get("id").getAsString());
        	System.out.println("Upvotes " + postJson.get("upvote").getAsInt());
        	System.out.println("Dowvotes " + postJson.get("downvote").getAsInt());
        	
        
        	List<String> comments = gson.fromJson(postJson.get("comments").getAsString(), type);
        	
        	for(String comment : comments) {
        		System.out.println(comment);
        	}
        	
        } else {
        	System.err.println(postJson.get("result-msg").getAsString());
        } 
    }
    
    private void deletePost(String action) throws IOException {
   		

    	if(currentUser == null) {
            System.err.println("ERROR: User not logged in");
            return;
        }
		
		String deleteMes = action + " " + currentUser;		                        	                                	
        WinUtils.send(deleteMes, clientSocket);
        String deleteResponse = WinUtils.receive(clientSocket);
        
        JsonObject deleteJson = new Gson().fromJson(deleteResponse, JsonObject.class);
        
        if(deleteJson.get("result").getAsInt() == 0) {
        	System.out.println("< " + deleteJson.get("result-msg").getAsString());
        	
        } else {
        	System.err.println(deleteJson.get("result-msg").getAsString());
        }
    }
    
    private void rewinPost(String action) throws IOException {
    	
    	if(currentUser == null) {
    		System.err.println("ERROR: User not logged in");
            return;
        }
    	
    	String rewin = action + " " + currentUser;
        WinUtils.send(rewin, clientSocket);
        String rewinResponse = WinUtils.receive(clientSocket);
        
        JsonObject rewinJson = new Gson().fromJson(rewinResponse, JsonObject.class);
        
        if(rewinJson.get("result").getAsInt() == 0) {     
        	System.out.println("< " + rewinJson.get("result-msg").getAsString());
        } else {
        	System.err.println(rewinJson.get("result-msg").getAsString());
        }
    }
    
    private void ratePost(String action) throws IOException {
 		
    	if(currentUser == null) {
    		System.err.println("ERROR: User not logged in");
            return;
        }
    	
    	String rate = action + " " + currentUser;    	
        WinUtils.send(rate, clientSocket);
        String rateResponse = WinUtils.receive(clientSocket);
        
        JsonObject rateJson = new Gson().fromJson(rateResponse, JsonObject.class);
        
        if(rateJson.get("result").getAsInt() == 0) {
        	System.out.println("< " + rateJson.get("result-msg").getAsString());
        } else {
        	System.err.println(rateJson.get("result-msg").getAsString());
        }
    }
    
    private void addComment(String action) throws IOException {
		
    	if(currentUser == null) {
    		System.err.println("ERROR: User not logged in");
            return;
        }
    	
		String commentMes = action + currentUser;    	
        WinUtils.send(commentMes, clientSocket);
        String commentResponse = WinUtils.receive(clientSocket);
        
        JsonObject commentJson = new Gson().fromJson(commentResponse, JsonObject.class);
        
        if(commentJson.get("result").getAsInt() == 0) {
        	System.out.println("< " + commentJson.get("result-msg").getAsString());
        } else {
        	System.err.println(commentJson.get("result-msg").getAsString());
        }
    }
    
    private void getWallet(String action) throws IOException {
    	
       	if(currentUser == null) {
       		System.err.println("ERROR: No user logged in");
        	return;
        }
		  
		String wallet = action + " " + currentUser;
	  	WinUtils.send(wallet, clientSocket);
	  	String walletResponse = WinUtils.receive(clientSocket);


        Gson gson = new Gson();
        Type type = new TypeToken<List<String>>(){}.getType();
	  	JsonObject walletJson = new Gson().fromJson(walletResponse, JsonObject.class);
	                                  
	  	if(walletJson.get("result").getAsInt() == 0) {
	  		System.out.println("< " + walletJson.get("result-msg").getAsString());
	      	
	      	List<String> transactionList = gson.fromJson(walletJson.get("transaction-list").getAsString(), type);
	      	
	      	for(String transaction : transactionList) {
	      		
	      		String[] values = transaction.split("/");
	      	
	      		System.out.printf("%.2f\n", Double.parseDouble(values[0]));
	      		System.out.println(values[1]);
	      	}
	      	System.out.printf("< TOTAL %.2f\n", walletJson.get("wallet-tot").getAsDouble());
	      	
	   
	      } else System.out.println(walletJson.get("result-msg").getAsString());
    }
    
    private void getWalletBitcoin(String action) throws IOException {
    	
  	  if(currentUser == null) {
  		  System.err.println("ERROR: No user logged in");
          return;
      }
		
  	  String walletbtc = action + " " + currentUser;
  	  WinUtils.send(walletbtc, clientSocket);
  	  String walletbtcResponse = WinUtils.receive(clientSocket);
        

      Gson gson = new Gson();
      Type type = new TypeToken<List<String>>(){}.getType();
  	  JsonObject walletbtcJson = new Gson().fromJson(walletbtcResponse, JsonObject.class);
                                    
  	  if(walletbtcJson.get("result").getAsInt() == 0) {
  		  System.out.println("< " + walletbtcJson.get("result-msg").getAsString());
        	
  		  List<String> transactionList = gson.fromJson(walletbtcJson.get("transaction-list").getAsString(), type);
        	
  		  for(String transaction : transactionList) {
        		
  			  String[] values = transaction.split("/");
        	
  			  System.out.printf("%.2f\n", Double.parseDouble(values[0]));
  			  System.out.println(values[1]);
  		  }
  		  
  		  System.out.printf("< TOTAL %.2f\n", walletbtcJson.get("wallet-tot").getAsDouble());

        } else System.out.println(walletbtcJson.get("result-msg").getAsString());
    }
    
    public static void main(String[] args) {

        //Parsing del file di configurazione
        WinClientMain winClient = new WinClientMain();
        winClient.configClient();

        // Inizializzo la lista che verra' utilizzata per salvare i follower dell'utente
        winClient.listFollowers = new ArrayList<String>();

        System.out.println("Server port:" + TCPserverport);
        System.out.println("Server name:" + hostAddress);

        winClient.registrationRegister();

        winClient.connect();

        try {
            Scanner scan = new Scanner(System.in);

            System.out.println("Welcome to WINSOME the reWardINg SOcial Media!");
            System.out.println("If you need help type -> help");
            System.out.print("> ");

            String action;
            while(scan.hasNextLine() && !((action = scan.nextLine()).equals("exit"))) {
            	            	
                // Divido la stringa per fare i controlli
                String[] command = action.split(" ");
                
                if(command.length == 0) continue;
                
                /*
                 * A seconda del tipo di operazione richiesta dal client controllo che l'input dell'utente sia corretto
                 * per poi chiamare la relativa funzione dove avviene la comunicazione col server
                 */

                switch (command[0]) {
                
                    case "register":
                    	
                    	if(command.length > 8 || command.length < 4) {
                    		System.out.print("< ");
                    		System.err.println("ERROR: use register <username> <password> <tag1 tag2... tag5> tag list must be max 5 tags long");
                    		break;
                    	}
                    	
                    	winClient.registerUser(command);
                    	
                        break;

                    case "login":
                    	
                        if(command.length != 3) {
                        	System.out.print("< ");
                            System.err.println("ERROR: correct login <username> <password>");
                            break;
                        }
                        
                    	winClient.loginUser(action, command);	
                    	
                        break;
                        
                    case "logout":
                    	
                    	winClient.logoutUser(command);
                    	
                        break;
                        
                    case "list":

                        if(command.length != 2 || (!(command[1].equals("users")) && !(command[1].equals("followers")) && !(command[1].equals("following")))) {
                            System.out.println("ERROR: correct list users followers following");
                            break;
                        }

                        if(command[1].equals("followers")) winClient.listFollowers();
                        else if (command[1].equals("users")) winClient.listUsers(action);
                        else if(command[1].equals("following")) winClient.listFollowing(action);
                    
                        break;
                        
                    case "follow":

                        if(command.length != 2) {
                        	System.out.println("ERROR: correct follow <username>");
                            break;
                        }
                        
                        winClient.followUser(action);

                        break;
                        
                    case "unfollow":

                        if(command.length != 2) {
                        	System.out.println("ERROR: correct unfollow <username>");
                            break;
                        }
                        
                        winClient.unfollowUser(action);

                        break;
                        
                    case "blog":
                    	
                        winClient.viewBlog(command[0]);
                        
                        break;
                        
                    case "post":
                        //catturo i dati del post
                        String[] elements = action.split("\"");

                        if(elements.length != 4) {
                            System.out.println("ERROR: correct post \"<title>\" \"<content>\"");
                            break;
                        }

                        if((elements[1].length() > 20) || (elements[3].length() > 500)) {
                            System.out.println("ERROR: format title must be under 20 characters content under 500");
                            break;
                        }
                        
                    	winClient.createPost(action);

                        break;
                        
                    case "show":
                    	
                        if((!(command[1].equals("feed")) && !(command[1].equals("post")))) {
                            System.err.println("ERROR: correct show feed OR show post");
                            break;
                        }

                        if(command[1].equals("feed")) {
                        	
                        	if(command.length != 2) {
                        		System.err.println("ERROR: correct use -> show feed");
                        		break;
                        	}
                        	
                        	winClient.showFeed(action);
                        }
                        else if(command[1].equals("post")) {
                        	
                        	if(command.length != 3) {
                        		System.err.println("ERROR: correct use -> show post <post id>");
                        		break;
                        	}
                        	
                        	winClient.showPost(action);                  
                        } else System.err.println("ERROR: command show " + command[1] + " not recognized");
                        
                        break;
                    
                    case "rewin":
                    	                                            
                    	if(command.length != 2) {
                    		System.out.println("ERROR: use -> rewin <idPost>");
                    		break;
                    	}                     	
                        
                    	winClient.rewinPost(action);
                    	
                    	break;
                    	
                    case "wallet":
                    
                    	if(command.length == 1) {
                    		winClient.getWallet(action);  
                            break;
                            
                    	} else if(command[1].equals("btc")) {
                    		winClient.getWalletBitcoin(action);
                            break;
                            
                    	} else {
                    		System.err.println("ERROR:");
                    		System.err.println("correct use -> 'wallet' to see your wallet");
                    		System.err.println("'wallet btc' to see your wallet in bitcoin");
                    		break;
                    	}
                    	                    	
                    case "delete":
                    	if(command.length != 2) {
                			System.err.println("ERROR: delete <idPost>");                   
                			break;
                		}
                    	
                    	winClient.deletePost(action);
                                     	                        
                        break;
                        
                    case "comment":
                    	
                    	String[] comment = action.split("\"");
                		if(comment.length != 2) {
                			System.err.println("ERROR: comment <idPost> \"<comment>\"");
                			break;
                		}
                		
                		winClient.addComment(action); 
                		
                        break;
                        
                    case "rate":
                    	if(command.length != 3) {
                			System.err.println("ERROR: rate <idPost> <vote>");
                			break;
                		}
                		
                		if(!command[2].equals("+1") && !command[2].equals("-1")) {
                			System.out.println("<vote> can be <+1> or <-1>");
                			break;
                		}
                		
                		winClient.ratePost(action);
                                    	                        
                        break;
                        
                    case "help":
                    	System.err.print("help");
                    	break;
				default:
                        System.out.println("Command " + command[0] + " not recognized");
                        break;
                }
                
                System.out.print("> ");
                
            }
            System.out.println("close");
            scan.close();
            
        } catch (IOException e1) {
            System.err.println("ERROR: problem communicating with server: " + e1.getMessage());
            e1.printStackTrace();
        }

        System.exit(0);
    }

}
