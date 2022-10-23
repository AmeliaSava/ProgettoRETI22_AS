import com.google.gson.JsonObject;

import java.nio.channels.SelectionKey;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Rejection Policy personalizzata, comunica al client che la sua richiesta e' stata respinta
 */
public class DenyPolicy implements RejectedExecutionHandler {
    @Override
    public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
        // Prendo la chiave relativa al task rifiutato
        SelectionKey rejectedKey = ((WinServerWorker) r).getKeyWorker();
        // Preparo un messaggio
        JsonObject response = new JsonObject();
        response.addProperty("result", -1);
        response.addProperty("result-msg", "Your request was not processed by server, retry");
        // Lo allego alla chiave
        rejectedKey.attach(response);
        // Comunico che c'e' un messaggio da inviare
        rejectedKey.interestOps(SelectionKey.OP_WRITE);
    }
}
