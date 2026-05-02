package com.example.trader.repository;

import com.example.trader.entity.EditSessionEntity;
import com.example.trader.entity.EditSessionId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EditSessionRepository extends JpaRepository<EditSessionEntity, EditSessionId> {
}
