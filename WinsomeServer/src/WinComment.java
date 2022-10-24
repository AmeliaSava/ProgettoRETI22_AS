import java.util.Date;

/**
 * Rappresenta un commento lasciato da un'utente. E' caratterizzata dallo username di chi ha lasciato il commento,
 * il commento stesso e il timestamp acquisito nel momento in cui viene creato il commento, utilizzato per
 * valutarlo durante il calcolo delle ricompense.
 */
public class WinComment {
	// L'utente che ha lasciato il commento
	private String author;
	// Il testo del commento
	private String comment;
	// Il timestamp che indica quando e' stato creato il commento
	private String timestamp;
	
	public WinComment(String author, String comment, String timestamp) {
		this.author = author;
		this.comment = comment;
		this.timestamp = timestamp;
	}

	// getters
	public String getAuthor() { return author; }
	public String getComment() { return comment; }
	public String getTimestamp() { return timestamp; }

}
