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
     * Metodo utilizzato dal server per notificare un nuovo follower e aggiungerlo alla lista
     */
    public void notifyFollow(String username, String follower) throws RemoteException {
        System.out.println(follower + " has started following you!");
        listFollowers.add(follower);
        return;
    }

    @Override
    public void notifyUnfollow(String username, String unfollower) throws RemoteException {
        System.out.println(unfollower + " has stopped following you!");
        listFollowers.remove(unfollower);
        return;
    }
}
