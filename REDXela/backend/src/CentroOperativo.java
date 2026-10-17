import java.io.*;
import java.util.*;
import java.util.concurrent.*;


/**
 * Núcleo concurrente del centro: coordina hilos de pedidos, recepción e inventario.
 * Todas las transiciones de estado compartido se realizan dentro del monitor gate.
 * La interfaz HTTP nunca modifica directamente el almacén ni los semáforos.
 */
public final class CentroOperativo implements AutoCloseable {
        private final List<CuentaCliente> clients=new ArrayList<>();
    private long nextClientId;
                    private final Object gate=new Object();

    private final AlmacenEstado store;

    private InventarioEspacial warehouse=new InventarioEspacial();

    private final List<EntradaStock> receipts=new ArrayList<>();

    private final CanalInventario buzon=new CanalInventario(8);

    private final ExecutorService areas=Executors.newFixedThreadPool(2);

    private String persistenceError="";

    private long nextReceiptId;

    private boolean recovered;


    private final List<PedidoOperativo> orders=new ArrayList<>();

    private final AdministradorRecursos administrador = new AdministradorRecursos();
    private final LinkedHashMap<String,RecursoOperativo> resources=administrador.recursos();

    private final CatalogoMercancia catalogo = new CatalogoMercancia();
    private final ConstructorEtapas flujo = new ConstructorEtapas(catalogo);

    private final ExecutorService workers=Executors.newFixedThreadPool(16);


    private final ScheduledExecutorService timers=Executors.newScheduledThreadPool(2);

    private final long born=System.nanoTime();

    private final int timeScale;

    private String policy="PRIORIDAD";

    private int agingSeconds=30;

    private int clockRate=60;

    private Map<String,Object> comparison=Map.of();

    private long lastWaitCheckpoint=System.nanoTime();

    private boolean automatic, closed;

    private PedidoOperativo conflictA, conflictB;

    private Thread conflictThreadA, conflictThreadB;

    private String conflictState="DISPONIBLE";

    private long conflictWinner;

    private int conflictReady;

    private final List<String> conflictLog=new ArrayList<>();

    private long nextId;

    public CentroOperativo() { this(1,AlmacenEstado.memory()); }


    CentroOperativo(int timeScale) { this(timeScale,AlmacenEstado.memory()); }
    CentroOperativo(int timeScale,AlmacenEstado store) {
        if(timeScale<1||timeScale>100)throw new IllegalArgumentException("Escala permitida: 1 a 100");
        this.timeScale=timeScale;this.store=store;
        addResource("BODEGA","Personal de bodega",10);
        addResource("MONTACARGAS","Montacargas",3);
        addResource("EMPAQUE","Estaciones de empaque",6);
        addResource("CALIDAD","Control de calidad",4);
        addResource("ESCANER","Escaner compartido",1);
        addResource("CARGA","Areas de carga",2);
        addResource("CONTROL_CONFLICTO","Control de despacho (demostracion)",1);
        restore();
        persist();
    }

    private void addResource(String key,String name,int count) {administrador.registrar(key,name,count);}

    public void start() {
        areas.submit(this::receptionLoop);areas.submit(this::inventoryLoop);
        timers.scheduleWithFixedDelay(()->{try{tick();}catch(RuntimeException ex){System.err.println(ex.getMessage());}},0,50,TimeUnit.MILLISECONDS);
        timers.scheduleWithFixedDelay(()->{
            synchronized(gate) {
                if(automatic && !closed && persistenceError.isEmpty()) {
                    var random=ThreadLocalRandom.current();
                    try{if(!clients.isEmpty())createForClient(clients.get(random.nextInt(clients.size())).id,List.of(new LineaPedido(random.nextInt(1,11),random.nextInt(1,13))));}
                    catch(RuntimeException ex){System.err.println(ex.getMessage());}
                }
            }
        },0,1,TimeUnit.SECONDS);
    }

