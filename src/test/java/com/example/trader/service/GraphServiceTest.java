package com.example.trader.service;

import com.example.trader.entity.Directory;
import com.example.trader.repository.DirectoryRepository;
import com.example.trader.repository.EdgeRepository;
import com.example.trader.repository.NodeRepository;
import com.example.trader.repository.PageRepository;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;


@Transactional
@SpringBootTest
class GraphServiceTest {
    @Autowired
    DirectoryRepository directoryRepository;
    @Autowired
    GraphService graphService;
    @Autowired
    NodeRepository nodeRepository;
    @Autowired
    EdgeRepository edgeRepository;
    @Autowired
    PageRepository pageRepository;
//    @Test
//    void getGraph(){
//        Directory.builder()
//                        .user()
//                                .pages()
//        directoryRepository.save()
//    }
}