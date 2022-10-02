import java.rmi.RemoteException;
import java.rmi.server.RemoteObject;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class NotificationServiceServerImpl extends RemoteObject implements NotificationServiceServer {
	
	private List<NotificationServiceClient> registredClients;
	
	public NotificationServiceServerImpl(ConcurrentHashMap<String, WinUser> onlineUsers) {
		super();
		registredClients = new ArrayList<NotificationServiceClient>();
	}

	@Override
	public synchronized void registerForCallback(NotificationServiceClient clientInterface) throws RemoteException {
		if(!registredClients.contains(clientInterface)) {
			registredClients.add(clientInterface);
			System.out.println("new client");
		}
		
	}

	@Override
	public synchronized void unregisterForCallback(NotificationServiceClient clientInterface) throws RemoteException {
		if(registredClients.remove(clientInterface)) {
			System.out.println("removed client");
		} else {
			System.out.println("unable to remove client");
		}
		
	}
	
	public void follow(String username) throws RemoteException{
		doCallbacks(username);
	}
	
	private synchronized void doCallbacks(String username) throws RemoteException { 
		System.out.println("Starting callbacks.");
		Iterator i = registredClients.iterator( );
		while (i.hasNext()) {
			NotificationServiceClient client = (NotificationServiceClient) i.next();
			client.notifyFollow(username);
		} 
		System.out.println("Callbacks complete.");
	} 
	 
}
