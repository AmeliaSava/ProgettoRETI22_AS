import java.rmi.Remote;
import java.rmi.RemoteException;

public interface NotificationServiceServer extends Remote{

    public void registerForCallback(NotificationServiceClient clientInterface, String username) throws RemoteException;

    public void unregisterForCallback(NotificationServiceClient clientInterface, String username) throws RemoteException;

}
