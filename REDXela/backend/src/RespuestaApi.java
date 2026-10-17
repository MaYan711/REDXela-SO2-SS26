import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
/** Respuestas HTTP uniformes en JSON. */
final class RespuestaApi {
    static void enviar(HttpExchange exchange,int code,Object value) throws IOException {
        byte[] bytes=ConversorJson.encode(value).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type","application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control","no-store");
        exchange.sendResponseHeaders(code,bytes.length);
        try(var out=exchange.getResponseBody()){out.write(bytes);}
    }

}
