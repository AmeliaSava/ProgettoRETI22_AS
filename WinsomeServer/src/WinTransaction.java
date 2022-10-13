import java.util.Date;

public class WinTransaction {
	
	private double value;
	private Date timestamp;
	
	public WinTransaction(double value) {
		this.value = value;
		this.timestamp = new Date();
	}
	
	public double getValue() { return value; }
	
	public Date getTimestamp() { return timestamp; }
}
