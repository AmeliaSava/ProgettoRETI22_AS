import java.rmi.RemoteException;
import java.util.List;

public class NotificationServiceClientImpl implements NotificationServiceClient {
	
	List<String> listFollowers;
	
	/*
	 * Crea un nuovo callback client
	 * la lista dei follower per aggiornarla
	 */
	public NotificationServiceClientImpl(List<String> listFollowers) throws RemoteException {
		super();
		this.listFollowers = listFollowers;
	}
	
	@Override
	/*
	 * Metodo utilizzato dal server per notificare un cambiamento nella lista dei followers
	 */
	public void notifyFollow(String username, String follower) throws RemoteException {
		System.out.println(follower + " has started following you!");
		listFollowers.add(follower);
	}
}
