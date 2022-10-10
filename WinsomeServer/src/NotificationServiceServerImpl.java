import java.rmi.RemoteException;
        import java.rmi.server.RemoteObject;
        import java.util.concurrent.ConcurrentHashMap;

public class NotificationServiceServerImpl extends RemoteObject implements NotificationServiceServer {

    //private List<NotificationServiceClient> registredClients;
    private ConcurrentHashMap<String, NotificationServiceClient> registeredClients;

    public NotificationServiceServerImpl(ConcurrentHashMap<String, WinUser> onlineUsers) {
        super();
        //registredClients = new ArrayList<NotificationServiceClient>();
        registeredClients = new ConcurrentHashMap<String, NotificationServiceClient>();
    }

    @Override
    public void registerForCallback(NotificationServiceClient clientInterface, String username) throws RemoteException {

        registeredClients.putIfAbsent(username, clientInterface);
        System.out.println("new client");

    }

    @Override
    public void unregisterForCallback(NotificationServiceClient clientInterface, String username) throws RemoteException {
    	
    	
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

}
