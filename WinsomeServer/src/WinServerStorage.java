import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.nio.channels.SelectionKey;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class WinServerStorage {

    private ConcurrentHashMap<String, WinUser> userMap;
    private ConcurrentHashMap<UUID, WinPost> postMap;
    private ConcurrentHashMap<String, WinUser> onlineUsers;

    public WinServerStorage() {
        this.userMap = new ConcurrentHashMap<>(); // altri paramentri hashmap?
        this.postMap = new ConcurrentHashMap<>();
        this.onlineUsers = new ConcurrentHashMap<>();
    }

    public WinPost getPost(UUID idPost) {
        return postMap.get(idPost);
    }

    public ConcurrentHashMap<String, WinUser> getUserMap() { return userMap; }

    public ConcurrentHashMap<UUID, WinPost> getPostMap() { return postMap; }

    public ConcurrentHashMap<String, WinUser> getOnlineUsers() { return onlineUsers; }

    public void setUserMap(ConcurrentHashMap<String, WinUser> userMap) { this.userMap = userMap; }

    public void setPostMap(ConcurrentHashMap<UUID, WinPost> postMap) { this.postMap = postMap; }

    /*
     * Inserisce il nuovo utente nella struttura dati degli utenti registrati,
     * controllando che lo username scelto non sia gia' in uso, ritorna errore in quel caso.
     * 
     * @param username lo username scelto dall'utente che ha chiesto la registrazione
     * @param password la password inserita dall'utente
     * @param tagList la lista dei tag scelti dall'utente
     * 
     * @return -1 se il nome utente e' gia' presente
     * @return 0 se tutto e' andato bene
     */
    public int registerUser(String username, String password, List<String> tagList) {

        // Controllo che lo username non sia gia' in uso
        if(userMap.containsKey(username)) {
            return -1;
        }

        // Creo il nuovo utente e lo inserisco nella hashmap degli utenti

        WinUser newUser = new WinUser(username, password, tagList);
        userMap.put(username, newUser);

        return 0;
    }

    /*
     * Un metodo che effettua i controlli per verificare che l'utente possa effettuare il login
     * e poi procede a inserirlo nella hashmap degli utenti online
     * Per comunicare l'esito dell'operazione aggiunge un attachment con il messaggio alla SelectionKey
     * 
     * @param username lo username scelto dall'utente che vuole effettuare il login
     * @param password la password inserita dall'utente
     * @param key
     * 
     * @return niente
     */
    public void loginUser(String username, String password, SelectionKey key) {

        // Cerco l'utente tra quelli registrati
        // Errore nel caso non sia presente o la password non sia corretta
        WinUser curUser = userMap.get(username);
        String toSend;
        
        // Nel caso il login vada a buon fine devo mandare la lista dei follower gia' esistenti per aggiornarla lato client
        List<String> followers = new ArrayList<>();

        if(curUser == null) {
            System.err.println("ERROR: login of not registered user");
            followers.add("USER-NOT-FOUND");
            toSend = WinUtils.prepareJson(followers);
            key.attach(toSend);
            return;
        }

        if(!(curUser.getPassword().equals(password))) {
            System.err.println("Incorrect password");
            followers.add("INCORRECT-PSW");
            toSend = WinUtils.prepareJson(followers);
            key.attach(toSend);
            return;
        }

        onlineUsers.put(username, curUser);
        followers = curUser.getfollowers();
        followers.add(0, "LOGIN-OK");
        toSend = WinUtils.prepareJson(followers);
        key.attach(toSend);
        followers.remove(0);
    }

    public void logoutUser(String username, SelectionKey key) {
    	
    	// I dati dell'utente sono stati persi
        if(!(userMap.containsKey(username))) throw new CorruptedStorageMemoryException("User data lost");

        if(onlineUsers.containsKey(username)) onlineUsers.remove(username);
        else throw new CorruptedStorageMemoryException("User data lost");

        key.attach("LOGOUT-OK");
    }
    

    public void listUsers(String username, SelectionKey key) {

        // Cerco l'utente tra quelli registrati
        // Errore nel caso non sia presente
        WinUser curUser = userMap.get(username);

        // lista con cui mando la risposta, il primo elemento contiene un messggio di errore o di successo
        List<String> toSend = new ArrayList<>();

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String json;

        if(curUser == null) {
            System.err.println("User not found");
            toSend.add("USER-NOT-FOUND");
            json = gson.toJson(toSend);
            key.attach(json);
            return;
        }

        // Scorro gli utenti iscritti
        for (WinUser user : userMap.values()) {
            // Ignoro l'utente corrente
            if(user.getUsername().equals(curUser.getUsername())) continue;
            // Lista dove salvo solo i tag in comune tra i due utenti
            List<String> commonTags = new ArrayList<>(curUser.getTagList());
            commonTags.retainAll(user.getTagList());
            if(commonTags.size() > 0) {
                String info = user.getUsername();
                for (String tag : commonTags) {
                    info = info.concat("/" + tag);
                    System.out.println("info " + info);
                }
                toSend.add(info);
            }
        }

        if(toSend.size() > 0) {
            toSend.add(0,"LIST-USERS-OK");
        } else { // non ho trovato utenti con tag in comune
            toSend.add("USER-NOT-FOUND");
        }
        json = gson.toJson(toSend);
        key.attach(json);
    }

    public void listFollowing(String username, SelectionKey key) {

        // Cerco l'utente tra quelli registrati
        // Errore nel caso non sia presente
        WinUser curUser = userMap.get(username);

        // lista con cui mando la risposta, il primo elemento contiene un messggio di errore o di successo
        List<String> toSend = new ArrayList<>();

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String json;

        if(curUser == null) {
            System.err.println("User not found");
            toSend.add("USER-NOT-FOUND");
            json = gson.toJson(toSend);
            key.attach(json);
            return;
        }

        //prendo la lista degli utenti seguiti
        toSend = curUser.getfollowedUsers();

        // Se seguo almeno un utente
        if(toSend.size() > 0) {
            toSend.add(0,"LIST-FOLLOWING-OK");
            for(String info : toSend) {
                System.out.println(info);
            }
        } else { // non seguo nessuno
            toSend.add("USER-NOT-FOUND");
        }
        json = gson.toJson(toSend);
        key.attach(json);
        toSend.remove(0);
    }

    public void followUser(String userFollowed, String userFollowing, SelectionKey key) {

        WinUser curUser = userMap.get(userFollowing);
        WinUser folUser = userMap.get(userFollowed);

        //L'utente non e' registrato
        if(curUser == null) {
            System.err.println("User not found");
            key.attach("CURUSER-NOT-FOUND");
            return;
        }

        // L'utente non era online
        if(onlineUsers.containsKey(userFollowing)) {
            // Controllo che l'utente da seguire esista
            if(folUser != null) {

                // Controllo che l'utente corrente non stia gia' seguendo l'utente da seguire
                
                if((curUser.followUser(userFollowed)) == 0) {
                    System.out.println(curUser.getUsername() + " followed " + userFollowed);
                    folUser.addFollower(userFollowing);
                    
                    // Aggiungo tutti i post dell'utente che viene seguito al feed dell'utente che segue
                    for(UUID post : folUser.getBlog()) {
                    	curUser.updateFeed(post);
                    }
                    
                    key.attach("FOLLOW-OK");
                } else {
                    key.attach("ALREADY-FOLLOWING");
                    return;
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

    public void unfollowUser(String userUnfollowed, String userUnfollowing, SelectionKey key) {

        WinUser curUser = userMap.get(userUnfollowing);
        WinUser unfolUser = userMap.get(userUnfollowed);

        //L'utente non e' registrato
        if(curUser == null) {
            System.err.println("User not found");
            key.attach("CURUSER-NOT-FOUND");
            return;
        }

        // L'utente non era online
        if(onlineUsers.containsKey(userUnfollowing)) {
            // Controllo che l'utente da smettere di seguire esista
            if(unfolUser != null) {

                // Controllo che l'utente corrente non stia gia' seguendo l'utente da seguire
                
                if((curUser.unfollowUser(userUnfollowed)) == 0) {
                    System.out.println(curUser.getUsername() + " unfollowed " + userUnfollowed);
                    unfolUser.removeFollower(userUnfollowing);
                    
                    //ATTENZIONE
                    for(UUID post : curUser.getFeed()) {
                    	if(postMap.get(post).getPostAuthor().equals(userUnfollowed)) {
                    		curUser.getFeed().remove(post);
                    	}
                    }
                    
                    key.attach("UNFOLLOW-OK");
                } else {
                    key.attach("NOT-FOLLOWING");
                    return;
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
    
    public void viewBlog(String currentUser, SelectionKey key) {
    	
    	WinUser curUser = userMap.get(currentUser);
    	
    	if(curUser == null) {
            System.err.println("User not found");
            key.attach("CURUSER-NOT-FOUND");
            return;
    	}
    	
    	List<String> blog = new ArrayList<String>();
    	
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String json;
        String info = null;
    	
    	for(UUID idPost : curUser.getBlog()) {
    		WinPost curPost = postMap.get(idPost);
    		
    		if(curPost == null) {
    			info = "[deleted]/[deleted]/[deleted]";
    			blog.add(info);
    			continue;
    		}
    		
    		info = curPost.getIdPost().toString();
    		
            info = info.concat("/" + curPost.getPostAuthor() + "/" + curPost.getPostTitle());
            
            blog.add(info);
            
    	}
    	
        // Se seguo almeno un utente
        if(blog.size() > 0) {
            blog.add(0,"BLOG-OK");
        } else { // non seguo nessuno
            blog.add("BLOG-EMPTY");
        }
        
        json = gson.toJson(blog);
        key.attach(json);
    }

    public void createPost(String author, String title, String text, SelectionKey key) {

        WinUser curUser = userMap.get(author);

        //L'utente non e' registrato
        if(curUser == null) {
            System.err.println("User not found");
            key.attach("CURUSER-NOT-FOUND");
            return;
        }

        //l'utente non era online
        if(!(onlineUsers.containsKey(author))) {
            key.attach("USER-NOT-FOUND");
            return;
        }

        WinPost post = new WinPost(author, title, text);
        postMap.put(post.getIdPost(), post);
        // Aggiungo l'id del post alla lista contenuta nell'utente per poter ricostruire il blog
        curUser.updateBlog(post.getIdPost());

        // Aggiungo il post al feed di ogni follower
        for(String user : curUser.getfollowers()) {
            WinUser follower = userMap.get(user);
            follower.updateFeed(post.getIdPost());
            System.out.println("follower feed size: " + follower.getFeed().size());
        }
       
        key.attach("POST-OK");
    }
    
    public void showFeed(String currentUser, SelectionKey key) {
    	
    	
    	WinUser curUser = userMap.get(currentUser);
    	
    	if(curUser == null) {
            System.err.println("User not found");
            key.attach("CURUSER-NOT-FOUND");
            return;
    	}
    	
    	List<String> feed = new ArrayList<String>();
    	
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String json;
        String info = null;
    	
    	for(UUID idPost : curUser.getFeed()) {
    		WinPost curPost = postMap.get(idPost);
    		
    		if(curPost == null) {
    			// e' sparito un post
    			// ECCEZIONE
    			info = "[deleted]/[deleted]/[deleted]";
    			feed.add(info);
    			continue;
    		}
    		
    		info = curPost.getIdPost().toString();
    		
            info = info.concat("/" + curPost.getPostAuthor() + "/" + curPost.getPostTitle());
            
            feed.add(info);
            
    	}
    	
        // Se ci sono post nel feed
        if(feed.size() > 0) {
            feed.add(0,"FEED-OK");
        } else {
            feed.add("FEED-EMPTY");
        }
        
        json = gson.toJson(feed);
        key.attach(json);
    	
    }
    
    public void showPost(String username, UUID postID, SelectionKey key) {
    	
    	WinPost curPost = postMap.get(postID);
    	   	
    	// Il post richiesto non esiste
    	if(curPost == null) {
    		key.attach("POST-NOT-FOUND");
    		return;
    	}
    	
    	if(userMap.get(username).getFeed().contains(postID)) curPost.isFeed();
    	
    	Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String json = gson.toJson(curPost);
        
        key.attach(json);
        
        curPost.resetFeed();
    	
    }
    
    public void deletePost(String username, UUID postID, SelectionKey key) {
    	
    	WinPost curPost = postMap.get(postID); 
    	
    	if(curPost == null) {
    		key.attach("POST-NOT-FOUND");
    		return;
    	}
    	
    	// rimuovo tutti i rewin
    	for(String user : curPost.getRewins()) {
    		userMap.get(user).removeBlog(postID);
    	}
    	
    	postMap.remove(postID);
    	
    	WinUser curUser = userMap.get(username);
    	
    	if(curUser == null) {
    		//ECCEZIONE
    	}
    	
    	// Rimuovo il post dal blog dell'utente
    	curUser.getBlog().remove(postID);
    	
    	//rimuovo il post dal feed di tutti i suoi followers
    	for(String user : curUser.getfollowers()) {
    		userMap.get(user).getFeed().remove(postID);
    	}
    	
    	key.attach("DELETE-OK");
    	
    }
    
    public void rewinPost(String username, UUID postID, SelectionKey key) {
    	

    	WinPost curPost = postMap.get(postID);    	
    	WinUser curUser = userMap.get(username);
    	
    	if(curPost == null) {
    		key.attach("POST-NOT-FOUND");
    		return;
    	}
    	
    	if(curUser == null) throw new CorruptedStorageMemoryException("ERROR: corrupted user memory");
    	
    	if(!curUser.getFeed().contains(postID)) {
    		key.attach("POST-NOT-IN-FEED");
    		return;
    	}
    	
    	// Controllo che l'utente non abbia gia' rewwinato il post
    	if(curUser.getBlog().contains(postID)) {
    		key.attach("ALREADY-REWIN");
    		return;
    	}
    	
    	curUser.updateBlog(postID);
    	curPost.addRewin(username);
    	
    	
    	key.attach("REWIN-OK");
    }
    
    public void ratePost(String uservoting, UUID postID, int vote, SelectionKey key) {
    	
    	WinPost postToRate = postMap.get(postID);
    	
    	if(postToRate == null) {
    		key.attach("POST-NOT-FOUND");
    		return;
    	}
    	
    	if(postToRate.addRate(uservoting, vote) == -1) key.attach("ALREADY-RATED");
    	else key.attach("RATE-OK");
    	
    }
    
    public void addComment(String usercommenting, UUID postID, String comment, SelectionKey key) {
    	
    	WinPost postToComment = postMap.get(postID);
    	
    	if(postToComment == null) {
    		key.attach("POST-NOT-FOUND");
    		return;
    	}
    	
    	postToComment.addComment(usercommenting, comment);
    	
    	key.attach("COMMENT-OK");
    }
    
    public void getWallet() {
    	
    }
    
    public void getWalletBitcoin() {
    	
    }
}
