package com.cleanfresh.ms_cleanfresh_notificaciones.repository;

import com.cleanfresh.ms_cleanfresh_notificaciones.entity.TicketTallerEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TicketTallerJpaRepository extends JpaRepository<TicketTallerEntity, Long> {

    List<TicketTallerEntity> findAllByOrderByIdDesc();
}
