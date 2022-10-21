import java.rmi.RemoteException;
        import java.rmi.server.RemoteObject;
        import java.util.concurrent.ConcurrentHashMap;

public class NotificationServiceServerImpl extends RemoteObject implements NotificationServiceServer {


	private static final long serialVersionUID = -2152304118811415827L;
	
    private ConcurrentHashMap<String, NotificationServiceClient> registeredClients;

    public NotificationServiceServerImpl() {
        super();     
        registeredClients = new ConcurrentHashMap<String, NotificationServiceClient>();
    }

    @Override
    public synchronized void registerForCallback(NotificationServiceClient clientInterface, String username) throws RemoteException {
        if(registeredClients.putIfAbsent(username, clientInterface) == null){
            System.out.println("new client");
        }
    }

    @Override
    public synchronized void unregisterForCallback(NotificationServiceClient clientInterface, String username) throws RemoteException {
        if(registeredClients.remove(username, clientInterface)) {
            System.out.println("removed client");
        } else {
            System.err.println("unable to remove client");
        }
    }

    public void follow(String username, String follower) throws RemoteException {
        NotificationServiceClient client = (NotificationServiceClient) registeredClients.get(username);
        client.notifyFollow(username, follower);
    }

    public void unfollow(String username, String unfollower) throws RemoteException {
        NotificationServiceClient client = (NotificationServiceClient) registeredClients.get(username);
        client.notifyUnfollow(username, unfollower);
    }
    public void emergencyUnregister(String username) {
        if(registeredClients.remove(username) != null) {
            System.out.println("emergency removed client");
        } else {
            System.err.println("unable to remove client em");
        }
    }
}
