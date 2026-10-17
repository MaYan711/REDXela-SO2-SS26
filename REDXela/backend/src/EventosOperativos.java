import com.sun.net.httpserver.HttpExchange;
import java.io.*;
import java.nio.charset.StandardCharsets;
/** Server-Sent Events para el panel en tiempo real sin modificar Angular. */
final class EventosOperativos {
    private final CentroOperativo simulation;
    EventosOperativos(CentroOperativo simulation){this.simulation=simulation;}
    void conectar(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Content-Type","text/event-stream; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control","no-cache, no-transform");
        exchange.getResponseHeaders().set("Connection","keep-alive");
        exchange.getResponseHeaders().set("X-Accel-Buffering","no");
        exchange.sendResponseHeaders(200,0);
        try(OutputStream out=exchange.getResponseBody()){
            out.write("retry: 1000\n\n".getBytes(StandardCharsets.UTF_8));
            out.flush();
            while(!Thread.currentThread().isInterrupted()){
                String event="data: "+ConversorJson.encode(simulation.snapshot())+"\n\n";
                out.write(event.getBytes(StandardCharsets.UTF_8));
                out.flush();
                Thread.sleep(300);
            }
        }catch(InterruptedException ex){
            Thread.currentThread().interrupt();
        }catch(IOException ignored){
        }
    }

}
