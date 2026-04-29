package com.example.trader.controller.logTest;

import com.example.trader.exception.BaseException;
import com.example.trader.httpresponse.BaseResponseStatus;
import com.example.trader.observability.ObservedLog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/internal")
public class LogTestController {

    @ObservedLog(domain = "observability", api = "log-test")
    @GetMapping("/test/log")
    public String testLog() {
        log.info("TEST_INFO");
        log.warn("TEST_WARN");
        throw new BaseException(BaseResponseStatus.INTERNAL_SERVER_ERROR);
    }

    //정상 로그
    @ObservedLog(domain = "user", api = "user-profile-test")
    @GetMapping("/test/user")
    public String userLog() {
        log.info("USER_INFO_TEST");
        return "ok";
    }
    //WarnLog
    @ObservedLog(domain = "node", api = "node-edit-test")
    @GetMapping("/test/node")
    public String nodeLog() {
        log.warn("NODE_CONFLICT_WARN_TEST");
        throw new BaseException(BaseResponseStatus.NODE_EDIT_CONFLICT);
    }
    //ErrorLog
    @ObservedLog(domain = "stock", api = "stock-query-test")
    @GetMapping("/test/stock")
    public String stockLog() {
        log.warn("STOCK_SLOW_QUERY_WARN_TEST");
        throw new BaseException(BaseResponseStatus.DATABASE_INSERT_ERROR);
    }
}
