import java.util.Date;

/**
 * Rappresenta il voto lasciato da un utente ad un post. E' caratterizzata da il valore del voto(+1, -1), il timestamp
 * legato al momento in cui e' stato creato il voto e l'utente che ha lasciato il voto.
 */
public class WinRate {
	// Il valore del voto
	private int rate;
	// Momento in cui e' stato creato il post
	private Date timestamp;
	// Utente che ha lasciato il voto
	private String userrating;
	
	public WinRate(String userrating, int vote) {
		this.userrating = userrating;
		this.rate = vote;
		this.timestamp = new Date();
	}
	// getters
	public String getUserrating() { return userrating; }
	public int getRate() { return rate; }
	public Date getTimestamp() { return timestamp; }
}
