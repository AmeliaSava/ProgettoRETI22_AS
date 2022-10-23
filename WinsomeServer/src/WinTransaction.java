import java.util.Date;

/**
 * Rappresenta una trasazione di wincoins, e' caratterizzata dal suo valore e un timestamp preso nel momento
 * in cui viene creata
 */
public class WinTransaction {
	// Il valore della transazione
	private double value;
	// Momento in cui la transazione viene creata
	private Date timestamp;
	public WinTransaction(double value) {
		this.value = value;
		this.timestamp = new Date();
	}
	// getters
	public double getValue() { return value; }
	public Date getTimestamp() { return timestamp; }
}
