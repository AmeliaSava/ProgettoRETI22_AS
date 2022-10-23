import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Interfaccia per la RMI callback lato server
 */
public interface NotificationServiceServer extends Remote{

    /**
     * Aggiunge un client alla lista di quelli che vogliono essere notificati con la callback, se non e' gia' presente
     * @param clientInterface L'iterfaccia del client
     * @param username Lo username dell'utente a cui corrisponde l'interfaccia
     * @throws RemoteException
     */
    public void registerForCallback(NotificationServiceClient clientInterface, String username) throws RemoteException;

    /**
     * Rimuove un client dalla lista di quelli che vogliono essere notificati con la callback, se e' presente
     * @param clientInterface L'iterfaccia del client
     * @param username Lo username dell'utente a cui corrisponde l'interfaccia
     * @throws RemoteException
     */
    public void unregisterForCallback(NotificationServiceClient clientInterface, String username) throws RemoteException;

}
