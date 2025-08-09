package com.example.trader.mongodb;

import com.example.trader.exception.BaseException;
import com.example.trader.httpresponse.BaseResponseStatus;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;

import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.TextCriteria;
import org.springframework.data.mongodb.core.query.TextQuery;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
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
                            .gte(Date.from(startDate.atZone(ZoneOffset.UTC).toInstant()))
                            .lte(Date.from(endDate.atZone(ZoneOffset.UTC).toInstant())))
                    .addCriteria(Criteria.where("symb").is(stock));
            query.fields().exclude("_id");
            documents = mongoTemplate.find(query, Document.class, "stock_ts");
        }catch (Exception e){
            throw new BaseException(BaseResponseStatus.INVALID_REQUEST);
        }
        return documents;
    }

    public List<Document> getLatestDataBefore(LocalDateTime latestDate, String stock, int count){
        List<Document> documents;
        try {
            Query query = new Query();
            query.addCriteria(Criteria.where("timestamp")
                            .lte(Date.from(latestDate.atZone(ZoneOffset.UTC).toInstant())))
                    .addCriteria(Criteria.where("symb").is(stock))
                            .limit(count).with(Sort.by(Sort.Direction.DESC, "timestamp"));
            query.fields().exclude("_id");
            documents = mongoTemplate.find(query, Document.class, "stock_ts");
        }catch (Exception e){
            throw new BaseException(BaseResponseStatus.INVALID_REQUEST);
        }
        return documents;
    }

    public List<Document> getLatestDataAfter(LocalDateTime latestDate, String stock, int count){
        List<Document> documents;
        try {
            Query query = new Query();
            query.addCriteria(Criteria.where("timestamp")
                            .gte(Date.from(latestDate.atZone(ZoneOffset.UTC).toInstant())))
                    .addCriteria(Criteria.where("symb").is(stock))
                    .limit(count).with(Sort.by(Sort.Direction.DESC, "timestamp"));
            query.fields().exclude("_id");
            documents = mongoTemplate.find(query, Document.class, "stock_ts");
        }catch (Exception e){
            throw new BaseException(BaseResponseStatus.INVALID_REQUEST);
        }
        return documents;
    }
    
    //TODO:검색의 경우 성능이 느려서 일단 만들어보고 엘라스틱서치 추가하기
    public List<Document> getSearchStock(String searchText){
        Query query = new Query();
        query.addCriteria(Criteria.where("symb").regex("^" + searchText, "i")); // symb가 searchText로 시작하는 경우
        query.fields().include("symb").exclude("_id");
        query.limit(100);
        return mongoTemplate.find(query, Document.class, "stock_list");
    }
}
