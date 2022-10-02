import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.SocketChannel;
import java.rmi.NotBoundException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.StringTokenizer;

public class WinClientMain {

    private static int TCPserverport;
    private static int UDPserverport;
    private static String hostAddress;
    private static String multicastAddress;
    
    private static RegistrationService serverRegister;
    private static Remote RemoteRegister;
    
    private static NotificationServiceServer server;
    private static NotificationServiceClient stub2;
    
    // dati dell'utente
    private String currentUser = null;
    private List<String> followedUsers;
    
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
    
	private void callbackRegister() {

		try {
			Registry registry2 = LocateRegistry.getRegistry(5556);
			server = (NotificationServiceServer) registry2.lookup("FOLLOWERS-SERVER");
			NotificationServiceClient followCallback = new NotificationServiceClientImpl();
			stub2 = (NotificationServiceClient) UnicastRemoteObject.exportObject(followCallback, 0);
			server.registerForCallback(stub2);
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
        winClient.followedUsers = new ArrayList<String>();

        System.out.println("Server port:" + TCPserverport);
        System.out.println("Server name:" + hostAddress);
        

        
        try {
        	Registry registry1 = LocateRegistry.getRegistry(5555);
        	RemoteRegister = registry1.lookup("REGISTER-SERVER");
        	serverRegister = (RegistrationService) RemoteRegister;
        } catch (Exception e) {
        	System.err.println("ERROR: invoking object method " + e.toString() + e.getMessage());
        	e.printStackTrace();
        }



        try {
            //mi connetto al server
            SocketAddress address = new InetSocketAddress(hostAddress, TCPserverport);
            SocketChannel clientSocket = SocketChannel.open(address);
            //timeout
            //aspetto che la connessione sia stabilita
            while(!clientSocket.finishConnect()) {}
                                                          
            Scanner scan = new Scanner(System.in);
			
            System.out.println("Do something >");
			
            String action;
			while(scan.hasNextLine() && !((action = scan.nextLine()).equals("exit"))) {
									
				//if(action.equals("exit")) scan.close();

				//splitto la stringa per fare dei controlli iniziali anche se poi la invio completa al server
				String[] command = action.split(" ");
				System.out.println("User requested " + command[0]);
					
					
				/*
				 * A seconda del tipo di operazione richiesta dal client vado a controllare che la richiesta
				 * sia corretta in ogni sua componente prima di inviarla al server
				 */
					
				switch (command[0]) {
						
					case "register":
							
					       if(command.length > 8 || command.length < 4) {
					           System.err.println("ERROR: correct register <username> <password> <tag1 tag2... tag5> tag list too long max 5 tags");
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
					       	System.out.println("User " + command[1] + " has been registred! You can now log in...");
					       else System.err.println("Username already in use, try logging in or choose another username");
						break;
					  case "login":
	
					        if(command.length != 3) {
					            System.out.println("ERROR: correct login <username> <password>");
					            break;
					        }
	
					        WinUtils.send(action, clientSocket);
	
					        String loginResponse = WinUtils.receive(clientSocket);
	
					        if(loginResponse.equals("LOGIN-OK")) {
					            System.out.println("Welcome " + command[1] + " you are now logged in!");
					            winClient.currentUser = command[1];
					            //metodo per recuperare followers gia' esistenti
					            //possibile mandarli tutti pianopiano con RMI?
					            //winClient.followedUsers = new ArrayList<String>();
					            winClient.callbackRegister();
					            winClient.connectMulticast();
					        } else if(loginResponse.equals("USER-NOT-FOUND")) {
					        	System.err.println("Username not found, you need to register first!");
					        } else if(loginResponse.equals("INCORRECT-PSW")) {
					        	System.err.println("Password is incorrect");
					        }
					        break;
					    case "logout":
					    	
					    	// Controllo se l'utente ha gia' effettuato il login
					    	if(winClient.currentUser == null) {
					    		System.err.println("No user logged in, cannot log out");
					    	}
					    	
					    	// Creo la stringa da inviare al server con "logout <username>
					    	
					    	String toSend = command[0] + " " + winClient.currentUser;
					    	
					    	System.out.println(toSend);
					    	
					    	WinUtils.send(toSend, clientSocket);
					    	
					    	String logoutResponse = WinUtils.receive(clientSocket);
					    	
					    	// Se il logout e' andato a buon fine resetto la sessione
					    	// e mi disconnetto da multicast e callback
					    	if(logoutResponse.equals("LOGOUT-OK")) {
					            System.out.println(winClient.currentUser + " logged out");
					            winClient.currentUser = null;					
					            winClient.followedUsers.clear();
					            
					            try {
					        		server.unregisterForCallback(stub2);
					        	} catch (RemoteException e) {
					        		e.printStackTrace();
					        	}
					            
					            winClient.disconnectMulticast();
					            
					        } else if(logoutResponse.equals("USER-NOT-FOUND")) {
					        	System.err.println("No user logged in, cannot log out");
					        }
					    	
					        break;
					    case "list":
					    	
					    	
					        if(command.length != 2 || (!(command[1].equals("users")) && !(command[1].equals("followers")) && !(command[1].equals("following")))) {
					            System.out.println("ERROR: correct list users followers following");
					            break;
					        }
					        
					        
					        if(command[1].equals("followers")) {
					        	System.out.println("Users you are following:");
					        	
					        	for(String user : winClient.followedUsers) {
					        		System.out.println(user);
					        	}
					        	
					        } else {
					        	WinUtils.send(action, clientSocket);
					        }
					    	break;
					    case "post":
	
					        //catturo i dati del post
					    	String[] elements = action.split("'");
					    	
					        if(elements.length != 4) {
					            System.out.println("ERROR: correct post '<title>' '<content>'");
					            break;
					        }
					        
					        if((elements[1].length() > 20) || (elements[3].length() > 500)) {
					            System.out.println("ERROR: format title must be under 20 characters content under 500");
					            break;
					        }
	
					        WinUtils.send(action, clientSocket);
	
					        String postResponse = WinUtils.receive(clientSocket);
	
					        if(postResponse.equals("OK")) {
					            System.err.println("You posted! Honk Honk!");
					        }
	
					        break;
					    //case "exit":
					    	//online = false;
					    	//break;
					    default:
					        System.out.println("Command " + command[0] + " not recognized");
					}
            }
			System.out.println("close");
			scan.close();
			

        } catch (IOException ex) {
            ex.printStackTrace();
        }
        
        System.exit(0);
    }

}
