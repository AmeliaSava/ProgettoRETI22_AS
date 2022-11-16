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

/**
 * Classe che implementa il worker che esegue le operazioni richieste dai client al server.
 * Parsa la richiesta del client e poi prepara una risposta che allega alla chiave che gli viene passata
 * dal server. Interagisce con lo storage per interrogarlo sullo stato degli utenti e dei post e apporta le
 * modifiche richieste dal client, verificandone la correttezza.
 */
public class WinServerWorker implements Runnable {

    // Informazioni sull'operazione da eseguire
    private final String operation;
    // La chiave associata al canale in cui verra' inserito l'esito dell'operazione
    private final SelectionKey keyWorker;
    // Implementazione dei metodi dell'interfaccia remota
    private final NotificationServiceServerImpl followersRMI;
    // Porta e indirizzo per la comunicazione multicast da comunicare al client al momento del login
    private final String multicastAddress;
    private final int UDPport;
    // Lo storage contenente tutti i dati riguardanti utenti, post e le loro relazioni
    private WinServerStorage serverStorage;

    public WinServerWorker(String operation, SelectionKey key, WinServerStorage serverStorage,
                           NotificationServiceServerImpl followersRMI, String multicastAddress, int UDPport) {
        this.operation = operation;
        this.keyWorker = key;
        this.serverStorage = serverStorage;
        this.followersRMI = followersRMI;
        this.multicastAddress = multicastAddress;
        this.UDPport = UDPport;
    }
    // getters
    public SelectionKey getKeyWorker() { return keyWorker; }

