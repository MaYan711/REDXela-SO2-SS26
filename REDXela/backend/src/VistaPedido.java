import java.util.*;
/** Proyección compatible con los modelos TypeScript del panel Angular. */
final class VistaPedido {
    private final CatalogoMercancia catalogo;
    private final int timeScale,agingSeconds;
    private final String policy;
    VistaPedido(CatalogoMercancia catalogo,int timeScale,String policy,int agingSeconds) {
        this.catalogo=catalogo;this.timeScale=timeScale;this.policy=policy;this.agingSeconds=agingSeconds;
    }
    private boolean isWaiting(PedidoOperativo o){return List.of("EN_ESPERA","ESPERA_STOCK","BLOQUEADO","CONFLICTO_REINTENTO").contains(o.state);}
    private long waitingNanos(PedidoOperativo o,long now){return o.waitNanos+(isWaiting(o)?Math.max(0,now-o.waitingSince):0);}
    private int effectiveLevel(PedidoOperativo o,long now){return policy.equals("ENVEJECIMIENTO")?PoliticasAtencion.effectiveLevel(o.level,waitingNanos(o,now)/1_000_000,agingSeconds):o.level;}
    Map<String,Object> convertir(PedidoOperativo o,long now) {
        long elapsed=o.stageStarted==0?0:(now-o.stageStarted)/1_000_000*timeScale;
        long estimated=o.estimateMs();
        long worked=o.finishedEstimatedMs;
        if(o.stageStarted>0)worked+=Math.min(elapsed,(long)o.stage().msPerUnit()*o.units);
        long waiting=waitingNanos(o,now);
        List<Map<String,Object>> lines=o.lines.stream().map(l->Map.<String,Object>of("productId",l.productId(),"name",catalogo.producto(l.productId()).name(),"type",catalogo.producto(l.productId()).type(),"units",l.units())).toList();
        Map<String,Object> s=new LinkedHashMap<>();
        s.put("id",o.id);s.put("clientId",o.clientId);s.put("clientName",o.clientId==0?"Pedido de version anterior":o.clientName);s.put("clientEmail",o.clientEmail==null?"":o.clientEmail);s.put("level",o.level);s.put("units",o.units);s.put("lines",lines);
        s.put("state",o.state);s.put("stage",o.state.equals("COMPLETADO")?"FINALIZADO":o.stage().name());
        s.put("reason",o.reason);s.put("progress",Math.min(100,worked*100/estimated));
        s.put("remainingMs",Math.max(0,estimated-worked));s.put("waitMs",waiting/1_000_000);
        int effective=effectiveLevel(o,now);
        s.put("effectiveLevel",effective);s.put("agingPromotions",o.level-effective);
        s.put("nextPromotionMs",policy.equals("ENVEJECIMIENTO")&&isWaiting(o)&&effective>1?(agingSeconds*1_000_000_000L-waiting%(agingSeconds*1_000_000_000L))/1_000_000:0);
        s.put("slaRate",o.slaRate);s.put("virtualWaitMs",waiting/1_000_000*o.slaRate);s.put("slaLimitMs",PoliticasAtencion.limitMs(o.level));
        s.put("slaStatus",o.slaRate==0?"SIN_MEDICION":PoliticasAtencion.sla(waiting/1_000_000*o.slaRate,o.level,o.state.equals("COMPLETADO")));
        s.put("heldResources",o.held.stream().map(r->r.key).toList());
        return s;
    }

}
