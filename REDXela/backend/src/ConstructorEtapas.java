import java.util.*;
/** Convierte mercancías de un pedido en etapas y recursos operativos reales. */
final class ConstructorEtapas {
    private final CatalogoMercancia catalogo;
    ConstructorEtapas(CatalogoMercancia catalogo){ this.catalogo=catalogo; }
    List<EtapaOperacion> etapas(List<LineaPedido> lines) {
        Set<String> needs=new HashSet<>();
        for(LineaPedido l:lines)needs.addAll(catalogo.necesidades().get(catalogo.producto(l.productId()).type()));
        List<EtapaOperacion> stages=new ArrayList<>();
        stages.add(new EtapaOperacion("PREPARACION",150,needs.contains("MONTACARGAS")?List.of("BODEGA","MONTACARGAS"):List.of("BODEGA")));
        if(needs.contains("EMPAQUE"))stages.add(new EtapaOperacion("EMPAQUE",250,List.of("BODEGA","EMPAQUE")));
        if(needs.contains("CALIDAD"))stages.add(new EtapaOperacion("CALIDAD",180,List.of("BODEGA","CALIDAD")));
        // El escaneo termina antes de cargar: no monopoliza el escaner durante la carga.
        stages.add(new EtapaOperacion("DESPACHO_ESCANEO",40,List.of("BODEGA","ESCANER")));
        stages.add(new EtapaOperacion("DESPACHO_CARGA",80,needs.contains("MONTACARGAS")?List.of("BODEGA","MONTACARGAS","CARGA"):List.of("BODEGA","CARGA")));
        return List.copyOf(stages);
    }
}
