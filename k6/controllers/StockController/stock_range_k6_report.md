### StockController 조회 성능 최적화: PostgreSQL TimeScaleDB확장 및 하이퍼테이블 구조, 청크(시간,공간) 영향 분석

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
| 데이터 구성    | 약 2,600만 행 규모의 OHLCV 시계열 데이터. PostgreSQL 17 + TimescaleDB로 확장하여 운용 |

#### 1차 테스트 결과

| 항목     | 설정                |
| -------- | ------------------- |
| RPS      | 8RPS                |
| 시간     | 1분으로 짧은 테스트 |
| 목표 P95 | <300ms              |
| 결과 P95 | 315ms               |

#### 문제점 요약

- 테스트가 무의미 할 정도의 병목이 발생하고 있음
- DB쿼리 성능저하로 인한 병목으로 진단
- 하이퍼테이블 누락

#### 해결방법

- 인덱스 확인 및 최적화
- 하이퍼 테이블 적용
- 청크 변경 기존(30day로 운영중)
  RPS 10<br>
  === k6 Summary (safe) ===<br>
  avg latency: 187.28 ms<br>
  p95 latency: 342.14 ms<br>
  throughput: 9.48 req/s<br>
  fail rate: 0.00%<br>

- 인덱싱 후
  RPS 10<br>
  === k6 Summary (safe) ===<br>
  avg latency: 18.71 ms<br>
  p95 latency: 32.06 ms<br>
  throughput: 9.94 req/s<br>
  fail rate: 0.00%<br>

#### ２.하이퍼테이블 누락 확인

```
SELECT
  hypertable_schema,
  hypertable_name,
  num_dimensions,
  num_chunks,
  compression_enabled
FROM timescaledb_information.hypertables
ORDER BY hypertable_schema, hypertable_name;
```

위 쿼리로 확인 결과 개발장비에는 존재 서버의 하이퍼테이블 누락 확인<br>
-> 기존 하이퍼테이블 추가 후 시간간격 30일 공간파티셔닝 32로 진행<br> -> 청크 갯수 7256개<br>
p95<300ms에 맞춰 테스트 결과<br>

-> 새 하이퍼테이블 추가 후 시간간격 30일 공간파티셔닝 8로 진행<br> -> 청크 갯수 1816개<br>

```
//3개월 데이터에 대해서 해당 쿼리를 비롯한 총 4개 쿼리로
//각 쿼리마다 테이블(stock,stock_new)의 소요시간 계산
EXPLAIN (ANALYZE, BUFFERS, TIMING, SUMMARY)
SELECT *
FROM stock
WHERE symb = 'ALGT'
AND timestamp BETWEEN '2022-01-01' AND '2022-04-01'
ORDER BY timestamp ASC;

EXPLAIN (ANALYZE, BUFFERS, TIMING, SUMMARY)
SELECT *
FROM stock
WHERE symb = 'CNOB'
AND timestamp BETWEEN '2024-01-01' AND '2024-04-01'
ORDER BY timestamp ASC;
...
```

> 💬 **Warm cache** 구간에서는 planner 재사용과 buffer hit으로 인해  
> 실제 차이가 0.1–0.2 ms 이내로 수렴하여 동일한 값으로 표기하였다.  
> **Cold/Warm execution time** 또한 동일한 인덱스 경로를 사용하므로  
> 측정값의 변동이 미미하며 근사치로 동일한 값으로 표기하였다.

| 항목                 | 평균 planningTime | 평균 executionTime |
| -------------------- | ----------------- | ------------------ |
| stock_30d_32콜드캐시 | 49ms              | 3ms                |
| stock_30d_8콜드캐시  | 47ms              | 3ms                |
| stock_30d_32웜캐시   | 3ms               | 0.4ms              |
| stock_30d_8웜캐시    | 3ms               | 0.4ms              |

- 둘 간의 성능차이가 없음
  -> num_partition은 조회성능 크게 영향을 주기보다 부가적인 기능

| 항목                 | 평균 planningTime | 평균 executionTime |
| -------------------- | ----------------- | ------------------ |
| stock_90d_8 콜드캐시 | 31ms              | 3ms                |
| stock_90d_4 콜드캐시 | 23ms              | 3ms                |
| stock_90d_8 웜캐시   | 3ms               | 0.3ms              |
| stock_90d_4 웜캐시   | 3ms               | 0.3ms              |

### 하이퍼테이블 구조

- TimescaleDB의 하이퍼테이블은 데이터를 시간(Time), 공간(Space)으로 나누어 관리한다.
- 테스트에 사용한 스키마의 경우 <br>시간은 timestamp값 <br>공간은 symb(주식티커)을 설정했다.

- 청크 분할은 다음 순서로 이루어진다(분할 순서: 시간 → 공간)

  1. 시간 기준 분할 (chunk_time_interval)

     - 우선 데이터를 timestamp 값 기준으로 일정 주기(예: 30일, 90일) 단위로 나눈다.

     - 이 단계에서 여러 symb의 데이터가 같은 시간 구간 청크에 포함된다.

  2. 공간 기준 분할 (num_partitions)

     - 이후 각 시간 청크 내부에서 symb 컬럼의 해시(hash) 값을 이용해 num_partitions 개의 버킷으로 나눈다.

     - 예시: hash(symb) % num_partitions

     - 동일한 해시 결과를 갖는 symb 값(즉, 같은 종목)은 항상 같은 버킷(같은 물리 청크)에 저장된다.

