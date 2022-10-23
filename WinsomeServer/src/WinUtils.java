import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;

/**
 * Operazioni di utility per la comunicazione
 */
public final class WinUtils {

    /**
     * Manda un messaggio sul canale
     * @param message Il messaggio da inviare
     * @param channel Il canale su cui inviarlo
     * @throws IOException
     */
    public static void send(String message, SocketChannel channel) throws IOException {

        // Prendo le dimensioni della stringa da inviare
        int size = message.getBytes().length;
        // Preparo il buffer con la grandezza del messaggio e la grandezza di un intero
        ByteBuffer writeBuffer = ByteBuffer.allocate(size + 4);

        // Metto prima le dimensioni e poi la stringa
        writeBuffer.putInt(size);
        writeBuffer.put(message.getBytes());
        writeBuffer.flip();
        // Invio i dati
        while(writeBuffer.hasRemaining()) {
            channel.write(writeBuffer);
        }
    }

    /**
     * Riceve un messaggio dal canale
     * @param channel Il canale da cui riceve
     * @return Il messaggio ricevuto
     * @throws IOException
     */
    public static String receive(SocketChannel channel) throws IOException {
        // Leggo quando è grande il messaggio
        ByteBuffer readBuffer = ByteBuffer.allocate(4);
        IntBuffer view = readBuffer.asIntBuffer();
        channel.read(readBuffer);
        int rSize = view.get();
        readBuffer.clear();
        view.rewind();

        // Rialloco il buffer con la nuova dimensione
        readBuffer = ByteBuffer.allocate(rSize);
        // Leggo il messaggio
        while(readBuffer.hasRemaining()) {
            channel.read(readBuffer);
        }

        // Converto il buffer in stringa
        readBuffer.rewind();
        byte[] buffer = new byte[readBuffer.remaining()];
        readBuffer.get(buffer);
        String response = new String(buffer, StandardCharsets.UTF_8);
        readBuffer.clear();

        //Ritorno il risultato
        return response;
    }

    /**
     * Converte un oggetto in un json
     * @param toSend l'oggetto da convertire
     * @return La stringa contenente l'oggetto convertito
     */
    public static String prepareJson(Object toSend) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String json = gson.toJson(toSend);
        return json;
    }
}
