import java.io.Serializable;
import java.util.*;
record EstadoGuardado(int format,List<PedidoOperativo> orders,List<EntradaStock> receipts,InventarioEspacial warehouse,
                                long nextId,long nextReceiptId,String policy,List<CuentaCliente> clients,long nextClientId,int agingSeconds,int clockRate,Map<String,Object> comparison) implements Serializable { }