    public Map<String,Object> registerClient(String name,String email,int level) {
        synchronized(gate){
            healthy();
            if(name==null||name.strip().length()<2||name.strip().length()>100||name.chars().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Nombre permitido: 2 a 100 caracteres sin caracteres de control");
            if(email==null||email.strip().length()>150||!email.strip().matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))throw new IllegalArgumentException("Correo invalido");
            if(level<1||level>5)throw new IllegalArgumentException("Servicio permitido: 1 a 5");
            String normalized=email.strip().toLowerCase(Locale.ROOT);
            if(clients.stream().anyMatch(c->c.email.equals(normalized)))throw new IllegalArgumentException("Ya existe un cliente con ese correo");
            CuentaCliente c=new CuentaCliente(++nextClientId,name.strip(),normalized,level);clients.add(c);persist();return clientSnapshot(c);
        }
    }

    private CuentaCliente client(long id){return clients.stream().filter(c->c.id==id).findFirst().orElseThrow(()->new IllegalArgumentException("Cliente inexistente"));}

    private Map<String,Object> clientSnapshot(CuentaCliente c){
        return Map.of("id",c.id,"name",c.name,"email",c.email,"level",c.level,"orders",orders.stream().filter(o->o.clientId==c.id).count(),"completed",orders.stream().filter(o->o.clientId==c.id&&o.state.equals("COMPLETADO")).count());
    }

    public void changeClientService(long id,int level){
        synchronized(gate){healthy();if(level<1||level>5)throw new IllegalArgumentException("Servicio permitido: 1 a 5");client(id).level=level;persist();}
    }

    public Map<String,Object> createForClient(long clientId,List<LineaPedido> input){
        synchronized(gate){healthy();CuentaCliente c=client(clientId);return createOrder(c.level,input,c);}
    }
    Map<String,Object> create(int level,List<LineaPedido> input){return createOrder(level,input,null);}

    private Map<String,Object> createOrder(int level,List<LineaPedido> input,CuentaCliente client) {
        synchronized(gate) {
            healthy();
            if(level<1 || level>5)throw new IllegalArgumentException("Nivel permitido: 1 a 5");
            if(input.isEmpty() || input.size()>10)throw new IllegalArgumentException("Selecciona entre 1 y 10 lineas");
            Map<Integer,Integer> merged=new LinkedHashMap<>();
            for(LineaPedido l:input) {
                catalogo.producto(l.productId());
                if(l.units()<1 || l.units()>600)throw new IllegalArgumentException("Unidades permitidas: 1 a 600");
                merged.merge(l.productId(),l.units(),Integer::sum);
            }
            int total=merged.values().stream().mapToInt(Integer::intValue).sum();
            if(total>InventarioEspacial.CELL_COUNT*InventarioEspacial.CELL_CAPACITY)throw new IllegalArgumentException("Maximo 600 unidades por pedido: capacidad fisica del almacen");
            List<LineaPedido> lines=merged.entrySet().stream().map(e->new LineaPedido(e.getKey(),e.getValue())).toList();
            PedidoOperativo o=new PedidoOperativo(++nextId,level,lines,flujo.etapas(lines));o.slaRate=clockRate;associate(o,client);o.state="ESPERA_STOCK";o.reason="Evaluando existencias";orders.add(o);persist();
            return orderSnapshot(o,System.nanoTime());
        }
    }


    public void setPolicy(String value) {
        if(!PoliticasAtencion.POLICIES.contains(value))throw new IllegalArgumentException("Politica invalida");
        synchronized(gate){healthy();policy=value;persist();}
    }


    public void setAutomatic(boolean enabled){synchronized(gate){healthy();if(enabled&&clients.isEmpty())throw new IllegalArgumentException("Registra al menos un cliente antes de iniciar el generador");automatic=enabled;}}

