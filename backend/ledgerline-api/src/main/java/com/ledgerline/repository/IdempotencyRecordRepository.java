package com.ledgerline.repository;

import com.ledgerline.entity.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, Long> {

    Optional<IdempotencyRecord> findByUserIdAndOperationTypeAndIdempotencyKey(Long userId, String operationType, String idempotencyKey);

    Optional<IdempotencyRecord> findByIdempotencyKey(String idempotencyKey);
}

