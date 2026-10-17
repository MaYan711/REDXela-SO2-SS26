import java.io.Serializable;
import java.util.*;
final class PedidoOperativo implements Serializable {
        private static final long serialVersionUID=1L;
        final long id, created=System.nanoTime();
        final int level, units;
        long clientId;
        String clientName,clientEmail;
        final List<LineaPedido> lines;
        final List<EtapaOperacion> stages;
        int stageIndex;
        String state="EN_ESPERA", reason="Esperando planificador";
        long waitingSince=created, stageStarted, finished, waitNanos, processNanos;
        int finishedEstimatedMs;
        long pendingWaitNanos;
        int slaRate;
        transient List<RecursoOperativo> held=List.of();
        Map<Long,Integer> reservation;
        PedidoOperativo(long id, int level, List<LineaPedido> lines, List<EtapaOperacion> stages) {
            this.id=id; this.level=level; this.lines=List.copyOf(lines); this.stages=stages;
            units=lines.stream().mapToInt(LineaPedido::units).sum();
        }
        EtapaOperacion stage() { return stages.get(stageIndex); }
        long estimateMs() { return stages.stream().mapToLong(s -> (long)s.msPerUnit()*units).sum(); }
    }
