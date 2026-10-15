package com.cleanfresh.ms_cleanfresh_notificaciones.repository;

import com.cleanfresh.ms_cleanfresh_notificaciones.entity.MensajeMuertoEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MensajeMuertoJpaRepository extends JpaRepository<MensajeMuertoEntity, Long> {

    List<MensajeMuertoEntity> findAllByOrderByIdDesc();
}
