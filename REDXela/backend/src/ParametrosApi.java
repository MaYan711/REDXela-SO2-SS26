import com.sun.net.httpserver.HttpExchange;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
/** Decodificación de la convención query-string usada por la interfaz actual. */
final class ParametrosApi {
    static Map<String,String> obtener(HttpExchange exchange) {
        Map<String,String> values=new HashMap<>();
        String raw=exchange.getRequestURI().getRawQuery();
        if(raw!=null){
            for(String pair:raw.split("&")){
                String[] item=pair.split("=",2);
                if(item.length==2)values.put(item[0],URLDecoder.decode(item[1],StandardCharsets.UTF_8));
            }
        }
        return values;
    }

}
