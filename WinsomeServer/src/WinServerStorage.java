import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Classe che mantiene i dati relativi agli utenti e ai post del social network
 */
public class WinServerStorage {
    // Gli utenti iscritti
    private ConcurrentHashMap<String, WinUser> userMap;
    // I post
    private ConcurrentHashMap<UUID, WinPost> postMap;
    // Gli utenti online
    private ConcurrentHashMap<String, WinUser> onlineUsers;

    public WinServerStorage() {
        this.userMap = new ConcurrentHashMap<>();
        this.postMap = new ConcurrentHashMap<>();
        this.onlineUsers = new ConcurrentHashMap<>();
    }
    // getters
    public ConcurrentHashMap<String, WinUser> getUserMap() { return userMap; }
    public ConcurrentHashMap<UUID, WinPost> getPostMap() { return postMap; }
    public ConcurrentHashMap<String, WinUser> getOnlineUsers() { return onlineUsers; }

    // setters
    public void setUserMap(ConcurrentHashMap<String, WinUser> userMap) { this.userMap = userMap; }
    public void setPostMap(ConcurrentHashMap<UUID, WinPost> postMap) { this.postMap = postMap; }

    // Metodi per interagire con gli utenti
    /**
     * Retiruisce l'utente richiesto
     * @param username l'utente che si vuole reperire
     * @return l'utente
     */
    public WinUser getUser(String username) {
    	return userMap.get(username);
    }
    /**
     * Ritorna una collezione di tutti gli utenti
     * @return la collezione di tutti gli utenti
     */
    public Collection<WinUser> getAllUsers() {
    	return userMap.values();
    }
    /**
     * Controlla se un utente e' registrato
     * @param username l'utente da controllare
     * @return true se e' registrato false altrimenti
     */
    public boolean userIsRegistred(String username) { return userMap.containsKey(username); }
    /**
     * Controlla se un utente e' online
     * @param username l'utente da controllare
     * @return true se e' online false altrimenti
     */
    public boolean userIsOnline(String username) { return onlineUsers.containsKey(username); }
    /**
     * Aggiunge un nuovo utente a quelli registrati
     * @param username Il nome dell'utente da aggiungere
     * @param user L'utente da aggiungere
     */
    public void addNewUser(String username, WinUser user) { userMap.put(username, user); }
    /**
     * Aggiunge l'utente tra gli utenti online se non era gia' presente
     * @param username Il nome dell'utente da aggiungere
     * @param user L'utente
     * @return Il valore associato alla chiave se era gia' assegnata, null altrimenti
     */
    public WinUser addOnlineUser(String username, WinUser user) { return onlineUsers.putIfAbsent(username, user); }
    /**
     * Rimuove un utente dagli utenti online
     * @param username
     */
    public void removeOnlineUser(String username) { onlineUsers.remove(username); }

    // Metodi per interagire con i post
    /**
     * Prende un post
     * @param idPost Il post da prendere
     * @return Il post
     */
    public WinPost getPost(UUID idPost) {
        return postMap.get(idPost);
    }
    /**
     * Aggiunge un nuovo post
     * @param post il post da aggiungere
     */
    public void addNewPost(WinPost post) { postMap.put(post.getIdPost(), post); }
    /**
     * Rimuove un post
     * @param postID Il post da rimuovere
     */
    public void removePost(UUID postID) { postMap.remove(postID); }
}