    @Override
    public void run() {
        // Converto il messaggio ricevuto dal client
        JsonObject message = new Gson().fromJson(operation, JsonObject.class);

        // A seconda dell'operazione richiesta chiamo la funzione apporopriata
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
                int r = followUser(message.get("user-to-follow").getAsString(),
                        message.get("user").getAsString(), keyWorker);
                // Se l'utente ne ha seguito un altro
                if((r == 0) && serverStorage.getOnlineUsers().containsKey(message.get("user-to-follow").getAsString())) {
                    // Notifico quell'utente tramite RMI, solo se e' online
                    try {
                        followersRMI.follow(message.get("user-to-follow").getAsString(),
                                message.get("user").getAsString());
                    } catch (RemoteException e) {
                        System.err.println("ERROR: RMI callback " + e.getMessage());
                        e.printStackTrace();
                    }
                }
                break;
            case "unfollow":
                r = unfollowUser(message.get("user-to-unfollow").getAsString(),
                        message.get("user").getAsString(), keyWorker);
                // Se l'utente ha smesso di seguirne un altro
                if((r == 0) && serverStorage.getOnlineUsers().containsKey(message.get("user-to-unfollow").getAsString())) {
                    // Notifico quell'utente tramite RMI, so se e' online
                    try {
                        followersRMI.unfollow(message.get("user-to-unfollow").getAsString(),
                                message.get("user").getAsString());
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
                createPost(message.get("user").getAsString(), message.get("title").getAsString(),
                        message.get("content").getAsString(), keyWorker);
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
            	ratePost(message.get("user").getAsString(), message.get("post-id").getAsString(),
                        message.get("rate").getAsInt(), keyWorker);
            	break;
            case "comment":
            	addComment(message.get("user").getAsString(), message.get("post-id").getAsString(),
                        message.get("comment").getAsString(), keyWorker);
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

    /**
     * Effettua i controlli per verificare che l'utente possa effettuare il login e poi procede a salvarlo
     * tra gli utenti online.
     * @param username Lo username dell'utente che vuole effettuare il login
     * @param password La password inserita dall'utente
     * @param key La chiave dove viene attaccato il messaggio con l'esito
     */
    private void loginUser(String username, String password, SelectionKey key) {

        System.out.println(username + " wants to login with psw " + password);
        // Cerco l'utente tra quelli registrati
        WinUser curUser = serverStorage.getUser(username);
        // L'oggetto dove salvo le informazioni sulla risposta
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
        // Informazione per il server che aggiungera' l'utente tra quelli attivi
        loginJson.addProperty("login-ok", 0);
        loginJson.addProperty("user", username);
        // Allego la risposta alla chiave
        key.attach(loginJson.toString());
    }
    
    /**
     * Rimuove l'utente dagli utenti online
     * @param username Lo username dell'utente che vuole effettuare il logout
     * @param key La chiave dove viene attaccato il messaggio con l'esito
     */
    public void logoutUser(String username, SelectionKey key) {
        System.out.println(username + " user is logging out");
        // Preparo l'oggetto dove inserire la risposta
    	JsonObject logoutJson = new JsonObject();
        // Rimuovo l'utente da quelli online
        serverStorage.removeOnlineUser(username);
    	// Preparo il messaggio
    	logoutJson.addProperty("result", 0);
    	logoutJson.addProperty("result-msg", username + " logged out");
        // Informazioni per il server per rimuovere l'utente dalle sessioni attive
        logoutJson.addProperty("logout-ok", 0);
        logoutJson.addProperty("user", username);
        // Allego il messaggio alla chiave
        key.attach(logoutJson.toString());
    }

    /**
     * Trova gli utenti con almeno un tag in comune con l'utente che ha fatto la richiesta e comunica la lista
     * di questi utenti con i loro tag al richiedente.
     * @param username L'utente che vuole ottenere la lista
     * @param key La chiave dove viene attaccato il messaggio con l'esito
     */
    public void listUsers(String username, SelectionKey key) {
        System.out.println("User " + username + " has requested the list of the other users");
        // Ottengo l'utente che ha fatto la richiesta
        WinUser curUser = serverStorage.getUser(username);
        // Lista dove salvo le informazioni da mandare
        List<String> toSend = new ArrayList<>();
        // Oggetto con le informazione del messaggio di risposta
        JsonObject usersJson = new JsonObject();

        // Scorro gli utenti iscritti
        for (WinUser user : serverStorage.getAllUsers()) {
            // Ignoro l'utente corrente
            if(user.getUsername().equals(curUser.getUsername())) continue;
            // Lista dove salvo solo i tag in comune tra i due utenti
            List<String> commonTags = new ArrayList<>(curUser.getTagList());
            commonTags.retainAll(user.getTagList());
            // Se ci sono dei tag in comune aggiungo l'utente e i tag alla lista da inviare
            if(commonTags.size() > 0) {
                String info = user.getUsername();
                for (String tag : commonTags) {
                    info = info.concat("/" + tag);
                    System.out.println("info " + info);
                }
                toSend.add(info);
            }
        }
        // Se ci sono utenti nella lista la invio
        if(toSend.size() > 0) {
        	usersJson.addProperty("result", 0);
        	usersJson.addProperty("result-msg", "We found these users that share your interests:");
        	usersJson.addProperty("users-list", WinUtils.prepareJson(toSend));
        } else {
            // Non ho trovato utenti con tag in comune
        	usersJson.addProperty("result", 0);
        	usersJson.addProperty("result-msg", "There are no users that share your interests.");                       
        }
        // Allego il messeggio alla chiave
        key.attach(usersJson.toString());
    }

     /**
     * Comunica all'utente che lo ha richiesto la lista degli utenti che sta seguendo
     * @param username L'utente che vuole ottenere la lista
     * @param key La chiave dove viene attaccato il messaggio con l'esito
     */
    public void listFollowing(String username, SelectionKey key) {
        System.out.println("User " + username + " has requested following users list");
        // Ottengo l'utente
        WinUser curUser = serverStorage.getUser(username);
        // Preparo il json per la risposta
        JsonObject followingJson = new JsonObject();

        // Prendo la lista degli utenti seguiti
        Vector<String> toSend = new Vector<>(curUser.getfollowedUsers());
        // Preparo la risposta
        // Se seguo almeno un utente
        if(toSend.size() > 0) {
           	followingJson.addProperty("result", 0);
        	followingJson.addProperty("result-msg", "You are following these users:");
        	followingJson.addProperty("following-list", WinUtils.prepareJson(toSend));
        } else { // non seguo nessuno
        	followingJson.addProperty("result", 0);
        	followingJson.addProperty("result-msg", "You are not following any user.");
        }
        // Allego il messaggio alla chiave
        key.attach(followingJson.toString());
    }

    /**
     * Segue un utente per conto di un altro, effettuando dei controlli sulla validita' della richiesta,
     * ed aggiunge al feed dell'utente che segue tutti i post dell'utente seguito e poi ritorna un messaggio
     * con l'esito.
     * @param userFollowed L'utente che viene seguito
     * @param userFollowing L'utente da seguire
     * @param key La chiave dove viene attaccato il messaggio con l'esito
     * @return 0 Se l'operazione e' andata a buon fine -1 Se l'utente che richiede l'operazione sta gia' seguendo l'altro
     */
    public int followUser(String userFollowed, String userFollowing, SelectionKey key) {
        System.out.println(userFollowing + " wants to follow " + userFollowed);
        // Ottengo i due utenti
        WinUser curUser = serverStorage.getUser(userFollowing);
        WinUser folUser = serverStorage.getUser(userFollowed);
        // Preparo il json per il messaggio di risposta
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
                // Inserisco la risposta nel messaggio
                followJson.addProperty("result", 0);
                followJson.addProperty("result-msg", userFollowing + " you are now following " + userFollowed);
                // Allego il messaggio alla chiave
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

    /**
     * Rimuove un utente dalla lista degli utenti seguiti dell'utente che ha richiesto l'operazione, effettuando
     * dei controlli sulla validita' della richiesta. Se e' valida rimuove tutti i post dell'utente dal feed di quello
     * che ha smesso di seguirlo.
     * @param userUnfollowed L'utente rimossso
     * @param userUnfollowing L'utente che smette di seguire
     * @param key La chiave dove viene attaccato il messaggio con l'esito
     * @return 0 Se l'operazione e' andata a buon fine -1 Se l'utente che ha richiesto l'operazione non stava seguendo l'altro
     */
    public int unfollowUser(String userUnfollowed, String userUnfollowing, SelectionKey key) {
        System.out.println(userUnfollowing + " wants to unfollow " + userUnfollowed);
        // Ottengo i due utenti
        WinUser curUser = serverStorage.getUser(userUnfollowing);
        WinUser unfolUser = serverStorage.getUser(userUnfollowed);
        // Preparo il json del messaggio
        JsonObject unfollowJson = new JsonObject();

        // Controllo che l'utente da smettere di seguire esista
        if(unfolUser != null) {
            // Smetto di seguire l'utente, se non lo stavo seguendo ritorna errore
            if((curUser.unfollowUser(userUnfollowed)) == 0) {
                System.out.println(curUser.getUsername() + " unfollowed " + userUnfollowed);
                // Aggiorno la lista dei follower lato server
                unfolUser.removeFollower(userUnfollowing);

                synchronized (curUser.getFeed()) {
                    // Rimuovo i post dal feed
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
                // Preparo il messaggio
                unfollowJson.addProperty("result", 0);
                unfollowJson.addProperty("result-msg", userUnfollowing + " you stopped following " + userUnfollowed);
                // Allego il messaggio alla chiave
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

    /**
     * Recupera i post dal blog di un utente
     * @param currentUser L'utente che vuole vedere il suo blog
     * @param key La chiave dove viene attaccato il messaggio con l'esito
     */
	public void viewBlog(String currentUser, SelectionKey key) {
        System.out.println("User " + currentUser + " has requested for their blog");
        // Recupera l'utente dallo storage
        WinUser curUser = serverStorage.getUser(currentUser);
    	// Prepara il json per la risposta
    	JsonObject blogJson = new JsonObject();
        // Prepara la lista per la risposta
    	List<String> blog = new ArrayList<String>();
    	
        String info = null;
        synchronized (curUser.getBlog()) {
            // Per ogni id del blog recupero il post associato
            for (UUID idPost : curUser.getBlog()) {
                WinPost curPost = serverStorage.getPost(idPost);
                // Se il post non esiste lo marco come cancellato
                if (curPost == null) {
                    info = "[deleted]/[deleted]/[deleted]";
                    blog.add(info);
                    continue;
                }
                // Lo aggiungo alla lista da inviare
                info = curPost.getIdPost().toString();
                info = info.concat("/" + curPost.getPostAuthor() + "/" + curPost.getPostTitle());
                blog.add(info);
            }
        }
        // Preparo la risposta
        if(blog.size() > 0) {
          	blogJson.addProperty("result", 0);
        	blogJson.addProperty("result-msg", "Blog: ");
        	blogJson.addProperty("blog", WinUtils.prepareJson(blog));
        } else { // non ci sono post nel blog
        	blogJson.addProperty("result", 0);
        	blogJson.addProperty("result-msg", "Your blog is still empty, make a new post!");
        }
        // Allego il messaggio alla chiave
        key.attach(blogJson.toString());
    }

    /**
     * Crea un nuovo post e lo inserisce nel feed di tutti i follower dell'autore
     * @param author L'autore del post
     * @param title Il titolo del post
     * @param text Il testo del post
     * @param key La chiave dove viene attaccato il messaggio con l'esito
     */
    public void createPost(String author, String title, String text, SelectionKey key) {
        // Recupero l'utente che ha fatto la richiesta
        WinUser curUser = serverStorage.getUser(author);
        // Preparo il json per la risposta
        JsonObject postJson = new JsonObject();
        // Creo un nuovo post e lo inserisco nello storage
        WinPost post = new WinPost(author, title, text);
        serverStorage.addNewPost(post);
        // Aggiungo l'id del post alla lista contenuta nell'utente per poter ricostruire il suo blog
        curUser.addPostToBlog(post.getIdPost());

        // Aggiungo il post al feed di ogni follower
        synchronized (curUser.getfollowers()) {
            for (String user : curUser.getfollowers()) {
                WinUser follower = serverStorage.getUser(user);
                follower.addPostToFeed(post.getIdPost());
            }
        }
        System.out.println(author + " created a new post");
    	// Preparo il messaggio
    	postJson.addProperty("result", 0);
    	postJson.addProperty("result-msg", "You created a new post with ID " + post.getIdPost());
        // Allego il messaggio alla chiave
        key.attach(postJson.toString());
    }

    /**
     * Recupera i post dal feed di un utente
     * @param currentUser L'utente che ha richiesto di vedere il proprio feed
     * @param key La chiave dove viene attaccato il messaggio con l'esito
     */
    public void showFeed(String currentUser, SelectionKey key) {
        System.out.println("User " + currentUser + " has requested for their feed");
        // Recupero l'utente che ha fatto la richiesta
    	WinUser curUser = serverStorage.getUser(currentUser);
    	// Preparo il json per il messaggio
    	JsonObject feedJson = new JsonObject();
    	// Preparo la lista da inviare
    	List<String> feed = new ArrayList<String>();

        String info = null;
        synchronized (curUser.getFeed()) {
            // Dagli id recupero i post associati
            for (UUID idPost : curUser.getFeed()) {
                WinPost curPost = serverStorage.getPost(idPost);
                // Se il post non esiste lo segno come cancellato
                if (curPost == null) {
                    info = "[deleted]/[deleted]/[deleted]";
                    feed.add(info);
                    continue;
                }
                // Inserico le informazioni nella lista
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
        // Allego il messaggio alla chiave
        key.attach(feedJson.toString());
    }

    /**
     * Recupera un post dal suo id
     * @param username L'utente che ha richiesto di vedere il post
     * @param postID L'id del post
     * @param key La chiave dove viene attaccato il messaggio con l'esito
     */
    public void showPost(String username, String postID, SelectionKey key) {
        System.out.println("Showing post " + postID + " for " + username);
        // Preparo il json per la risposta
        JsonObject postJson = new JsonObject();
    	// Converto l'id in UUID
    	UUID CurPostID;
    	try {
    		CurPostID = UUID.fromString(postID); 
    	} catch(IllegalArgumentException ex) {
    		System.err.println("ERROR: Post ID not valid");
        	sendError(postJson, "Post ID not valid", key);
            return;
    	}
    	// Recupero il post
    	WinPost curPost = serverStorage.getPost(CurPostID);
    	// Il post richiesto non esiste
        if (curPost == null) {
            sendError(postJson, "The post you requested doesn't exist", key);
            return;
        }
        // Preparo il messaggio recuperando le informazioni dal post
        synchronized (curPost) {
            postJson.addProperty("result", 0);
            postJson.addProperty("result-msg", "Post:");
            postJson.addProperty("title", curPost.getPostTitle());
            postJson.addProperty("content", curPost.getPostContent());
            postJson.addProperty("author", curPost.getPostAuthor());
            postJson.addProperty("id", curPost.getIdPost().toString());
            postJson.addProperty("upvote", curPost.getUpvoteCount());
            postJson.addProperty("downvote", curPost.getDownvoteCount());
            // Preparo la lista dei commenti
            List<String> comments = new ArrayList<String>();
            String info;
            for (WinComment comment : curPost.getComments()) {
                info = comment.getComment() + " by " + comment.getAuthor() + " " + comment.getTimestamp();
                comments.add(info);
            }
            postJson.addProperty("comments", WinUtils.prepareJson(comments));
        }
        // Allego il messaggio alla chiave
    	key.attach(postJson.toString());
    }

    /**
     * Cancella un post dalla memoria, dal blog del suo autore e dal feed di tutti i suoi follower, elimina anche tutti
     * i rewin. Se invece il post e' un rewin lo elimina semplicemente dal blog di chi ha fatto il rewin.
     * Un utente puo' cancellare un post solo se ne e' l'autore.
     * @param username L'utente che richiede la cancellazione
     * @param postID L'id del post da cancellare
     * @param key La chiave dove viene attaccato il messaggio con l'esito
     */
    public void deletePost(String username, String postID, SelectionKey key) {
        System.out.println("Deleting post " + postID + " for " + username);
        // Preparo il json per il messaggio
        JsonObject deleteJson = new JsonObject();
    	// Controllo che l'id sia valido
    	UUID curPostID;
    	try {
    		curPostID = UUID.fromString(postID);
    	} catch(IllegalArgumentException ex) {
    		System.err.println("ERROR: Post ID not valid");
        	sendError(deleteJson, "Post ID not valid", key);
            return;
    	}
    	// Recupero il post e l'utente
    	WinPost curPost = serverStorage.getPost(curPostID);
    	WinUser curUser = serverStorage.getUser(username);
        // Il post non esiste
    	if(curPost == null) {
    		sendError(deleteJson, "The post you are trying to delete doesn't exist", key);
    		return;
    	}

    	synchronized (curPost) {
            // Se l'utente non e' autore del post
            if (!curPost.getPostAuthor().equals(username)) {
                // Ma ha il post nel suo blog, allora l'utente vuole cancellare un rewin
                synchronized (curUser.getBlog()) {
                    if (curUser.getBlog().contains(curPostID)) {
                        // Cancello il post solo dal blog dell'utente e mando la risposta
                        curUser.removePostBlog(curPostID);
                        curPost.removeRewin(username);
                        // Rimuovo il rewin dal feed di tutti follower
                        synchronized (curUser.getfollowers()) {
                            for (String user : curUser.getfollowers()) {
                                synchronized (serverStorage.getUser(user).getFeed()) {
                                    serverStorage.getUser(user).getFeed().remove(curPostID);
                                }
                            }
                        }
                        deleteJson.addProperty("result", 0);
                        deleteJson.addProperty("result-msg", "Your rewin was successfully deleted!");
                        key.attach(deleteJson.toString());
                        return;
                    }
                }
                sendError(deleteJson, "You are not the author of this post, you cannot delete it", key);
                return;
            }

            // Rimuovo tutti i rewin
            for (String user : curPost.getRewins()) {
                serverStorage.getUser(user).removePostBlog(curPostID);
            }
            // Rimuovo il post dal blog dell'utente e dallo storage
            synchronized (curUser.getBlog()) {
                curUser.getBlog().remove(curPostID);
                serverStorage.removePost(curPostID);
            }
        }
    	// Rimuovo il post dal feed di tutti i suoi followers
        synchronized (curUser.getfollowers()) {
            for (String user : curUser.getfollowers()) {
                synchronized (serverStorage.getUser(user).getFeed()) {
                    serverStorage.getUser(user).getFeed().remove(curPostID);
                }
            }
        }
    	deleteJson.addProperty("result", 0);  
    	deleteJson.addProperty("result-msg", "The post was successfully deleted!");
        // Allega la risposta alla chiave
        key.attach(deleteJson.toString());
    }

    /**
     * Effettua il rewin di un post aggiungendone l'id al blog di un utente, e' possibile effettuare il rewin di un
     * post solo se l'utente ce l'ha nel proprio feed
     * @param username L'utente che vuole fare il rewin
     * @param postID l'id del post
     * @param key La chiave dove viene attaccato il messaggio con l'esito
     */
    public void rewinPost(String username, String postID, SelectionKey key) {
        System.out.println("Rewin post " + postID + " for " + username);
        // Preparo il json per la risposta
    	JsonObject rewinJson = new JsonObject();
        // Controllo se l'id e' valido
    	UUID CurPostID;
    	try {
    		CurPostID = UUID.fromString(postID); 
    	} catch(IllegalArgumentException ex) {
    		System.err.println("ERROR: Post ID not valid");
        	sendError(rewinJson, "Post ID not valid", key);
            return;
    	}
    	// Recupero il post e l'utente
    	WinPost curPost = serverStorage.getPost(CurPostID);    	
    	WinUser curUser = serverStorage.getUser(username);
    	// Il post non esiste
    	if(curPost == null) {
    		sendError(rewinJson, "The post you tried to rewin does not exist", key);
    		return;
    	}

    	synchronized (curUser.getFeed()) {
            // Controllo se l'utente ha il post nel suo feed
            if (!curUser.getFeed().contains(CurPostID)) {
                sendError(rewinJson, "The post you tried to rewin is not in your feed, you can only rewin post that are in your feed.", key);
                return;
            }
        }

        // Aggiungo l'utente alla lista dei rewin, se ha gia' rewinnato il post mando un errore
        if(curPost.addRewin(username) != 0) {
            sendError(rewinJson, "You already rewinned this post", key);
            return;
        }

        // Aggiungo l'ID del post al blog dell'utente
        curUser.addPostToBlog(CurPostID);

    	rewinJson.addProperty("result", 0);
    	rewinJson.addProperty("result-msg", "You rewinned the post");
        // Allego la risposta alla chiave
        key.attach(rewinJson.toString());
    }

    /**
     * Aggiunge un voto ad un post. Un utente puo' votare un post solo una volta con lo stesso valore e solo
     * se ha il post nel suo feed
     * @param uservoting L'utente che lascia il voto
     * @param postID L'id del post
     * @param vote Il valore del voto
     * @param key La chiave dove viene attaccato il messaggio con l'esito
     */
    public void ratePost(String uservoting, String postID, int vote, SelectionKey key) {
        System.out.println("Rating post " + postID + " " + vote + " for " + uservoting);
        // Preparo il json per mandare la risposta
        JsonObject rateJson = new JsonObject();
    	// Controllo se l'id del post e' valido
    	UUID CurPostID;
    	try {
    		CurPostID = UUID.fromString(postID); 
    	} catch(IllegalArgumentException ex) {
    		System.err.println("ERROR: Post ID not valid");
        	sendError(rateJson, "Post ID not valid", key);
            return;
    	}
    	// Recupro il post e l'utente
    	WinPost postToRate = serverStorage.getPost(CurPostID);
    	WinUser curUser = serverStorage.getUser(uservoting);
    	// Il post non esiste
    	if(postToRate == null) {
    		sendError(rateJson, "The post you tried to rate does not exist", key);
            return;
    	}
        // Controllo se l'utente ha il post nel suo feed
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
        // Allego la risposta alla chiava
        key.attach(rateJson.toString());	
    }

    /**
     * Aggiunge un commento al post. Un utente puo' lasciare un commento solo se non e' l'autore del post e se ha il
     * post nel suo feed
     * @param usercommenting L'utente che commenta
     * @param postID L'id del post
     * @param comment Il testo del commento
     * @param key La chiave dove viene attaccato il messaggio con l'esito
     */
    public void addComment(String usercommenting, String postID, String comment, SelectionKey key) {
        // Preparo il json per la risposta
    	JsonObject commentJson = new JsonObject();
    	// Controllo se l'id e' valido
    	UUID CurPostID;
    	try {
    		CurPostID = UUID.fromString(postID); 
    	} catch(IllegalArgumentException ex) {
    		System.err.println("ERROR: Post ID not valid");
        	sendError(commentJson, "Post ID not valid", key);
            return;
    	}
    	// Recupero il post e l'utente
    	WinPost postToComment = serverStorage.getPost(CurPostID);
    	WinUser curUser = serverStorage.getUser(usercommenting);
        // Il post non esiste
    	if(postToComment == null) {
    		sendError(commentJson, "The post you tried to comment does not exist", key);
    		return;
    	}
        // Controllo che il post sia nel feed dell'utente che vuole lasciare il commento
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
            // Aggiungo il commento al post
            postToComment.addComment(usercommenting, comment);
        }

    	commentJson.addProperty("result", 0);  
    	commentJson.addProperty("result-msg", "Your comment was added to the post!");
        // Allego la risposta alla chiave
        key.attach(commentJson.toString());
    }

    /**
     * Recupare le informazioni sul portafoglio dell'utente
     * @param username L'utente che ha fatto la richiesta
     * @param key La chiave dove viene attaccato il messaggio con l'esito
     */
    public void getWallet(String username, SelectionKey key) {
        // Preparo il json per la risposta
    	JsonObject walletJson = new JsonObject();
    	// Recupero l'utente
    	WinUser curUser = serverStorage.getUser(username);
    	// Preparo la lista da inviare
    	List<String> transactionList = new ArrayList<String>();
        // Recupero le informazioni dal portafoglio
    	synchronized (curUser.getWallet()) {
            // Il portafoglio e' vuoto
            if (curUser.getWallet().size() == 0) {
                walletJson.addProperty("result", 0);
                walletJson.addProperty("result-msg", "Your wallet is empty");
                key.attach(walletJson.toString());
                return;
            }
            // Salvo le trasazioni
            for (WinTransaction transaction : curUser.getWallet()) {
                String t = transaction.getValue() + "/" + transaction.getTimestamp().toString();
                transactionList.add(t);
            }
            // Salvo il totale
            walletJson.addProperty("wallet-tot", curUser.getWalletTot());
        }
       	walletJson.addProperty("result", 0);
		walletJson.addProperty("result-msg", "WALLET");
    	walletJson.addProperty("transaction-list", WinUtils.prepareJson(transactionList));
    	// Allego il messaggio alla chiave
        key.attach(walletJson.toString());
    }

    /**
     * Recupera le informazioni sul portafoglio dell'utente applicando un tasso di cambio generato casualmente tramite
     * URL per simulare il cambio in bitcoin
     * @param username L'utente che ha fatto la richiesta
     * @param key La chiave dove viene attaccato il messaggio con l'esito
     * @throws MalformedURLException
     * @throws IOException
     */
    public void getWalletBitcoin(String username, SelectionKey key) {
        // Preparo il json per la risposta
    	JsonObject walletJson = new JsonObject();
    	// Recupero l'utente
    	WinUser curUser = serverStorage.getUser(username);
        // La lista dove salvo le informazioni
    	List<String> transactionList = new ArrayList<String>();
    	// Informazioni per il tasso di cambio e il totale del portafoglio
    	double exchangeRate = 0;
    	double walletTotBitcoin = 0;

    	try {
            // Mi connetto all'URL
			URL url = new URL("https://www.random.org/integers/?num=1&min=1&max=2000&col=1&base=10&format=plain&rnd=new");
            String number = null;
            // Leggo il valore
			try (BufferedReader in = new BufferedReader(new InputStreamReader(url.openStream(), StandardCharsets.UTF_8))) {
				String inputLine;
				while((inputLine = in.readLine()) != null) {
					number = inputLine;
				}
                // Se non ho letto niente c'e' stato un errore
				if(number == null) {
                    System.err.println("ERROR: reading from URL");
				    return;
                }
                // Applico il tasso di cambio
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
            // Se il portafoglio e' vuoto
            if (curUser.getWallet().size() == 0) {
                walletJson.addProperty("result", -1);
                walletJson.addProperty("result-msg", "Your wallet is empty");
                key.attach(walletJson.toString());
                return;
            }
            // Altrimenti mando i valori convertiti con il tasso di cambio
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
        // Allego il messaggio alla chiave
    	key.attach(walletJson.toString());
    }

    /**
     * Prepara un messaggio di errore e lo allega alla chiave
     * @param jsonObj
     * @param errorMsg
     * @param key
     */
    public void sendError(JsonObject jsonObj, String errorMsg, SelectionKey key) {
    	jsonObj.addProperty("result", -1);
    	jsonObj.addProperty("result-msg", errorMsg);
        key.attach(jsonObj.toString());
    }
}
