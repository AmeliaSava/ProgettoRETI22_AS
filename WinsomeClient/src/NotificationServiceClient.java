import java.rmi.Remote;
import java.rmi.RemoteException;

public interface NotificationServiceClient extends Remote {
	
	public void notifyFollow(String username) throws RemoteException;
}
