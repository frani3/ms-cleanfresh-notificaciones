package com.cleanfresh.ms_cleanfresh_notificaciones.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Documentación OpenAPI del servicio: {@code /swagger-ui.html} y {@code /v3/api-docs}. */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openApi() {
        return new OpenAPI().info(new Info()
                .title("ms-cleanfresh-notificaciones")
                .version("0.0.1")
                .description("Notificaciones de Clean&Fresh Manager. Consume los comandos de RabbitMQ (ACK manual, reintentos, DLQ) y guarda avisos, "
                        + "tickets de taller, boletas y mensajes muertos en PostgreSQL. No es público: solo lo consulta el BFF."));
    }
}
