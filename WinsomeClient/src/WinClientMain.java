import com.google.gson.Gson;
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

            System.out.println("Do something >");

            String action;
            while(scan.hasNextLine() && !((action = scan.nextLine()).equals("exit"))) {


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
                            //winClient.listFollowers = new ArrayList<String>();
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

                        } else if(logoutResponse.equals("USER-NOT-FOUND")) {
                            System.err.println("Username not found, login or register");
                        }

                        break;
                    case "list":

                        if(command.length != 2 || (!(command[1].equals("users")) && !(command[1].equals("followers")) && !(command[1].equals("following")))) {
                            System.out.println("ERROR: correct list users followers following");
                            break;
                        }

                        if(winClient.currentUser == null) {
                            System.err.println("User not logged in");
                        }

                        // Nel caso del comando "list followers" gestisco lato client
                        // Altrimenti spedisco le informazioni al server
                        if(command[1].equals("followers")) {

                            // Stampo le informazioni sulla lista dei followers

                            if(winClient.listFollowers.isEmpty()) System.out.println("You have no followers");

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
                            Gson gson = new Gson();
                            Type type = new TypeToken<List<String>>(){}.getType();
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
                        }

                        String blog = action + " " + winClient.currentUser;

                        WinUtils.send(blog, clientSocket);

                        String blogResponse = WinUtils.receive(clientSocket);
                        break;
                    case "post":

                        if(winClient.currentUser == null) {
                            System.err.println("User not logged in");
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
                        if(command.length != 2 || (!(command[1].equals("feed")) && !(command[1].equals("post")))) {
                            System.err.println("ERROR: correct show feed OR show post");
                            break;
                        }

                        if(command[1].equals("feed")) {

                        } else if(command[1].equals("post")) {

                            WinUtils.send(action, clientSocket);

                            String showpostResponse = WinUtils.receive(clientSocket);

                        }
                        break;


                    default:
                        System.out.println("Command " + command[0] + " not recognized");
                }
            }
            System.out.println("close");
            scan.close();
        } catch (IOException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }

        System.exit(0);
    }

}
