package com.example.trader.service;

import com.example.trader.mongodb.StockService;
import org.assertj.core.api.Assertions;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Transactional
@SpringBootTest
public class StockServiceTest {
    @Autowired
    private StockService stockService;

    @Test
    void getTimeSeriesData(){
        List<Document> stockTs = stockService.getTimeSeriesData(LocalDateTime.of(2025, 03, 01, 0, 0),LocalDateTime.now(),"TSLA");
        for (Document stockT : stockTs) {
            System.out.println(stockT);
        }
        Assertions.assertThat(stockTs).isNotEmpty();
    }
}