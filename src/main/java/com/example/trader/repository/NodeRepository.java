package com.example.trader.repository;

import com.example.trader.entity.Node;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NodeRepository extends JpaRepository<Node,Long> {
    // NodeRepository.java
    List<Node> findByPageId(Long pageId);
}
