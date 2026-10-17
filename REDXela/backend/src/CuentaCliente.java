import java.io.Serializable;
final class CuentaCliente implements Serializable {
        private static final long serialVersionUID=1L;
        final long id;
        final String name,email;
        int level;
        CuentaCliente(long id,String name,String email,int level){this.id=id;this.name=name;this.email=email;this.level=level;}
    }
