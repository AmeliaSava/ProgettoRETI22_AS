import java.util.Date;

public class WinComment {
	
	private String author;
	private String comment;
	private Date timestamp;
	
	public WinComment(String author, String comment) {
		this.author = author;
		this.comment = comment;
		this.timestamp = new Date();
	}
	
	public String getAuthor() { return author; }
	public String getComment() { return comment; }
	public Date getTimestamp() { return timestamp; }

}
