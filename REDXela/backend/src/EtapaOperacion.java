import java.io.Serializable;
import java.util.List;
record EtapaOperacion(String name, int msPerUnit, List<String> needs) implements Serializable { }
