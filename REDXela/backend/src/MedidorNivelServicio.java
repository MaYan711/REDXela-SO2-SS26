import java.util.*;
/** Indicadores SLA de cinco niveles medidos con reloj virtual. */
final class MedidorNivelServicio {
    private MedidorNivelServicio() { }
    private static long espera(PedidoOperativo o,long now) {
        return o.waitNanos+(List.of("EN_ESPERA","ESPERA_STOCK","BLOQUEADO","CONFLICTO_REINTENTO").contains(o.state)?Math.max(0,now-o.waitingSince):0);
    }
    static List<Map<String,Object>> calcular(List<PedidoOperativo> orders,long now){
        List<Map<String,Object>> result=new ArrayList<>();
        for(int level=1;level<=5;level++){
            final int l=level;var measured=orders.stream().filter(o->o.level==l&&o.slaRate>0).toList();
            var done=measured.stream().filter(o->o.state.equals("COMPLETADO")).toList();
            long missed=done.stream().filter(o->o.waitNanos/1_000_000*o.slaRate>PoliticasAtencion.limitMs(l)).count();
            result.add(Map.of("level",l,"limitMinutes",PoliticasAtencion.limitMs(l)/60000,"measured",measured.size(),"completed",done.size(),"met",done.size()-missed,"missed",missed,"overduePending",measured.stream().filter(o->!o.state.equals("COMPLETADO")&&espera(o,now)/1_000_000*o.slaRate>PoliticasAtencion.limitMs(l)).count(),"averageWaitMinutes",done.stream().mapToLong(o->o.waitNanos/1_000_000*o.slaRate).average().orElse(0)/60000,"unmeasured",orders.stream().filter(o->o.level==l&&o.slaRate==0).count()));
        }
        return result;
    }

}
