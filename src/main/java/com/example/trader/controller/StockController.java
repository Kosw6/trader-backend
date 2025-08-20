package com.example.trader.controller;

import com.example.trader.dto.StockRequest;

import com.example.trader.entity.Stock;
import com.example.trader.service.StockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@Tag(name = "Stock API", description = "주가 관련 데이터 API")
@RestController
@RequestMapping("/api/stock")
@RequiredArgsConstructor
public class StockController {

    private final StockService stockService;
        @Operation(
                summary = "기간내 주식의 주가데이터 조회",
                description = "요청한 두 일자를 포함하여 기간사이의 해당 주식의 주가,거래량을 조회합니다."
        )
        @ApiResponse(responseCode = "200", description = "성공")
        @GetMapping()
        public ResponseEntity<List<Stock>> getStockListByTimestamp(@ModelAttribute StockRequest stockRequest){
            List<Stock> timeSeriesData = stockService.getTimeSeriesData(stockRequest.getStart(),stockRequest.getEnd(),stockRequest.getStockName());
            return ResponseEntity.status(HttpStatus.OK).body(timeSeriesData);
        }


        //TODO:어떤 일자의 이전 데이터만을 제공해주는 기능(ex)2025-01-01과 100을 받으면 2024-12-31부터 이전까지 100개를 반환)
        @Operation(
                summary = "해당 기간 이전의 주가데이터 조회",
                description = "요청한 기간을 포함하여 이전의 해당 주식의 주가,거래량을 조회합니다."
        )
        @ApiResponse(responseCode = "200", description = "성공")
        @Parameters({
                @Parameter(name = "latestDate", description = "기준일자", example = "2024-01-01T00:00:00",required = true),
                @Parameter(name = "stock", description = "주식명", example = "TSLA",required = true),
                @Parameter(name = "count",description = "데이터 개수",example = "10",required = true)
        })
        @GetMapping("/latest-stock-before")
        public ResponseEntity<List<Stock>> getStockListByLatestTimestampBefore(@RequestParam LocalDateTime latestDate, @RequestParam String stock, @RequestParam int count){
            List<Stock> timeSeriesData = stockService.getLatestDataBefore(latestDate,stock,count);
            return ResponseEntity.status(HttpStatus.OK).body(timeSeriesData);
        }
        @Operation(
                summary = "해당 기간 이후의 주가데이터 조회",
                description = "요청한 기간을 포함하여 이후의 해당 주식의 주가,거래량을 조회합니다."
        )
        @ApiResponse(responseCode = "200", description = "성공")
        @Parameters({
                @Parameter(name = "latestDate", description = "기준일자", example = "2024-01-01T00:00:00",required = true),
                @Parameter(name = "stock", description = "주식명", example = "TSLA",required = true),
                @Parameter(name = "count",description = "데이터 개수",example = "10",required = true)
        })
        @GetMapping("/latest-stock-after")
        public ResponseEntity<List<Stock>> getStockListByLatestTimestampAfter(@RequestParam LocalDateTime latestDate, @RequestParam String stock, @RequestParam int count){
            List<Stock> timeSeriesData = stockService.getLatestDataAfter(latestDate,stock,count);
            return ResponseEntity.status(HttpStatus.OK).body(timeSeriesData);
        }

//        @Operation(
//                summary = "주식 검색 자동완성을 위한 api",
//                description = "전송한 텍스트를 포함하여 완성될 수 있는 주식의 전체 이름 목록을 반환"
//        )
//        @ApiResponse(responseCode = "200", description = "성공")
//        @GetMapping("/search/{searchText}")
//        public ResponseEntity<List<Stock>> getSearchStockName(@PathVariable String searchText){
//            List<Stock> searchStock = stockService.getSearchStock(searchText);
//            return ResponseEntity.status(HttpStatus.OK).body(searchStock);
//        }
}
