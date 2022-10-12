import java.util.Date;

public class WinTransaction {
	
	private int value;
	private Date timestamp;
	
	public WinTransaction(int value) {
		this.value = value;
		this.timestamp = new Date();
	}
	
	public int getValue() { return value; }
	
	public Date getTimestamp() { return timestamp; }
}
