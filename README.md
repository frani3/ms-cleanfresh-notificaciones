# ms-cleanfresh-notificaciones

Microservicio de notificaciones de Clean&Fresh Manager. Spring Boot 4.1.1 /
Java 21, puerto **8083**. No tiene base de datos ni endpoints: el BFF no tiene
ruta hacia él porque este servicio solo recibe mensajes.

## Qué hace

Lee de AWS SQS (cola `cleanfresh-ordenes`) los mensajes `ORDEN_CREADA` que
publica `ms-cleanfresh-orders` al crearse una orden y registra un aviso en su
log por cada uno, por ejemplo:

```
Notificación: la orden ORD-0008 de Cliente Cola (Planchado, sucursal Providencia, total 15000.0) fue creada
```

Sin envío real de correo en esta entrega. Usa long polling (espera hasta 10 s
por mensajes) y elimina un mensaje de la cola **solo después de procesarlo
bien**: si falla, queda en la cola y SQS lo vuelve a entregar. Está **apagado
por defecto**: sin `SQS_ENABLED=true` el servicio arranca pero no consume nada.

Proyecto individual de **DSY1107 Cloud Native 1** (DuocUC).

## Configuración (variables de entorno)

| Variable | Descripción |
|---|---|
| `SQS_ENABLED` | `true` para consumir la cola (por defecto `false`) |
| `SQS_QUEUE_URL` | URL de la cola, p. ej. `.../cleanfresh-ordenes` |
| `AWS_REGION` | Región (por defecto `us-east-1`) |
| `SQS_ENDPOINT` | Solo para emuladores locales; vacío en AWS |

Las credenciales salen de la cadena por defecto del AWS SDK (variables
`AWS_ACCESS_KEY_ID`/`AWS_SECRET_ACCESS_KEY`, perfil de la EC2, etc.).

## Requisitos

- Java 21 (`JAVA_HOME` apuntando a un JDK 21)
- Para consumir: una cola SQS alcanzable (o ElasticMQ en local)

## Levantar en local

Con ElasticMQ (emulador de SQS) y la cola creada como se explica en el README
de `ms-cleanfresh-orders`:

```powershell
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

Corre en `http://localhost:8083`. Los tests (`.\mvnw.cmd test`) usan un cliente
de SQS simulado y no necesitan AWS.

## Arquitectura y decisiones técnicas

Ver [`CLAUDE.md`](CLAUDE.md) y, en el repo del frontend,
`EP2/ARQUITECTURA.md`.
