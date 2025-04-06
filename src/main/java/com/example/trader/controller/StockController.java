package com.example.trader.controller;

import com.example.trader.dto.StockRequest;
import com.example.trader.mongodb.StockService;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class StockController {

    private final StockService stockService;
    @GetMapping("/stock")
    public ResponseEntity<List<Document>> getStockListByTimestamp(@ModelAttribute StockRequest stockRequest){
        List<Document> timeSeriesData = stockService.getTimeSeriesData(stockRequest.getStart(),stockRequest.getEnd(),stockRequest.getStockName());
        return ResponseEntity.status(HttpStatus.OK).body(timeSeriesData);
    }
}
