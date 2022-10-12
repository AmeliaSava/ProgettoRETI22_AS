import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

public class WinPost {

    private UUID idPost;
    private String postAuthor;
    private String postContent;
    private String postTitle;
    
    private boolean feed;
    private List<String> rewins;

    private List<WinComment> comments;
    private List<WinRate> ratings;
    
    private int upvoteCount;
    private int downvoteCount;

    public WinPost(String postAuthor, String postTitle, String postContent){
        this.idPost = UUID.randomUUID();
        this.postAuthor = postAuthor;
        this.postContent = postContent;
        this.postTitle = postTitle;
        
        this.feed = false;
        this.rewins = new ArrayList<String>();
        
        this.comments = new ArrayList<WinComment>();
        this.ratings = new ArrayList<WinRate>();
        
        this.upvoteCount = 0;
        this.downvoteCount = 0;
    }

    public UUID getIdPost() { return idPost; }

    public String getPostTitle() { return postTitle; }

    public String getPostContent() { return  postContent; }

    public String getPostAuthor() { return postAuthor; }
    
    public boolean getFeed() { return feed; }
    
    public int getUpvoteCount() { return upvoteCount; }
    
    public int getDownvoteCount() { return downvoteCount; }
    
    public List<WinRate> getRatings() { return ratings; }
    
    public List<WinComment> getComments() { return comments; }
    
    public List<String> getRewins() { return rewins; }
    
    public void isFeed() {
    	feed = true;
    }
    
    public void resetFeed() {
    	feed = false;
    }
    
    public void addRewin(String user) {
    	rewins.add(user);
    }
      
    public int addRate(String uservoting, int vote) {
    	  
    	WinRate curRate = new WinRate(uservoting, vote);
    	
    	// Scorro la lista dei voti per vedere se l'utente ha gia' votato nello stesso modo
    	if(ratings.size() > 0) {
    		Iterator<WinRate> iter = ratings.iterator();
    		while(iter.hasNext()) {
    		  WinRate rate = iter.next();
    		  if(rate.getUserrating().equals(uservoting) && rate.getRate() == vote) return -1;
    		  if(rate.getUserrating().equals(uservoting) && rate.getRate() != vote) {
    			  iter.remove();
    			  if (vote == 1) downvoteCount--;
    			  else upvoteCount--;
    		  }
    		}  			    	
    	}
    	
    	// Altrimenti aggiungo il voto alla lista
    	ratings.add(curRate);
    	
    	if(vote == 1) upvoteCount++;
    	else downvoteCount++;
    	
    	System.out.println("post rated by" + ratings.get(0).getUserrating());
    	return 0;
    }
    
    public void addComment(String commentAuthor, String comment) {
    	
    	WinComment curComment = new WinComment(commentAuthor, comment);
    	
    	comments.add(curComment);
    	
    	System.out.println("Comment added by " + curComment.getAuthor() + " on " + curComment.getTimestamp() + ": " + curComment.getComment());
    }
}
