import java.rmi.Remote;
import java.rmi.RemoteException;

public interface NotificationServiceServer extends Remote{

    void registerForCallback(NotificationServiceClient clientInterface, String username) throws RemoteException;

    void unregisterForCallback(NotificationServiceClient clientInterface, String usename) throws RemoteException;
}
