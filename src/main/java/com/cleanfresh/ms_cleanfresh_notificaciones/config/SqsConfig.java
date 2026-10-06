package com.cleanfresh.ms_cleanfresh_notificaciones.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.SqsClientBuilder;

import java.net.URI;

/**
 * Cliente de SQS y planificador del consumidor. Solo con app.sqs.enabled=true:
 * sin AWS el servicio arranca igual, pero no consume nada.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.sqs.enabled", havingValue = "true")
public class SqsConfig {

    @Bean
    public SqsClient sqsClient(@Value("${app.sqs.region}") String region,
                               @Value("${app.sqs.endpoint:}") String endpoint) {
        SqsClientBuilder builder = SqsClient.builder().region(Region.of(region));
        if (!endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint));
        }
        return builder.build();
    }
}
