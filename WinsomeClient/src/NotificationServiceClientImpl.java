import java.rmi.RemoteException;
import java.util.List;

/**
 * Implementazione dell'interfaccia per la RMI callback lato client
 */
public class NotificationServiceClientImpl implements NotificationServiceClient {
    // La lista dei followers da modificare
    private List<String> listFollowers;

    /**
     * Crea un nuovo callback client
     * @param listFollowers la lista dei follower per aggiornarla
     * @throws RemoteException
     */
    public NotificationServiceClientImpl(List<String> listFollowers) throws RemoteException {
        super();
        this.listFollowers = listFollowers;
    }

    @Override
    public void notifyFollow(String username, String follower) throws RemoteException {
        System.out.println(follower + " has started following you!");
        listFollowers.add(follower);
    }

    @Override
    public void notifyUnfollow(String username, String unfollower) throws RemoteException {
        System.out.println(unfollower + " has stopped following you!");
        listFollowers.remove(unfollower);
    }
}
