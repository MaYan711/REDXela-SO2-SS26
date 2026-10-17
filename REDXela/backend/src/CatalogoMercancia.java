import java.util.*;
/** Catálogo fijo de diez productos y cinco combinaciones diferentes de recursos. */
final class CatalogoMercancia {
    private final List<ProductoCatalogo> products=List.of(
        new ProductoCatalogo(1,"Caja de papeleria","GENERAL"), new ProductoCatalogo(2,"Vajilla de vidrio","FRAGIL"),
        new ProductoCatalogo(3,"Generador electrico","PESADO"), new ProductoCatalogo(4,"Silla ejecutiva","VOLUMINOSO"),
        new ProductoCatalogo(5,"Laptop empresarial","ELECTRONICO"), new ProductoCatalogo(6,"Paquete textil","GENERAL"),
        new ProductoCatalogo(7,"Lampara decorativa","FRAGIL"), new ProductoCatalogo(8,"Compresor industrial","PESADO"),
        new ProductoCatalogo(9,"Mesa modular","VOLUMINOSO"), new ProductoCatalogo(10,"Monitor LED 24","ELECTRONICO"));
    private final Map<String,List<String>> typeNeeds=Map.of(
        "GENERAL",List.of("BODEGA","EMPAQUE"),
        "FRAGIL",List.of("BODEGA","EMPAQUE","CALIDAD"),
        "PESADO",List.of("BODEGA","MONTACARGAS","CARGA"),
        "VOLUMINOSO",List.of("BODEGA","MONTACARGAS","EMPAQUE","CARGA"),
        "ELECTRONICO",List.of("BODEGA","CALIDAD","ESCANER"));
    List<ProductoCatalogo> productos() { return products; }
    Map<String,List<String>> necesidades() { return typeNeeds; }
    ProductoCatalogo producto(int id) {
        return products.stream().filter(p->p.id()==id).findFirst().orElseThrow(()->new IllegalArgumentException("Producto inexistente"));
    }
    String descripcion(String type) {
        return switch(type) {
            case "GENERAL" -> "Productos livianos de manejo comun";
            case "FRAGIL" -> "Requiere empaque y revision para evitar roturas";
            case "PESADO" -> "Requiere montacargas; traslado sin empaque en estacion";
            case "VOLUMINOSO" -> "Requiere montacargas y estacion de empaque";
            default -> "Requiere control funcional y trazabilidad por escaner";
        };
    }
}
