import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Classe che rappresenta un post. Un post e' caratterizzato dal suo id, generato randomicamente in modo da essere univoco,
 * l'autore del post, il titolo e il contenuto. Inoltre contiene una lista di utenti da cui e' stato rewwinato per
 * facilitarne la cancellazione e evitare doppi rewin, la lista dei commenti e dei voti e il numero di volte
 * che e' stato valutato.
 */
public class WinPost {
    // L'identificativo del post
    private final UUID idPost;
    // L'autore
    private final String postAuthor;
    // Il contenuto
    private final String postContent;
    // Il titolo
    private final String postTitle;
    // Gli utenti che hanno rewwinnato il post
    private Vector<String> rewins;
    // Commenti e voti assegnati al post
    private Vector<WinComment> comments;
    private Vector<WinRate> ratings;
    // Totale di voti positivi e negativi per agevolarne la visualizzazione
    private int upvoteCount;
    private int downvoteCount;
    // Numero di volte che e' stato processato dal calcolo delle ricompense
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
    // getters
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

    /**
     * Aggiunge un utente alla lista dei rewin, controllando che non sia gia' presente
     * @param user L'utente che ha fatto il rewin
     * @return 0 se e' andato tutto bene -1 se l'utente ha gia' rewwinato il post
     */
    public int addRewin(String user) {
    	if (rewins.contains(user)) return -1;
    	else {
    	    rewins.add(user);
    	    return 0;
        }
    }

    /**
     * Aggiunge un voto controllando che l'utente non abbia gia' votato con lo stesso voto e modifica le variabili
     * che tengono il conto dei voti
      * @param uservoting utente che vota
     * @param vote valore del voto
     * @return 0 se e' andato tutto bene -1 se l'utente ha gia' votato
     */
    public synchronized int addRate(String uservoting, int vote) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("hh:mm a, EEE M/d/uuuu").withZone(ZoneId.systemDefault());
        String timestamp = formatter.format(Instant.now());
    	// Creo il voto
    	WinRate curRate = new WinRate(uservoting, vote, timestamp);
    	// Scorro la lista dei voti per vedere se l'utente ha gia' votato nello stesso modo
    	if(ratings.size() > 0) {
    		Iterator<WinRate> iter = ratings.iterator();
    		while(iter.hasNext()) {
    		  WinRate rate = iter.next();
              // L'utente ha gia' votato
    		  if(rate.getUserrating().equals(uservoting) && rate.getRate() == vote) return -1;
              // L'utente ha votato in modo diverso, modifico il voto
    		  if(rate.getUserrating().equals(uservoting) && rate.getRate() != vote) {
    			  iter.remove();
    			  if (vote == 1) downvoteCount--;
    			  else upvoteCount--;
    		  }
    		}  			    	
    	}
    	// Altrimenti aggiungo il voto alla lista
    	ratings.add(curRate);
    	// Aggiorno i contatori
    	if(vote == 1) upvoteCount++;
    	else downvoteCount++;
    	return 0;
    }

    /**
     * Aggiungo il commento di un utente
     * @param commentAuthor utente che commenta
     * @param comment il testo del commento
     */
    public synchronized void addComment(String commentAuthor, String comment) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("hh:mm a, EEE M/d/uuuu").withZone(ZoneId.systemDefault());
        String timestamp = formatter.format(Instant.now());
        // Creo il nuovo commento
    	WinComment curComment = new WinComment(commentAuthor, comment, timestamp);
    	// Lo aggiungo
    	comments.add(curComment);
    	System.out.println("Comment added by " + curComment.getAuthor() + " on " + curComment.getTimestamp() + ": " + curComment.getComment());
    }

    /**
     * Incremanta il numero di iterazioni del post
     */
    public void iterInc() { Niter++;}
    
}
