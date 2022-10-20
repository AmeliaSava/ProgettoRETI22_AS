import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;

import java.io.*;
import java.lang.reflect.Type;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class WinServerStorageKeeper implements Runnable {

    private WinServerStorage serverStorage;
    public static final String FileUsers = ".\\src\\memory\\Users.json";
    public static final String FilePosts = ".\\src\\memory\\Posts.json";
    private File userFile;
    private File postFile;
    private int time;

    private boolean stop;

    public WinServerStorageKeeper(WinServerStorage serverStorage, int time) {
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
            if(!(userFile.createNewFile())) {
                System.out.println("Loading user file storge");

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

                System.out.println("dimensioni nuova map " + newUserMap.size());

                serverStorage.setUserMap(newUserMap);
            }

            if(!(postFile.createNewFile())) {
                System.out.println("Loading post file storge");

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

                System.out.println("dimensioni nuova map " + newPostMap.size());

                serverStorage.setPostMap(newPostMap);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        while(!stop) {

            WinUtils.sleep(time * 1000);

            try(FileWriter fw = new FileWriter(userFile)) {
                System.out.println("Saving Server user status");
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                String json = gson.toJson(serverStorage.getUserMap());
                fw.write(json);
            } catch (IOException e) {
                System.out.println("ERROR: write user map file");
                e.printStackTrace();
            }

            try(FileWriter fw = new FileWriter(postFile)) {
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
        stop = true;
    }

    public void delete() {
        if(!userFile.delete() && !postFile.delete()) System.err.println("ERROR: resetting server storage");
    }
}
