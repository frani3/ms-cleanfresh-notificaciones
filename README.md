# ms-cleanfresh-notificaciones

Microservicio de notificaciones de Clean&Fresh Manager. Spring Boot 4.1.1 /
Java 21, puerto **8083**.

**Estado:** esqueleto. Arranca pero todavía no hace nada; no expone endpoints
y el BFF no tiene ruta hacia él, porque este servicio solo recibe mensajes.

**Diseño (Spec 029, fase 3):** consumirá de AWS SQS los mensajes
`ORDEN_CREADA` que publica `ms-cleanfresh-orders` al crearse una orden,
registrará un aviso en su log por cada uno y lo eliminará de la cola. Sin
envío real de correo. No tiene base de datos.

Proyecto individual de **DSY1107 Cloud Native 1** (DuocUC).

## Requisitos

- Java 21 (`JAVA_HOME` apuntando a un JDK 21)

## Levantar en local

```powershell
.\mvnw.cmd spring-boot:run
```

O compilar y correr el jar:

```powershell
.\mvnw.cmd clean package
java -jar target\ms-cleanfresh-notificaciones-0.0.1-SNAPSHOT.jar
```

Corre en `http://localhost:8083`.

## Arquitectura y decisiones técnicas

Ver [`CLAUDE.md`](CLAUDE.md) y, en el repo del frontend,
`EP2/ARQUITECTURA.md`.
