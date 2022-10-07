import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface RegistrationService extends Remote {

    int registerUser(String username, String password, List<String> tagList) throws RemoteException;
}
