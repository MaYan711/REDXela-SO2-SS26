/** Persistencia temporal para ejecución sin PostgreSQL. */
final class RepositorioVolatil implements AlmacenEstado {
    private byte[] value;
    public synchronized byte[] load(){return value==null?null:value.clone();}
    public synchronized void save(byte[] bytes,String projection){value=bytes.clone();}
    public String mode(){return "MEMORIA_PRUEBAS";}
}
