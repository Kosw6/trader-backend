### NodeController 테스트

- EndPoint:/api/nodes/{id}

- 설명:노드 단순 조회

### 테스트 환경

| 항목           | 설정                                                                                  |
| -------------- | ------------------------------------------------------------------------------------- |
| 서버 사양      | 4 Core / 16GB / SSD                                                                   |
| DB             | PostgreSQL 17 + TimescaleDB                                                           |
| 커넥션 풀      | HikariCP max=150,idle=80                                                              |
| Redis          | max-active=128                                                                        |
| 테스트 도구    | k6 v0.52                                                                              |
| 초기 부하 유형 | ramping-arrival 20rps 시작으로도 과부하 -><br>매우 낮은 constant-arrival-rate(5~7rps) |
| 네트워크       | 내부 브릿지 (Docker Compose 환경)                                                     |
| 데이터 구성    | 방대한 주식 OHLCV데이터로 구성되어 PostgreSQL의 TimeScaleDB로 확장, 데이터셋          |

단건조회
20RPS웜업후 85RPS
=== k6 Summary (phase:main) ===
avg latency: 72.85 ms
p95 latency: 418.47 ms
throughput: 60.44 req/s (avg over test)
throughput(active): 87.34 req/s (active-window)
fail rate: 0.00%
목록조회
20RPS웜업후 60RPS
=== k6 Summary (phase:main) ===
avg latency: 63.94 ms
p95 latency: 309.90 ms
throughput: 42.34 req/s (avg over test)
throughput(active): 61.34 req/s (active-window)
fail rate: 0.00%
목록조회
20RPS웜업후 60RPS
=== k6 Summary (phase:main) ===
avg latency: 38.73 ms
p95 latency: 230.72 ms
throughput: 42.24 req/s (avg over test)
throughput(active): 61.34 req/s (active-window)
fail rate: 0.00%

### 문제

### 해결

```
@Query("""
        select distinct n
        from Node n
        left join fetch n.noteLinks l
        left join fetch l.note
        where n.id = :id
        """)
    Optional<Node> findByIdWithLinks(@Param("id") Long id);
```
