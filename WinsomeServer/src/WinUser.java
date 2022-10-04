import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class WinUser {

    private UUID idUser;
    private String username;
    private String password;
    private List<String> tagList;
    private List<String> followedUsers;
    private int wallet;
    //private blog;
    //flag online per controllare se l'utente è online?
    public WinUser(String username, String password, List<String> tagList) {
        this.idUser = UUID.randomUUID(); //trovare un modo per id univoco
        this.username = username;
        this.password = password;
        this.tagList = tagList;
        this.followedUsers = new ArrayList<String>();
        this.wallet = 0;
        //this.blog;
    }
    
    public String getUsername() {
    	return username;
    }
    
    public String getPassword() {
    	return password;
    }
    
    public List<String> getfollowedUsers() {
    	return followedUsers;
    }
    
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

}
