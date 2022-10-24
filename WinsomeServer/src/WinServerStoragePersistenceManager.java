import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread che salva periodicamente lo stato dello storage del server in una cartella predefinita, all'inizio
 * dell'esecuzione controlla se ci sono dei dati precedenti da caricare e permette di salvarli quando necessario
 */
public class WinServerStoragePersistenceManager implements Runnable {
    // Lo storage
    private WinServerStorage serverStorage;
    // Dove salvare i file
    public static final String FileUsers = ".\\src\\memory\\Users.json";
    public static final String FilePosts = ".\\src\\memory\\Posts.json";
    // I file degli utenti e dei post
    private File userFile;
    private File postFile;
    // Il tempo tra un salvataggio e l'altro
    private int time;
    // Variabile per fermare il ciclo del thread
    private volatile boolean stop;

    public WinServerStoragePersistenceManager(WinServerStorage serverStorage, int time) {
        this.serverStorage = serverStorage;
        this.time = time;
        this.stop = false;
    }

    @Override
    public void run() {
        System.out.println("Storage Keeper Started");
        // Creo i file
        userFile = new File(FileUsers);
        postFile = new File(FilePosts);
        try {
            // Se non li posso creare vuol dire che esistono gia' dei file, carico quei file
            if(!(userFile.createNewFile())) {
                System.out.println("Loading user storge");

                StringBuilder jsonInput = new StringBuilder();
                String line;
                // Leggo i dati salvati
                try(BufferedReader reader = new BufferedReader(new FileReader(userFile))) {
                    while((line = reader.readLine()) != null) {
                        jsonInput.append(line);
                        jsonInput.append(System.lineSeparator());
                    }
                }
                // Converto i dati
                Gson gson = new Gson();
                Type type = new TypeToken<ConcurrentHashMap<String, WinUser>>(){}.getType();
                ConcurrentHashMap<String, WinUser> newUserMap = gson.fromJson(jsonInput.toString(), type);
                // Se non si e' creata una usermap allora vuol dire che il file e' illeggibile, eccezione
                if(newUserMap == null) throw new CorruptedStorageMemoryException("User map file corrupted");
                System.out.println("User storage size " + newUserMap.size());
                // Imposto la usermap caricata nello storage
                serverStorage.setUserMap(newUserMap);
            }
            if(!(postFile.createNewFile())) {
                System.out.println("Loading post storge");

                StringBuilder jsonInput = new StringBuilder();
                String line;
                // Leggo i dati salvati
                try(BufferedReader reader = new BufferedReader(new FileReader(postFile))) {
                    while((line = reader.readLine()) != null) {
                        jsonInput.append(line);
                        jsonInput.append(System.lineSeparator());
                    }
                }
                // Converto i dati
                Gson gson = new Gson();
                Type type = new TypeToken<ConcurrentHashMap<UUID, WinPost>>(){}.getType();
                ConcurrentHashMap<UUID, WinPost> newPostMap = gson.fromJson(jsonInput.toString(), type);
                // Se non si e' creata una postmap allora vuol dire che il file e' illeggibile, eccezione
                if(newPostMap == null) throw new CorruptedStorageMemoryException("Post map file corrupted");
                System.out.println("Post storage size " + newPostMap.size());
                // Imposto la postmap caricata nello storage
                serverStorage.setPostMap(newPostMap);
            }
        } catch (IOException e) {
            System.err.println("ERROR: storage persistence managare" + e.getMessage());
            e.printStackTrace();
        }
        // Dopo aver creato o caricato inizio il salvataggio periodico
        while(!stop) {
            // Aspetto
            try {
                Thread.sleep(time * 1000);
            } catch (InterruptedException e) {
                // Se il thread viene interrotto mi fermo subito
                System.out.println("Periodic Save Stopped");
                break;
            }
            // Salvo
            saveStorage();
        }
    }

    /**
     * Salva i dati presenti nello storage nei file
     */
    private void saveStorage() {
        // Prendo gli utenti
        synchronized (serverStorage.getUserMap()) {
            // Scrivo nel file utenti
            try (FileWriter fw1 = new FileWriter(userFile)) {
                System.out.println("Saving Server user status");
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                String json = gson.toJson(serverStorage.getUserMap());
                fw1.write(json);
            } catch (IOException e) {
                System.out.println("ERROR: write user map file");
                e.printStackTrace();
            }
        }
        // Prendo i post
        synchronized (serverStorage.getPostMap()) {
            // Scrivo nel file post
            try (FileWriter fw = new FileWriter(postFile)) {
                System.out.println("Saving Server post status");
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                String json = gson.toJson(serverStorage.getPostMap());
                fw.write(json);
            } catch (IOException e) {
                System.out.println("ERROR: write post map file");
                e.printStackTrace();
            }
        }
    }

    /**
     * Ferma il ciclo del salvataggio periodico salvando un'ultima volta
     */
    public void stop() {
        saveStorage();
        stop = true;
    }

}