    public void setAgingSeconds(int seconds){

        synchronized(gate){healthy();if(seconds<1||seconds>3600)throw new IllegalArgumentException("Intervalo permitido: 1 a 3600 segundos");agingSeconds=seconds;persist();}
    }

    private boolean isWaiting(PedidoOperativo o){return List.of("EN_ESPERA","ESPERA_STOCK","BLOQUEADO","CONFLICTO_REINTENTO").contains(o.state);}

    private long waitingNanos(PedidoOperativo o,long now){return o.waitNanos+(isWaiting(o)?Math.max(0,now-o.waitingSince):0);}

    private int effectiveLevel(PedidoOperativo o,long now){
        if(!policy.equals("ENVEJECIMIENTO"))return o.level;
        return PoliticasAtencion.effectiveLevel(o.level,waitingNanos(o,now)/1_000_000,agingSeconds);
    }

    private Comparator<PedidoOperativo> comparator() {
        long now=System.nanoTime();
        Comparator<PedidoOperativo> c=Comparator.comparingLong(o->PoliticasAtencion.rank(policy,o.id,o.level,o.units,waitingNanos(o,now)/1_000_000,agingSeconds));
        return c.thenComparingLong(o->o.id);
    }

    /** Planificación no bloqueante: reserva stock y toma el conjunto de recursos
     * solamente cuando todos están disponibles, evitando retención parcial. */
    void tick() {
        synchronized(gate) {
            if(closed||!persistenceError.isEmpty())return;
            List<PedidoOperativo> waiting=orders.stream().filter(o->o.state.equals("EN_ESPERA")||o.state.equals("ESPERA_STOCK")).sorted(comparator()).toList();
            boolean dirty=false;
            for(PedidoOperativo o:waiting) {
                if(o.reservation==null) {
                    String shortage=warehouse.shortage(o.lines);
                    if(!shortage.isEmpty()) {
                        String reason="Stock insuficiente: "+shortage;
                        dirty|=!o.state.equals("ESPERA_STOCK")||!o.reason.equals(reason);
                        o.state="ESPERA_STOCK";o.reason=reason;continue;
                    }
                    o.reservation=warehouse.reserve(o.lines);o.state="EN_ESPERA";o.reason="Esperando recursos";persist();
                }
                List<RecursoOperativo> needed=o.stage().needs().stream().map(resources::get).toList();
                List<String> missing=needed.stream().filter(r->r.permits.availablePermits()==0).map(r->r.name).toList();
                if(!missing.isEmpty()){String reason="Sin disponibilidad: "+String.join(", ",missing);dirty|=!o.reason.equals(reason);o.reason=reason;continue;}
                long now=System.nanoTime();
                administrador.asignar(o,needed,now);
                o.held=needed;o.waitNanos+=now-o.waitingSince;o.stageStarted=now;
                o.state="PROCESANDO";o.reason="";
                try{persist();workers.submit(()->runStage(o));}
                catch(RuntimeException ex){release(o);o.state="PAUSADO_PERSISTENCIA";o.reason=ex.getMessage();throw ex;}
            }
            if(dirty||(!waiting.isEmpty()&&System.nanoTime()-lastWaitCheckpoint>=5_000_000_000L))persist();
        }
    }

    /** Cada etapa se procesa en un hilo del pool y libera los semáforos al terminar. */
    private void runStage(PedidoOperativo o) {
        boolean success=false;
        try {
            Thread.sleep(Math.max(1,(long)o.stage().msPerUnit()*o.units/timeScale));success=true;
        } catch(InterruptedException ex){Thread.currentThread().interrupt();}
        finally {
            synchronized(gate) {
                long now=System.nanoTime();o.processNanos+=now-o.stageStarted;
                release(o);o.stageStarted=0;
                if(success && !closed && persistenceError.isEmpty()) {
                    o.finishedEstimatedMs+=o.stage().msPerUnit()*o.units;
                    o.stageIndex++;
                    if(o.stageIndex==o.stages.size()){warehouse.consume(o.reservation);o.reservation=Map.of();o.state="COMPLETADO";o.finished=now;}
                    else{o.state="EN_ESPERA";o.waitingSince=now;o.reason="Esperando recursos de "+o.stage().name();}
                } else {o.state=persistenceError.isEmpty()?"EN_ESPERA":"PAUSADO_PERSISTENCIA";o.waitingSince=now;o.reason="Etapa pausada; conserva su reserva";}
                if(persistenceError.isEmpty())try{persist();}catch(RuntimeException ex){System.err.println(ex.getMessage());}
            }
        }
    }

