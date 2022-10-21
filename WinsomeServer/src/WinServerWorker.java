import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.channels.SelectionKey;
import java.nio.charset.StandardCharsets;
import java.rmi.RemoteException;
import java.util.*;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

public class WinServerWorker implements Runnable {

    private final String operation;
    private final SelectionKey keyWorker;
    private final WinServerStorage serverStorage;

    private final NotificationServiceServerImpl followersRMI;

    private final String multicastAddress;
    private final int UDPport;

    public WinServerWorker(String operation, SelectionKey key, WinServerStorage serverStorage, NotificationServiceServerImpl followersRMI,
                           String multicastAddress, int UDPport) {
        this.operation = operation;
        this.keyWorker = key;
        this.serverStorage = serverStorage;
        this.followersRMI = followersRMI;
        this.multicastAddress = multicastAddress;
        this.UDPport = UDPport;
    }

    @Override
    public void run() {

        JsonObject message = new Gson().fromJson(operation, JsonObject.class);

        switch (message.get("operation").getAsString()) {
        
            case "login":
                loginUser(message.get("user").getAsString(), message.get("password").getAsString(), keyWorker);
                break;
            case "logout":
                logoutUser(message.get("user").getAsString(), keyWorker);
                break;
            case "list users":
               listUsers(message.get("user").getAsString(), keyWorker);
                break;
            case "list following":
                listFollowing(message.get("user").getAsString(), keyWorker);
                break;
            case "follow":
                int r = followUser(message.get("user-to-follow").getAsString(), message.get("user").getAsString(), keyWorker);

                // salvo il socket channel del client per controllare che non si sia disconnesso in modo anomalo
                // TODO non funziona

                // Se l'utente ne ha seguito un'altro
                if((r == 0) && serverStorage.getOnlineUsers().contains(message.get("user-to-follow").getAsString())) {
                    // notifico quell'utente tramite RMI, solo se e' online
                    try {
                        followersRMI.follow(message.get("user-to-follow").getAsString(), message.get("user").getAsString());
                    } catch (RemoteException e) {
                        System.err.println("ERROR: RMI callback " + e.getMessage());
                        e.printStackTrace();
                    }
                }

                break;
                
            case "unfollow":
                r = unfollowUser(message.get("user-to-unfollow").getAsString(), message.get("user").getAsString(), keyWorker);

                // Se l'utente ha smesso di seguirne un altro
                if((r == 0) && serverStorage.getOnlineUsers().containsKey(message.get("user-to-unfollow").getAsString())) {
                    // notifico quell'utente tramite RMI, so se e' online
                    try {
                        followersRMI.unfollow(message.get("user-to-unfollow").getAsString(), message.get("user").getAsString());
                    } catch (RemoteException e) {
                        System.err.println("ERROR: RMI callback " + e.getMessage());
                        e.printStackTrace();
                    }
                }

                break;
                
            case "blog":
                viewBlog(message.get("user").getAsString(), keyWorker);
            	break;
            	
            case "post":
                createPost(message.get("user").getAsString(), message.get("title").getAsString(), message.get("content").getAsString(), keyWorker);
                break;
                
            case "show feed":
                showFeed(message.get("user").getAsString(), keyWorker);
                break;

            case "show post":
                showPost(message.get("user").getAsString(), message.get("post-id").getAsString(), keyWorker);
                break;
                
            case "delete":
            	deletePost(message.get("user").getAsString(), message.get("post-id").getAsString(), keyWorker);
            	break;
            	
            case "rewin":
            	rewinPost(message.get("user").getAsString(), message.get("post-id").getAsString(), keyWorker);
            	break;
            	
            case "rate":
            	ratePost(message.get("user").getAsString(), message.get("post-id").getAsString(), message.get("comment").getAsInt(), keyWorker);
            	break;
            	
            case "comment":
            	addComment(message.get("user").getAsString(), message.get("post-id").getAsString(), message.get("comment").getAsString(), keyWorker);
            	break;
            	
            case "wallet":
                getWallet(message.get("user").getAsString(), keyWorker);
            	break;
            case "wallet btc":
                getWalletBitcoin(message.get("user").getAsString(), keyWorker);
                break;
            default:
                System.err.println("ERROR: command not recognized, problem communicating with client");
        }
        // Comunico al main che c'e' una risposta da mandare
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

        System.out.println(username + " wants to login with psw " + password);

        WinUser curUser = serverStorage.getUser(username);
       
        JsonObject loginJson = new JsonObject();
                      
        // L'utente non e' registrato
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
        if(serverStorage.addOnlineUser(username, curUser) != null) {
        	System.err.println("ERROR: user already online");        	
        	sendError(loginJson, "User is already online, cannot login!", key);
            return;
        }

        // Comunico l'esito dell'operazione
        loginJson.addProperty("result", 0);
        loginJson.addProperty("result-msg", "Welcome " + username + " you are now logged in!");
        
        // Mando la lista dei follower gia' esistenti per aggiornarla lato client
        loginJson.addProperty("followers-list", WinUtils.prepareJson(curUser.getfollowers()));

        // Mondo le informazioni per la comunicazione UDP multicast
        loginJson.addProperty("multicast", multicastAddress);
        loginJson.addProperty("UDPport", UDPport);

        loginJson.addProperty("login-ok", 0);
        loginJson.addProperty("user", username);
        
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

        System.out.println(username + " user is logging out");
    	JsonObject logoutJson = new JsonObject();

        serverStorage.removeOnlineUser(username);

        System.out.println("Loggin out user " + username);
    	
    	logoutJson.addProperty("result", 0);
    	logoutJson.addProperty("result-msg", username + " logged out");
        logoutJson.addProperty("logout-ok", 0);
        logoutJson.addProperty("user", username);
        
        key.attach(logoutJson.toString());
    }
        
