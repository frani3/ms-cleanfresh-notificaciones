# ms-cleanfresh-notificaciones

Microservicio de notificaciones de Clean&Fresh Manager. Spring Boot 4.1.1 /
Java 21, puerto **8083**. Guarda los avisos en PostgreSQL (base
`notificaciones_db`, usuario propio) y los expone para que el BFF los entregue
a cada rol. Como los demás microservicios, no valida JWT: confía en que solo el
BFF le habla.

## Qué hace

Lee de AWS SQS (cola `cleanfresh-ordenes`) los mensajes que publica
`ms-cleanfresh-orders` y guarda un **aviso dirigido** por cada uno:

| Mensaje | Aviso | Va dirigido a |
|---|---|---|
| `ORDEN_CREADA` | "Nuevo pedido ORD-0014: Planchado de ... en Providencia (total $9.500)" | la **sucursal** de la orden (le llega al Operador que esté en turno en ella) |
| `ORDEN_LISTA` (la orden pasó a `DESPACHADO`) | "Tu pedido ORD-0014 (Planchado) está listo" | el **cliente** dueño de la orden (su `username` de Cognito) |

Además lo registra en el log (`Notificación: ...`). No hay envío real de correo.
Usa long polling (espera hasta 10 s por mensajes) y elimina un mensaje de la
cola **solo después de guardarlo bien**: si falla, queda en la cola y SQS lo
vuelve a entregar. Un mismo mensaje entregado dos veces no duplica el aviso (es
único por tipo y orden), y un tipo desconocido se ignora. El consumo está
**apagado por defecto**: sin `SQS_ENABLED=true` el servicio arranca pero no
consume nada.

## Endpoints

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/api/notificaciones?cliente=<username>` | Avisos de ese cliente, del más nuevo al más viejo |
| GET | `/api/notificaciones?sucursal=<nombre>` | Avisos de esa sucursal |
| GET | `/api/notificaciones` | Todos (uso del Admin) |
| PUT | `/api/notificaciones/leidas?cliente=` o `?sucursal=` | Marca como leídos los de ese destinatario; devuelve `{"marcadas": n}` |

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
| `SQS_ENABLED` | `true` para consumir la cola (por defecto `false`) |
| `SQS_QUEUE_URL` | URL de la cola, p. ej. `.../cleanfresh-ordenes` |
| `AWS_REGION` | Región (por defecto `us-east-1`) |
| `SQS_ENDPOINT` | Solo para emuladores locales; vacío en AWS |

Las credenciales salen de la cadena por defecto del AWS SDK (variables
`AWS_ACCESS_KEY_ID`/`AWS_SECRET_ACCESS_KEY`, perfil de la EC2, etc.).

## Requisitos

- Java 21 (`JAVA_HOME` apuntando a un JDK 21)
- Una base PostgreSQL con `notificaciones_db` y un usuario con acceso solo a ella
- Para consumir: una cola SQS alcanzable (o ElasticMQ en local)

## Levantar en local

Con ElasticMQ (emulador de SQS) y la cola creada como se explica en el README
de `ms-cleanfresh-orders`:

Base de prueba en el mismo Postgres de Docker de los otros servicios:

```powershell
docker exec -it cleanfresh-pg psql -U postgres -c "CREATE USER notificaciones_user WITH PASSWORD '<clave>'" -c "GRANT notificaciones_user TO postgres" -c "CREATE DATABASE notificaciones_db OWNER notificaciones_user"
```

(El `GRANT` es necesario en RDS: el usuario maestro debe ser miembro del rol
dueño para crear la base a su nombre.)

```powershell
$env:DB_URL="jdbc:postgresql://localhost:5432/notificaciones_db"
$env:DB_USER="notificaciones_user"; $env:DB_PASSWORD="<clave>"
$env:SQS_ENABLED="true"; $env:SQS_ENDPOINT="http://localhost:9324"
$env:SQS_QUEUE_URL="http://localhost:9324/000000000000/cleanfresh-ordenes"
$env:AWS_ACCESS_KEY_ID="local"; $env:AWS_SECRET_ACCESS_KEY="local"
.\mvnw.cmd spring-boot:run
```

O compilar y correr el jar:

```powershell
.\mvnw.cmd clean package
java -jar target\ms-cleanfresh-notificaciones-0.0.1-SNAPSHOT.jar
```

Corre en `http://localhost:8083`. Los tests (`.\mvnw.cmd test`) usan H2 en memoria
y un cliente de SQS simulado: no necesitan PostgreSQL ni AWS.

## Arquitectura y decisiones técnicas

Ver [`CLAUDE.md`](CLAUDE.md) y, en el repo del frontend,
`EP2/ARQUITECTURA.md`.

## Docker

`Dockerfile` multi-etapa (Maven + JDK 21 para compilar, JRE 21 sin root para correr). Se configura solo por variables de entorno.

```bash
docker build -t cleanfresh/notificaciones .
docker run -p 8083:8083 -e SQS_ENABLED=true -e SQS_QUEUE_URL=... cleanfresh/notificaciones
```

Los 5 microservicios se levantan juntos con el `docker-compose.yml` de `EP2/despliegue/` en el repo `cleanfresh-frontend`.
