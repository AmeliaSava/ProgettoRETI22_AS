import java.nio.channels.SelectionKey;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

public class WinServerStorage {

    private ConcurrentHashMap<String, WinUser> userMap;
    private ConcurrentHashMap<UUID, WinPost> postMap;
    //aggiungere un attributo negli user per la callback?
    //usare questa per salvare i dati callback?
    private ConcurrentHashMap<String, WinUser> onlineUsers;

    public WinServerStorage() {
        this.userMap = new ConcurrentHashMap<>(); // altri paramentri hashmap?
        this.postMap = new ConcurrentHashMap<>();
        this.onlineUsers = new ConcurrentHashMap<>();
    }

    public WinPost getPost(UUID idPost) {
        return postMap.get(idPost);
    }
    
    /*
     * ritorna -1 se il nome utente e' gia' presente
     * ritorna 0 se tutto e' andato bene
     */
    public int registerUser(String username, String password, List<String> tagList) {
    	
    	// Controllo che lo username non sia gia' in uso    	
    	if(userMap.containsKey(username)) {
    		return -1;
    	}
    	
    	//pass vuota? potrebbe succedere?
    	
    	// Creo il nuovo utente e lo inserisco nella hashmap degli utenti
    	
    	WinUser newUser = new WinUser(username, password, tagList);
    	userMap.put(username, newUser);
    	   	
    	return 0;
    }
    
    /*
     * Un metodo che effettua i controlli per verificare che l'utente possa effettuare il login
     * e poi procede a inserirlo nella hashmap degli utenti online
     * Per comunicare l'esito dell'operazione aggiunge un attachment con il messaggio alla SelectionKey
     * @return niente
     */
    public void loginUser(String username, String password, SelectionKey key) {
    	
    	// Cerco l'utente tra quelli registrati
    	// Errore nel caso non sia presente o la password non sia corretta
    	WinUser curUser = userMap.get(username);
    	
    	if(curUser == null) {
    		System.err.println("User not found");
    		key.attach("USER-NOT-FOUND");
    		return;
    	}
    	
    	if(!(curUser.getPassword().equals(password))) {
    		System.err.println("Incorrect password");
    		key.attach("INCORRECT-PSW");
    		return;
    	}
    	
    	onlineUsers.put(username, curUser);
    	key.attach("LOGIN-OK");
    	return;
    }
    
    public void logoutUser(String username, SelectionKey key) {
    	
    	//l'utente non era registrato
    	if(!(userMap.containsKey(username))) {
    		key.attach("USER-NOT-FOUND");
    		return;
    	}
    	
    	//l'utente non era online
    	if(onlineUsers.containsKey(username)) onlineUsers.remove(username);
    		else {
    			key.attach("USER-NOT-FOUND");
    			return;
    		}
    	
    	key.attach("LOGOUT-OK");
    	return;
    	
    }
    
    public void createPost(WinPost post) {
        postMap.put(post.getIdPost(), post);
        //fare qualcosa con gli user
        // trovo l'autore e gli linko il post nel suo blog?
    }
    
    public ArrayBlockingQueue<WinUser> listUsers(UUID user) {
		
    	
    	//cerco l'id dell'utente nella userMap e recupero la sua lista tag
    	
    	//faccio una ricerca sulle liste tag degli altri utenti per cercare i tag in comune
    	
    	//se trovo un tag in comune mi fermo subito e aggiungo alla lista da ritornare
    	
    	//possibile implementazione, mandarne un tot alla volta e chiedere se ne vogliono di piu'
    	
    	return null;
    }
    
    public ArrayBlockingQueue<WinUser> listFollowing() {
    	
    	//cerco l'id dell'utente, nella struttura utente trovo la sua lista di seguiti e la restituisco
    	return null;
    }
    
    public void followUser(String userFollowed, String userFollowing, SelectionKey key) {
    	
    	WinUser curUser = userMap.get(userFollowing);
    	
    	//L'utente non e' registrato
    	if(curUser == null) {
    		System.err.println("User not found");
    		key.attach("CURUSER-NOT-FOUND");
    		return;
    	}
    	
    	// L'utente non era online
    	if(onlineUsers.containsKey(userFollowing)) {
    		// Controllo che l'utente da seguire esista
    		if(userMap.containsKey(userFollowed)) {
    			
    			// Controllo che l'utente corrente non stia gia' seguendo l'utente da seguire
    			int r;
    			
    			if((r = curUser.followUser(userFollowed)) == 0) {
    				System.out.println(curUser.getUsername() + " followed " + userMap.get(userFollowing).getfollowedUsers().get(0));
    				key.attach("FOLLOW-OK");
    			} else {
    				key.attach("ALREADY-FOLLOWING");
    			}
    			
    		} else {
    			key.attach("USER-NOT-FOUND");
    			return;
    		}
    		
    	} else {
    			key.attach("CURUSER-NOT-FOUND");
    			return;
    		}

    	return;
    }
    
    public void unfollowUser(UUID userUnfollowed, UUID userUnfollowing) {
    	//cerco l'id dell'utente che vuole seguire
    	//tolgo alla lista dei seguiti
    	//mando notifica a chi e' stato seguito
    	return;
    }
}
