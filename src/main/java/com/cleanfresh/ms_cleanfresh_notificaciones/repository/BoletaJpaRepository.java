package com.cleanfresh.ms_cleanfresh_notificaciones.repository;

import com.cleanfresh.ms_cleanfresh_notificaciones.entity.BoletaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BoletaJpaRepository extends JpaRepository<BoletaEntity, Long> {

    List<BoletaEntity> findAllByOrderByIdDesc();
}
