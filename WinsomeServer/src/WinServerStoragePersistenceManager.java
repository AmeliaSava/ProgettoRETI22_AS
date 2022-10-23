import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class WinServerStoragePersistenceManager implements Runnable {

    private WinServerStorage serverStorage;
    public static final String FileUsers = ".\\src\\memory\\Users.json";
    public static final String FilePosts = ".\\src\\memory\\Posts.json";
    private File userFile;
    private File postFile;
    private final int time;

    private volatile boolean stop;

    public WinServerStoragePersistenceManager(WinServerStorage serverStorage, int time) {
        this.serverStorage = serverStorage;
        this.time = time;
        this.stop = false;
    }

    @Override
    public void run() {
        System.out.println("Storage Keeper Started");

        // Se non ci sono dati precedenti da caricare creo i file
        userFile = new File(FileUsers);
        postFile = new File(FilePosts);
        try {
            // Altrimenti carico i file gia' esistenti
            if(!(userFile.createNewFile())) {
                System.out.println("Loading user storge");

                StringBuilder jsonInput = new StringBuilder();
                String line;

                //rileggo gli oggetti della lista salvati nel json
                try(BufferedReader reader = new BufferedReader(new FileReader(userFile))) {
                    while((line = reader.readLine()) != null) {
                        jsonInput.append(line);
                        jsonInput.append(System.lineSeparator());
                    }
                }

                Gson gson = new Gson();
                Type type = new TypeToken<ConcurrentHashMap<String, WinUser>>(){}.getType();
                ConcurrentHashMap<String, WinUser> newUserMap = gson.fromJson(jsonInput.toString(), type);

                if(newUserMap == null) throw new CorruptedStorageMemoryException("User map file corrupted");

                System.out.println("User storage size " + newUserMap.size());

                serverStorage.setUserMap(newUserMap);
            }

            if(!(postFile.createNewFile())) {
                System.out.println("Loading post storge");

                StringBuilder jsonInput = new StringBuilder();
                String line;

                //rileggo gli oggetti della lista salvati nel json
                try(BufferedReader reader = new BufferedReader(new FileReader(postFile))) {
                    while((line = reader.readLine()) != null) {
                        jsonInput.append(line);
                        jsonInput.append(System.lineSeparator());
                    }
                }

                Gson gson = new Gson();
                Type type = new TypeToken<ConcurrentHashMap<UUID, WinPost>>(){}.getType();
                ConcurrentHashMap<UUID, WinPost> newPostMap = gson.fromJson(jsonInput.toString(), type);

                if(newPostMap == null) throw new CorruptedStorageMemoryException("Post map file corrupted");

                System.out.println("Post storage size " + newPostMap.size());

                serverStorage.setPostMap(newPostMap);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        while(!stop) {
            try {
                Thread.sleep(time * 1000);
            } catch (InterruptedException e) {
                break;
            }
            saveStorage();
        }
    }
    private void saveStorage() {
        synchronized (serverStorage.getUserMap()) {
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
        synchronized (serverStorage.getPostMap()) {
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
    public void stop() {
        saveStorage();
        stop = true;
    }

}
