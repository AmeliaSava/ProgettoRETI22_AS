import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread che calcola periodicamente le ricompense dei post e manda una notifica in multicast ai client connessi
 * informando dell'avvenuto calcolo
 */
public class WinRewardCalculator implements Runnable {
	// Lo storage per reperire i post e gli utenti
    private WinServerStorage serverStorage;
	// Ogni quanto vanno calcolate le ricompense
    private final int time;
	// Indirizzo e porta multicast
    private final String multicastAddress;
    private final int udpPort;
	// Percentuale destinata all'autore
    private final int authorPercentage;
	// Variabile per fermare il ciclo nel thread
    private volatile boolean stop;

    public WinRewardCalculator(WinServerStorage serverStorage, int time, String multicastAddress, int udpPort, int authorPercentage) {
        this.serverStorage = serverStorage;
        this.time = time;
        this.multicastAddress = multicastAddress;
        this.udpPort = udpPort;
        this.authorPercentage = authorPercentage;
        this.stop = false;
    }

    @Override
    public void run() {
        System.out.println("Reward calculator started");

        while(!stop) {
			// Aspetta il tempo prestabilito
			try {
				Thread.sleep(time * 1000);
			} catch (InterruptedException e) {
				break;
			}
			// Calcola le ricompense
			calculateReward();
        	// Invia la notifica
            try (DatagramSocket mc = new DatagramSocket()) {
                InetAddress udpAdd = InetAddress.getByName(multicastAddress);
                byte[] message = "Your wallet has been updated!".getBytes(StandardCharsets.UTF_8);
				System.out.println(new String(message));
                DatagramPacket dp = new DatagramPacket(message, message.length, udpAdd, udpPort);
                mc.send(dp);
				System.out.println("Sent update on wallet status to " + multicastAddress);
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        }
    }

	/**
	 * Effettua il calcolo delle ricompense scorrendo la mappa dei post
	 */
	private void calculateReward() {
    	// Prendo tutti post
    	ConcurrentHashMap<UUID, WinPost> postMap = serverStorage.getPostMap();
    	// Se non ce ne sono mi fermo
    	if(postMap == null) { return; }
    	// L'ultima volta che le ricompense sono state calcolate
    	Instant lastReward = Instant.now().minusSeconds(time);
    	// Scorro i post
    	for(UUID postID : postMap.keySet()) {
    	 	// Prendo il post
			WinPost post = postMap.get(postID);
			// Variabili per il calcolo
			double postRevenue = 0;
    		double commentSum = 0;
			// L'autore del post
    		String postAuthor;
    		// Salvo i nomi di chi ha commentato per contare i commenti della stessa persona
    		List<String> commentAuthors = new ArrayList<String>();
			// Lista dove salvo solo una volta chi ha commentato per non calcolare due volte il commento
			// e per avere la lista finale dei contributori
			List<String> contributors = new ArrayList<String>();

    		synchronized (post) {
    			postAuthor = post.getPostAuthor();
				System.out.println("Evaluating post " + post.getIdPost() + " post was evaluated " + post.getNiter() + " times");
    			// Scorro il vettore dei commenti per prendere tutti gli utenti che hanno lasciato un commento
				for (int i = post.getComments().size() - 1; i >= 0; i--) {
					WinComment comment = post.getComments().get(i);
					// Se il commento e' piu' vecchio dell'ultimo calcolo ricompense mi fermo
					LocalDateTime ldt = LocalDateTime.parse(comment.getTimestamp(),
							DateTimeFormatter.ofPattern( "hh:mm a, EEE M/d/uuuu", Locale.ITALY));
					ZonedDateTime zdt = ldt.atZone(ZoneId.systemDefault());
					Instant instant = zdt.toInstant();
					if (instant.isBefore(lastReward)) break;
					// Aggiungo l'autore
					commentAuthors.add(comment.getAuthor());
				}
				// Scorro il vettore dei commenti per calcolare i guadagni dei commenti
				for (int i = post.getComments().size() - 1; i >= 0; i--) {
					WinComment comment = post.getComments().get(i);
					//Ho gia' valutato un commento di quest'autore
					if (contributors.contains(comment.getAuthor())) continue;
					// Se il commento e' piu' vecchio dell'ultimo calcolo ricompense mi fermo
					LocalDateTime ldt = LocalDateTime.parse(comment.getTimestamp(),
							DateTimeFormatter.ofPattern( "hh:mm a, EEE M/d/uuuu", Locale.ITALY));
					ZonedDateTime zdt = ldt.atZone(ZoneId.systemDefault());
					Instant instant = zdt.toInstant();
					if (instant.isBefore(lastReward)) break;
					// Quante volte ha commentato l'autore del commento
					double Cp = Collections.frequency(commentAuthors, comment.getAuthor());
					// Calcolo i guadagni dai commenti
					commentSum += (2 / (1 + Math.exp(-(Cp - 1))));
					// Aggiungo l'autore alla lista dei contributori
					contributors.add(comment.getAuthor());
				}
				// Variabile per la somma dei voti
				double voteSum = 0;
				// Scorro il vettore dei voti per calcolarne i guadagni
				for (int i = post.getRatings().size() - 1; i >= 0; i--) {
					WinRate rate = post.getRatings().get(i);
					// Se il voto e' piu' vecchio dell'ultimo calcolo ricompense mi fermo
					LocalDateTime ldt = LocalDateTime.parse(rate.getTimestamp(),
							DateTimeFormatter.ofPattern( "hh:mm a, EEE M/d/uuuu", Locale.ITALY));
					ZonedDateTime zdt = ldt.atZone(ZoneId.systemDefault());
					Instant instant = zdt.toInstant();
					if (instant.isBefore(lastReward)) break;
					// Calcolo la somma
					voteSum += rate.getRate();
					// Se l'utente ha espresso un voto positivo viene inserito nella lista dei contributori
					if (rate.getRate() == 1)
						if (!contributors.contains(rate.getUserrating())) contributors.add(rate.getUserrating());
				}
				// Calcolo il guadagno totale del post
				postRevenue = (Math.log(Math.max(voteSum, 0) + 1) + Math.log(commentSum + 1)) / post.getNiter();
				// Il post e' stato valutato, incremento il numero di iterazioni
				post.iterInc();

				System.out.print("Post: " + post.getIdPost() + " has earned ");
				System.out.printf("%.2f\n", postRevenue);
			}
    		// Se il post ha guadagnato una quantita' significativa di wincoin
    		if(postRevenue > 0.01) {
				// Calcolo il guadagno dell'autore
				double authorEarnings = percentage(authorPercentage, postRevenue);
				// Lo aggiungo se il guadagno e' significativo
				if(authorEarnings > 0.01) {
					serverStorage.getUserMap().get(postAuthor).updateWallet(authorEarnings);
				}
				// Calcolo il guadagno dei contributori
	    		double contributorsEarnings = (percentage(100 - authorPercentage, postRevenue))/contributors.size();
				// Lo aggiungo se significativo
				if(contributorsEarnings > 0.01) {
					for (String contributor : contributors) {
						serverStorage.getUserMap().get(contributor).updateWallet(contributorsEarnings);
					}
				}
    		}
    	}
    }
	/**
	 * Ferma il cliclo del calcolo ricompense
	 */
    public void stop() {
        stop = true;
    }
	/**
	 * Calcola la percentuale
	 * @param percentage percentuale
	 * @param number numero di cui fare la percentuale
	 * @return il valore calcolato
	 */
	public double percentage(double percentage, double number) {
    	double result = percentage * number;
    	result = result/100;
    	return result;
    }
}
