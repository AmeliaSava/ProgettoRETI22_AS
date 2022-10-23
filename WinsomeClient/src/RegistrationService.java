import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

/**
 * Interfaccia RMI per la registrazione di un utente
 */
public interface RegistrationService extends Remote {

    /**
     * Inserisce il nuovo utente nella struttura dati degli utenti registrati,
     * controllando che lo username scelto non sia gia' in uso, ritorna errore in quel caso.
     * @param username Lo username scelto dall'utente che ha chiesto la registrazione
     * @param password La password inserita dall'utente
     * @param tagList La lista dei tag scelti dall'utente
     * @return 0 se tutto e' andato bene -1 se il nome utente e' gia' presente
     * @throws RemoteException
     */
    int registerUser(String username, String password, List<String> tagList) throws RemoteException;
}