    private void release(PedidoOperativo o) {administrador.liberar(o);}

    private Map<String,Object> orderSnapshot(PedidoOperativo o,long now) {
        return new VistaPedido(catalogo, timeScale, policy, agingSeconds).convertir(o,now);
    }

    public Map<String,Object> snapshot() {
        synchronized(gate) {
            long now=System.nanoTime();List<PedidoOperativo> done=orders.stream().filter(o->o.state.equals("COMPLETADO")).toList();
            Map<String,Object> s=new LinkedHashMap<>();
            s.put("version","1.0.0");s.put("persistence",store.mode());s.put("persistenceError",persistenceError);s.put("recovered",recovered);s.put("policy",policy);s.put("automatic",automatic);s.put("agingSeconds",agingSeconds);s.put("clockRate",clockRate);s.put("comparison",comparison);s.put("serviceMetrics",MedidorNivelServicio.calcular(orders,now));
            s.put("total",orders.size());s.put("completed",done.size());
            s.put("activeCount",orders.stream().filter(o->o.state.equals("PROCESANDO")).count());
            s.put("waitingCount",orders.stream().filter(o->List.of("EN_ESPERA","ESPERA_STOCK","BLOQUEADO","CONFLICTO_REINTENTO").contains(o.state)).count());
            s.put("averageWaitMs",done.stream().mapToLong(o->o.waitNanos/1_000_000).average().orElse(0));
            s.put("averageProcessMs",done.stream().mapToLong(o->o.processNanos/1_000_000).average().orElse(0));
            List<Map<String,Object>> resourceData=administrador.instantanea(now,born);
            s.put("resources",resourceData);
            s.put("conflict",conflictSnapshot());
            s.put("clients",clients.stream().map(this::clientSnapshot).toList());
            s.put("products",catalogo.productos().stream().map(v->Map.of("id",v.id(),"name",v.name(),"type",v.type())).toList());
            s.put("types",catalogo.necesidades().entrySet().stream().sorted(Map.Entry.comparingByKey()).map(e->Map.of("name",e.getKey(),"needs",e.getValue(),"description",catalogo.descripcion(e.getKey()))).toList());
            s.put("active",orders.stream().filter(o->o.state.equals("PROCESANDO")).map(o->orderSnapshot(o,now)).toList());
            s.put("waiting",orders.stream().filter(o->o.state.equals("EN_ESPERA")||o.state.equals("ESPERA_STOCK")||o.state.equals("PAUSADO_PERSISTENCIA")||o.state.equals("BLOQUEADO")||o.state.equals("CONFLICTO_REINTENTO")).sorted(comparator()).map(o->orderSnapshot(o,now)).toList());
            // Historial visible acotado; el total y los promedios conservan toda la corrida.
            s.put("history",orders.stream().filter(o->o.state.equals("COMPLETADO")||o.state.equals("INTERRUMPIDO")).sorted(Comparator.comparingLong((PedidoOperativo o)->o.id).reversed()).limit(100).map(o->orderSnapshot(o,now)).toList());
            s.put("warehouse",warehouse.snapshot(catalogo.productos()));
            s.put("receipts",receipts.stream().map(this::receiptSnapshot).toList());
            s.put("channel",Map.of("capacity",8,"queued",buzon.pendientes(),"durableBacklog",receipts.stream().filter(r->!r.state.equals("REGISTRADA")).count()));
            return s;
        }
    }

