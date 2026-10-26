package com.vertex.csms.product;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface VariantRepository extends JpaRepository<Variant, Long> {

    Optional<Variant> findByBarcode(String barcode);

    Optional<Variant> findBySku(String sku);

    boolean existsByBarcode(String barcode);

    boolean existsBySku(String sku);

    /**
     * SELECT ... FOR UPDATE (REQ-INV-5). Rows are locked in id order so two counters
     * locking the same items never deadlock. The second counter waits here until the first commits.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Variant v join fetch v.product where v.id in :ids order by v.id")
    List<Variant> lockByIds(@Param("ids") Collection<Long> ids);

    @Query(value = "SELECT MAX(barcode) FROM variant WHERE barcode LIKE '200%' AND LENGTH(barcode) = 13", nativeQuery = true)
    String maxInStoreBarcode();
}
