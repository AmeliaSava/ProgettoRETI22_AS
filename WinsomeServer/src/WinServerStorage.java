import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.channels.SelectionKey;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
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

        WinUser curUser = userMap.get(username);
       
        JsonObject loginJson = new JsonObject();
                      
        // Se l'utente non e' registrato
        if(curUser == null) {
        	
        	System.err.println("ERROR: login of not registered user");
        	
        	loginJson.addProperty("result", -1);
        	loginJson.addProperty("result-msg", "Username not found, you need to register first!");
            
            key.attach(loginJson.toString());
            return;
        }
        
        // Password errata
        if(!(curUser.getPassword().equals(password))) {
        	
            System.err.println("ERROR: incorrect password");
            
        	loginJson.addProperty("result", -1);
        	loginJson.addProperty("result-msg", "Password is incorrect");
        	
        	key.attach(loginJson.toString());
            return;
        }
        
        // Aggiungo l'utente agli utenti online, se non c'e' gia'
        if(!onlineUsers.containsKey(username)) onlineUsers.put(username, curUser);
        else {
        	System.err.println("ERROR: user already online");
        	
        	loginJson.addProperty("result", -1);
        	loginJson.addProperty("result-msg", "User is already online, cannot login!");
            
            key.attach(loginJson.toString());
            return;
        }
        
        loginJson.addProperty("result", 0);
        loginJson.addProperty("result-msg", "Welcome " + username + " you are now logged in!");
        
        // Mando la lista dei follower gia' esistenti per aggiornarla lato client
        loginJson.addProperty("followers-list", WinUtils.prepareJson(curUser.getfollowers()));
        
        key.attach(loginJson.toString());
    }

    public void logoutUser(String username, SelectionKey key) {
    	
    	JsonObject logoutJson = new JsonObject();
    	
        if(!(userMap.containsKey(username))) {
        	System.err.println("ERROR: logout of not registered user");
        	
        	logoutJson.addProperty("result", -1);
        	logoutJson.addProperty("result-msg", "Username not found, could not logout!");
            
            key.attach(logoutJson.toString());
        }

        if(onlineUsers.containsKey(username)) onlineUsers.remove(username);
        else {
        	System.err.println("ERROR: user was not online");
        	
        	logoutJson.addProperty("result", -1);
        	logoutJson.addProperty("result-msg", "User not online, could not logout!");
            
            key.attach(logoutJson.toString());
            return;
        }

        System.out.println("Loggin out user " + username);
    	
    	logoutJson.addProperty("result", 0);
    	logoutJson.addProperty("result-msg", username + " logged out");
        
        key.attach(logoutJson.toString());
    }
    
    public void listUsers(String username, SelectionKey key) {

        WinUser curUser = userMap.get(username);

        // lista con cui mando la risposta
        List<String> toSend = new ArrayList<>();
        
        JsonObject usersJson = new JsonObject();

        if(curUser == null) {
        	
        	System.err.println("ERROR: User not found");
        	
        	usersJson.addProperty("result", -1);
        	usersJson.addProperty("result-msg", "Username not found");
            
            key.attach(usersJson.toString());
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
        	usersJson.addProperty("result", 0);
        	usersJson.addProperty("result-msg", "We found these users that share your interests:");
        	usersJson.addProperty("users-list", WinUtils.prepareJson(toSend));
        } else { // non ho trovato utenti con tag in comune
        	usersJson.addProperty("result", 0);
        	usersJson.addProperty("result-msg", "There are no users that share your interests.");                       
        }
        
        key.attach(usersJson.toString());
    }

    public void listFollowing(String username, SelectionKey key) {

        WinUser curUser = userMap.get(username);

        // lista con cui mando la risposta
        List<String> toSend = new ArrayList<>();
        
        JsonObject followingJson = new JsonObject();

        if(curUser == null) {
        	System.err.println("ERROR: User not found");
        	
        	followingJson.addProperty("result", -1);
        	followingJson.addProperty("result-msg", "Username not found");
            
            key.attach(followingJson.toString());
            return;
        }

        //prendo la lista degli utenti seguiti
        toSend.addAll(curUser.getfollowedUsers());
        
        // Se seguo almeno un utente
        if(toSend.size() > 0) {
           	followingJson.addProperty("result", 0);
        	followingJson.addProperty("result-msg", "You are following these users:");
        	followingJson.addProperty("following-list", WinUtils.prepareJson(toSend));
        } else { // non seguo nessuno
        	followingJson.addProperty("result", 0);
        	followingJson.addProperty("result-msg", "You are not following any user.");
        }
        
        key.attach(followingJson.toString());
    }

    public void followUser(String userFollowed, String userFollowing, SelectionKey key) {

        WinUser curUser = userMap.get(userFollowing);
        WinUser folUser = userMap.get(userFollowed);
        
        JsonObject followJson = new JsonObject();

        //L'utente non e' registrato
        if(curUser == null) {
        	System.err.println("ERROR: User not found");
        	
        	followJson.addProperty("result", -1);
        	followJson.addProperty("result-msg", "Username not found, login or register");
            
            key.attach(followJson.toString());
            return;
        }

        // L'utente non era online
        if(onlineUsers.containsKey(userFollowing)) {
            // Controllo che l'utente da seguire esista
            if(folUser != null) {

                // Controllo che l'utente corrente non stia gia' seguendo l'utente da seguire
                
                if((curUser.followUser(userFollowed)) == 0) {
                    
                    folUser.addFollower(userFollowing);
                    
                    // Aggiungo tutti i post dell'utente che viene seguito al feed dell'utente che segue
                    for(UUID post : folUser.getBlog()) {
                    	curUser.updateFeed(post);
                    }
                    
                    System.out.println(userFollowing + " followed " + userFollowed);
                    followJson.addProperty("result", 0);
                	followJson.addProperty("result-msg", userFollowing + " you are now following " + userFollowed);
                    
                    key.attach(followJson.toString());
                } else {
                	followJson.addProperty("result", -1);
                	followJson.addProperty("result-msg", "You are already following this user");
                    
                    key.attach(followJson.toString());
                    return;
                }

            } else {
            	
            	followJson.addProperty("result", -1);
            	followJson.addProperty("result-msg", "The user you are trying to follow does not exist");
                
                key.attach(followJson.toString());
                return;
            }

        } else {
        	System.err.println("ERROR: User not online");
        	
        	followJson.addProperty("result", -1);
        	followJson.addProperty("result-msg", "Username not online");
            
            key.attach(followJson.toString());
            return;
        }

        return;
    }

    public void unfollowUser(String userUnfollowed, String userUnfollowing, SelectionKey key) {

        WinUser curUser = userMap.get(userUnfollowing);
        WinUser unfolUser = userMap.get(userUnfollowed);

        JsonObject unfollowJson = new JsonObject();

        //L'utente non e' registrato
        if(curUser == null) {
        	System.err.println("ERROR: User not found");
        	
        	unfollowJson.addProperty("result", -1);
        	unfollowJson.addProperty("result-msg", "Username not found, login or register");
            
            key.attach(unfollowJson.toString());
            return;
        }

        // L'utente non era online
        if(onlineUsers.containsKey(userUnfollowing)) {
            // Controllo che l'utente da smettere di seguire esista
            if(unfolUser != null) {

                // Controllo 
                
                if((curUser.unfollowUser(userUnfollowed)) == 0) {
                    System.out.println(curUser.getUsername() + " unfollowed " + userUnfollowed);
                    unfolUser.removeFollower(userUnfollowing);
                                        
                    Iterator<UUID> iter = curUser.getFeed().iterator();
                    while(iter.hasNext()) {
                      UUID post = iter.next();
                      if(postMap.get(post).getPostAuthor().equals(userUnfollowed)) {
                        iter.remove();
                      }
                    }
                   
                    unfollowJson.addProperty("result", 0);
                	unfollowJson.addProperty("result-msg", userUnfollowing + " you stopped following " + userUnfollowed);
                    
                    key.attach(unfollowJson.toString());                    
                } else {
                 	unfollowJson.addProperty("result", -1);
                	unfollowJson.addProperty("result-msg", "You are not following this user");
                    
                    key.attach(unfollowJson.toString());
                    return;
                }

            } else {
            	unfollowJson.addProperty("result", -1);
            	unfollowJson.addProperty("result-msg", "The user you are trying to unfollow does not exist");
                
                key.attach(unfollowJson.toString());
                return;
            }
        } else {
        	System.err.println("ERROR: User not online");
        	
        	unfollowJson.addProperty("result", -1);
        	unfollowJson.addProperty("result-msg", "Username not online");
            
            key.attach(unfollowJson.toString());
            return;
        }
    }
    
    public void viewBlog(String currentUser, SelectionKey key) {
    	
    	WinUser curUser = userMap.get(currentUser);
    	
    	JsonObject blogJson = new JsonObject();
    	
    	if(curUser == null) {
        	System.err.println("ERROR: User not found");
        	
        	blogJson.addProperty("result", -1);
        	blogJson.addProperty("result-msg", "Username not found");
            
            key.attach(blogJson.toString());
            return;
    	}
    	
    	List<String> blog = new ArrayList<String>();
    	
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
    	
        if(blog.size() > 0) {
          	blogJson.addProperty("result", 0);
        	blogJson.addProperty("result-msg", "Blog: ");
        	blogJson.addProperty("blog", WinUtils.prepareJson(blog));
        } else { // non ci sono post nel blog
        	blogJson.addProperty("result", 0);
        	blogJson.addProperty("result-msg", "Your blog is still empty, make a new post!");
        }
        
        key.attach(blogJson.toString());
    }

    public void createPost(String author, String title, String text, SelectionKey key) {

        WinUser curUser = userMap.get(author);

        JsonObject postJson = new JsonObject();
        
        //L'utente non e' registrato
        if(curUser == null) {
        	System.err.println("ERROR: User not found");
        	
        	postJson.addProperty("result", -1);
        	postJson.addProperty("result-msg", "Username not found");
            
            key.attach(postJson.toString());
            return;
        }

        //l'utente non era online
        if(!(onlineUsers.containsKey(author))) {
        	System.err.println("ERROR: User not online");
        	
        	postJson.addProperty("result", -1);
        	postJson.addProperty("result-msg", "User not online");
            
            key.attach(postJson.toString());
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
       
        System.out.println(author + "created a new post");
    	
    	postJson.addProperty("result", 0);
    	postJson.addProperty("result-msg", "You posted!");
        
        key.attach(postJson.toString());
        return;
    }
    
    public void showFeed(String currentUser, SelectionKey key) {
    	
    	
    	WinUser curUser = userMap.get(currentUser);
    	
    	JsonObject feedJson = new JsonObject();
    	
    	if(curUser == null) {
        	System.err.println("ERROR: User not found");
        	
        	feedJson.addProperty("result", -1);
        	feedJson.addProperty("result-msg", "Username not found");
            
            key.attach(feedJson.toString());
            return;
    	}
    	
    	List<String> feed = new ArrayList<String>();

        String info = null;
    	
    	for(UUID idPost : curUser.getFeed()) {
    		WinPost curPost = postMap.get(idPost);
    		
    		if(curPost == null) {    		
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
        	feedJson.addProperty("result", 0);
        	feedJson.addProperty("result-msg", "Feed: ");
        	feedJson.addProperty("feed", WinUtils.prepareJson(feed));
        } else {
        	feedJson.addProperty("result", 0);
        	feedJson.addProperty("result-msg", "There are no posts on your feed.");
        }
        
        key.attach(feedJson.toString());
    }
    
    public void showPost(String username, UUID postID, SelectionKey key) {
    	
    	WinPost curPost = postMap.get(postID);
    	
    	JsonObject postJson = new JsonObject();
    	
    	if(!userMap.containsKey(username)) {
        	System.err.println("ERROR: User not found");
        	
        	postJson.addProperty("result", -1);  
        	postJson.addProperty("result-msg", "Username not found");
            
            key.attach(postJson.toString());
            return;
    	}
    	
    	// Il post richiesto non esiste
    	if(curPost == null) {
    		postJson.addProperty("result", -1);
        	postJson.addProperty("result-msg", "The post you requested doesn't exist");
            
            key.attach(postJson.toString());
    		return;
    	}
    	
    	postJson.addProperty("result", 0);
    	postJson.addProperty("result-msg", "Post:");       	
    	postJson.addProperty("title", curPost.getPostTitle());
    	postJson.addProperty("content", curPost.getPostContent());
    	postJson.addProperty("author", curPost.getPostAuthor());
    	postJson.addProperty("id", curPost.getIdPost().toString());
    	postJson.addProperty("upvote", curPost.getUpvoteCount());
    	postJson.addProperty("downvote", curPost.getDownvoteCount());
    	postJson.addProperty("comments", WinUtils.prepareJson(curPost.getComments()));
    	key.attach(postJson.toString());
           	
    }
    
    public void deletePost(String username, UUID postID, SelectionKey key) {
    	
    	WinPost curPost = postMap.get(postID); 
    	WinUser curUser = userMap.get(username);
    	
    	JsonObject deleteJson = new JsonObject();
	    	
    	if(curUser == null) {
    		System.err.println("ERROR: User not found");
        	
        	deleteJson.addProperty("result", -1);  
        	deleteJson.addProperty("result-msg", "Username not found");
            
            key.attach(deleteJson.toString());
            return;
    	}
    
    	if(curPost == null) {
    		deleteJson.addProperty("result", -1);  
        	deleteJson.addProperty("result-msg", "The post you are trying to delete doesn't exist");
            
            key.attach(deleteJson.toString());
    		return;
    	}
    	
    	if(!curPost.getPostAuthor().equals(username)) {
    		
    		deleteJson.addProperty("result", -1);  
        	deleteJson.addProperty("result-msg", "You are not the author of this post, you cannot delete it");
            
            key.attach(deleteJson.toString());
    		return;
    	}
    	
    	// rimuovo tutti i rewin
    	for(String user : curPost.getRewins()) {
    		userMap.get(user).removeBlog(postID);
    	}
    	
    	postMap.remove(postID);
    	 
    	// Rimuovo il post dal blog dell'utente
    	curUser.getBlog().remove(postID);
    	
    	//rimuovo il post dal feed di tutti i suoi followers
    	for(String user : curUser.getfollowers()) {
    		userMap.get(user).getFeed().remove(postID);
    	}
    	
    	deleteJson.addProperty("result", 0);  
    	deleteJson.addProperty("result-msg", "The post was successfully deleted!");
        
        key.attach(deleteJson.toString());
    	
    }
    
    public void rewinPost(String username, UUID postID, SelectionKey key) {
    	

    	WinPost curPost = postMap.get(postID);    	
    	WinUser curUser = userMap.get(username);
    	
    	JsonObject rewinJson = new JsonObject();
    	
    	if(curPost == null) {
    		
        	rewinJson.addProperty("result", -1);
        	rewinJson.addProperty("result-msg", "The post you tried to rewin does not exist");
            
            key.attach(rewinJson.toString());
    		return;
    	}
    	    	  	
    	if(curUser == null) {
        	System.err.println("ERROR: User not found");
        	
        	rewinJson.addProperty("result", -1);
        	rewinJson.addProperty("result-msg", "Username not found");
            
            key.attach(rewinJson.toString());
            return;
    	}
    	
    	if(!curUser.getFeed().contains(postID)) {
    		
        	rewinJson.addProperty("result", -1);
        	rewinJson.addProperty("result-msg", "The post you tried to rewin is not in your feed, you can only rewin post that are in your feed.");
            
            key.attach(rewinJson.toString());
    		return;
    	}
    	
    	// Controllo che l'utente non abbia gia' rewwinato il post
    	if(curUser.getBlog().contains(postID)) {
    		
        	rewinJson.addProperty("result", -1);
        	rewinJson.addProperty("result-msg", "You already rewinned this post");
            
            key.attach(rewinJson.toString());
    		return;
    	}
    	
    	curUser.updateBlog(postID);
    	curPost.addRewin(username);
        
    	rewinJson.addProperty("result", 0);
    	rewinJson.addProperty("result-msg", "You rewinned the post");
        
        key.attach(rewinJson.toString());
    }
    
    public void ratePost(String uservoting, UUID postID, int vote, SelectionKey key) {
    	
    	WinPost postToRate = postMap.get(postID);
    	WinUser curUser = userMap.get(uservoting);
    	
    	JsonObject rateJson = new JsonObject();
	    	
    	if(curUser == null) {
    		System.err.println("ERROR: User not found");
        	
        	rateJson.addProperty("result", -1);  
        	rateJson.addProperty("result-msg", "Username not found");
            
            key.attach(rateJson.toString());
            return;
    	}
    	
    	if(postToRate == null) {
    		rateJson.addProperty("result", -1);  
        	rateJson.addProperty("result-msg", "The post you tried to rate does not exist");
            
            key.attach(rateJson.toString());
    		return;
    	}
    	
    	if(!curUser.getFeed().contains(postID)) {
    		rateJson.addProperty("result", -1);  
        	rateJson.addProperty("result-msg", "The post is not in your feed. You cannot rate a post that is not in your feed");
            
            key.attach(rateJson.toString());
    		return;
    	}
    	
    	if(postToRate.addRate(uservoting, vote) == -1) {
    		rateJson.addProperty("result", -1);  
        	rateJson.addProperty("result-msg", "You cannot rate the same post more than once");
            
            key.attach(rateJson.toString());
    		return;
    	}
    	
    	rateJson.addProperty("result", 0);  
    	rateJson.addProperty("result-msg", "Your vote was added to the post!");
        
        key.attach(rateJson.toString());
    	
    }
    
    public void addComment(String usercommenting, UUID postID, String comment, SelectionKey key) {
    	
    	WinPost postToComment = postMap.get(postID);
    	WinUser curUser = userMap.get(usercommenting);
    	
    	JsonObject commentJson = new JsonObject();
	    	
    	if(curUser == null) {
    		System.err.println("ERROR: User not found");
        	
    		commentJson.addProperty("result", -1);  
    		commentJson.addProperty("result-msg", "Username not found");
            
            key.attach(commentJson.toString());
            return;
    	}
    	
    	if(postToComment == null) {
    		commentJson.addProperty("result", -1);  
    		commentJson.addProperty("result-msg", "The post you tried to comment does not exist");
            
            key.attach(commentJson.toString());
    		return;
    	}
    	
    	if(!curUser.getFeed().contains(postID)) {
    		commentJson.addProperty("result", -1);  
    		commentJson.addProperty("result-msg", "The post is not in your feed. You can comment on a post only if it is in your feed");
            
            key.attach(commentJson.toString());
    		return;
    	}
    	
    	postToComment.addComment(usercommenting, comment);
    	
    	commentJson.addProperty("result", 0);  
    	commentJson.addProperty("result-msg", "Your comment was added to the post!");
        
        key.attach(commentJson.toString());
    }
    
    public void getWallet(String username, SelectionKey key) {
    	
    	WinUser curUser = userMap.get(username);
    	
    	if(curUser == null) {
    		//TODO
    	}
    	
    	JsonObject walletJson = new JsonObject();
    	
    	List<String> transactionList = new ArrayList<String>();
    	
    	if(curUser.getWallet().size() == 0) {
    		walletJson.addProperty("result", -1);
    		walletJson.addProperty("result-msg", "Your wallet is empty");
    		key.attach(walletJson.toString());
    		return;
    	}
    	
    	for(WinTransaction transaction : curUser.getWallet()) {
    		String t = transaction.getValue()+"/"+transaction.getTimestamp().toString();
    		transactionList.add(t);
    	}
    	
    	String listJson = WinUtils.prepareJson(transactionList);
    	
    	walletJson.addProperty("result", 0);
		walletJson.addProperty("result-msg", "Your wallet: ");    	
    	walletJson.addProperty("wallet-tot", curUser.getWalletTot());
    	walletJson.addProperty("transaction-list", listJson);
    	key.attach(walletJson.toString());
    }
    
    public void getWalletBitcoin(String username, SelectionKey key) {
    	WinUser curUser = userMap.get(username);
    	
    	if(curUser == null) {
    		//TODO
    	}
    	
    	JsonObject walletJson = new JsonObject();
    	
    	List<String> transactionList = new ArrayList<String>();
    	
    	double exchangeRate = 0;
    	double walletTotBitcoin = 0;
    	
    	if(curUser.getWallet().size() == 0) {
    		walletJson.addProperty("result", -1);
    		walletJson.addProperty("result-msg", "Your wallet is empty");
    		key.attach(walletJson.toString());
    		return;
    	}
    	
    	try {
			URL url = new URL("https://www.random.org/integers/?num=1&min=1&max=2000&col=1&base=10&format=plain&rnd=new");
			
			try (BufferedReader in = new BufferedReader(new InputStreamReader(url.openStream(), StandardCharsets.UTF_8))) {
				String inputLine;
				String number = null;
				
				while((inputLine = in.readLine()) != null) {
					number = inputLine;
				}
							    		            
		        exchangeRate = Double.parseDouble(number);
		        
		        exchangeRate = exchangeRate * 0.0001;
		        
		        System.out.println("Current exchange rate: " + exchangeRate);
		        
			} catch (IOException ioe) { ioe.printStackTrace(System.err); }

		} catch (MalformedURLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    	    	
    	for(WinTransaction transaction : curUser.getWallet()) {
    		String t = (transaction.getValue()*exchangeRate)+"/"+transaction.getTimestamp().toString();
    		transactionList.add(t);
    		walletTotBitcoin += (transaction.getValue()*exchangeRate);
    	}
    	
    	String listJson = WinUtils.prepareJson(transactionList);
    	
    	walletJson.addProperty("result", 0);
		walletJson.addProperty("result-msg", "Your wallet: ");    	
    	walletJson.addProperty("wallet-tot", walletTotBitcoin);
    	walletJson.addProperty("transaction-list", listJson);
    	key.attach(walletJson.toString());
    }
}
