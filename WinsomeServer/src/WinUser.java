import java.util.List;
import java.util.UUID;

public class WinUser {

    private UUID idUser;
    private String username;
    private String password;
    //taglist immutabile
    private List<String> tagList;
    private int wallet;
    //private blog;
    //flag online per controllare se l'utente è online?
    public WinUser(String username, String password, List<String> tagList) {
        this.idUser = UUID.randomUUID(); //trovare un modo per id univoco
        this.username = username;
        this.password = password;
        this.tagList = tagList;
        this.wallet = 0;
        //this.blog;
    }
    
    public String getUsername() {
    	return username;
    }
    
    public String getPassword() {
    	return password;
    }

}
