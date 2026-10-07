package com.cleanfresh.ms_cleanfresh_notificaciones.repository;

import com.cleanfresh.ms_cleanfresh_notificaciones.entity.NotificationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NotificationJpaRepository extends JpaRepository<NotificationEntity, Long> {

    boolean existsByTipoAndNumeroOrden(String tipo, String numeroOrden);

    List<NotificationEntity> findAllByOrderByIdDesc();

    List<NotificationEntity> findByDestinatarioTipoAndDestinatarioIgnoreCaseOrderByIdDesc(
            String destinatarioTipo, String destinatario);

    @Modifying
    @Query("update NotificationEntity n set n.leida = true where n.leida = false")
    int marcarTodasLeidas();

    @Modifying
    @Query("""
            update NotificationEntity n set n.leida = true
            where n.leida = false and n.destinatarioTipo = :tipo
              and lower(n.destinatario) = lower(:destinatario)""")
    int marcarLeidasDe(@Param("tipo") String destinatarioTipo, @Param("destinatario") String destinatario);
}
