import java.nio.channels.SelectionKey;

public class WinServerWorker implements Runnable {

    private String operation;
    private SelectionKey keyWorker;
    private WinServerStorage serverStorage;

    public WinServerWorker(String operation, SelectionKey key, WinServerStorage serverStorage) {
        this.operation = operation;
        this.keyWorker = key;
        this.serverStorage = serverStorage;
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
            default:
                // anche questo mi sa che lo faccio prima
                System.out.println("ERROR: " + args[0] + "unknown operation requested");
        }
        
        // Comunico al main che c'e' una risposta da mandare
        keyWorker.interestOps(SelectionKey.OP_WRITE);
    }
}
