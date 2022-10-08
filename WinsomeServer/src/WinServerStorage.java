import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.nio.channels.SelectionKey;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
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
                int r;

                if((r = curUser.followUser(userFollowed)) == 0) {
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
                int r;

                if((r = curUser.unfollowUser(userUnfollowed)) == 0) {
                    System.out.println(curUser.getUsername() + " unfollowed " + userUnfollowed);
                    unfolUser.removeFollower(userUnfollowing);
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
    			// e' sparito un post
    			// ECCEZIONE
    		}
    		
    		info = curPost.getIdPost().toString();
    		
            info = info.concat("/" + curPost.getPostAuthor() + "/" + curPost.getPostTitle());
            
            blog.add(info);
            
    	}
    	
        // Se seguo almeno un utente
        if(blog.size() > 0) {
            blog.add(0,"BLOG-OK");
            for(String info2 : blog) {
                System.out.println(info);
            }
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
        System.out.println("blog size: " + curUser.getBlog().size());
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
    		}
    		
    		info = curPost.getIdPost().toString();
    		
            info = info.concat("/" + curPost.getPostAuthor() + "/" + curPost.getPostTitle());
            
            feed.add(info);
            
    	}
    	
        // Se ci sono post nel feed
        if(feed.size() > 0) {
            feed.add(0,"FEED-OK");
            for(String info2 : feed) {
                System.out.println(info);
            }
        } else {
            feed.add("FEED-EMPTY");
        }
        
        json = gson.toJson(feed);
        key.attach(json);
    	
    }
    
    public void showPost(UUID postID, SelectionKey key) {
    	
    	WinPost curPost = postMap.get(postID);
    	
    	// Il post richiesto non esiste
    	if(curPost == null) {
    		key.attach("POST-NOT-FOUND");
    		return;
    	}
    	
    	Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String json = gson.toJson(curPost);
        
        key.attach(json);
    	
    	
    }
    
    public void deletePost() {
    	
    }
    
    public void rewinPost() {
    	
    }
    
    public void ratePost() {
    	
    }
    
    public void addComment() {
    	
    }
    
    public void getWallet() {
    	
    }
    
    public void getWalletBitcoin() {
    	
    }
}
