import java.util.List;
import java.util.UUID;
import java.util.Vector;

/**
 * Classe che rappresenta un utente. Contiene le sue credenziali, informazioni sugli utenti che segue
 * e che lo seguono, informazioni per recuperare i post del suo blog e del suo feed.
 * Contiene anche il suo portafoglio.
 */
public class WinUser {
    // Username univoco di ogni utente, funge anche da identificatore
    private final String username;
    // Password dell'utente
    private final String password;
    // Lista dei tag scelti dall'utente
    private final List<String> tagList;

    // Utenti seguiti dall'utente
    private Vector<String> followedUsers;
    // Utenti che seguono l'utente
    private Vector<String> followers;
    // Vettore di id dei post contenuti nel blog dell'utente
    private Vector<UUID> blog;
    // Vettore degli id dei post contenuti nel feed dell'utente
    private Vector<UUID> feed;
    // Portafoglio dell'utente
    private Vector<WinTransaction> wallet;
    // Totale di wincoins contenute nel portafoglio
    private double walletTot;

    public WinUser(String username, String password, List<String> tagList) {
        this.username = username;
        this.password = password;
        this.tagList = tagList;
        this.followedUsers = new Vector<String>();
        this.followers = new Vector<String>();
        this.blog = new Vector<UUID>();
        this.feed = new Vector<UUID>();
        this.wallet = new Vector<WinTransaction>();
        this.walletTot = 0;
    }

    // getters
    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public List<String> getTagList(){ return tagList; }
    public Vector<String> getfollowedUsers() { return followedUsers; }
    public Vector<String> getfollowers() { return followers; }
    public Vector<UUID> getFeed() { return feed; }
    public Vector<UUID> getBlog() { return blog; }
    public Vector<WinTransaction> getWallet() { return wallet; }
    public double getWalletTot() { return walletTot; }

    /**
     * Aggiunge un utente a quelli seguiti dall'utente, se gia' non lo segue, ritorna errore altrimenti
     * @param username Lo username dell'utente da seguire
     * @return 0 Se e' andato a buon fine -1 Se l'utente sta gia' seguendo l'altro utente
     */
    public synchronized int followUser(String username) {
        if(followedUsers.contains(username)) return -1;
        followedUsers.add(username);
        return 0;
    }

    /**
     * Rimuove un utente da quelli seguiti dall'utente, se lo segue, ritorna errore altrimenti
     * @param username Lo username dell'utente da smettere di seguire
     * @return 0 Se e' andato a buon fine -1 Se l'utente non sta seguendo l'altro utente
     */
    public synchronized int unfollowUser(String username) {
        if(!(followedUsers.contains(username))) return -1;
        followedUsers.remove(username);
        return 0;
    }

    /**
     * Aggiunge un utente ai followers dell'utente, se non e' gia' presente
     * @param username l'utente da aggiungere
     */
    public synchronized void addFollower(String username) {
        if(followers.contains(username)) return;
        followers.add(username);
    }

    /**
     * Rimuove un utente dai followers dell'utente, se e' presente
     * @param username l'utente da rimuovere
     */
    public synchronized void removeFollower(String username) {
        if(!(followers.contains(username))) return;
        followers.remove(username);
    }

    /**
     * Aggiunge un post al blog dell'utente
     * @param idPost il post da aggiungere
     */
    public void addPostToBlog(UUID idPost) {
    	blog.add(idPost);
    }

    /**
     * Rimuove un post dal blog dell'utente
     * @param idPost Il post da rimuovere
     */
    public void removePostBlog(UUID idPost) {
    	blog.remove(idPost); 
    }

    /**
     * Aggiunge un post al feed dell'utente
     * @param idPost Il post da aggiungere
     */
    public void addPostToFeed(UUID idPost) { feed.add(idPost); }

    /**
     * Rimuove un post dal feed dell'utente
     * @param idPost il post da rimuovere
     */
    public void removePostFeed(UUID idPost) {
    	feed.remove(idPost);
    }

    /**
     * Aggiunge una transazione al portafoglio dell'utente
     * @param value il valore da attribuire alla transazione
     */
    public synchronized void updateWallet(double value) {
    	// Crea una transazione con un certo valore
    	WinTransaction newT = new WinTransaction(value);
    	// La aggiunge al portafoglio
    	wallet.add(newT);
    	// Aggiorna il valore totale
    	walletTot += value;
    }
}
