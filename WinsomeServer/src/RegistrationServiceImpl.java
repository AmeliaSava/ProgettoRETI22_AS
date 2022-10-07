import java.rmi.RemoteException;
import java.util.List;

public class RegistrationServiceImpl implements RegistrationService{

    private WinServerStorage serverStorage;

    public RegistrationServiceImpl(WinServerStorage serverStorage) {
        this.serverStorage = serverStorage;
    }

    @Override
    public int registerUser(String username, String password, List<String> tagList) throws RemoteException {
        System.out.println("Register user: " + username + password);

        for(int i = 0; i < tagList.size(); i++) {
            System.out.println(tagList.get(i));
        }
        int result = serverStorage.registerUser(username, password, tagList);
        return result;
    }

}
