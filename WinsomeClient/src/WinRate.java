import java.util.Date;

public class WinRate {

	private int rate;
	private Date timestamp;
	private String userrating;
	
	public WinRate(String userrating, int vote) {
		this.userrating = userrating;
		this.rate = vote;
		this.timestamp = new Date();
	}
	
	public String getUserrating() { return userrating; }
	
	public int getRate() { return rate; }
	
	public Date getTimestamp() { return timestamp; }
}
