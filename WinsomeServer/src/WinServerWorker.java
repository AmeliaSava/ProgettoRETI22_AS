import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.channels.SelectionKey;
import java.nio.charset.StandardCharsets;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import com.google.gson.JsonObject;

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
            	
            	// args[1] -> user
            	// args[2] -> password
            	
                System.out.println(args[1] + " wants to login with psw " + args[2]);

                loginUser(args[1], args[2], keyWorker);

                break;
                
            case "logout":
            	
            	// args[1] -> user
            	
                System.out.println(args[1] + " user is logging out");

                logoutUser(args[1], keyWorker);

                break;
                
            case "list":
            	
            	// args[1] -> operation: user OR following
            	// args[2] -> user
            	
                if(args[1].equals("users")) {
                    System.out.println("list users" + args[2]);

                    listUsers(args[2], keyWorker);

                }
                
                if(args[1].equals("following")) {
                    System.out.println("list following" + args[2]);
                    listFollowing(args[2], keyWorker);
                }
                
                break;
                
            case "follow":
            	
            	// args[1] -> user followed
            	// args[2] -> user following
            	
                System.out.println(args[2] + " wants to follow " + args[1]);

                int r = followUser(args[1], args[2], keyWorker);

                // salvo il socket channel del client per controllare che non si sia disconnesso in modo anomalo
                // TODO non funziona

                // Se l'utente ne ha seguito un'altro
                if((r == 0) && serverStorage.getOnlineUsers().containsKey(args[1])) {
                    // notifico quell'utente tramite RMI, solo se e' online
                    try {
                        followersRMI.follow(args[1], args[2]);
                    } catch (RemoteException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }

                break;
                
            case "unfollow":
            	
            	// args[1] -> user unfollowed
            	// args[2] -> user unfollowing

                System.out.println(args[2] + " wants to unfollow " + args[1]);

                r = unfollowUser(args[1], args[2], keyWorker);

                // Se l'utente ha smesso di seguirne un altro
                if((r == 0) && serverStorage.getOnlineUsers().containsKey(args[1])) {
                    // notifico quell'utente tramite RMI, so se e' online
                    try {
                        followersRMI.unfollow(args[1], args[2]);
                    } catch (RemoteException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }

                break;
                
            case "blog":
            	
            	// args[1] -> user
            	
                System.out.println("User " + args[1] + " has requested for their blog");

                viewBlog(args[1], keyWorker);
            	
            	break;
            	
            case "post":
                
            	// elements[1] -> post title
            	// elements[3] -> post text
            	// elements[4] -> post author
            	
                String[] elements = operation.split("\"");

                createPost(elements[4], elements[1], elements[3], keyWorker);

                System.out.println( elements[4] + " created post " + elements[1] + ": " + elements[3]);

                break;
                
            case "show":
            	
            	// args[1] -> operation: feed OR post
            	// args[2] -> if(feed) user || if(post) postID
            	// args[3] -> if(post) user
            	
                if(args[1].equals("feed")) {
                	
                	System.out.println("User " + args[2] + " has requested for their feed");
                	showFeed(args[2], keyWorker);

                } else if(args[1].equals("post")) {
                	
                	System.out.println("Showing post " + args[2] + " for " + args[3]);                	           	
                	
                	showPost(args[3], args[2], keyWorker);
                    
                }
                break;
                
            case "delete":
            	
            	// args[1] -> post id
            	// args[2] -> user
            	
            	System.out.println("Deleting post " + args[1] + " for " + args[2]);
            	
            	
            	deletePost(args[2], args[1], keyWorker);
            	
            	break;
            	
            case "rewin":
            	
            	// args[1] -> postID
            	// args[2] -> user rewinning
            	
            	System.out.println("Rewin post " + args[1] + " for " + args[2]);
            	            	
            	rewinPost(args[2], args[1], keyWorker);
            	
            	break;
            	
            case "rate":
            	
            	// args[1] -> postID
            	// args[2] -> vote
            	// args[3] -> user rating
            	
            	System.out.println("Rating post " + args[1] + " " + args[2] + " for " + args[3]);
            	
            	int vote = Integer.parseInt(args[2]);
            	            	
            	ratePost(args[3], args[1], vote, keyWorker);
            	
            	break;
            	
            case "comment":
            	
            	// args[1] -> postID
            	
            	// comment[1] -> commento
            	// comment[2] -> autore
            	
            	String[] comment = operation.split("\"");
            	addComment(comment[2], args[1], comment[1], keyWorker);
            	
            	break;
            	
            case "wallet":
            	
            	// args[1] -> user || btc
            	// args[2] -> if args[1] == btc then user
            	
            	if(args[1].equals("btc")) {
            		getWalletBitcoin(args[2], keyWorker);
            	} else getWallet(args[1], keyWorker);
            	
            	break;
        }

        // Comunico al server che c'e' una risposta da mandare
        keyWorker.interestOps(SelectionKey.OP_WRITE);
    }
    
    /*
     * Effettua i controlli per verificare che l'utente possa effettuare il login
     * e poi procede a salvarlo tra gli utenti online
     * Per comunicare l'esito dell'operazione aggiunge un attachment con il messaggio alla SelectionKey
     * 
     * @param username lo username scelto dall'utente che vuole effettuare il login
     * @param password la password inserita dall'utente
     * @param key
     * 
     * @return niente
     */
    private void loginUser(String username, String password, SelectionKey key) {

        WinUser curUser = serverStorage.getUser(username);
       
        JsonObject loginJson = new JsonObject();
                      
        // Se l'utente non e' registrato
        if(curUser == null) {        	
        	System.err.println("ERROR: login of not registered user");
        	sendError(loginJson, "Username not found, you need to register first!", key);
            return;
        }
        
        // Password errata
        if(!(curUser.getPassword().equals(password))) {        	
            System.err.println("ERROR: incorrect password");
            sendError(loginJson, "Password is incorrect", key);
            return;
        }
        
        // Aggiungo l'utente agli utenti online, se non c'e' gia'
        if(!serverStorage.userIsOnline(username)) serverStorage.addOnlineUser(username, curUser);
        else {
        	System.err.println("ERROR: user already online");        	
        	sendError(loginJson, "User is already online, cannot login!", key);
            return;
        }
        
        loginJson.addProperty("result", 0);
        loginJson.addProperty("result-msg", "Welcome " + username + " you are now logged in!");
        
        // Mando la lista dei follower gia' esistenti per aggiornarla lato client
        loginJson.addProperty("followers-list", WinUtils.prepareJson(curUser.getfollowers()));
        
        key.attach(loginJson.toString());
    }
    
    /*
     * Effettua i controlli per verificare che l'utente possa effettuare il logout
     * e poi procede a rimuoverlo dagli utenti online
     * Per comunicare l'esito dell'operazione aggiunge un attachment con il messaggio alla SelectionKey
     * 
     * @param username lo username scelto dall'utente che vuole effettuare il login
     * @param key
     * 
     * @return niente
     */
    public void logoutUser(String username, SelectionKey key) {
    	
    	JsonObject logoutJson = new JsonObject();
    	
        if(!serverStorage.userIsRegistred(username)) {
        	System.err.println("ERROR: logout of not registered user");
        	sendError(logoutJson,"Username not found, could not logout!", key);        	
        }

        if(serverStorage.userIsOnline(username)) serverStorage.removeOnlineUser(username);
        else {
        	System.err.println("ERROR: user was not online");        	
        	sendError(logoutJson, "User not online, cannot logout!", key);
            return;
        }

        System.out.println("Loggin out user " + username);
    	
    	logoutJson.addProperty("result", 0);
    	logoutJson.addProperty("result-msg", username + " logged out");
        
        key.attach(logoutJson.toString());
    }
        
    public void listUsers(String username, SelectionKey key) {

        WinUser curUser = serverStorage.getUser(username);

        // lista dove salvo le informazioni da mandare
        List<String> toSend = new ArrayList<>();
        
        JsonObject usersJson = new JsonObject();

        if(curUser == null) {        	
        	System.err.println("ERROR: User not found");        	
        	sendError(usersJson, "User not found, login or register", key);
            return;
        }
        
        //l'utente non era online
        if(!serverStorage.userIsOnline(username)) {
        	System.err.println("ERROR: User not online");
        	sendError(usersJson, "User not online", key);
        	return;
        }
        
        // Scorro gli utenti iscritti
        for (WinUser user : serverStorage.getAllUsers()) {
        	
            // Ignoro l'utente corrente
            if(user.getUsername().equals(curUser.getUsername())) continue;
            
            // Lista dove salvo solo i tag in comune tra i due utenti
            List<String> commonTags = new ArrayList<>(curUser.getTagList());
            commonTags.retainAll(user.getTagList());
            
            // Se ci sono dei tag in comune aggiungo l'utente alla lista da inviare
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

        WinUser curUser = serverStorage.getUser(username);

        // lista dove salvo le informazioni da mandare
        List<String> toSend = new ArrayList<>();
        
        JsonObject followingJson = new JsonObject();

        if(curUser == null) {
        	System.err.println("ERROR: User not found");
        	sendError(followingJson, "User not found, login or register", key);
            return;
        }
        if(!serverStorage.userIsOnline(username)) {
        	System.err.println("ERROR: User not online");
        	sendError(followingJson, "User not online", key);
        	return;
        }

        // Prendo la lista degli utenti seguiti
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
    
    public int followUser(String userFollowed, String userFollowing, SelectionKey key) {

        WinUser curUser = serverStorage.getUser(userFollowing);
        WinUser folUser = serverStorage.getUser(userFollowed);
        
        JsonObject followJson = new JsonObject();

        //L'utente non e' registrato
        if(curUser == null) {
        	System.err.println("ERROR: User not found");
        	sendError(followJson, "User not found, login or register", key);
            return -1;
        }

        // L'utente e' online?
        if(serverStorage.userIsOnline(userFollowing)) {
        	
            // Controllo che l'utente da seguire esista
            if(folUser != null) {

                // Controllo che l'utente corrente non stia gia' seguendo l'utente da seguire                
                if((curUser.followUser(userFollowed)) == 0) {
                    
                	System.out.println(userFollowing + " followed " + userFollowed);
                	
                	// Aggiorno la lista dei follower lato server
                    folUser.addFollower(userFollowing);
                    
                    // Aggiungo tutti i post dell'utente che viene seguito al feed dell'utente che segue
                    for(UUID post : folUser.getBlog()) {
                    	curUser.updateFeed(post);
                    }
                                       
                    followJson.addProperty("result", 0);
                	followJson.addProperty("result-msg", userFollowing + " you are now following " + userFollowed);
                    
                    key.attach(followJson.toString());
                } else {
                	sendError(followJson,  "You are already following this user", key);
                    return -1;
                }

            } else {            	
            	sendError(followJson, "The user you are trying to follow does not exist", key);
                return -1;
            }
        } else {
        	System.err.println("ERROR: User not online");        	
        	sendError(followJson, "User not online", key);
            return -1;
        }

        return 0;
    }

    public int unfollowUser(String userUnfollowed, String userUnfollowing, SelectionKey key) {

        WinUser curUser = serverStorage.getUser(userUnfollowing);
        WinUser unfolUser = serverStorage.getUser(userUnfollowed);

        JsonObject unfollowJson = new JsonObject();

        //L'utente non e' registrato
        if(curUser == null) {
        	System.err.println("ERROR: User not found");
        	sendError(unfollowJson, "Username not found, login or register", key);
            return -1;
        }

        // L'utente e' online?
        if(serverStorage.userIsOnline(userUnfollowing)) {
        	
            // Controllo che l'utente da smettere di seguire esista
            if(unfolUser != null) {

                // Controllo che l'utente stesse seguendo l'utente che deve smettere di seguire                
                if((curUser.unfollowUser(userUnfollowed)) == 0) {
                	
                    System.out.println(curUser.getUsername() + " unfollowed " + userUnfollowed);
                    
                    //Aggiorno la lista dei follower lato server
                    unfolUser.removeFollower(userUnfollowing);
                                        
                    Iterator<UUID> iter = curUser.getFeed().iterator();
                    while(iter.hasNext()) {
                      UUID post = iter.next();
                      if(serverStorage.getPost(post).getPostAuthor().equals(userUnfollowed)) {
                        iter.remove();
                      }
                    }
                   
                    unfollowJson.addProperty("result", 0);
                	unfollowJson.addProperty("result-msg", userUnfollowing + " you stopped following " + userUnfollowed);
                    
                    key.attach(unfollowJson.toString());
                    return 0;
                } else {
                	sendError(unfollowJson, "You are not following this user", key);
                    return -1;
                }

            } else {
            	sendError(unfollowJson, "The user you are trying to unfollow does not exist", key);
                return -1;
            }
        } else {
        	System.err.println("ERROR: User not online");        	
        	sendError(unfollowJson, "Username not online", key);
            return -1;
        }
        
    }

	public void viewBlog(String currentUser, SelectionKey key) {
    	
		WinUser curUser = serverStorage.getUser(currentUser);
    	
    	JsonObject blogJson = new JsonObject();
    	
    	if(curUser == null) {
        	System.err.println("ERROR: User not found");
        	sendError(blogJson, "User not found", key);
            return;
    	}
    	
        if(!serverStorage.userIsOnline(currentUser)) {
        	System.err.println("ERROR: User not online");
        	sendError(blogJson, "User not online", key);
        	return;
        }
        
    	List<String> blog = new ArrayList<String>();
    	
        String info = null;
    	
    	for(UUID idPost : curUser.getBlog()) {
    		WinPost curPost = serverStorage.getPost(idPost);
    		
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

        WinUser curUser = serverStorage.getUser(author);

        JsonObject postJson = new JsonObject();
             
        if(curUser == null) {
        	System.err.println("ERROR: User not found");
        	sendError(postJson, "Username not found", key);
            return;
        }

        if(!serverStorage.userIsOnline(author)) {
        	System.err.println("ERROR: User not online");
        	sendError(postJson, "User not online", key);
            return;
        }

        WinPost post = new WinPost(author, title, text);
        serverStorage.addNewPost(post);
        
        // Aggiungo l'id del post alla lista contenuta nell'utente per poter ricostruire il suo blog
        curUser.updateBlog(post.getIdPost());

        // Aggiungo il post al feed di ogni follower
        // TODO follower null?
        for(String user : curUser.getfollowers()) {
            WinUser follower = serverStorage.getUser(user);
            follower.updateFeed(post.getIdPost());
        }
       
        System.out.println(author + " created a new post");
    	
    	postJson.addProperty("result", 0);
    	postJson.addProperty("result-msg", "You posted!");
        
        key.attach(postJson.toString());
        return;
    }
    
    public void showFeed(String currentUser, SelectionKey key) {
    	
    	
    	WinUser curUser = serverStorage.getUser(currentUser);
    	
    	JsonObject feedJson = new JsonObject();
    	
    	if(curUser == null) {
        	System.err.println("ERROR: User not found");
        	sendError(feedJson, "User not found", key);         
            return;
    	}
    	
    	if(!serverStorage.userIsOnline(currentUser)) {
          	System.err.println("ERROR: User not online");
          	sendError(feedJson, "User not online", key);
              return;
        }
    	
    	List<String> feed = new ArrayList<String>();

        String info = null;
    	
    	for(UUID idPost : curUser.getFeed()) {
    		WinPost curPost = serverStorage.getPost(idPost);
    		
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
    
    public void showPost(String username, String postID, SelectionKey key) {
    	
    	JsonObject postJson = new JsonObject();
    	
    	UUID CurPostID;
    	try {
    		CurPostID = UUID.fromString(postID); 
    	} catch(IllegalArgumentException ex) {
    		System.err.println("ERROR: Post ID not valid");
        	sendError(postJson, "Post ID not valid", key);
            return;
    	}
    	
    	WinPost curPost = serverStorage.getPost(CurPostID);
    	  	
    	if(!serverStorage.userIsRegistred(username)) {
        	System.err.println("ERROR: User not found");
        	sendError(postJson, "User not found", key);
            return;
    	}
    	
    	if(!serverStorage.userIsOnline(username)) {
          	System.err.println("ERROR: User not online");
          	sendError(postJson, "User not online", key);
            return;
        }
    	
    	// Il post richiesto non esiste
    	if(curPost == null) {
    		sendError(postJson, "The post you requested doesn't exist", key);
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
    	
    	List<String> comments = new ArrayList<String>();
    	
    	String info = null;
    	for(WinComment comment : curPost.getComments()) {
    		info = comment.getComment() + " by " + comment.getAuthor() + " " + comment.getTimestamp();
    		comments.add(info);
    	}
    	
    	postJson.addProperty("comments", WinUtils.prepareJson(comments));
    	    	
    	key.attach(postJson.toString());
           	
    }
    
    public void deletePost(String username, String postID, SelectionKey key) {
    	
    	JsonObject deleteJson = new JsonObject();
    	
    	UUID CurPostID;
    	try {
    		CurPostID = UUID.fromString(postID); 
    	} catch(IllegalArgumentException ex) {
    		System.err.println("ERROR: Post ID not valid");
        	sendError(deleteJson, "Post ID not valid", key);
            return;
    	}
    	
    	WinPost curPost = serverStorage.getPost(CurPostID); 
    	WinUser curUser = serverStorage.getUser(username);
    	  	
    	if(curUser == null) {
    		System.err.println("ERROR: User not found");
        	sendError(deleteJson, "User not found", key);            
            return;
    	}
    	
    	if(!serverStorage.userIsOnline(username)) {
          	System.err.println("ERROR: User not online");
          	sendError(deleteJson, "User not online", key);
            return;
    	}
    	
    	if(curPost == null) {
    		sendError(deleteJson, "The post you are trying to delete doesn't exist", key);
    		return;
    	}
    	
    	if(!curPost.getPostAuthor().equals(username)) {
    		sendError(deleteJson, "You are not the author of this post, you cannot delete it", key);
    		return;
    	}
    	
    	// rimuovo tutti i rewin
    	for(String user : curPost.getRewins()) {
    		serverStorage.getUser(user).removeBlog(CurPostID);
    	}
    	
    	serverStorage.removePost(CurPostID);
    	 
    	// Rimuovo il post dal blog dell'utente
    	curUser.getBlog().remove(CurPostID);
    	
    	//rimuovo il post dal feed di tutti i suoi followers
    	for(String user : curUser.getfollowers()) {
    		serverStorage.getUser(user).getFeed().remove(CurPostID);
    	}
    	
    	deleteJson.addProperty("result", 0);  
    	deleteJson.addProperty("result-msg", "The post was successfully deleted!");
        
        key.attach(deleteJson.toString());
    	
    }
    
    public void rewinPost(String username, String postID, SelectionKey key) {
    	
    	JsonObject rewinJson = new JsonObject();

    	UUID CurPostID;
    	try {
    		CurPostID = UUID.fromString(postID); 
    	} catch(IllegalArgumentException ex) {
    		System.err.println("ERROR: Post ID not valid");
        	sendError(rewinJson, "Post ID not valid", key);
            return;
    	}
    	
    	WinPost curPost = serverStorage.getPost(CurPostID);    	
    	WinUser curUser = serverStorage.getUser(username);
    	  	
    	if(curPost == null) {
    		sendError(rewinJson, "The post you tried to rewin does not exist", key);
    		return;
    	}
    	    	  	
    	if(curUser == null) {
    		System.err.println("ERROR: User not found");
        	sendError(rewinJson, "User not found", key);      
            return;
    	}
    	if(!serverStorage.userIsOnline(username)) {
          	System.err.println("ERROR: User not online");
          	sendError(rewinJson, "User not online", key);
            return;
    	}
    	
    	if(!curUser.getFeed().contains(CurPostID)) {
    		sendError(rewinJson, "The post you tried to rewin is not in your feed, you can only rewin post that are in your feed.", key);
            return;
    	}
    	
    	// Controllo che l'utente non abbia gia' rewwinato il post
    	if(curUser.getBlog().contains(CurPostID)) {
    		sendError(rewinJson, "You already rewinned this post", key);
    		return;
    	}
    	
    	curUser.updateBlog(CurPostID);
    	curPost.addRewin(username);
        
    	rewinJson.addProperty("result", 0);
    	rewinJson.addProperty("result-msg", "You rewinned the post");
        
        key.attach(rewinJson.toString());
    }
    
    public void ratePost(String uservoting, String postID, int vote, SelectionKey key) {
    	
    	JsonObject rateJson = new JsonObject();
    	
    	UUID CurPostID;
    	try {
    		CurPostID = UUID.fromString(postID); 
    	} catch(IllegalArgumentException ex) {
    		System.err.println("ERROR: Post ID not valid");
        	sendError(rateJson, "Post ID not valid", key);
            return;
    	}
    	
    	WinPost postToRate = serverStorage.getPost(CurPostID);
    	WinUser curUser = serverStorage.getUser(uservoting);
    	    	    	
    	if(curUser == null) {
    		System.err.println("ERROR: User not found");
    		sendError(rateJson, "User not found", key);      
            return;
    	}
    	if(!serverStorage.userIsOnline(uservoting)) {
          	System.err.println("ERROR: User not online");
          	sendError(rateJson, "User not online", key);
            return;
    	}
    	
    	if(postToRate == null) {
    		sendError(rateJson, "The post you tried to rate does not exist", key);
            return;
    	}
    	
    	if(!curUser.getFeed().contains(CurPostID)) {
    		sendError(rateJson,"The post is not in your feed. You cannot rate a post that is not in your feed", key);            
    		return;
    	}
    	
    	if(postToRate.addRate(uservoting, vote) == -1) {
    		sendError(rateJson, "You cannot rate the same post more than once", key);
    		return;
    	}
    	
    	rateJson.addProperty("result", 0);  
    	rateJson.addProperty("result-msg", "Your vote was added to the post!");
        
        key.attach(rateJson.toString());	
    }
    
    public void addComment(String usercommenting, String postID, String comment, SelectionKey key) {
    	
    	JsonObject commentJson = new JsonObject();
    	
    	UUID CurPostID;
    	try {
    		CurPostID = UUID.fromString(postID); 
    	} catch(IllegalArgumentException ex) {
    		System.err.println("ERROR: Post ID not valid");
        	sendError(commentJson, "Post ID not valid", key);
            return;
    	}
    	
    	WinPost postToComment = serverStorage.getPost(CurPostID);
    	WinUser curUser = serverStorage.getUser(usercommenting);
    			
    	if(curUser == null) {
    		System.err.println("ERROR: User not found");
    		sendError(commentJson, "Username not found", key);
            return;
    	}
    	if(!serverStorage.userIsOnline(usercommenting)) {
          	System.err.println("ERROR: User not online");
          	sendError(commentJson, "User not online", key);
            return;
    	}
    	
    	if(postToComment == null) {
    		sendError(commentJson, "The post you tried to comment does not exist", key);
    		return;
    	}
    	
    	if(!curUser.getFeed().contains(CurPostID)) {
    		sendError(commentJson, "The post is not in your feed. You can comment on a post only if it is in your feed", key);
    		return;
    	}
    	
    	postToComment.addComment(usercommenting, comment);
    	
    	commentJson.addProperty("result", 0);  
    	commentJson.addProperty("result-msg", "Your comment was added to the post!");
        
        key.attach(commentJson.toString());
    }
    
    public void getWallet(String username, SelectionKey key) {
    	
    	JsonObject walletJson = new JsonObject();
    	
    	WinUser curUser = serverStorage.getUser(username);
    	
    	if(curUser == null) {
    		System.err.println("ERROR: User not found");
    		sendError(walletJson, "Username not found", key);
            return;
    	}
    	 	
    	List<String> transactionList = new ArrayList<String>();
    	
    	if(curUser.getWallet().size() == 0) {
    		sendError(walletJson, "Your wallet is empty", key);
    		return;
    	}
    	if(!serverStorage.userIsOnline(username)) {
          	System.err.println("ERROR: User not online");
          	sendError(walletJson, "User not online", key);
            return;
    	}
    	
    	for(WinTransaction transaction : curUser.getWallet()) {
    		String t = transaction.getValue()+"/"+transaction.getTimestamp().toString();
    		transactionList.add(t);
    	}
    	
       	walletJson.addProperty("result", 0);
		walletJson.addProperty("result-msg", "Your wallet: ");    	
    	walletJson.addProperty("wallet-tot", curUser.getWalletTot());
    	walletJson.addProperty("transaction-list", WinUtils.prepareJson(transactionList));
    	key.attach(walletJson.toString());
    }
    
    public void getWalletBitcoin(String username, SelectionKey key) {
    	
    	JsonObject walletJson = new JsonObject();
    	
    	WinUser curUser = serverStorage.getUser(username);
    	
    	if(curUser == null) {
    		sendError(walletJson, "Your wallet is empty", key);
    		return;
    	}
    	if(!serverStorage.userIsOnline(username)) {
          	System.err.println("ERROR: User not online");
          	sendError(walletJson, "User not online", key);
            return;
    	}
    	  	
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

    	walletJson.addProperty("result", 0);
		walletJson.addProperty("result-msg", "Your wallet: ");    	
    	walletJson.addProperty("wallet-tot", walletTotBitcoin);
    	walletJson.addProperty("transaction-list", WinUtils.prepareJson(transactionList));
    	key.attach(walletJson.toString());
    }
    
    public void sendError(JsonObject jsonObj, String errorMsg, SelectionKey key) {
    	jsonObj.addProperty("result", -1);
    	jsonObj.addProperty("result-msg", errorMsg);
        
        key.attach(jsonObj.toString());
    }
}
