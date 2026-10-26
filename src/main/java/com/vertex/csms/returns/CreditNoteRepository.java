package com.vertex.csms.returns;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface CreditNoteRepository extends JpaRepository<CreditNote, Long> {

    Optional<CreditNote> findByCnNo(String cnNo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CreditNote c where c.cnNo = :cnNo")
    Optional<CreditNote> lockByCnNo(@Param("cnNo") String cnNo);
}
