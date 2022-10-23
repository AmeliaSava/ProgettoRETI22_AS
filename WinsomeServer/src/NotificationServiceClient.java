import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Interfaccia per la RMI callback lato client
 */
public interface NotificationServiceClient extends Remote {
    /**
     * Metodo utilizzato dal server per notificare un nuovo follower e aggiungerlo alla lista lato client
     * @param username L'utente che e' stato seguito
     * @param follower L'utente che ha seguito
     * @throws RemoteException
     */
    public void notifyFollow(String username, String follower) throws RemoteException;

    /**
     * Metodo utilizzato dal server per notificare la perdita di un follower e lo rimuove dalla lista lato client
     * @param username L'utente che ha perso un follower
     * @param unfollower L'utente che ha smesso di seguire
     * @throws RemoteException
     */
    public void notifyUnfollow(String username, String unfollower) throws RemoteException;
}
