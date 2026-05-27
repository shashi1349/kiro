package com.shashikiranreddy.splitwise.settlement.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SettlementRepository extends JpaRepository<Settlement, Long> {
    List<Settlement> findByGroupIdOrderBySettledAtDesc(Long groupId);
    List<Settlement> findByGroupId(Long groupId);
}
