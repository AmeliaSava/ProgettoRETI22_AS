import java.util.Collection;
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

    // Getters
    public ConcurrentHashMap<String, WinUser> getUserMap() { return userMap; }

    public ConcurrentHashMap<UUID, WinPost> getPostMap() { return postMap; }

    public ConcurrentHashMap<String, WinUser> getOnlineUsers() { return onlineUsers; }

    // Setters

    public void setUserMap(ConcurrentHashMap<String, WinUser> userMap) { this.userMap = userMap; }

    public void setPostMap(ConcurrentHashMap<UUID, WinPost> postMap) { this.postMap = postMap; }

    // Metodi per interaire con gli utenti

    public WinUser getUser(String username) {
    	return userMap.get(username);
    }
    
    public Collection<WinUser> getAllUsers() {
    	return userMap.values();
    }

    public boolean userIsRegistred(String username) { return userMap.containsKey(username); }

    public boolean userIsOnline(String username) { return onlineUsers.containsKey(username); }

    public void addNewUser(String username, WinUser user) { userMap.put(username, user); }

    /*
    * Aggiunge l'utente tra gli utenti online se non era gia' presente
     */
    public WinUser addOnlineUser(String username, WinUser user) { return onlineUsers.putIfAbsent(username, user); }

    public void removeOnlineUser(String username) { onlineUsers.remove(username); }

    // Metodi per interagire con i post
    
    public WinPost getPost(UUID idPost) {
        return postMap.get(idPost);
    }

    public void addNewPost(WinPost post) { postMap.put(post.getIdPost(), post); }
    
    public void removePost(UUID postID) { postMap.remove(postID); }
}
