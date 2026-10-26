package com.vertex.csms.billing;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.vertex.csms.product.TaxClass;

public interface GstSlabRepository extends JpaRepository<GstSlab, Long> {

    /** Slabs in force on a date, lowest first (effective-dated, REQ-BIL-3). */
    @Query("""
            select s from GstSlab s where s.taxClass = :taxClass and s.effectiveFrom <= :day
              and (s.effectiveTo is null or s.effectiveTo >= :day)
            order by s.minTaxable""")
    List<GstSlab> findInForce(@Param("taxClass") TaxClass taxClass, @Param("day") LocalDate day);
}