    public void listUsers(String username, SelectionKey key) {

        System.out.println("User " + username + " has requested the list of the other users");

        WinUser curUser = serverStorage.getUser(username);

        // lista dove salvo le informazioni da mandare
        Vector<String> toSend = new Vector<>();

        JsonObject usersJson = new JsonObject();

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

        System.out.println("User " + username + " has requested following users list");

        WinUser curUser = serverStorage.getUser(username);

        JsonObject followingJson = new JsonObject();

        // Prendo la lista degli utenti seguiti
        Vector<String> toSend = new Vector<>(curUser.getfollowedUsers());
        
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

        System.out.println(userFollowing + " wants to follow " + userFollowed);

        WinUser curUser = serverStorage.getUser(userFollowing);
        WinUser folUser = serverStorage.getUser(userFollowed);
        
        JsonObject followJson = new JsonObject();

        // Controllo che l'utente da seguire esista
        if(folUser != null) {
            // Seguo l'utente, nel caso lo stia gia' seguendo ritorno errore
            if((curUser.followUser(userFollowed)) == 0) {

                System.out.println(userFollowing + " followed " + userFollowed);

                // Aggiorno la lista dei follower lato server
                folUser.addFollower(userFollowing);

                // Aggiungo tutti i post dell'utente che viene seguito al feed dell'utente che segue
                synchronized (folUser.getBlog()) {
                    for (UUID post : folUser.getBlog()) {
                        curUser.addPostToFeed(post);
                    }
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

        return 0;
    }

    public int unfollowUser(String userUnfollowed, String userUnfollowing, SelectionKey key) {

        System.out.println(userUnfollowing + " wants to unfollow " + userUnfollowed);

        WinUser curUser = serverStorage.getUser(userUnfollowing);
        WinUser unfolUser = serverStorage.getUser(userUnfollowed);

        JsonObject unfollowJson = new JsonObject();

        // Controllo che l'utente da smettere di seguire esista
        if(unfolUser != null) {
            // Smetto di seguire l'utente, se non lo stavo seguendo ritorna errore
            if((curUser.unfollowUser(userUnfollowed)) == 0) {

                System.out.println(curUser.getUsername() + " unfollowed " + userUnfollowed);

                // Aggiorno la lista dei follower lato server
                unfolUser.removeFollower(userUnfollowing);

                synchronized (curUser.getFeed()) {
                    //rimuovo i post dal feed
                    Iterator<UUID> iter = curUser.getFeed().iterator();
                    while (iter.hasNext()) {
                        UUID post = iter.next();
                        synchronized (serverStorage.getPostMap()) {
                            if (serverStorage.getPost(post).getPostAuthor().equals(userUnfollowed)) {
                                iter.remove();
                            }
                        }
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
    }


	public void viewBlog(String currentUser, SelectionKey key) {

        System.out.println("User " + currentUser + " has requested for their blog");

        WinUser curUser = serverStorage.getUser(currentUser);
    	
    	JsonObject blogJson = new JsonObject();

    	List<String> blog = new ArrayList<String>();
    	
        String info = null;
        synchronized (curUser.getBlog()) {
            for (UUID idPost : curUser.getBlog()) {
                WinPost curPost = serverStorage.getPost(idPost);

                if (curPost == null) {
                    info = "[deleted]/[deleted]/[deleted]";
                    blog.add(info);
                    continue;
                }

                info = curPost.getIdPost().toString();
                info = info.concat("/" + curPost.getPostAuthor() + "/" + curPost.getPostTitle());
                blog.add(info);
            }
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

        WinPost post = new WinPost(author, title, text);
        serverStorage.addNewPost(post);
        
        // Aggiungo l'id del post alla lista contenuta nell'utente per poter ricostruire il suo blog
        curUser.updateBlog(post.getIdPost());

        // Aggiungo il post al feed di ogni follower
        // TODO follower null?
        synchronized (curUser.getfollowers()) {
            for (String user : curUser.getfollowers()) {
                WinUser follower = serverStorage.getUser(user);
                follower.addPostToFeed(post.getIdPost());
            }
        }
        System.out.println(author + " created a new post");
    	
    	postJson.addProperty("result", 0);
    	postJson.addProperty("result-msg", "You created a new post with ID " + post.getIdPost());
        
        key.attach(postJson.toString());
    }
    
    public void showFeed(String currentUser, SelectionKey key) {

        System.out.println("User " + currentUser + " has requested for their feed");
    	WinUser curUser = serverStorage.getUser(currentUser);
    	
    	JsonObject feedJson = new JsonObject();
    	
    	List<String> feed = new ArrayList<String>();

        String info = null;
        synchronized (curUser.getFeed()) {
            for (UUID idPost : curUser.getFeed()) {
                WinPost curPost = serverStorage.getPost(idPost);

                if (curPost == null) {
                    info = "[deleted]/[deleted]/[deleted]";
                    feed.add(info);
                    continue;
                }

                info = curPost.getIdPost().toString();
                info = info.concat("/" + curPost.getPostAuthor() + "/" + curPost.getPostTitle());
                feed.add(info);
            }
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
        System.out.println("Showing post " + postID + " for " + username);
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

    	// Il post richiesto non esiste
        if (curPost == null) {
            sendError(postJson, "The post you requested doesn't exist", key);
            return;
        }

        synchronized (curPost) {
            postJson.addProperty("result", 0);
            postJson.addProperty("result-msg", "Post:");
            postJson.addProperty("title", curPost.getPostTitle());
            postJson.addProperty("content", curPost.getPostContent());
            postJson.addProperty("author", curPost.getPostAuthor());
            postJson.addProperty("id", curPost.getIdPost().toString());
            postJson.addProperty("upvote", curPost.getUpvoteCount());
            postJson.addProperty("downvote", curPost.getDownvoteCount());

            List<String> comments = new ArrayList<String>();

            String info;
            for (WinComment comment : curPost.getComments()) {
                info = comment.getComment() + " by " + comment.getAuthor() + " " + comment.getTimestamp();
                comments.add(info);
            }
            postJson.addProperty("comments", WinUtils.prepareJson(comments));
        }

    	key.attach(postJson.toString());
    }
    
    public void deletePost(String username, String postID, SelectionKey key) {

        System.out.println("Deleting post " + postID + " for " + username);
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

    	if(curPost == null) {
    		sendError(deleteJson, "The post you are trying to delete doesn't exist", key);
    		return;
    	}

    	synchronized (curPost) {
            if (!curPost.getPostAuthor().equals(username)) {
                sendError(deleteJson, "You are not the author of this post, you cannot delete it", key);
                return;
            }

            // rimuovo tutti i rewin
            for (String user : curPost.getRewins()) {
                serverStorage.getUser(user).removeBlog(CurPostID);
            }

            //TODO
            synchronized (curUser.getBlog()) {
                // Rimuovo il post dal blog dell'utente
                curUser.getBlog().remove(CurPostID);
                serverStorage.removePost(CurPostID);
            }
        }
    	//rimuovo il post dal feed di tutti i suoi followers
        synchronized (curUser.getfollowers()) {
            for (String user : curUser.getfollowers()) {
                synchronized (serverStorage.getUser(user).getFeed()) {
                    serverStorage.getUser(user).getFeed().remove(CurPostID);
                }
            }
        }
    	deleteJson.addProperty("result", 0);  
    	deleteJson.addProperty("result-msg", "The post was successfully deleted!");
        
        key.attach(deleteJson.toString());
    }
    
    public void rewinPost(String username, String postID, SelectionKey key) {

        System.out.println("Rewin post " + postID + " for " + username);

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

    	synchronized (curUser.getFeed()) {
            if (!curUser.getFeed().contains(CurPostID)) {
                sendError(rewinJson, "The post you tried to rewin is not in your feed, you can only rewin post that are in your feed.", key);
                return;
            }
        }

        // Aggiungo l'utente alla lista dei rewin, se ha gia' rewinnato il post error
        if(curPost.addRewin(username) != 0) {
            sendError(rewinJson, "You already rewinned this post", key);
            return;
        }

        // Aggiungo l'ID del post al blog dell'utente
        curUser.updateBlog(CurPostID);

    	rewinJson.addProperty("result", 0);
    	rewinJson.addProperty("result-msg", "You rewinned the post");
        
        key.attach(rewinJson.toString());
    }
    
    public void ratePost(String uservoting, String postID, int vote, SelectionKey key) {

        System.out.println("Rating post " + postID + " " + vote + " for " + uservoting);

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
    	
    	if(postToRate == null) {
    		sendError(rateJson, "The post you tried to rate does not exist", key);
            return;
    	}

    	synchronized (curUser.getFeed()) {
            if (!curUser.getFeed().contains(CurPostID)) {
                sendError(rateJson, "The post is not in your feed. You cannot rate a post that is not in your feed", key);
                return;
            }
        }

        synchronized (postToRate) {
            // Contorllo che l'utente non sia l'autore del post
            if (postToRate.getPostAuthor().equals(uservoting)) {
                sendError(rateJson, "You can't rate your own post", key);
                return;
            }
            // Aggiungo il voto, se l'utente ha gia' votato il post ritorno errore
            if (postToRate.addRate(uservoting, vote) == -1) {
                sendError(rateJson, "You cannot rate the same post more than once", key);
                return;
            }
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

    	if(postToComment == null) {
    		sendError(commentJson, "The post you tried to comment does not exist", key);
    		return;
    	}

        synchronized (curUser.getFeed()) {
            if (!curUser.getFeed().contains(CurPostID)) {
                sendError(commentJson, "The post is not in your feed. You can comment on a post only if it is in your feed", key);
                return;
            }
        }

    	synchronized (postToComment) {
            // Controllo che l'utente non sia l'autore del post
            if (postToComment.getPostAuthor().equals(usercommenting)) {
                sendError(commentJson, "You can't rate your own post", key);
                return;
            }

            postToComment.addComment(usercommenting, comment);
        }

    	commentJson.addProperty("result", 0);  
    	commentJson.addProperty("result-msg", "Your comment was added to the post!");
        
        key.attach(commentJson.toString());
    }
    
    public void getWallet(String username, SelectionKey key) {
    	
    	JsonObject walletJson = new JsonObject();
    	
    	WinUser curUser = serverStorage.getUser(username);
    	 	
    	List<String> transactionList = new ArrayList<String>();

    	synchronized (curUser.getWallet()) {
            if (curUser.getWallet().size() == 0) {
                sendError(walletJson, "Your wallet is empty", key);
                return;
            }

            for (WinTransaction transaction : curUser.getWallet()) {
                String t = transaction.getValue() + "/" + transaction.getTimestamp().toString();
                transactionList.add(t);
            }

            walletJson.addProperty("wallet-tot", curUser.getWalletTot());
        }
       	walletJson.addProperty("result", 0);
		walletJson.addProperty("result-msg", "Your wallet: ");
    	walletJson.addProperty("transaction-list", WinUtils.prepareJson(transactionList));
    	key.attach(walletJson.toString());
    }
    
    public void getWalletBitcoin(String username, SelectionKey key) {
    	
    	JsonObject walletJson = new JsonObject();
    	
    	WinUser curUser = serverStorage.getUser(username);

    	List<String> transactionList = new ArrayList<String>();
    	
    	double exchangeRate = 0;
    	double walletTotBitcoin = 0;

    	try {
			URL url = new URL("https://www.random.org/integers/?num=1&min=1&max=2000&col=1&base=10&format=plain&rnd=new");
            String number = null;
			try (BufferedReader in = new BufferedReader(new InputStreamReader(url.openStream(), StandardCharsets.UTF_8))) {
				String inputLine;
				while((inputLine = in.readLine()) != null) {
					number = inputLine;
				}

				if(number == null) {
                    System.err.println("ERROR: reading from URL");
				    return;
                }

                exchangeRate = Double.parseDouble(number);
                exchangeRate = exchangeRate * 0.0001;

                System.out.println("Current exchange rate: " + exchangeRate);

			} catch (IOException ioe) {
			    System.err.println("ERROR: reading from URL");
			    ioe.printStackTrace(System.err);
			    return;
			}
		} catch (MalformedURLException e) {
            System.err.println("ERROR: reading from URL " + e.getMessage());
			e.printStackTrace();
		}

    	synchronized (curUser.getWallet()) {
            if (curUser.getWallet().size() == 0) {
                walletJson.addProperty("result", -1);
                walletJson.addProperty("result-msg", "Your wallet is empty");
                key.attach(walletJson.toString());
                return;
            }

            for (WinTransaction transaction : curUser.getWallet()) {
                String t = (transaction.getValue() * exchangeRate) + "/" + transaction.getTimestamp().toString();
                transactionList.add(t);
                walletTotBitcoin += (transaction.getValue() * exchangeRate);
            }
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