    private void associate(PedidoOperativo o,CuentaCliente c){if(c!=null){o.clientId=c.id;o.clientName=c.name;o.clientEmail=c.email;}}
    public void startConflictForClient(long clientId,int productId,int units){synchronized(gate){healthy();startConflictInternal(productId,units,client(clientId));}}
    void startConflict(int productId,int units){startConflictInternal(productId,units,null);}

    /** Escenario deliberado: dos hilos adquieren ESCANER y CONTROL_CONFLICTO
     * en orden inverso para hacer visible una espera circular recuperable. */
    private void startConflictInternal(int productId,int units,CuentaCliente client) {
        synchronized(gate) {
            healthy();catalogo.producto(productId);
            if(!List.of("DISPONIBLE","RESUELTO").contains(conflictState))throw new IllegalArgumentException("Ya existe un conflicto activo");
            if(units<1||units>300)throw new IllegalArgumentException("Unidades por pedido: 1 a 300");
            if(!warehouse.shortage(List.of(new LineaPedido(productId,units*2))).isEmpty())throw new IllegalArgumentException("Se necesita stock disponible para los dos pedidos");
            conflictLog.clear();conflictWinner=0;conflictReady=0;conflictState="PREPARANDO";
            conflictA=new PedidoOperativo(++nextId,client==null?3:client.level,List.of(new LineaPedido(productId,units)),flujo.etapas(List.of(new LineaPedido(productId,units))));
            conflictB=new PedidoOperativo(++nextId,client==null?3:client.level,List.of(new LineaPedido(productId,units)),flujo.etapas(List.of(new LineaPedido(productId,units))));
            for(PedidoOperativo o:List.of(conflictA,conflictB)){o.slaRate=clockRate;associate(o,client);o.reservation=warehouse.reserve(o.lines);o.state="BLOQUEADO";o.reason="Preparando adquisicion inversa";orders.add(o);}
            persist();
            CountDownLatch firstHeld=new CountDownLatch(2);
            conflictThreadA=new Thread(()->conflictRun(conflictA,"ESCANER","CONTROL_CONFLICTO",firstHeld),"conflicto-pedido-"+conflictA.id);
            conflictThreadB=new Thread(()->conflictRun(conflictB,"CONTROL_CONFLICTO","ESCANER",firstHeld),"conflicto-pedido-"+conflictB.id);
            conflictThreadA.start();conflictThreadB.start();
        }
    }

    private void conflictAcquire(PedidoOperativo o,RecursoOperativo r) throws InterruptedException {
        administrador.adquirirBloqueante(o,r,gate);
        synchronized(gate){conflictLog.add("Pedido #"+o.id+" adquiere "+r.name);}
    }

    private void conflictRun(PedidoOperativo o,String first,String second,CountDownLatch firstHeld) {
        boolean continued=false;
        try {
            conflictAcquire(o,resources.get(first));firstHeld.countDown();firstHeld.await();
            synchronized(gate){
                o.reason="Retiene "+resources.get(first).name+"; espera "+resources.get(second).name;
                if(++conflictReady==2&&conflictState.equals("PREPARANDO")){conflictState="INTERBLOQUEO";}
                persist();
            }
            conflictAcquire(o,resources.get(second));
            synchronized(gate){o.reason="Conflicto liberado; prepara regreso al flujo";persist();}
            Thread.sleep(Math.max(1,200/timeScale));continued=true;
        } catch(InterruptedException ex){Thread.currentThread().interrupt();}
        catch(RuntimeException ex){System.err.println("Conflicto pausado: "+ex.getMessage());}
        finally {
            synchronized(gate){
                release(o);
                if(continued){
                    conflictState="RESUELTO";conflictLog.add("Pedido #"+o.id+" continua; ambos pedidos regresan al flujo normal");
                    for(PedidoOperativo v:List.of(conflictA,conflictB)){v.state="EN_ESPERA";v.reason="Conflicto resuelto; conserva reserva";}
                }else{o.state=closed?"EN_ESPERA":"CONFLICTO_REINTENTO";o.reason="Devuelto a cola; espera liberacion del conflicto";conflictLog.add("Pedido #"+o.id+" libera sus recursos sin consumir stock");}
                if(!closed&&persistenceError.isEmpty())try{persist();}catch(RuntimeException ex){System.err.println(ex.getMessage());}
            }
        }
    }

