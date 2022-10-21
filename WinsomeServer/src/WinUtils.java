import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public final class WinUtils {

    public static void send(String message, SocketChannel channel) throws IOException {

        //prendo le dimensioni della stringa da inviare
        int size = message.getBytes().length;

        ByteBuffer writeBuffer = ByteBuffer.allocate(size + 4);

        //invio prima del dimensioni e poi la stringa
        writeBuffer.putInt(size);
        writeBuffer.put(message.getBytes());
        writeBuffer.flip();
        while(writeBuffer.hasRemaining()) {
            channel.write(writeBuffer);
        }
    }

    public static String receive(SocketChannel channel) throws IOException {
        //catturare l'eccezione
        ByteBuffer readBuffer = ByteBuffer.allocate(4);
        IntBuffer view = readBuffer.asIntBuffer();

        channel.read(readBuffer);
        int rSize = view.get();

        readBuffer.clear();
        view.rewind();

        readBuffer = ByteBuffer.allocate(rSize);
        while(readBuffer.hasRemaining()) {
            channel.read(readBuffer);
        }

        readBuffer.rewind();
        byte[] buffer = new byte[readBuffer.remaining()];
        readBuffer.get(buffer);
        String response = new String(buffer, StandardCharsets.UTF_8);

        readBuffer.clear();

        return response;
    }
    
    public static String prepareJson(Object toSend) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String json = gson.toJson(toSend);
        return json;
    }

}
