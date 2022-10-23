import java.util.*;

public class WinPost {

    private final UUID idPost;
    private final String postAuthor;
    private final String postContent;
    private final String postTitle;

    private Vector<String> rewins;

    private Vector<WinComment> comments;
    private Vector<WinRate> ratings;
    
    private int upvoteCount;
    private int downvoteCount;

    private int Niter;

    public WinPost(String postAuthor, String postTitle, String postContent){
        this.idPost = UUID.randomUUID();
        this.postAuthor = postAuthor;
        this.postContent = postContent;
        this.postTitle = postTitle;

        this.rewins = new Vector<String>();
        
        this.comments = new Vector<WinComment>();
        this.ratings = new Vector<WinRate>();
        
        this.upvoteCount = 0;
        this.downvoteCount = 0;

        this.Niter = 1;
    }

    public UUID getIdPost() { return idPost; }

    public String getPostTitle() { return postTitle; }

    public String getPostContent() { return  postContent; }

    public String getPostAuthor() { return postAuthor; }

    public int getUpvoteCount() { return upvoteCount; }
    
    public int getDownvoteCount() { return downvoteCount; }

    public int getNiter() {
        return Niter;
    }

    public Vector<WinRate> getRatings() { return ratings; }
    
    public Vector<WinComment> getComments() { return comments; }
    
    public Vector<String> getRewins() { return rewins; }

    public int addRewin(String user) {
    	if (rewins.contains(user)) return -1;
    	else {
    	    rewins.add(user);
    	    return 0;
        }
    }
      
    public synchronized int addRate(String uservoting, int vote) {
    	  
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

    	return 0;
    }
    
    public synchronized void addComment(String commentAuthor, String comment) {
    	
    	WinComment curComment = new WinComment(commentAuthor, comment);
    	
    	comments.add(curComment);
    	
    	System.out.println("Comment added by " + curComment.getAuthor() + " on " + curComment.getTimestamp() + ": " + curComment.getComment());
    }

    public void iterInc() { Niter++;}
    
}
