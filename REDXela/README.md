
## Ejecutar con Docker Desktop en Windows

1. Descomprime el proyecto y abre PowerShell en esta carpeta.
2. Crea `.env` desde `.env.example`: `Copy-Item .env.example .env`.
3. Ejecuta `docker compose up --build -d`.
4. Abre http://localhost:4200. La API funciona en http://localhost:8090/api/state.
5. Para detener sin borrar datos: `docker compose down`.

Puertos: web `4200`, API `8090`, PostgreSQL `5439`. Se recomienda no publicar PostgreSQL fuera del equipo de demostración.

## Compilar backend sin Docker

Desde la carpeta `backend/`:

```bash
javac -encoding UTF-8 -d out src/*.java
```

Para ejecutar con PostgreSQL local:

```bash
java -cp "out;lib/*" InicioServidor
```


## Arquitectura del backend

- `InicioServidor` inicia Java HTTP, el motor y los hilos.
- `EnrutadorApi`, `ParametrosApi`, `RespuestaApi` y `EventosOperativos` mantienen las rutas y eventos utilizados por Angular.
- `CentroOperativo` coordina los pedidos, escaneo, recepción, inventario e interbloqueo mediante un monitor compartido.
- `PedidoOperativo`, `CuentaCliente`, `EntradaStock`, `ProductoCatalogo` y otras clases definen los modelos.
- `CatalogoMercancia` y `ConstructorEtapas` separan tipos de mercancía de la ejecución.
- `PoliticasAtencion`, `AnalizadorPoliticas` y `MedidorNivelServicio` calculan planificación, comparaciones y SLA.
- `AdministradorRecursos` gestiona semáforos, propietarios y ocupación.
- `CanalInventario` implementa el canal productor-consumidor acotado.
- `InventarioEspacial` controla 60 ubicaciones, lotes y reservas de stock.
- `AlmacenEstado` y `RepositorioPostgresql` guardan checkpoints y proyecciones JSON.



## Endpoints

- `GET /api/health`, `/api/state`, `/api/events` (SSE)
- `POST /api/clients`, `/api/clients/service`, `/api/orders`
- `POST /api/receipts`, `/api/generator`
- `POST /api/policy`, `/api/aging`, `/api/clock`, `/api/comparison`
- `POST /api/conflict/start`, `/api/conflict/resolve`


