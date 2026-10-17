import java.util.*;
import java.util.concurrent.*;
/** Canal productor-consumidor de capacidad fija: mensajes retenidos en el estado persistido. */
final class CanalInventario {
    private final BlockingQueue<AvisoInventario> events;
    private final Set<Long> queued=new HashSet<>();
    CanalInventario(int capacity){events=new ArrayBlockingQueue<>(capacity);}
    boolean ofrecer(AvisoInventario event){return events.offer(event);}
    AvisoInventario recibir(long timeout,TimeUnit unit) throws InterruptedException {return events.poll(timeout,unit);}
    boolean estaPendiente(long id){return queued.contains(id);}
    void marcar(long id){queued.add(id);}
    void confirmar(long id){queued.remove(id);}
    int pendientes(){return events.size();}
}
