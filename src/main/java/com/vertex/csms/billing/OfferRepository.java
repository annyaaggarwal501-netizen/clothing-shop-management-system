package com.vertex.csms.billing;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OfferRepository extends JpaRepository<Offer, Long> {

    @Query("select o from Offer o where o.isActive = true and o.validFrom <= :now and o.validTo >= :now")
    List<Offer> findRunning(@Param("now") LocalDateTime now);
}
