CREATE TABLE IF NOT EXISTS centro_operativo_estado (
    id INTEGER PRIMARY KEY CHECK (id=1),
    format_version INTEGER NOT NULL CHECK (format_version=3),
    payload BYTEA NOT NULL,
    projection JSONB NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE OR REPLACE VIEW vista_existencias AS
SELECT (p->>'productId')::integer AS producto_id, p->>'name' AS producto,
       p->>'type' AS tipo_mercancia, (p->>'stock')::integer AS stock_fisico,
       (p->>'reserved')::integer AS reservado, (p->>'available')::integer AS disponible
FROM centro_operativo_estado, jsonb_array_elements(projection->'warehouse'->'stocks') p;

CREATE OR REPLACE VIEW vista_ubicaciones_almacen AS
SELECT (c->>'id')::integer AS ubicacion_id, c->>'label' AS ubicacion,
       (c->>'capacity')::integer AS capacidad, (c->>'used')::integer AS ocupacion,
       c->'contents' AS contenido
FROM centro_operativo_estado, jsonb_array_elements(projection->'warehouse'->'cells') c;

CREATE OR REPLACE VIEW vista_recepciones AS
SELECT (r->>'id')::bigint AS recepcion_id, (r->>'productId')::integer AS producto_id,
       r->>'product' AS producto, (r->>'units')::integer AS unidades,
       r->>'state' AS estado, r->>'reason' AS detalle, (r->>'lotId')::bigint AS lote_id
FROM centro_operativo_estado, jsonb_array_elements(projection->'receipts') r;

CREATE OR REPLACE VIEW vista_lotes_almacen AS
SELECT (l->>'id')::bigint AS lote_id, (l->>'receiptId')::bigint AS recepcion_id,
       (l->>'productId')::integer AS producto_id, (l->>'receivedUnits')::integer AS unidades_recibidas,
       l->'locations' AS ubicaciones
FROM centro_operativo_estado, jsonb_array_elements(projection->'warehouse'->'lots') l;

CREATE OR REPLACE VIEW vista_pedidos_distribucion AS
SELECT (o->>'id')::bigint AS pedido_id, (o->>'clientId')::bigint AS cliente_id,
       o->>'clientName' AS cliente, (o->>'level')::integer AS nivel_servicio,
       (o->>'units')::integer AS unidades_totales, o->>'state' AS estado,
       o->>'stage' AS etapa, o->'lines' AS productos
FROM centro_operativo_estado, jsonb_array_elements(projection->'orders') o;

CREATE OR REPLACE VIEW vista_clientes AS
SELECT (c->>'id')::bigint AS cliente_id, c->>'name' AS nombre,
       c->>'email' AS correo, (c->>'level')::integer AS nivel_servicio,
       (c->>'orders')::bigint AS pedidos, (c->>'completed')::bigint AS completados
FROM centro_operativo_estado, jsonb_array_elements(projection->'clients') c;
