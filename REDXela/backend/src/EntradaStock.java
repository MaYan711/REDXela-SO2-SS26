import java.io.Serializable;
final class EntradaStock implements Serializable {
        private static final long serialVersionUID=1L;
        final long id;
        final int productId, units;
        String state="PENDIENTE_ESCANEO", reason="Esperando escaner";
        boolean scanned;
        long lotId;
        EntradaStock(long id,int productId,int units){this.id=id;this.productId=productId;this.units=units;}
    }