    public void resolveConflict(long winner) {
        synchronized(gate){
            healthy();
            if(!conflictState.equals("INTERBLOQUEO"))throw new IllegalArgumentException("No hay un interbloqueo listo para resolver");
            if(winner!=conflictA.id&&winner!=conflictB.id)throw new IllegalArgumentException("Selecciona uno de los dos pedidos");
            conflictWinner=winner;conflictState="RESOLVIENDO";
            conflictLog.add("Usuario elige pedido #"+winner+"; se interrumpe al otro hilo");
            persist();(winner==conflictA.id?conflictThreadB:conflictThreadA).interrupt();
        }
    }

    private Map<String,Object> conflictSnapshot(){
        long now=System.nanoTime();
        return Map.of("state",conflictState,"winner",conflictWinner,"orders",conflictA==null?List.of():List.of(orderSnapshot(conflictA,now),orderSnapshot(conflictB,now)),"events",List.copyOf(conflictLog));
    }


    public void setClockRate(int rate){synchronized(gate){healthy();if(rate<1||rate>3600)throw new IllegalArgumentException("Escala permitida: 1 a 3600");clockRate=rate;persist();}}

    public void compare(long seed,int count){
        int rate,aging;synchronized(gate){healthy();rate=clockRate;aging=agingSeconds;}
        Map<String,Object> result=AnalizadorPoliticas.run(seed,count,rate,aging);
        synchronized(gate){healthy();comparison=result;persist();}
    }
    List<PoliticasAtencion.Step> experimentSteps(List<LineaPedido> lines){int units=lines.stream().mapToInt(LineaPedido::units).sum();return flujo.etapas(lines).stream().map(s->new PoliticasAtencion.Step(s.name(),s.msPerUnit()*units,s.needs())).toList();}
    Map<String,Integer> experimentCapacities(){return administrador.capacidadesExperimento();}

    private void healthy(){
        if(closed)throw new IllegalStateException("Simulacion cerrada");
        if(!persistenceError.isEmpty())throw new IllegalStateException(persistenceError);
    }

    private Map<String,Object> receiptSnapshot(EntradaStock r){
        return Map.of("id",r.id,"productId",r.productId,"product",catalogo.producto(r.productId).name(),"units",r.units,"state",r.state,"reason",r.reason,"lotId",r.lotId);
    }

    public Map<String,Object> receive(int productId,int units){
        synchronized(gate){
            healthy();catalogo.producto(productId);
            if(units<1||units>600)throw new IllegalArgumentException("Recepcion permitida: 1 a 600 unidades");
            EntradaStock r=new EntradaStock(++nextReceiptId,productId,units);receipts.add(r);persist();
            return receiptSnapshot(r);
        }
    }

    void seedStock(int productId,int units){synchronized(gate){catalogo.producto(productId);if(warehouse.receive(0,productId,units)==0)throw new IllegalArgumentException("Stock de prueba excede capacidad");persist();}}


    private EntradaStock receipt(long id){return receipts.stream().filter(r->r.id==id).findFirst().orElseThrow();}


    private void releaseScan(EntradaStock r){
        RecursoOperativo scanner=resources.get("ESCANER");
        if(scanner.owners[0]==-r.id){scanner.busyNanos+=System.nanoTime()-scanner.since[0];scanner.owners[0]=0;scanner.since[0]=0;scanner.permits.release();}
    }