### 인터벌 30에서의 비교

- 애플리케이션의 조회기능은 주로 90일간격으로 조회하여 테스트에 반영하였다.
- 30일 인터벌(공간청크 차이 없음) : 3~4개 시간청크 스캔
- 90일 인터벌+공간청크8개 : 대게 1개 시간청크 스캔 → 결과 PlanningTime 35% 감소

### 인터벌 90에서의 비교

- 공간을 8로 나눈 것과 4로 나눈 것의 예시
  - 공간을 8로 나누었을 경우 8개의 버킷, 4로 나눌 경우 4개의 버킷만 스캔하면 됨 -> 평균 PlanningTime 20%감소
- 최종적으로 같은 90일 간격 공간청크(8)보다 대략 30%,<br>
  처음 30일간격보다 PlanningTime 55% 감소

### 공간분할의 장단점

1. 공간청크가 클 수록(num_partitions ↓)

- 장점:

  - Planner 오버헤드 ↓
    - 청크 수가 적어 쿼리 계획을 세우는 시간이 짧다.
  - I/O효율 ↑
    - 큰 청크를 연속적으로 읽으므로 디스크 접근이 더 효율적이다.
  - 통계·인덱스 관리 간단
    - ANALYZE, VACUUM 등 관리 주기가 단순하다.
    - 청크 10개와 500의 경우 같은 ANALYZE를 돌려도 10번 500을 돌려야 하는 차이가 있음

- 단점:
  - 락 경합 및 쓰기 병목 ↑
    - insert,update가 같은 청크에 몰리게 되어 락·인덱스 경합 발생.
  - 병렬처리 효율 ↓
    - 청크가 적어 Timescale이 병렬 워커(하나의 쿼리를 여러 코어에 병렬로 실행)를 여러 청크에 분산X → CPU활용률 낮음
  - 청크가 너무 커져 인덱스·테이블 크기 비대해짐
    - 인덱스 재구성 및 압축이나 삭제시에도 청크단위로 하다보니 부하와 걸리는 시간이 올라간다.
    - 청크마다 크기가 크다보니 ANALYZE,VACUMM등의 부하가 심해진다.

2. 공간청크가 작을 수록(num_partitions ↑)

- 장점:
  - symb별로 다른 청크에 분산되어 동시 쓰기 병목 완화된다.
    - postgreSQL은 데이터를 디스크에 페이지 단위로 저장하기에 청크가 많을 수록 symb별로 테이블이 분리되며 서로 같은 페이지에 쓰려 할 가능성이 적어 lock충돌이 일어날 가능성이 적어져 동시 쓰기 병목이 완화된다.
  - 병렬 쿼리 및 유지보수 효율 ↑
    - 여러 청크를 병렬로 VACUUM/압축/SELECT 가능하다.
  - 청크 단위 관리 유연성 ↑
    - 오래된 종목 일부만 압축하는 등 청크단위가 많아져 세밀하게 작업이 가능하다.
    - 일부 청크 장애나 데이터 삭제시에 영향 범위가 적다
- 단점:
  - Planner 오버헤드 ↑
    - 청크 수가 많아 쿼리 계획 세우는 시간이 길어진다.
  - 랜덤 I/O 증가
    - 데이터가 여러 청크로 흩어져 연속 읽기 불가하다.
  - 통계·인덱스 관리 비용 ↑
    - 청크별 ANALYZE(쿼리 실행 계획을 위한 통계 정보 수집), 인덱스 메타데이터가 늘어나 관리 오버헤드 발생.

### 테스트 고정시드 추가

- 테스트의 경우 본 부하 테스트 RPS의 20%의 RPS로 3분간 웜업을 하여 캐쉬를 채움 -> DB 캐시의 경우 99프로 채워짐
- 성능, 구조등을 변경하며 동일한 조건에서 테스트를 하기 위해 웜업을 제외한 본 부하 테스트의 경우 시드값을 토대로 테스트가 같은 파라미터 순서를 타도록 설정함

### 2차 테스트 결과

| 항목        | RPS | P95      | throughtput  | failRate |
| ----------- | --- | -------- | ------------ | -------- |
| stock_90d_8 | 300 | 331.99ms | 292.21 req/s | 0.00%    |
| stock_90d_4 | 300 | 235.32ms | 292.23 req/s | 0.00%    |

- 같은 시간청크, RPS 300기준 성능 30%향상

### 하이퍼테이블 최종 선택 결과

1. 시간분할 : 90day
2. 공간분할 : 4

- 단순 종목 조회(90일)가 주 기능이다보니 시간간격은 90일로 설정
- 쓰기작업이 하루에 한번 배치처리로 되고 약 10000개의 데이터이다 보니 쓰기작업에서 병렬도나 시간은 중요하지 않다고 판단.
- 오히려 읽기 성능의 최적화가 중요하다 생각하여 num_partitions=4로 설정
- 다만 통계, 청크 장애등을 고려하여 공간청크를 두지 않는 것보다 4개로 나눔
- 부하테스트, DB내의 5~6개의 고정된 조회쿼리를 사용한 EXPLAIN결과를 토대로 결정하게 되었음
