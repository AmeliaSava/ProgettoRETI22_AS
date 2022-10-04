import java.rmi.Remote;
import java.rmi.RemoteException;

public interface NotificationServiceClient extends Remote {
	
	public void notifyFollow(String username, String follower) throws RemoteException;
	
	public void notifyUnfollow(String username, String follower) throws RemoteException;
}