    /** Productor independiente: comparte el escáner con despacho y encola avisos. */
    private void receptionLoop(){
        try {
            while(!Thread.currentThread().isInterrupted()){
                EntradaStock scanning=null;
                synchronized(gate){
                    if(closed)return;
                    if(persistenceError.isEmpty()){
                        for(EntradaStock r:receipts)if(r.state.equals("ESPERANDO_CANAL")&&!buzon.estaPendiente(r.id)){
                            if(!buzon.ofrecer(new AvisoInventario(r.id,r.productId,r.units))){r.reason="Canal lleno; recepcion conservada en PostgreSQL";break;}
                            buzon.marcar(r.id);r.state="EN_COLA_INVENTARIO";r.reason="Mensaje enviado a Inventario";persist();
                        }
                        RecursoOperativo scanner=resources.get("ESCANER");
                        if(scanner.permits.availablePermits()>0){
                            scanning=receipts.stream().filter(r->r.state.equals("PENDIENTE_ESCANEO")).findFirst().orElse(null);
                            if(scanning!=null){scanner.permits.acquire();scanner.owners[0]=-scanning.id;scanner.since[0]=System.nanoTime();scanning.state="ESCANEANDO";scanning.reason="Recepcion utiliza escaner compartido";persist();}
                        }
                    }
                }
                if(scanning==null){Thread.sleep(30);continue;}
                boolean finished=false;
                try{Thread.sleep(Math.max(1,(100L+scanning.units*5L)/timeScale));finished=true;}
                finally{
                    synchronized(gate){
                        releaseScan(scanning);
                        if(finished&&!closed&&persistenceError.isEmpty()){scanning.scanned=true;scanning.state="ESPERANDO_CANAL";scanning.reason="Esperando enviar mensaje";persist();}
                        else{scanning.state="PENDIENTE_ESCANEO";scanning.reason="Escaneo pausado";}
                    }
                }
            }
        }catch(InterruptedException ex){Thread.currentThread().interrupt();}
        catch(RuntimeException ex){System.err.println("Recepcion pausada: "+ex.getMessage());}
        finally{synchronized(gate){for(EntradaStock r:receipts)releaseScan(r);}}
    }

    /** Consumidor: registra lotes idempotentes y reactiva pedidos sin stock. */
    private void inventoryLoop(){
        try {
            while(!Thread.currentThread().isInterrupted()){
                boolean halted;
                synchronized(gate){if(closed)return;halted=!persistenceError.isEmpty();}
                if(halted){Thread.sleep(100);continue;}
                AvisoInventario event=buzon.recibir(100,TimeUnit.MILLISECONDS);
                EntradaStock r=null;
                synchronized(gate){
                    if(closed)return;
                    if(!persistenceError.isEmpty())continue;
                    if(event!=null){buzon.confirmar(event.receiptId());r=receipt(event.receiptId());if(r.state.equals("REGISTRADA"))continue;}
                    else r=receipts.stream().filter(v->v.state.equals("ESPERA_ESPACIO")&&v.units<=warehouse.free()).findFirst().orElse(null);
                    if(r!=null){r.state="INVENTARIO_PROCESANDO";r.reason="Inventario asigna ubicaciones";persist();}
                }
                if(r==null)continue;
                Thread.sleep(Math.max(1,1200/timeScale));
                synchronized(gate){
                    if(closed||!persistenceError.isEmpty())continue;
                    // Idempotencia: un mensaje repetido no puede crear un segundo lote.
                    if(r.lotId!=0)continue;
                    long lot=warehouse.receive(r.id,r.productId,r.units);
                    if(lot==0){r.state="ESPERA_ESPACIO";r.reason="Faltan "+(r.units-warehouse.free())+" espacios; se reintenta automaticamente";}
                    else{r.lotId=lot;r.state="REGISTRADA";r.reason="Lote #"+lot+" disponible en Inventario";}
                    persist();
                }
            }
        }catch(InterruptedException ex){Thread.currentThread().interrupt();}
        catch(RuntimeException ex){System.err.println("Inventario pausado: "+ex.getMessage());}
    }

