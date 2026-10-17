import java.sql.*;
import java.nio.file.*;
import java.util.*;
/** Transacciones JDBC y cerrojo asesor para evitar escrituras concurrentes. */
final class RepositorioPostgresql implements AlmacenEstado {
    private final Connection connection;
    RepositorioPostgresql(String url,String user,String password,Path schema) throws Exception {
        Properties properties=new Properties();properties.setProperty("user",user);properties.setProperty("password",password);
        properties.setProperty("connectTimeout","5");properties.setProperty("socketTimeout","10");
        connection=DriverManager.getConnection(url,properties);
        try {

            try(var st=connection.createStatement();var rs=st.executeQuery("SELECT pg_try_advisory_lock(7340503)")){
                rs.next();if(!rs.getBoolean(1))throw new IllegalStateException("Otra instancia de REDXela ya utiliza esta base");
            }
            try(var st=connection.createStatement()){st.execute(Files.readString(schema));}
        }catch(Exception ex){connection.close();throw ex;}
    }
    public synchronized byte[] load(){
        try(var st=connection.createStatement();var rs=st.executeQuery("SELECT payload FROM centro_operativo_estado WHERE id=1")){
            return rs.next()?rs.getBytes(1):null;
        }catch(SQLException ex){throw new IllegalStateException("No se pudo leer PostgreSQL",ex);}
    }
    public synchronized void save(byte[] bytes,String projection){
        try {
            connection.setAutoCommit(false);
            try(var q=connection.prepareStatement("INSERT INTO centro_operativo_estado(id,format_version,payload,projection) VALUES(1,3,?,?::jsonb) ON CONFLICT(id) DO UPDATE SET payload=EXCLUDED.payload, projection=EXCLUDED.projection, updated_at=clock_timestamp()")){
                q.setBytes(1,bytes);q.setString(2,projection);q.executeUpdate();
            }
            connection.commit();
        }catch(SQLException ex){
            try{connection.rollback();}catch(SQLException ignored){ }
            throw new IllegalStateException("Fallo de persistencia; reinicia el backend cuando PostgreSQL este disponible",ex);
        }finally{try{connection.setAutoCommit(true);}catch(SQLException ignored){ }}
    }
    public String mode(){return "POSTGRESQL";}
    public synchronized void close(){try{connection.close();}catch(SQLException ex){System.err.println("No se pudo cerrar JDBC: "+ex.getMessage());}}
}
