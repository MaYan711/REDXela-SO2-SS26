import java.util.*;
/** Semáforos FIFO por recurso, titulares y tiempo acumulado de ocupación. */
final class AdministradorRecursos {
    private final LinkedHashMap<String,RecursoOperativo> resources=new LinkedHashMap<>();
    LinkedHashMap<String,RecursoOperativo> recursos(){return resources;}
    void registrar(String key,String name,int count){resources.put(key,new RecursoOperativo(key,name,count));}
    /** El coordinador llama asignar únicamente tras comprobar todos los permisos bajo gate. */
    void asignar(PedidoOperativo o,List<RecursoOperativo> requested,long now){
        for(RecursoOperativo r:requested){
            if(!r.permits.tryAcquire())throw new IllegalStateException("Permisos inconsistentes");
            for(int i=0;i<r.owners.length;i++)if(r.owners[i]==0){r.owners[i]=o.id;r.since[i]=now;break;}
        }
    }
    void liberar(PedidoOperativo o){
        long now=System.nanoTime();
        for(RecursoOperativo r:o.held){
            for(int i=0;i<r.owners.length;i++)if(r.owners[i]==o.id){r.busyNanos+=now-r.since[i];r.owners[i]=0;r.since[i]=0;break;}
            r.permits.release();
        }
        o.held=List.of();
    }
    void adquirirBloqueante(PedidoOperativo o,RecursoOperativo r,Object gate) throws InterruptedException {
        r.permits.acquire();
        synchronized(gate){
            for(int i=0;i<r.owners.length;i++)if(r.owners[i]==0){r.owners[i]=o.id;r.since[i]=System.nanoTime();break;}
            List<RecursoOperativo> held=new ArrayList<>(o.held);held.add(r);o.held=List.copyOf(held);
        }
    }
    List<Map<String,Object>> instantanea(long now,long born){
        List<Map<String,Object>> result=new ArrayList<>();
        for(RecursoOperativo r:resources.values()) {
            long busy=r.busyNanos;List<Map<String,Object>> slots=new ArrayList<>();
            for(int i=0;i<r.owners.length;i++) {
                if(r.owners[i]!=0)busy+=now-r.since[i];
                slots.add(Map.of("slot",i+1,"owner",r.owners[i],"state",r.owners[i]==0?"DISPONIBLE":"EN_USO","ownerLabel",r.owners[i]==0?"Disponible":r.owners[i]>0?"Pedido #"+r.owners[i]:"Recepcion #"+(-r.owners[i])));
            }
            result.add(Map.of("key",r.key,"name",r.name,"capacity",r.owners.length,"available",r.permits.availablePermits(),"slots",slots,"occupancyPercent",busy*100.0/(Math.max(1,now-born)*r.owners.length)));
        }
        return result;
    }
    Map<String,Integer> capacidadesExperimento(){
        Map<String,Integer> result=new LinkedHashMap<>();
        resources.forEach((k,v)->{if(!k.equals("CONTROL_CONFLICTO"))result.put(k,v.owners.length);});
        return result;
    }
}
