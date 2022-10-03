import java.nio.channels.SelectionKey;
import java.rmi.RemoteException;

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
            		System.out.println("list users1");
            	}
            	if(args[1].equals("following")) {
            		System.out.println("list following");
            	}
            	break;
            case "follow":
            	System.out.println(args[2] + " wants to follow " + args[1]);
            	
            	serverStorage.followUser(args[1], args[2], keyWorker);
            	
                // Se l'utente ne ha seguito un'altro
                if(keyWorker.attachment().equals("FOLLOW-OK")) {
                	// notifico quell'utente tramite RMI
                	try {
						followersRMI.follow(args[1], args[2]);
					} catch (RemoteException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
                }
                
            	break;
            case "unfollow":
            	break;
            case "post":
                //come mettere l'autore di un post?
            	String[] elements = operation.split("'");
            	
                WinPost post = new WinPost("honk", elements[1], elements[3]);
                serverStorage.createPost(post);

                System.out.println("Created post with title " + serverStorage.getPost(post.getIdPost()).getPostTitle() + ":\n" + serverStorage.getPost(post.getIdPost()).getPostContent());

                if(true) {
                    keyWorker.interestOps(SelectionKey.OP_WRITE);
                }
                break;
        }
        
        // Comunico al server che c'e' una risposta da mandare
        keyWorker.interestOps(SelectionKey.OP_WRITE);
    }
}
