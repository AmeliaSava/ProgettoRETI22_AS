import java.rmi.RemoteException;
import java.rmi.server.RemoteObject;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementazione dell'interfaccia per la notifica RMI callback lato server
  */
public class NotificationServiceServerImpl extends RemoteObject implements NotificationServiceServer {
	private static final long serialVersionUID = -2152304118811415827L;
	// Struttura dati dove salvo le interfacce degli utenti associate al loro username
    private ConcurrentHashMap<String, NotificationServiceClient> registeredClients;

    public NotificationServiceServerImpl() {
        super();     
        registeredClients = new ConcurrentHashMap<String, NotificationServiceClient>();
    }

    @Override
    public synchronized void registerForCallback(NotificationServiceClient clientInterface, String username)
            throws RemoteException {
        if(registeredClients.putIfAbsent(username, clientInterface) == null){
            System.out.println("new client");
        }
    }

    @Override
    public synchronized void unregisterForCallback(NotificationServiceClient clientInterface, String username)
            throws RemoteException {
        if(registeredClients.remove(username, clientInterface)) {
            System.out.println("removed client");
        } else {
            System.err.println("unable to remove client");
        }
    }

    /**
     * Chiama il metodo dell'interfaccia del client per aggiungere un follower
     * @param username l'utente da notificare
     * @param follower l'utente che ha seguito
     * @throws RemoteException
     */
    public void follow(String username, String follower) throws RemoteException {
        NotificationServiceClient client = (NotificationServiceClient) registeredClients.get(username);
        client.notifyFollow(username, follower);
    }

    /**
     * Chiama il metodo dell'interfaccia del client per rimuovere un follower
     * @param username l'utente da notificare
     * @param unfollower l'utente che ha smesso di seguire
     * @throws RemoteException
     */
    public void unfollow(String username, String unfollower) throws RemoteException {
        NotificationServiceClient client = (NotificationServiceClient) registeredClients.get(username);
        client.notifyUnfollow(username, unfollower);
    }

    /**
     * Metodo utilizzato dal server per rimuovere un'utente dalla lista di quelli da notificare in caso di
     * disconnessione anomala del client
     * @param username L'utente che si e' disconnesso
     */
    public void emergencyUnregister(String username) {
        if(registeredClients.remove(username) != null) {
            System.out.println("emergency removed client");
        } else {
            System.err.println("unable to remove client em");
        }
    }
}
