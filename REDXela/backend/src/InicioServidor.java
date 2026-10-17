import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.concurrent.*;
/** Inicia Java 21, servicio REST y bucles operativos. */
public final class InicioServidor {
    public static void main(String[] args) throws Exception {
        AlmacenEstado store;
        if(System.getenv().getOrDefault("REDXELA_MODE","postgres").equals("memory")){
            store=AlmacenEstado.memory();
        }else{
            store=new RepositorioPostgresql(
                System.getenv().getOrDefault("DATABASE_URL","jdbc:postgresql://localhost:5439/redxela_201930761"),
                System.getenv().getOrDefault("DATABASE_USER","redxela_app"),
                System.getenv().getOrDefault("DATABASE_PASSWORD","redxela_201930761_local"),
                java.nio.file.Path.of(System.getenv().getOrDefault("SCHEMA_PATH","db/V1__schema.sql"))
            );
        }
        CentroOperativo simulation=new CentroOperativo(Integer.parseInt(System.getenv().getOrDefault("SIMULATION_TIME_SCALE","1")),store);
        int port=Integer.parseInt(System.getenv().getOrDefault("PORT","8090"));
        HttpServer server=HttpServer.create(new InetSocketAddress(port),0);
        ExecutorService httpWorkers=Executors.newFixedThreadPool(12);
        server.createContext("/api",new EnrutadorApi(simulation)::atender);
        server.setExecutor(httpWorkers);
        server.start();
        simulation.start();
        Runtime.getRuntime().addShutdownHook(new Thread(()->{
            server.stop(0);
            simulation.close();
            httpWorkers.shutdownNow();
        }));
        System.out.println("REDXela 1.0 API: http://localhost:"+port+"/api/state");
    }
}
