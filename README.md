# ms-cleanfresh-notificaciones

Microservicio de notificaciones de Clean&Fresh Manager. Spring Boot 4.1.1 /
Java 21, puerto **8083**. Guarda los avisos en PostgreSQL (base
`notificaciones_db`, usuario propio) y los expone para que el BFF los entregue
a cada rol. Como los demás microservicios, no valida JWT: confía en que solo el
BFF le habla.

## Qué hace

Consume de RabbitMQ (Spec 033) los comandos que publica `ms-cleanfresh-orders`, desde tres colas, cada una
con su cola de mensajes muertos (DLQ):

| Cola | Comando | Efecto | Va dirigido a |
|---|---|---|---|
| `q.cmd.email` | `email.send[.*]` | Aviso: `ORDEN_CREADA` → "Nuevo pedido ORD-0014: ..." / `ORDEN_LISTA` → "Tu pedido ORD-0014 (...) está listo" | la **sucursal** (Operador en turno) / el **cliente** dueño (su `username`) |
| `q.cmd.kitchen` | `kitchen.ticket[.*]` | Ticket de taller de la sucursal (solo `ORDEN_CREADA`; la variante `.thermal` queda anotada) | la sucursal |
| `q.cmd.invoice` | `invoice.gen[.*]` | Boleta con neto e IVA 19 % (solo `ORDEN_LISTA`) | el cliente |

Cada mensaje se procesa con **ACK manual**: hasta 3 intentos (con pausas de 0,5 y 1 s); si todos fallan se
rechaza sin reencolar y RabbitMQ lo manda a la DLQ de esa cola. Es **idempotente por `eventId`** (y por flujo):
un mensaje que llega dos veces no repite el efecto. Un revisor periódico lee las tres DLQ, **registra cada
mensaje muerto** (cola de origen, motivo, intentos, cuerpo) en la base y en el log, y solo entonces lo
confirma. El consumo está **apagado por defecto**: sin `RABBIT_ENABLED=true` el servicio arranca pero no
consume nada.

## Endpoints

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/api/notificaciones?cliente=<username>` | Avisos de ese cliente, del más nuevo al más viejo |
| GET | `/api/notificaciones?sucursal=<nombre>` | Avisos de esa sucursal |
| GET | `/api/notificaciones` | Todos (uso del Admin) |
| PUT | `/api/notificaciones/leidas?cliente=` o `?sucursal=` | Marca como leídos los de ese destinatario; devuelve `{"marcadas": n}` |
| PUT | `/api/notificaciones/{id}/leida?cliente=` o `?sucursal=` | Marca como leído **un** aviso (Spec 034), solo si es de ese destinatario: `204`; si no existe o es de otro, `404`; sin filtro, en blanco o con los dos, `400`. Repetirlo no es error |
| GET | `/api/tickets` | Tickets de taller registrados |
| GET | `/api/boletas` | Boletas registradas |
| GET | `/api/muertos` | Mensajes que pasaron por una DLQ (registro) |

Un filtro en blanco, o `cliente` y `sucursal` a la vez, responde `400`: nunca cae
en "todos" por descuido. Es el BFF quien decide qué filtro corresponde a cada
rol; este servicio no lo sabe.

Proyecto individual de **DSY1107 Cloud Native 1** (DuocUC).

## Configuración (variables de entorno)

La conexión a la base llega solo por variables de entorno, sin valores por
defecto: si faltan, el servicio no arranca. Las tablas se crean/actualizan solas.

| Variable | Descripción |
|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/notificaciones_db` |
| `DB_USER` | `notificaciones_user` |
| `DB_PASSWORD` | (la del usuario) |
| `RABBIT_ENABLED` | `true` para consumir las colas (por defecto `false`) |
| `RABBITMQ_ADDRESSES` | Nodos del clúster, p. ej. `localhost:5672,localhost:5673` |
| `RABBITMQ_USER` / `RABBITMQ_PASSWORD` | Credenciales de RabbitMQ |
| `RABBIT_DLQ_INTERVALO_MS` | Cada cuánto se revisan las DLQ (por defecto `30000`) |

## Requisitos

- Java 21 (`JAVA_HOME` apuntando a un JDK 21)
- Una base PostgreSQL con `notificaciones_db` y un usuario con acceso solo a ella
- Para consumir: el clúster RabbitMQ de `EP2/despliegue/mq/` (en el repo del frontend)

## Levantar en local

Con el clúster RabbitMQ levantado (`EP2/despliegue/mq/`):

Base de prueba en el mismo Postgres de Docker de los otros servicios:

```powershell
docker exec -it cleanfresh-pg psql -U postgres -c "CREATE USER notificaciones_user WITH PASSWORD '<clave>'" -c "GRANT notificaciones_user TO postgres" -c "CREATE DATABASE notificaciones_db OWNER notificaciones_user"
```

(El `GRANT` es necesario en RDS: el usuario maestro debe ser miembro del rol
dueño para crear la base a su nombre.)

```powershell
$env:DB_URL="jdbc:postgresql://localhost:5432/notificaciones_db"
$env:DB_USER="notificaciones_user"; $env:DB_PASSWORD="<clave>"
$env:RABBIT_ENABLED="true"; $env:RABBITMQ_ADDRESSES="localhost:5672,localhost:5673"
$env:RABBITMQ_USER="<usuario>"; $env:RABBITMQ_PASSWORD="<clave>"
.\mvnw.cmd spring-boot:run
```

O compilar y correr el jar:

```powershell
.\mvnw.cmd clean package
java -jar target\ms-cleanfresh-notificaciones-0.0.1-SNAPSHOT.jar
```

Corre en `http://localhost:8083`. Los tests (`.\mvnw.cmd test`) usan H2 en memoria
y un RabbitMQ simulado: no necesitan PostgreSQL ni un broker.

## Arquitectura y decisiones técnicas

Ver [`CLAUDE.md`](CLAUDE.md) y, en el repo del frontend,
`EP2/ARQUITECTURA.md`.

## Docker

`Dockerfile` multi-etapa (Maven + JDK 21 para compilar, JRE 21 sin root para correr). Se configura solo por variables de entorno.

```bash
docker build -t cleanfresh/notificaciones .
docker run -p 8083:8083 -e RABBIT_ENABLED=true -e RABBITMQ_ADDRESSES=... cleanfresh/notificaciones
```

Los 5 microservicios se levantan juntos con el `docker-compose.yml` de `EP2/despliegue/` en el repo `cleanfresh-frontend`.
