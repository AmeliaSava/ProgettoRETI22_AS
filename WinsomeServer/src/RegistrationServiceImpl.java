import java.rmi.RemoteException;
import java.util.List;

/**
 * Implementazione dell'interfaccia RMI per la registrazione
 */
public class RegistrationServiceImpl implements RegistrationService{
    // Lo storage in cui l'utente va inserito
    private static WinServerStorage serverStorage;

    public RegistrationServiceImpl(WinServerStorage serverStorage) {
        this.serverStorage = serverStorage;
    }

    @Override
    public int registerUser(String username, String password, List<String> tagList) throws RemoteException {
    	synchronized (serverStorage.getUserMap()) {
            // Controllo che lo username non sia gia' in uso
            if (serverStorage.userIsRegistred(username)) {
                return -1;
            }
            // Creo il nuovo utente e lo inserisco in memoria
            WinUser newUser = new WinUser(username, password, tagList);
            serverStorage.addNewUser(username, newUser);
        }
        System.out.println("User " + username + " registered");
        return 0;
    }
}
