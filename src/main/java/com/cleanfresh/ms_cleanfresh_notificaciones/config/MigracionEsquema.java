package com.cleanfresh.ms_cleanfresh_notificaciones.config;

import jakarta.persistence.EntityManagerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Ajuste de esquema de la Spec 035. Hasta la Spec 034 la tabla {@code notificaciones} tenía una restricción
 * única en {@code (tipo, numero_orden)}: un aviso de cada tipo por orden. Con los avisos de cambio de estado
 * (ORDEN_ESTADO) una orden tiene varios avisos del mismo tipo, uno por estado, y esa restricción los impediría.
 *
 * {@code ddl-auto: update} crea restricciones nuevas pero nunca elimina las viejas, así que en una base que
 * ya existe (la RDS) hay que quitarla a mano. Esta clase lo hace al arrancar, es idempotente y solo actúa
 * sobre PostgreSQL (las pruebas con H2 arrancan con el esquema ya nuevo). La restricción que la reemplaza,
 * {@code (tipo, numero_orden, mensaje)}, la declara la entidad y la crea Hibernate.
 *
 * Depende de EntityManagerFactory para correr después de que Hibernate actualiza el esquema, y como
 * InitializingBean corre antes de que arranquen los consumidores de RabbitMQ.
 */
@Component
public class MigracionEsquema implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(MigracionEsquema.class);

    // Restricciones únicas de notificaciones cuyas columnas son exactamente (numero_orden, tipo).
    static final String BUSCAR_RESTRICCION_VIEJA = """
            select c.conname
            from pg_constraint c
            join pg_class t on t.oid = c.conrelid
            where t.relname = 'notificaciones' and c.contype = 'u'
              and (select array_agg(a.attname::text order by a.attname::text)
                   from pg_attribute a
                   where a.attrelid = t.oid and a.attnum = any(c.conkey)) = array['numero_orden', 'tipo']::text[]""";

    private final DataSource dataSource;

    public MigracionEsquema(DataSource dataSource, EntityManagerFactory entityManagerFactory) {
        this.dataSource = dataSource;
    }

    @Override
    public void afterPropertiesSet() throws SQLException {
        try (Connection conexion = dataSource.getConnection()) {
            if (!"PostgreSQL".equals(conexion.getMetaData().getDatabaseProductName())) {
                return;
            }
            List<String> viejas = new ArrayList<>();
            try (Statement consulta = conexion.createStatement();
                 ResultSet resultado = consulta.executeQuery(BUSCAR_RESTRICCION_VIEJA)) {
                while (resultado.next()) {
                    viejas.add(resultado.getString(1));
                }
            }
            for (String nombre : viejas) {
                try (Statement sentencia = conexion.createStatement()) {
                    sentencia.execute("alter table notificaciones drop constraint \"" + nombre.replace("\"", "\"\"") + "\"");
                }
                log.info("Migración (Spec 035): se eliminó la restricción única {} de notificaciones (tipo, numero_orden)", nombre);
            }
        }
    }
}
