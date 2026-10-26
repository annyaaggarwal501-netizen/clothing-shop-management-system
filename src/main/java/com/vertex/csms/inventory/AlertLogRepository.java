package com.vertex.csms.inventory;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertLogRepository extends JpaRepository<AlertLog, Long> {

    boolean existsByAlertTypeAndEntityIdAndResolvedAtIsNull(String alertType, Long entityId);

    List<AlertLog> findByEntityIdAndAlertTypeInAndResolvedAtIsNull(Long entityId, List<String> types);

    List<AlertLog> findTop50ByResolvedAtIsNullOrderByIdDesc();
}
