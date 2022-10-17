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

    public ConcurrentHashMap<String, WinUser> getUserMap() { return userMap; }

    public ConcurrentHashMap<UUID, WinPost> getPostMap() { return postMap; }

    public ConcurrentHashMap<String, WinUser> getOnlineUsers() { return onlineUsers; }

    public void setUserMap(ConcurrentHashMap<String, WinUser> userMap) { this.userMap = userMap; }

    public void setPostMap(ConcurrentHashMap<UUID, WinPost> postMap) { this.postMap = postMap; }

    public WinUser getUser(String username) {
    	return userMap.get(username);
    }
    
    public Collection<WinUser> getAllUsers() {
    	return userMap.values();
    }
    
    public WinPost getPost(UUID idPost) {
        return postMap.get(idPost);
    }
    
    public boolean userIsRegistred(String username) {
    	
    	if(userMap.containsKey(username)) return true;
        else return false;
    	
    }
    
    public boolean userIsOnline(String username) {
    	if(onlineUsers.containsKey(username)) return true;
    	else return false;
    }
    
    public void addNewUser(String username, WinUser user) {
    	userMap.put(username, user);
    }
    
    public void addNewPost(WinPost post) {
    	postMap.put(post.getIdPost(), post);
    }
    
    public void removePost(UUID postID) {
    	postMap.remove(postID);
    }
    
    public void addOnlineUser(String username, WinUser user) {
    	onlineUsers.put(username, user);
    }

    public void removeOnlineUser(String username) {
    	//TODO fare qualcosa con la return?
    	onlineUsers.remove(username);
    }
    
}
