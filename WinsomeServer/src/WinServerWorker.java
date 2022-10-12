import java.net.Socket;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.rmi.RemoteException;
import java.util.List;
import java.util.UUID;

public class WinServerWorker implements Runnable {

    private String operation;
    private SelectionKey keyWorker;
    private WinServerStorage serverStorage;

    private NotificationServiceServerImpl followersRMI;

    public WinServerWorker(String operation, SelectionKey key, WinServerStorage serverStorage, NotificationServiceServerImpl followersRMI) {
        this.operation = operation;
        this.keyWorker = key;
        this.serverStorage = serverStorage;
        this.followersRMI = followersRMI;
    }

    @Override
    public void run() {

        String[] args = operation.split(" ");
        System.out.println("printing args " + operation);
        switch (args[0]) {
            case "login":

                System.out.println(args[1] + " wants to login with psw " + args[2]);

                // Metto il risultato dell'operazione nell'attachment della key
                serverStorage.loginUser(args[1], args[2], keyWorker);

                break;
            case "logout":

                System.out.println(args[1] + " user is logging out");

                serverStorage.logoutUser(args[1], keyWorker);

                break;
            case "list":
                if(args[1].equals("users")) {
                    System.out.println("list users" + args[2]);

                    serverStorage.listUsers(args[2], keyWorker);

                }
                if(args[1].equals("following")) {
                    System.out.println("list following" + args[2]);
                    serverStorage.listFollowing(args[2], keyWorker);
                }
                break;
            case "follow":

                System.out.println(args[2] + " wants to follow " + args[1]);

                serverStorage.followUser(args[1], args[2], keyWorker);

                // salvo il socket channel del client per controllare che non si sia disconnesso in modo anomalo
                // ATTENZIONE non funziona

                // Se l'utente ne ha seguito un'altro
                if(keyWorker.attachment().equals("FOLLOW-OK") && serverStorage.getOnlineUsers().containsKey(args[1])) {
                    // notifico quell'utente tramite RMI, solo se è online
                    try {
                        followersRMI.follow(args[1], args[2]);
                    } catch (RemoteException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }

                break;
            case "unfollow":

                System.out.println(args[2] + " wants to unfollow " + args[1]);

                serverStorage.unfollowUser(args[1], args[2], keyWorker);

                // Se l'utente ne ha seguito un'altro
                if(keyWorker.attachment().equals("UNFOLLOW-OK") && serverStorage.getOnlineUsers().containsKey(args[1])) {
                    // notifico quell'utente tramite RMI
                    try {
                        followersRMI.unfollow(args[1], args[2]);
                    } catch (RemoteException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }

                break;
            case "blog":
            	
                System.out.println("User " + args[1] + "has requested for their blog");

                serverStorage.viewBlog(args[1], keyWorker);
            	
            	break;
            case "post":
                
                String[] elements = operation.split("\"");

                serverStorage.createPost(elements[4], elements[1], elements[3], keyWorker);

                System.out.println( elements[4] + " created post with title " + elements[1] + ":\n" + elements[3]);

                break;
            case "show":
                if(args[1].equals("feed")) {
                	
                	System.out.println("User " + args[2] + "has requested for their feed");
                	serverStorage.showFeed(args[2], keyWorker);

                } else if(args[1].equals("post")) {
                	
                	System.out.println("Showing post " + args[2] + " for " + args[3]);
                	
                	UUID postID = UUID.fromString(args[2]);
                	
                	serverStorage.showPost(args[3], postID, keyWorker);
                    
                }
                break;
            case "delete":
            	System.out.println("Deleting post " + args[1] + " for " + args[2]);
            	
            	UUID postID = UUID.fromString(args[1]);
            	serverStorage.deletePost(args[2], postID, keyWorker);
            	
            	break;
            case "rewin":
            	// args[1] -> postID
            	// args[2] -> user rewinning
            	
            	System.out.println("Rewin post " + args[1] + " for " + args[2]);
            	
            	UUID rewinpostID = UUID.fromString(args[1]);
            	serverStorage.rewinPost(args[2], rewinpostID, keyWorker);
            	
            	break;
            case "rate":
            	// args[1] -> postID
            	// args[2] -> vote
            	// args[3] -> user rating
            	System.out.println("Rating post " + args[1] + " " + args[2] + " for " + args[3]);
            	
            	int vote = Integer.parseInt(args[2]);
            	UUID ratepostID = UUID.fromString(args[1]);
            	
            	serverStorage.ratePost(args[3], ratepostID, vote, keyWorker);
            	
            	break;
            case "comment":
            	// args[1] -> postID
            	UUID commentpostID = UUID.fromString(args[1]);
            	// comment[1] -> commento
            	// comment[2] -> autore
            	String[] comment = operation.split("\"");
            	serverStorage.addComment(comment[2], commentpostID, comment[1], keyWorker);
            	
            	break;
            case "wallet":
            	break;
                
        }

        // Comunico al server che c'e' una risposta da mandare
        keyWorker.interestOps(SelectionKey.OP_WRITE);
    }
}
