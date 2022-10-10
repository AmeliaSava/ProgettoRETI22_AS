import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class WinPost {

    private UUID idPost;
    private String postAuthor;
    private String postContent;
    private String postTitle;
    
    private boolean feed;

    //lista commenti?
    //lista reween?
    //lista utenti che hanno espresso un voto in modo che non possa essere rivotato
    private int upvoteCount;
    private int downvoteCount;

    public WinPost(String postAuthor, String postTitle, String postContent){
        this.idPost = UUID.randomUUID();
        this.postAuthor = postAuthor;
        this.postContent = postContent;
        this.postTitle = postTitle;
        
        this.feed = false;
        
        this.upvoteCount = 0;
        this.downvoteCount = 0;
    }


    public void upvote(){
        this.upvoteCount++;
    }

    public void downvote(){
        this.downvoteCount++;
    }

    public UUID getIdPost() { return idPost; }

    public String getPostTitle() { return postTitle; }

    public String getPostContent() { return  postContent; }

    public String getPostAuthor() { return postAuthor; }
    
    public boolean getFeed() {  return feed; }
    
    public void isFeed() {
    	feed = true;
    }
    
    public void resetFeed() {
    	feed = false;
    }
}