    /** Checkpoint binario + proyección JSON: escrituras transaccionales en JDBC. */
    private void persist(){
        if(!persistenceError.isEmpty())return;
        try {
            long now=System.nanoTime();
            for(PedidoOperativo o:orders)o.pendingWaitNanos=isWaiting(o)?Math.max(0,now-o.waitingSince):0;
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            try(ObjectOutputStream out=new ObjectOutputStream(bytes)){out.writeObject(new EstadoGuardado(3,orders,receipts,warehouse,nextId,nextReceiptId,policy,clients,nextClientId,agingSeconds,clockRate,comparison));}
            Map<String,Object> projection=new LinkedHashMap<>(snapshot());
            projection.put("orders",orders.stream().map(o->orderSnapshot(o,System.nanoTime())).toList());
            store.save(bytes.toByteArray(),ConversorJson.encode(projection));lastWaitCheckpoint=now;
        }catch(Exception ex){persistenceError="Persistencia detenida. Reinicia backend cuando PostgreSQL este disponible.";automatic=false;throw new IllegalStateException(persistenceError,ex);}
    }
    /** Recupera reservas y devuelve operaciones incompletas a la cola. */
    private void restore(){
        byte[] bytes=store.load();if(bytes==null)return;
        try(ObjectInputStream in=new ObjectInputStream(new ByteArrayInputStream(bytes))){
            EstadoGuardado state=(EstadoGuardado)in.readObject();if(state.format()!=3)throw new IOException("Formato incompatible");
            orders.addAll(state.orders());receipts.addAll(state.receipts());warehouse=state.warehouse();
            nextId=state.nextId();nextReceiptId=state.nextReceiptId();policy=state.policy();recovered=true;
            if(state.clients()!=null)clients.addAll(state.clients());nextClientId=state.nextClientId();
            agingSeconds=state.agingSeconds()==0?30:state.agingSeconds();
            clockRate=state.clockRate()==0?60:state.clockRate();comparison=state.comparison()==null?Map.of():state.comparison();
            for(PedidoOperativo o:orders){
                o.waitNanos+=o.pendingWaitNanos;o.pendingWaitNanos=0;
                o.held=List.of();o.stageStarted=0;o.waitingSince=System.nanoTime();
                if(!o.state.equals("COMPLETADO")){o.state=o.reservation==null?"ESPERA_STOCK":"EN_ESPERA";o.reason="Recuperado desde PostgreSQL";}
            }
            for(EntradaStock r:receipts)if(!List.of("REGISTRADA","ESPERA_ESPACIO").contains(r.state)){r.state=r.scanned?"ESPERANDO_CANAL":"PENDIENTE_ESCANEO";r.reason="Recepcion recuperada";}
        }catch(Exception ex){throw new IllegalStateException("Checkpoint incompatible o corrupto; no se modificaron datos",ex);}
    }
    @Override public void close() {
        synchronized(gate){closed=true;automatic=false;if(conflictThreadA!=null)conflictThreadA.interrupt();if(conflictThreadB!=null)conflictThreadB.interrupt();}
        try{if(conflictThreadA!=null)conflictThreadA.join(5000);if(conflictThreadB!=null)conflictThreadB.join(5000);}catch(InterruptedException ex){Thread.currentThread().interrupt();}
        timers.shutdownNow();workers.shutdownNow();areas.shutdownNow();
        try{workers.awaitTermination(5,TimeUnit.SECONDS);areas.awaitTermination(5,TimeUnit.SECONDS);}
        catch(InterruptedException ex){Thread.currentThread().interrupt();}
        synchronized(gate){if(persistenceError.isEmpty())try{persist();}catch(RuntimeException ex){System.err.println(ex.getMessage());}store.close();}
    }
}
