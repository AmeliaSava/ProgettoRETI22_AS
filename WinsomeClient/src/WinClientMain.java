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
                
                System.out.println("User requested " + command[0]);
                
                // Preparo le variabili per la conversione dei json delle risposte
                Gson gson = new Gson();
                Type type = new TypeToken<List<String>>(){}.getType();


                /*
                 * A seconda del tipo di operazione richiesta dal client vado a controllare che la richiesta
                 * sia corretta in ogni sua componente prima di inviarla al server
                 */

                switch (command[0]) {
                
                    case "register":
                    	
                    	if(winClient.currentUser != null) {
                    		System.err.println("ERROR: You are already logged in");
                        	break;
                        }

                    	if(command.length > 8 || command.length < 4) {
                    		System.err.println("ERROR: use register <username> <password> <tag1 tag2... tag5> tag list must be max 5 tags long");
                    		break;
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
	                        
                        break;

                    case "login":

                        if(command.length != 3) {
                            System.err.println("ERROR: correct login <username> <password>");
                            break;
                        }
                        
                        if(winClient.currentUser != null) {
                        	System.err.println("ERROR: You are already logged in");
                        	break;
                        }

                        WinUtils.send(action, clientSocket);

                        String loginResponse = WinUtils.receive(clientSocket);
                                            
                        List<String> followers = gson.fromJson(loginResponse.toString(), type);
                       
                        if(followers.get(0).equals("LOGIN-OK")) {
                            System.out.println("Welcome " + command[1] + " you are now logged in!");
                            winClient.currentUser = command[1];
                            
                            // Se il login e' andato a buon fine recupero dalla risposta la lista dei follower gia' esistenti
                            followers.remove(0);
                            winClient.listFollowers = followers;
                        
                            winClient.callbackRegister();
                            winClient.connectMulticast();
                        } else if(followers.get(0).equals("USER-NOT-FOUND")) {
                            System.err.println("Username not found, you need to register first!");
                        } else if(followers.get(0).equals("INCORRECT-PSW")) {
                            System.err.println("Password is incorrect");
                        }
                        break;
                        
                    case "logout":

                        // Controllo se l'utente ha gia' effettuato il login
                        if(winClient.currentUser == null) {
                            System.err.println("No user logged in, cannot log out");
                            break;
                        }

                        // Creo la stringa da inviare al server con "logout <username>"

                        String logout = command[0] + " " + winClient.currentUser;

                        System.out.println(logout);

                        WinUtils.send(logout, clientSocket);

                        String logoutResponse = WinUtils.receive(clientSocket);

                        // Se il logout e' andato a buon fine resetto la sessione
                        // e mi disconnetto da multicast e callback
                        if(logoutResponse.equals("LOGOUT-OK")) {
                            System.out.println(winClient.currentUser + " logged out");

                            try {
                                server.unregisterForCallback(stub2, winClient.currentUser);
                            } catch (RemoteException e) {
                                e.printStackTrace();
                            }

                            winClient.currentUser = null;
                            winClient.listFollowers.clear();
                            winClient.disconnectMulticast();

                        } else throw new InvalidServerResponseException("Invalid logout response");

                        break;
                    case "list":

                        if(command.length != 2 || (!(command[1].equals("users")) && !(command[1].equals("followers")) && !(command[1].equals("following")))) {
                            System.out.println("ERROR: correct list users followers following");
                            break;
                        }

                        if(winClient.currentUser == null) {
                            System.err.println("User not logged in");
                            break;
                        }

                        // Nel caso del comando "list followers" gestisco lato client
                        // Altrimenti spedisco le informazioni al server
                        if(command[1].equals("followers")) {

                            // Stampo le informazioni sulla lista dei followers

                            if(winClient.listFollowers.isEmpty()) {
                            	System.out.println("You have no followers");
                            	break;
                            }

                            System.out.println("You have " + winClient.listFollowers.size() + " followers:");

                            for(String user : winClient.listFollowers) {
                                System.out.println(user);
                            }

                        } else {
                            String list = action + " " + winClient.currentUser;
                            System.out.println(list);
                            WinUtils.send(list, clientSocket);

                            String listResponse = WinUtils.receive(clientSocket);

                            // converto in lista la risposta                           
                            List<String> users = gson.fromJson(listResponse.toString(), type);

                            if(command[1].equals("users")) {
                                if (users.get(0).equals("USER-NOT-FOUND")) {
                                    System.out.println("No user with common tags found");
                                } else if (users.get(0).equals("LIST-USERS-OK")) {
                                    // se non ci sono stati errori stampo il risultato
                                    users.remove(0);
                                    System.out.println("We found these users that share your interests:");
                                    for (String info : users) {
                                        String[] userTag = info.split("/");
                                        System.out.println("Username: " + userTag[0]);
                                        System.out.println("You have these tags in common:");
                                        for (int i = 1; i < userTag.length; i++) System.out.println(userTag[i]);
                                    }
                                }
                            }
                            if(command[1].equals("following")) {
                                if (users.get(0).equals("USER-NOT-FOUND")) {
                                    System.out.println("You are not following any user");
                                } else if (users.get(0).equals("LIST-FOLLOWING-OK")) {
                                    // se non ci sono stati errori stampo il risultato
                                    users.remove(0);
                                    System.out.println("You are following these users:");
                                    for (String info : users) {
                                        System.out.println("Username: " + info);
                                    }
                                }
                            }
                        }
                        break;
                    case "follow":

                        if(winClient.currentUser == null) {
                            System.err.println("User not logged in");
                            break;
                        }

                        // Creo la stringa da inviare al server con "follow <username da seguire> <username utente corrente>"

                        String follow = action + " " + winClient.currentUser;

                        WinUtils.send(follow, clientSocket);

                        String followResponse = WinUtils.receive(clientSocket);

                        if(followResponse.equals("FOLLOW-OK")) {
                            System.out.println(winClient.currentUser + " you are now following " + command[1]);
                        } else if(followResponse.equals("USER-NOT-FOUND")) {
                            System.err.println("The user you tried to follow does not exist");
                        } else if(followResponse.equals("CURUSER-NOT-FOUND")) {
                            System.err.println("Username not found, login or register");
                        } else if(followResponse.equals("ALREADY-FOLLOWING")) {
                            System.err.println("You are already following this user");
                        }
                        break;
                    case "unfollow":

                        if(winClient.currentUser == null) {
                            System.err.println("User not logged in");
                            break;
                        }

                        // Creo la stringa da inviare al server con
                        // "unfollow <username da non seguire piu'> <username utente corrente>"

                        String unfollow = action + " " + winClient.currentUser;

                        WinUtils.send(unfollow, clientSocket);

                        String unfollowResponse = WinUtils.receive(clientSocket);

                        if(unfollowResponse.equals("UNFOLLOW-OK")) {
                            System.out.println(winClient.currentUser + " you stopped following " + command[1]);
                        } else if(unfollowResponse.equals("USER-NOT-FOUND")) {
                            System.err.println("The user you tried to unfollow does not exist");
                        } else if(unfollowResponse.equals("CURUSER-NOT-FOUND")) {
                            System.err.println("Username not found, login or register");
                        } else if(unfollowResponse.equals("NOT-FOLLOWING")) {
                            System.err.println("You are not following this user");
                        }
                        break;
                        
                    case "blog":
                    	
                        if(winClient.currentUser == null) {
                            System.err.println("User not logged in");
                            break;
                        }

                        String blogMes = action + " " + winClient.currentUser;

                        WinUtils.send(blogMes, clientSocket);

                        String blogResponse = WinUtils.receive(clientSocket);
                        
                        // converto in lista la risposta
                        
                        List<String> blog = gson.fromJson(blogResponse.toString(), type);
                        
                        if (blog.get(0).equals("BLOG-EMPTY")) {
                            System.out.println("Your blog is still empty, make a new post!");
                        } else if (blog.get(0).equals("BLOG-OK")) {
                            // se non ci sono stati errori stampo il risultato
                            blog.remove(0);
                            System.out.println("BLOG:");
                            for (String info : blog) {
                                String[] blogEntry = info.split("/");
                                System.out.println("Post ID: " + blogEntry[0]);
                                System.out.println("Author: " + blogEntry[1]);
                                System.out.println("Title: " + blogEntry[2]);
                            }
                        }
                        
                        
                        break;
                        
                    case "post":

                        if(winClient.currentUser == null) {
                            System.err.println("User not logged in");
                            break;
                        }

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

                        String post = action.concat(winClient.currentUser);
                        WinUtils.send(post, clientSocket);

                        String postResponse = WinUtils.receive(clientSocket);

                        if(postResponse.equals("POST-OK")) {
                            System.err.println("You posted!");
                        } else if(postResponse.equals("USER-NOT-FOUND")) {
                            System.err.println("error!");
                        }

                        break;
                        
                    case "show":
                    	
                        if((!(command[1].equals("feed")) && !(command[1].equals("post")))) {
                            System.err.println("ERROR: correct show feed OR show post");
                            break;
                        }
                        
                        if(winClient.currentUser == null) {
                            System.err.println("ERROR: User not logged in");
                            break;
                        }

                        if(command[1].equals("feed")) {
                        	
                            String feedMes = action + " " + winClient.currentUser;

                            WinUtils.send(feedMes, clientSocket);

                            String feedResponse = WinUtils.receive(clientSocket);
                            
                            // converto in lista la risposta
                            List<String> feed = gson.fromJson(feedResponse.toString(), type);
                            
                            if (feed.get(0).equals("FEED-EMPTY")) {
                                System.out.println("There are no posts on your feed.");
                            } else if (feed.get(0).equals("FEED-OK")) {
                                // se non ci sono stati errori stampo il risultato
                                feed.remove(0);
                                System.out.println("FEED:");
                                for (String info : feed) {
                                    String[] feedEntry = info.split("/");
                                    System.out.println("Post ID: " + feedEntry[0]);
                                    System.out.println("Author: " + feedEntry[1]);
                                    System.out.println("Title: " + feedEntry[2]);
                                }
                            }

                        } else if(command[1].equals("post")) {
                        	
                        	if(command.length != 3) {
                        		System.err.println("ERROR: correct use -> show post <post id>");
                        		break;
                        	}
                        	
                        	String postMes = action + " " + winClient.currentUser;
                        	
                            WinUtils.send(postMes, clientSocket);

                            String showpostResponse = WinUtils.receive(clientSocket);
                            
                            if(showpostResponse.equals("POST-NOT-FOUND")) {
                            	System.err.println("The post you requested doesn't exist");
                            } else {
                            	WinPost curPost = gson.fromJson(showpostResponse.toString(), WinPost.class);
                            	
                            	System.out.println("\nPOST:");
                            	System.out.println("'" + curPost.getPostTitle() + "'");
                            	System.out.println("'" + curPost.getPostContent() + "'");
                            	System.out.println("By: " + curPost.getPostAuthor() + " ID:" + curPost.getIdPost().toString());
                            	System.out.println("Upvotes " + curPost.getUpvoteCount());
                            	System.out.println(curPost.getComments().size());
                            	
                            	if(curPost.getPostAuthor().equals(winClient.currentUser)) {
                            		System.out.println("\nWant to delete this post? type -> delete <idPost>");
                            		System.out.println("Otherwise type -> no");
                            		System.out.print("> ");
                            		
                            		String delete = scan.nextLine();
                            		
                            		if(delete.equals("no")) break;
                            		
                            		
                            		String[] deleteInfo = delete.split(" ");
                            		
                            		if(!delete.contains("delete") && deleteInfo.length != 2) {
                            			System.err.println("ERROR: delete <idPost>");                   
                            			break;
                            		}
                            		
                           			String deleteMes = delete + " " + winClient.currentUser;
                        			                        	                                	
                                    WinUtils.send(deleteMes, clientSocket);

                                    String deleteResponse = WinUtils.receive(clientSocket);
                                    
                                    if(deleteResponse.equals("DELETE-OK")) {
                                    	System.out.println("> The post was successfully deleted!");
                                    } else if(deleteResponse.equals("POST-NOT-FOUND")) {
                                    	System.err.println("The post you tried to delete does not exist");
                                    }
                                    
                            		break;
                            	}
                            	
                            	if(curPost.getFeed()) {
                            		// Se l'utente ha il post nel suo feed puo' votarlo o commentare
                            		System.out.println("\nIf you want to add a comment on this post type -> comment <idPost> \"<comment>\"");
                            		System.out.println("If you want to rate this post type -> rate <idPost> <vote>");
                            		System.out.println("<vote> can be <+1> or <-1>");
                            		System.out.println("Otherwise type -> no");
                            		System.out.print("> ");
                            		
                            		String newAction = scan.nextLine();
                            		
                            		if(newAction.equals("no")) break;
                            		
                            		
                            		String[] actionInfo = newAction.split(" ");
                            		
                            		// Controllo che l'utente abbia inserito l'input corretto
                            		
                            		if((!newAction.contains("rate") && !newAction.contains("comment"))) {
                            			System.err.println("ERROR: comment <idPost> \"<comment>\"");   
                            			System.err.println("ERROR: rate <idPost> <vote>");
                            			break;
                            		}
                            		
                            		// Voto
                            		if(actionInfo[0].equals("rate")) {
                            			
                            			if(actionInfo.length != 3) {
                            				System.err.println("ERROR: rate <idPost> <vote>");
                            				break;
                            			}
                            			
                            			if(!actionInfo[2].equals("+1") && !actionInfo[2].equals("-1")) {
                            				System.out.println("<vote> can be <+1> or <-1>");
                            				break;
                            			}
                            			
                               			String rate = newAction + " " + winClient.currentUser;
	                                	
                                        WinUtils.send(rate, clientSocket);

                                        String rateResponse = WinUtils.receive(clientSocket);
                                        
                                        if(rateResponse.equals("RATE-OK")) {
                                        	System.out.println("Your vote was added to the post!");
                                        } else if(rateResponse.equals("POST-NOT-FOUND")) {
                                        	System.err.println("The post you tried to rate does not exist");
                                        } else if(rateResponse.equals("ALREADY-RATED")) {
                                        	System.err.println("You cannot rate the same post more than once");
                                        }

                            		}
                            		
                            		// Commento
                            		if(actionInfo[0].equals("comment")) {
                            			String[] comment = newAction.split("\"");
                            			if(comment.length != 2) {
                            				System.err.println("ERROR: comment <idPost> \"<comment>\"");
                            				break;
                            			}

                            			String commentMes = newAction + winClient.currentUser;
	                                	
                                        WinUtils.send(commentMes, clientSocket);

                                        String commentResponse = WinUtils.receive(clientSocket);
                                        
                                        if(commentResponse.equals("COMMENT-OK")) {
                                        	System.out.println("Your comment was added to the post!");
                                        } else if(commentResponse.equals("POST-NOT-FOUND")) {
                                        	System.err.println("The post you tried to rate does not exist");
                                        }
                            		}
                            		
                            		break;
                            	}
                            	
                            }                        
                        }
                        
                        break;
                    
                    case "rewin":
                    	
                    	if(winClient.currentUser == null) {
                            System.err.println("User not logged in");
                            break;
                        }
                        
                    	if(command.length != 2) {
                    		System.out.println("ERROR: use -> rewin <idPost>");
                    		break;
                    	} 
                    	
                    	String rewin = action + " " + winClient.currentUser;
                    	
                        WinUtils.send(rewin, clientSocket);

                        String rewinResponse = WinUtils.receive(clientSocket);
                        
                        if(rewinResponse.endsWith("REWIN-OK")) {
                        	System.out.println("< You rewinned the post!");
                        } else System.out.println(rewinResponse);
                    	
                    	break;
                    case "wallet":
                    	
                        if(winClient.currentUser == null) {
                            System.err.println("User not logged in");
                            break;
                        }
                        
                    	if(command.length == 1) {
                    		
                    		String wallet = action + " " + winClient.currentUser;
                        	
                            WinUtils.send(wallet, clientSocket);

                            String walletResponse = WinUtils.receive(clientSocket);
                            
                            JsonObject walletJson = new Gson().fromJson(walletResponse, JsonObject.class);
                                                        
                            if(walletJson.get("result").getAsInt() == 0) {
                            	System.out.println("< " + walletJson.get("result-msg").getAsString());
                            	
                            	List<String> transactionList = gson.fromJson(walletJson.get("transaction-list").getAsString(), type);
                            	
                            	for(String transaction : transactionList) {
                            		
                            		String[] values = transaction.split("/");
                            	
                            		System.out.printf("%.2f\n", Double.parseDouble(values[0]));
                            		System.out.println(values[1]);
                            	}
                            	System.out.println("< TOTAL " + walletJson.get("wallet-tot").getAsDouble());
                            	
                         
                            } else System.out.println(walletJson.get("result-msg").getAsString());
                            break;
                            
                    	} else if(command[1].equals("btc")) {
                    		System.out.println("wallet btc");
                    		
                    		String walletbtc = action + " " + winClient.currentUser;
                        	
                            WinUtils.send(walletbtc, clientSocket);

                            String walletbtcResponse = WinUtils.receive(clientSocket);
                            
                            JsonObject walletbtcJson = new Gson().fromJson(walletbtcResponse, JsonObject.class);
                                                        
                            if(walletbtcJson.get("result").getAsInt() == 0) {
                            	System.out.println("< " + walletbtcJson.get("result-msg").getAsString());
                            	
                            	List<String> transactionList = gson.fromJson(walletbtcJson.get("transaction-list").getAsString(), type);
                            	
                            	for(String transaction : transactionList) {
                            		
                            		String[] values = transaction.split("/");
                            	
                            		System.out.printf("%.2f\n", Double.parseDouble(values[0]));
                            		System.out.println(values[1]);
                            	}
                            	System.out.println("< TOTAL " + walletbtcJson.get("wallet-tot").getAsDouble());
                            	
                         
                            } else System.out.println(walletbtcJson.get("result-msg").getAsString());
                            break;
                    	} else {
                    		System.err.println("ERROR:");
                    		System.err.println("correct use -> 'wallet' to see your wallet");
                    		System.err.println("'wallet btc' to see your wallet in bitcoin");
                    		break;
                    	}
                    	                    	
                    case "delete":                    	
                    	System.err.println("ERROR: to delete a post you must first do -> show post <idPost>");
                    	System.err.println("You can delete a post only if you are the author");                  	                        
                        break;
                        
                    case "comment":
                    	System.err.println("ERROR: to comment on a post you must first do -> show post <idPost>");
                    	System.err.println("You can comment on a post only if it is in your feed");                  	                        
                        break;
                        
                    case "rate":
                    	System.err.println("ERROR: to rate a post you must first do -> show post <idPost>");
                    	System.err.println("You can rate a post only if it is in your feed");                  	                        
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
