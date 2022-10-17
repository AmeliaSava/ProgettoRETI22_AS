import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class WinUser {

    //TODO private UUID idUser;
    private String username;
    private String password;
    private List<String> tagList;
    
    private List<String> followedUsers;
    private List<String> followers;
    private List<UUID> blog;
    private List<UUID> feed;
    
    private List<WinTransaction> wallet;
    private double walletTot;

    public WinUser(String username, String password, List<String> tagList) {
        //this.idUser = UUID.randomUUID(); //trovare un modo per id univoco
        this.username = username;
        this.password = password;
        this.tagList = tagList;
        this.followedUsers = new ArrayList<String>();
        this.followers = new ArrayList<String>();
        this.blog = new ArrayList<UUID>();
        this.feed = new ArrayList<UUID>();
        this.wallet = new ArrayList<WinTransaction>();
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

    public List<String> getfollowedUsers() {
        return followedUsers;
    }

    public List<String> getfollowers() {
        return followers;
    }

    public List<UUID> getFeed() { return feed; }

    public List<UUID> getBlog() { return blog; }
    
    public List<WinTransaction> getWallet() { return wallet; }
    
    public double getWalletTot() { return walletTot; }

    public int followUser(String username) {
        if(followedUsers.contains(username)) return -1;
        followedUsers.add(username);
        return 0;
    }

    public int unfollowUser(String username) {
        if(!(followedUsers.contains(username))) return -1;
        followedUsers.remove(username);
        return 0;
    }

    public void addFollower(String username) {
        if(followers.contains(username)) return;
        followers.add(username);
    }

    public void removeFollower(String username) {
        if(!(followers.contains(username))) return;
        followers.remove(username);
    }

    public void updateBlog(UUID idPost) {
    	blog.add(idPost);
    }

    public void removeBlog(UUID idPost) {
    	blog.remove(idPost); 
    }
    
    public void updateFeed(UUID idPost) {
    	feed.add(idPost);
    }
    
    public void removeFeed(UUID idPost) {
    	feed.remove(idPost);
    }
    
    public void updateWallet(double value) {
    	
    	WinTransaction newT = new WinTransaction(value);
    	
    	wallet.add(newT);
    	
    	walletTot += value;
    }
    
}
