/** Almacenamiento reemplazable de checkpoints y proyecciones JSON. */
interface AlmacenEstado extends AutoCloseable {
    byte[] load();
    void save(byte[] bytes,String projection);
    String mode();
    default void close(){ }
    static AlmacenEstado memory(){return new RepositorioVolatil();}
}
