import java.rmi.RemoteException;
import java.util.List;

public class RegistrationServiceImpl implements RegistrationService{

    private WinServerStorage serverStorage;

    public RegistrationServiceImpl(WinServerStorage serverStorage) {
        this.serverStorage = serverStorage;
    }

    @Override
    /*
     * Inserisce il nuovo utente nella struttura dati degli utenti registrati,
     * controllando che lo username scelto non sia gia' in uso, ritorna errore in quel caso.
     * 
     * @param username lo username scelto dall'utente che ha chiesto la registrazione
     * @param password la password inserita dall'utente
     * @param tagList la lista dei tag scelti dall'utente
     * 
     * @return -1 se il nome utente e' gia' presente
     * @return 0 se tutto e' andato bene
     */
    public int registerUser(String username, String password, List<String> tagList) throws RemoteException {
       
    	System.out.println("User " + username + " wants to register with password " + password);

    	synchronized (serverStorage.getUserMap()) {
            // Controllo che lo username non sia gia' in uso
            if (serverStorage.userIsRegistred(username)) {
                return -1;
            }

            // Creo il nuovo utente e lo inserisco in memoria
            WinUser newUser = new WinUser(username, password, tagList);
            serverStorage.addNewUser(username, newUser);
        }
        return 0;
        
    }

}
