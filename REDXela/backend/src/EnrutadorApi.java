import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.util.*;
/** Contrato público REST compatible con el frontend existente. */
final class EnrutadorApi {
    private final CentroOperativo simulation;
    private final EventosOperativos eventos;
    EnrutadorApi(CentroOperativo simulation){this.simulation=simulation;this.eventos=new EventosOperativos(simulation);}
    void atender(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin","*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods","GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers","Content-Type");
        if(exchange.getRequestMethod().equals("OPTIONS")){
            exchange.sendResponseHeaders(204,-1);
            exchange.close();
            return;
        }
        try {
            String path=exchange.getRequestURI().getPath();
            Map<String,String> values=ParametrosApi.obtener(exchange);
            if(path.equals("/api/health")&&exchange.getRequestMethod().equals("GET")){
                RespuestaApi.enviar(exchange,200,Map.of("status","UP","system","REDXela","version","1.0.0"));
                return;
            }
            if(path.equals("/api/state")&&exchange.getRequestMethod().equals("GET")){
                RespuestaApi.enviar(exchange,200,simulation.snapshot());
                return;
            }
            if(path.equals("/api/events")&&exchange.getRequestMethod().equals("GET")){
                eventos.conectar(exchange);
                return;
            }
            if(!List.of(
                "/api/orders","/api/policy","/api/generator","/api/receipts",
                "/api/conflict/start","/api/conflict/resolve","/api/clients",
                "/api/clients/service","/api/aging","/api/clock","/api/comparison"
            ).contains(path)){
                RespuestaApi.enviar(exchange,404,Map.of("error","Ruta no encontrada"));
                return;
            }
            if(!exchange.getRequestMethod().equals("POST")){
                RespuestaApi.enviar(exchange,405,Map.of("error","Usa POST"));
                return;
            }
            switch(path) {
                case "/api/orders" -> {
                    List<LineaPedido> lines=new ArrayList<>();
                    String items=values.get("items");
                    if(items==null)throw new IllegalArgumentException("Indica productos: items=1:5,2:3");
                    if(items.length()>300)throw new IllegalArgumentException("Demasiadas lineas");
                    for(String item:items.split(",",-1)) {
                        String[] pair=item.split(":",-1);
                        if(pair.length!=2)throw new IllegalArgumentException("Formato items invalido");
                        lines.add(new LineaPedido(Integer.parseInt(pair[0]),Integer.parseInt(pair[1])));
                    }
                    RespuestaApi.enviar(exchange,201,simulation.createForClient(Long.parseLong(values.getOrDefault("clientId","0")),lines));
                }
                case "/api/conflict/start" -> {
                    simulation.startConflictForClient(
                        Long.parseLong(values.getOrDefault("clientId","0")),
                        Integer.parseInt(values.getOrDefault("productId","0")),
                        Integer.parseInt(values.getOrDefault("units","0"))
                    );
                    RespuestaApi.enviar(exchange,201,Map.of("ok",true));
                }
                case "/api/conflict/resolve" -> {
                    simulation.resolveConflict(Long.parseLong(values.getOrDefault("winner","0")));
                    RespuestaApi.enviar(exchange,200,Map.of("ok",true));
                }
                case "/api/clients" -> RespuestaApi.enviar(exchange,201,simulation.registerClient(
                    values.get("name"),values.get("email"),Integer.parseInt(values.getOrDefault("level","0"))
                ));
                case "/api/clients/service" -> {
                    simulation.changeClientService(
                        Long.parseLong(values.getOrDefault("clientId","0")),
                        Integer.parseInt(values.getOrDefault("level","0"))
                    );
                    RespuestaApi.enviar(exchange,200,Map.of("ok",true));
                }
                case "/api/receipts" -> RespuestaApi.enviar(exchange,202,simulation.receive(
                    Integer.parseInt(values.getOrDefault("productId","0")),
                    Integer.parseInt(values.getOrDefault("units","0"))
                ));
                case "/api/clock" -> {
                    simulation.setClockRate(Integer.parseInt(values.getOrDefault("rate","0")));
                    RespuestaApi.enviar(exchange,200,Map.of("ok",true));
                }
                case "/api/comparison" -> {
                    simulation.compare(
                        Long.parseLong(values.getOrDefault("seed","201930761")),
                        Integer.parseInt(values.getOrDefault("count","40"))
                    );
                    RespuestaApi.enviar(exchange,200,Map.of("ok",true));
                }
                case "/api/aging" -> {
                    simulation.setAgingSeconds(Integer.parseInt(values.getOrDefault("seconds","0")));
                    RespuestaApi.enviar(exchange,200,Map.of("ok",true));
                }
                case "/api/policy" -> {
                    simulation.setPolicy(values.getOrDefault("value",""));
                    RespuestaApi.enviar(exchange,200,Map.of("ok",true));
                }
                case "/api/generator" -> {
                    String enabled=values.getOrDefault("enabled","");
                    if(!List.of("true","false").contains(enabled))throw new IllegalArgumentException("enabled debe ser true o false");
                    simulation.setAutomatic(Boolean.parseBoolean(enabled));
                    RespuestaApi.enviar(exchange,200,Map.of("ok",true));
                }
                default -> RespuestaApi.enviar(exchange,404,Map.of("error","Ruta no encontrada"));
            }
        }catch(IllegalArgumentException ex){
            RespuestaApi.enviar(exchange,400,Map.of("error",ex.getMessage()==null?"Parametros invalidos":ex.getMessage()));
        }catch(Exception ex){
            ex.printStackTrace();
            RespuestaApi.enviar(exchange,500,Map.of("error","Error interno"));
        }finally{
            exchange.close();
        }
    }

}
