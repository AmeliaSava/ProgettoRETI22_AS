import java.util.List;
import java.util.UUID;
import java.util.Vector;

public class WinUser {

    //TODO private UUID idUser;
    private final String username;
    private final String password;
    private final List<String> tagList;
    
    private Vector<String> followedUsers;
    private Vector<String> followers;
    private Vector<UUID> blog;
    private Vector<UUID> feed;
    
    private Vector<WinTransaction> wallet;
    private double walletTot;

    public WinUser(String username, String password, List<String> tagList) {
        //this.idUser = UUID.randomUUID(); //trovare un modo per id univoco
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

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public List<String> getTagList(){
        return tagList;
    }

    public Vector<String> getfollowedUsers() { return followedUsers; }

    public Vector<String> getfollowers() {
        return followers;
    }

    public Vector<UUID> getFeed() { return feed; }

    public Vector<UUID> getBlog() { return blog; }
    
    public Vector<WinTransaction> getWallet() { return wallet; }
    
    public double getWalletTot() { return walletTot; }

    public synchronized int followUser(String username) {
        if(followedUsers.contains(username)) return -1;
        followedUsers.add(username);
        return 0;
    }

    public synchronized int unfollowUser(String username) {
        if(!(followedUsers.contains(username))) return -1;
        followedUsers.remove(username);
        return 0;
    }

    public synchronized void addFollower(String username) {
        if(followers.contains(username)) return;
        followers.add(username);
    }

    public synchronized void removeFollower(String username) {
        if(!(followers.contains(username))) return;
        followers.remove(username);
    }

    public void updateBlog(UUID idPost) {
    	blog.add(idPost);
    }

    public void removeBlog(UUID idPost) {
    	blog.remove(idPost); 
    }
    
    public void addPostToFeed(UUID idPost) { feed.add(idPost); }
    
    public void removeFeed(UUID idPost) {
    	feed.remove(idPost);
    }
    
    public synchronized void updateWallet(double value) {
    	
    	WinTransaction newT = new WinTransaction(value);
    	
    	wallet.add(newT);
    	
    	walletTot += value;
    }
    
}
