import java.rmi.Remote;
import java.rmi.RemoteException;

public interface NotificationServiceServer extends Remote{

	public void registerForCallback(NotificationServiceClient clientInterface) throws RemoteException;
	
	public void unregisterForCallback(NotificationServiceClient clientInterface) throws RemoteException;
}
