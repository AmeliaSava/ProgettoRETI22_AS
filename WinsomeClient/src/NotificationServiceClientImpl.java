import java.rmi.RemoteException;

public class NotificationServiceClientImpl implements NotificationServiceClient {
	
	/*
	 * Crea un nuovo callback client
	 */
	public NotificationServiceClientImpl() throws RemoteException {
		super();
	}
	
	@Override
	/*
	 * Metodo utilizzato dal server per notificare un cambiamento nella lista dei followers
	 */
	public void notifyFollow(String username) throws RemoteException {
		System.out.println(username + " has started following you");		
	}
}
