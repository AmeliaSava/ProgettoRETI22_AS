import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class WinRewardCalculator implements Runnable {

    private WinServerStorage serverStorage;
    private final int time;
    private final String multicastAddress;
    private final int udpPort;
    private final int authorPercentage;

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

			try {
				Thread.sleep(time * 1000);
			} catch (InterruptedException e) {
				break;
			}

			calculateReward();
        	
            try (DatagramSocket mc = new DatagramSocket()) {
                InetAddress udpAdd = InetAddress.getByName(multicastAddress);
                byte[] message = "Your wallet has been updated!".getBytes(StandardCharsets.UTF_8);
                DatagramPacket dp = new DatagramPacket(message, message.length, udpAdd, udpPort);
                mc.send(dp);
				System.out.println("Sent update on wallet status to " + multicastAddress);
            } catch (IOException e1) {
                e1.printStackTrace();
            }
            
        }
    }
    
    private void calculateReward() {
    	
    	ConcurrentHashMap<UUID, WinPost> postMap = serverStorage.getPostMap();
    	
    	if(postMap == null) { return; }
    	
    	// L'ultima volta che le ricompense sono state calcolate
    	Instant lastReward = Instant.now().minusSeconds(time);
    	    	
    	for(UUID postID : postMap.keySet()) {
    	 			
			WinPost post = postMap.get(postID);

			double postRevenue = 0;
    		double commentSum = 0;

    		String postAuthor;

    		// Salvo i nomi di chi ha commentato per contare i commenti della stessa persona
    		List<String> commentAuthors = new ArrayList<String>();

			// Lista dove salvo solo una volta chi ha commentato per non calcolare due volte il commento
			// e per avere la lista finale dei contributori
			List<String> contributors = new ArrayList<String>();

    		synchronized (post) {

    			postAuthor = post.getPostAuthor();
				System.out.println("Evaluating post " + post.getIdPost() + " post was evaluated " + post.getNiter() + " times");
    			// Scorro il vettore del commenti per prendere tutti gli utenti che hanno lasciato un commento
				for (int i = post.getComments().size() - 1; i >= 0; i--) {
					WinComment comment = post.getComments().get(i);

					// Se il commento e' piu' vecchio dell'ultimo calcolo ricompense mi fermo
					if (comment.getTimestamp().toInstant().isBefore(lastReward)) break;

					commentAuthors.add(comment.getAuthor());
				}

				// Scorro il vettore dei commenti per calcolare i guadagni dei commenti
				for (int i = post.getComments().size() - 1; i >= 0; i--) {
					WinComment comment = post.getComments().get(i);

					//Ho gia' valutato un commento di quest'autore
					if (contributors.contains(comment.getAuthor())) continue;
					// Se il commento e' piu' vecchio dell'ultimo calcolo ricompense mi fermo
					if (comment.getTimestamp().toInstant().isBefore(Instant.now().minusSeconds(time))) break;

					// Quante volte ha commetato l'autore del commento
					double Cp = Collections.frequency(commentAuthors, comment.getAuthor());

					commentSum += (2 / (1 + Math.exp(-(Cp - 1))));
					contributors.add(comment.getAuthor());
				}

				double voteSum = 0;

				// Scorro il vettore dei voti per calcolarne i guadagni
				for (int i = post.getRatings().size() - 1; i >= 0; i--) {
					WinRate rate = post.getRatings().get(i);
					// Se il voto e' piu' vecchio dell'ultimo calcolo ricompense mi fermo
					if (rate.getTimestamp().toInstant().isBefore(lastReward)) break;

					voteSum += rate.getRate();

					// se l'utente ha espresso un voto positivo viene inserito nella lista dei contributori
					if (rate.getRate() == 1)
						if (!contributors.contains(rate.getUserrating())) contributors.add(rate.getUserrating());

				}

				postRevenue = (Math.log(Math.max(voteSum, 0) + 1) + Math.log(commentSum + 1)) / post.getNiter();

				// Il post e' stato valutato, incremento il numero di iterazioni
				post.iterInc();

				System.out.println("Post: " + post.getIdPost() + " has earned " + postRevenue);
			}
    		
    		if(postRevenue > 0) {
				double authorEarnings = percentage(authorPercentage, postRevenue);

	    		serverStorage.getUserMap().get(postAuthor).updateWallet(authorEarnings);

	    		double contributorsEarnings = (percentage(100 - authorPercentage, postRevenue))/contributors.size();

	    		for(String contributor : contributors) {
	    			serverStorage.getUserMap().get(contributor).updateWallet(contributorsEarnings);
	    		}
    		}
    	}
    	
    }

    public void stop() {
        stop = true;
    }
    
    public double percentage(double percentage, double number) {
    	double result = percentage * number;
    	result = result/100;
    	return result;
    }
    
}
