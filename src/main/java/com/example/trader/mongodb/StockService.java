package com.example.trader.mongodb;

import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;

import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StockService {

    private final MongoTemplate mongoTemplate;

    public List<Document> getTimeSeriesData(LocalDateTime startDate, LocalDateTime endDate, String stock) {
        List<Document> documents;
        try {
            Query query = new Query();
            query.addCriteria(Criteria.where("timestamp")
                            .gte(Date.from(startDate.atZone(ZoneId.systemDefault()).toInstant()))
                            .lte(Date.from(endDate.atZone(ZoneId.systemDefault()).toInstant())))
                    .addCriteria(Criteria.where("symb").is(stock));

            documents = mongoTemplate.find(query, Document.class, "stock_ts");
        }catch (Exception e){
            throw new RuntimeException("기간과 주식명을 확인해주세요");
        }
        return documents;
    }
}
