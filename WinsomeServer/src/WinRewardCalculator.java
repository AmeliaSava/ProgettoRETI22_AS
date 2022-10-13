import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class WinRewardCalculator implements Runnable {

    private WinServerStorage serverStorage;
    private int time;
    private int nIter;
    private String multicastAddress;
    private int udpPort;
    private int authorPercentage;

    private boolean stop;

    public WinRewardCalculator(WinServerStorage serverStorage, int time, String multicastAddress, int udpPort, int authorPercentage) {
        this.serverStorage = serverStorage;
        this.time = time;
        this.nIter = 0;
        this.multicastAddress = multicastAddress;
        this.udpPort = udpPort;
        this.authorPercentage = authorPercentage;
        this.stop = false;
    }

    @Override
    public void run() {
        System.out.println("Reward calculator started");

        while(!stop) {
        	
            WinUtils.sleep(time * 1000);
            
        	nIter++;
        	
        	System.out.println("Number it: " + nIter);
        	
        	calculateReward();
        	
            try (DatagramSocket mc = new DatagramSocket()) {
                System.out.println("Multadd" + multicastAddress);
                InetAddress udpAdd = InetAddress.getByName(multicastAddress);
                byte[] message = "Your wallet has been updated!".getBytes();
                DatagramPacket dp = new DatagramPacket(message, message.length, udpAdd, udpPort);
                mc.send(dp);
            } catch (IOException e1) {
                e1.printStackTrace();
            }
            
        }
    }
    
    private void calculateReward() {
    	
    	ConcurrentHashMap<UUID, WinPost> postMap = serverStorage.getPostMap();
    	
    	if(postMap == null) { /*ATTENZIONE */}
    	
    	// L'ultima volta che le ricompense sono state calcolate
    	Instant lastReward = Instant.now().minusSeconds(time);
    	    	
    	for(UUID postID : postMap.keySet()) {
    	 			
			WinPost post = postMap.get(postID);
    		
    		double commentSum = 0;
    		
    		// Salvo i nomi di chi ha commentato per contare i commenti della stessa persona
    		List<String> commentAuthors = new ArrayList<String>();
    		
    		for(int i = post.getComments().size() - 1; i >= 0 ; i--) {
    			WinComment comment = post.getComments().get(i);
    			
    			// Se il commento e' piu' vecchio dell'ultimo calcolo ricompense mi fermo
    			if(comment.getTimestamp().toInstant().isBefore(lastReward)) break;
    			
    			commentAuthors.add(comment.getAuthor());
    			
    		}
    		
    		// Lista dove salvo solo una volta chi ha commentato per non calcolare due volte il commento
    		// e per avere la lista finale dei contributori
    		List<String> contributors = new ArrayList<String>();
    		for(int i = post.getComments().size() - 1; i >= 0 ; i--) {
    			
    			
    			WinComment comment = post.getComments().get(i);
    			
    			if(contributors.contains(comment.getAuthor())) continue;
    			// Se il commento e' piu' vecchio dell'ultimo calcolo ricompense mi fermo
    			if(comment.getTimestamp().toInstant().isBefore(Instant.now().minusSeconds(time))) break;
    			double Cp = Collections.frequency(commentAuthors, comment.getAuthor());
    			
    			commentSum += (2/(1+Math.exp(-(Cp - 1))));
    			contributors.add(comment.getAuthor());
    		}
    		   		
    		double voteSum = 0;
    		
    		//scorro all'indietro la lista dei voti per prendere i piu' recenti
    		for(int i = post.getRatings().size() - 1; i >= 0 ; i--) {
    			WinRate rate = post.getRatings().get(i);
    			// Se il voto e' piu' vecchio dell'ultimo calcolo ricompense mi fermo
    			if(rate.getTimestamp().toInstant().isBefore(lastReward)) break;
    			
    			voteSum += rate.getRate();
    			
    			// se l'utente ha espresso un voto positivo viene inserito nella lista dei contributori
    			if(rate.getRate() == 1) if(!contributors.contains(rate.getUserrating())) contributors.add(rate.getUserrating());
    			
    		}
    		
    		double postRevenue = (Math.log(Math.max(voteSum, 0) + 1) + Math.log(commentSum + 1))/nIter;
    		
    		System.out.println("postRevenue: " + postRevenue);
    		
    		if(postRevenue > 0) {
    		
	    		//da fare la percentuale
	    		serverStorage.getUserMap().get(post.getPostAuthor()).updateWallet(percentage(authorPercentage, postRevenue));
	    		 	
	    		for(String author : contributors) {
	    			serverStorage.getUserMap().get(author).updateWallet(percentage(100 - authorPercentage, postRevenue));
	    		}
    		}
    	}
    	
    }

    public void disconnect() {
        stop = true;
        return;
    }
    
    public double percentage(double percentage, double number) {
    	double result = percentage * number;
    	result = result/100;
    	return result;
    }
    
}
