package com.example.trader.repository;

import com.example.trader.entity.Directory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DirectoryRepository extends JpaRepository<Directory,Long> {
}
