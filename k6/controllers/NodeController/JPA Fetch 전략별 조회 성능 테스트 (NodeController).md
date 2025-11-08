### NodeController 조회 성능 최적화: JPA Fetch 전략(Lazy, Batch, Fetch Join) 비교 및 캐시·메모리 영향 분석

## 📋 목차

- [1. 테스트 환경](#테스트-환경)
  - [1.1 성능저하 문제 확인](#성능저하-문제파악)
  - [1.2 테스트 결과-웜,콜드캐시](#테스트-결과)
- [2. JPA Fetch 전략 비교](#jpa-fetch-전략별-성능-비교)
  - [2.1 Postgres work_mem 설명 및 디스크 스필 확인](#비교-전-postgresql-work_mem-설명)
  - [2.2 Lazy Loading](#1️-lazy-loading)
  - [2.3 Fetch Join](#2️-fetch-join)
  - [2.4 Batch Fetch](#3️-batch-fetch-hibernatedefault_batch_fetch_size)
- [4. 종합 비교 및 결론](#📈-종합-비교-요약)
- [5. 결론](#💬-결론)
- [6. 🚀 2차 성능개선점 (Fetch Join 튜닝)](#🚀-2차-성능개선점)

  - [6.1 Redis 2차 캐시](#redis-2차-캐시)
  - [6.2 인덱스 및 실행계획 튜닝](#인덱스-및-실행계획-튜닝)
  - [6.3 쿼리 구조 최적화](#쿼리-구조-최적화)
  - [6.4 개선 후 성능 비교 (p95/RPS)](#개선-후-성능-비교)

- EndPoint:/api/nodes/{id}

## 테스트 환경

| 항목           | 설정                                                                                                       |
| -------------- | ---------------------------------------------------------------------------------------------------------- |
| 서버 사양      | 4 Core / 16GB / SSD                                                                                        |
| DB             | PostgreSQL 17 + TimescaleDB                                                                                |
| 커넥션 풀      | HikariCP max=150,idle=80                                                                                   |
| Redis          | max-active=128                                                                                             |
| 테스트 도구    | k6 v0.52                                                                                                   |
| 초기 부하 유형 | EdgeController와 비슷한 RPS를 상정하였을때 매우 큰 과부하 -> 요청량 >= 10000에 맞춰서 120RPS \* 90s로 진행 |
| 네트워크       | 내부 브릿지 (Docker Compose 환경)                                                                          |

### 성능저하 문제파악

- 지난 엣지 컨트롤러 부하테스트에 비해 낮은 처리량 확인

- 기존 데이터의 경우 노드-링크매핑테이블(node_note_link)-노트 다대일 구조

- 초기 테스트 구조 노드 1대 링크매핑테이블 1로 테스트 진행 Lazy로딩만 사용하였다.

- 개선 방향이 필요해보였고 확실한 비교를 위해 노드와 연결된 노트의 개수를 10개로 늘려서 테스트 진행 -> 추후 서비스 운영시에 예상되는 노드1개당 최대 5개의 노트 사용량으로 예측되므로 10개의 노트링크를 5개로 줄여 안정값 테스트 진행예정이다.

## 테스트 결과

### 웜캐시 테스트

- 각 동일 조건, (APP,DB)컨테이너 내린 후 재시작, OS캐시 제거 후 3회 중에 중앙값으로 기록

| 항목                      | RPS | P95        | Throughtput  |
| ------------------------- | --- | ---------- | ------------ |
| Lazy단건(work_mem:8)      | 120 | 1348.48 ms | 127.23 req/s |
| Lazy단건(work_mem:128)    | 120 | 1561.42 ms | 127.23 req/s |
| Lazy목록(work_mem:8)      | 120 | 2551.14 ms | 125.01 req/s |
| Lazy목록(work_mem:128)    | 120 | 2753.94 ms | 125.01 req/s |
| 배치단건(work_mem:8)      | 120 | 1464.53 ms | 127.23 req/s |
| 배치단건(work_mem:128)    | 120 | 1720.38 ms | 127.23 req/s |
| 배치목록(work_mem:8)      | 120 | 1887.67 ms | 125.01 req/s |
| 배치목록(work_mem:128)    | 120 | 2714.83 ms | 125.01 req/s |
| FetchJoin단건(work_mem:8) | 120 | 874.27 ms  | 127.22 req/s |
| FetchJoin목록(work_mem:8) | 120 | 412.91 ms  | 125.01 req/s |

### 콜드캐시 테스트

- 각 동일 조건, (APP,DB)컨테이너 내린 후 재시작, OS캐시 제거 후 3회 중에 중앙값으로 기록

| 항목                      | RPS | P95        | Throughtput |
| ------------------------- | --- | ---------- | ----------- |
| Lazy단건(work_mem:8)      | 40  | 3362.82 ms | 46.70 req/s |
| Lazy목록(work_mem:8)      | 40  | 6643.57 ms | 46.67 req/s |
| 배치단건(work_mem:8)      | 40  | 7516.25 ms | 46.67 req/s |
| 배치목록(work_mem:8)      | 40  | 7246.47 ms | 46.67 req/s |
| FetchJoin단건(work_mem:8) | 40  | 3149.68 ms | 46.70 req/s |
| FetchJoin목록(work_mem:8) | 40  | 4871.70 ms | 46.70 req/s |

## JPA Fetch 전략별 성능 비교

(테스트 환경: 동일 조건 / APP·DB 초기화 / OS 캐시 제거 후 3회 중앙값 기준)

### 비교 전 PostgreSQL work_mem 설명

- work_mem이란?

  - PostgreSQL에서 정렬(Sort), 해시(Hash Join, Hash Aggregate) 등을 수행할 때
    연산당 사용할 수 있는 메모리 한도를 지정하는 파라미터
  - 기본값은 수 MB(현재 서비스는 8MB) 수준이며, 작을수록 디스크 임시파일(temp spill)이 늘어나고 크면 RAM을 더 사용

- 이번 테스트에서 설정값

  - 해당 서비스에서 기본값 8MB -> 테스트를 위해 128MB로 진행
  - 주의점 : 해당 설정은 모든 병렬쿼리에서 전역적으로 사용되므로 128MB처럼 과도하게 사용시 OOM, 성능저하 발생
  - work_mem의 크기에 따른 성능을 보고자 테스트 환경에서만 임의적으로 사용

- 이번 테스트에서 효과가 거의 없었던 이유
  - JPA Fetch 전략에 따른 차이는 **쿼리 패턴 및 왕복 횟수** 차이이지,
    정렬 또는 해시 작업량 차이가 아니기 때문이다.
  - 따라서 work_mem을 8MB→128MB로 늘려도 쿼리 플랜이나 I/O 패턴이 변하지 않아
    p95 개선이 관찰되지 않았다.

<details>
<summary>📜 work_mem관련 디스크 스필 확인로그 (클릭하여 보기)</summary>

```sql
# work_mem 8에서 PostgreSQL이 쿼리 수행 중 임시 디스크(temp) 를 사용했는지 확인하는 쿼리
trader=# SELECT datname,
trader-# temp_files,
trader-# temp_bytes,
trader-# (temp_bytes/1024/1024)::numeric(10,2) AS temp_mb
trader-# FROM pg_stat_database
trader-# ORDER BY temp_bytes DESC
trader-# LIMIT 10;
datname | temp_files | temp_bytes | temp_mb
-----------+------------+------------+---------
postgres | 0 | 0 | 0.00
trader | 0 | 0 | 0.00
template1 | 0 | 0 | 0.00
template0 | 0 | 0 | 0.00
(5 rows)

# temp_files:DB 레벨에서 생성된 임시파일 개수 (work_mem 초과 시 발생)
# temp_bytes:생성된 임시파일의 총 크기 (바이트 단위)
# temp_mb:위를 MB로 환산한 계산 컬럼

trader=#
trader=# SELECT queryid, calls, temp_blks_read, temp_blks_written,
trader-# (temp_blks_written\*8/1024)::numeric(10,2) AS temp_mb,
trader-# query
trader-# FROM pg_stat_statements
trader-# WHERE temp_blks_written > 0
trader-# ORDER BY temp_blks_written DESC
trader-# LIMIT 10;
queryid | calls | temp_blks_read | temp_blks_written | temp_mb | query
---------+-------+----------------+-------------------+---------+-------
(0 rows)

# temp_blks_read:임시파일에서 읽은 블록 수
# temp_blks_written:임시파일에 쓴 블록 수 (work_mem 초과 시 기록됨)
# temp_mb:8KB 블록을 MB로 환산
# query:해당 SQL 쿼리
```

</details>

### 1️. Lazy Loading

| 구분      | 콜드캐시              | 웜캐시                 |
| --------- | --------------------- | ---------------------- |
| 단건 조회 | p95 **3362ms** @40RPS | p95 **1348ms** @120RPS |
| 목록 조회 | p95 **6643ms** @40RPS | p95 **2551ms** @120RPS |

#### 💡 설명

엔티티를 지연로딩(Lazy)으로 가져올 때, 연관 엔티티 접근 시마다 추가 쿼리가 발생해 **N+1 문제**가 발생한다.
단건은 상대적으로 덜하지만, 목록의 경우 **왕복 쿼리 횟수가 기하급수적으로 증가**하여 DB I/O 병목이 생긴다.
work_mem 8→128로 변경 시 큰 차이가 없으며, 이는 병목이 정렬/해시가 아니라 **왕복 I/O**을 확인할 수 있다.

<details>
<summary>📜 Lazy목록 로그 결과 (클릭하여 보기)</summary>

```
# 쿼리 11번 노드 1번 + 링크 10번
Hibernate:
    /* select
        n
    from
        Node n
    where
        n.page.id = :pageId
    order by
        n.id  */ select
            n1_0.id,
            n1_0.content,
            n1_0.created_date,
            n1_0.modified_date,
            n1_0.page_id,
            n1_0.record_date,
            n1_0.subject,
            n1_0.symb,
            n1_0.x,
            n1_0.y
        from
            node n1_0
        where
            n1_0.page_id=?
        order by
            n1_0.id
Hibernate:
    select
        nl1_0.node_id,
        nl1_0.id,
        nl1_0.note_id
    from
        node_note_link nl1_0
    where
        nl1_0.node_id=?
Hibernate:
    select
        nl1_0.node_id,
        nl1_0.id,
        nl1_0.note_id
    from
        node_note_link nl1_0
    where
        nl1_0.node_id=?
Hibernate:
    select
        nl1_0.node_id,
        nl1_0.id,
        nl1_0.note_id
    from
        node_note_link nl1_0
    where
        nl1_0.node_id=?
Hibernate:
    select
        nl1_0.node_id,
        nl1_0.id,
        nl1_0.note_id
    from
        node_note_link nl1_0
    where
        nl1_0.node_id=?
Hibernate:
    select
        nl1_0.node_id,
        nl1_0.id,
        nl1_0.note_id
    from
        node_note_link nl1_0
    where
        nl1_0.node_id=?
Hibernate:
    select
        nl1_0.node_id,
        nl1_0.id,
        nl1_0.note_id
    from
        node_note_link nl1_0
    where
        nl1_0.node_id=?
Hibernate:
    select
        nl1_0.node_id,
        nl1_0.id,
        nl1_0.note_id
    from
        node_note_link nl1_0
    where
        nl1_0.node_id=?
Hibernate:
    select
        nl1_0.node_id,
        nl1_0.id,
        nl1_0.note_id
    from
        node_note_link nl1_0
    where
        nl1_0.node_id=?
Hibernate:
    select
        nl1_0.node_id,
        nl1_0.id,
        nl1_0.note_id
    from
        node_note_link nl1_0
    where
        nl1_0.node_id=?
Hibernate:
    select
        nl1_0.node_id,
        nl1_0.id,
        nl1_0.note_id
    from
        node_note_link nl1_0
    where
        nl1_0.node_id=?
```

</details>

#### ✅ 장점

- 코드 단순, 필요한 시점에 로딩 → 초기 부하 적음
- 작은 연관관계(1:1, 1:소량)에서는 유효

#### ⚠️ 단점

- N+1 쿼리로 인한 대규모 목록 처리 성능 저하
- 캐시 미스 시 I/O 부담 심함
- RPS 상승 시 커넥션/락 대기 증가

#### 🔧 권장 상황

- **단건 중심 API**
- **연관관계 접근이 거의 없는 목록** (DTO projection으로 최소화)

---

### 2️. Fetch Join

| 구분      | 콜드캐시              | 웜캐시                |
| --------- | --------------------- | --------------------- |
| 단건 조회 | p95 **3149ms** @40RPS | p95 **874ms** @120RPS |
| 목록 조회 | p95 **4872ms** @40RPS | p95 **413ms** @120RPS |

#### 💡 설명

`fetch join`으로 필요한 연관 엔티티를 한 번의 쿼리로 가져오면 **왕복 횟수가 최소화**되어 레이턴시가 급감한다.
테스트 결과, 웜 상태에서 단건 조회는 목록 조회는 **874ms(p95)** 목록 조회는 **412ms(p95)** 로 Lazy의 약 **6배 이상 빠르다**.

<details>
<summary>📜 fetch목록 로그 결과 (클릭하여 보기)</summary>

```
#쿼리 1번
Hibernate:
    /* select
        distinct n
    from
        Node n
    left join

    fetch
        n.noteLinks l
    where
        n.page.id = :pageId
    order by
        n.id  */ select
            distinct n1_0.id,
            n1_0.content,
            n1_0.created_date,
            n1_0.modified_date,
            nl1_0.node_id,
            nl1_0.id,
            nl1_0.note_id,
            n1_0.page_id,
            n1_0.record_date,
            n1_0.subject,
            n1_0.symb,
            n1_0.x,
            n1_0.y
        from
            node n1_0
        left join
            node_note_link nl1_0
                on n1_0.id=nl1_0.node_id
        where
            n1_0.page_id=?
        order by
            n1_0.id
```

</details>

#### ✅ 장점

- 왕복 최소화 → **가장 낮은 p95**
- DB 캐시가 잡히면 매우 안정적이고 일관된 응답
- DTO projection과 병행 시 전송량 최소화 가능

#### ⚠️ 단점

- **한방 컬렉션 Fetch Join + 페이징 불가** (카르테시안/중복 문제)
  - Fetch Join 시 Page(Pageable)로 받기 제한적인 이유
  ```
  # 예를 들어 노드 A,B....Z가 있으며 각각 링크가 5개씩 있다고 한다면
  # 페이징으로 5개 만큼의 노드를 가지고 오고 싶을 때 보통 아래와 같은 쿼리로 조인한다.
  SELECT n
  FROM node n
  ORDER BY n.id
  LIMIT 5 OFFSET 0;
  # 하지만 페이징은 DB에서 행단위로 자르고 fetch join의 경우 연관된 링크까지 조인하여 중복된 행으로 펼쳐지게 된다.
  select *
  from node n
  left join node_note_link l on n.id = l.node_id
  order by n.id
  limit 5 offset 0;
  # (A,1),(A,2),(A,3),(A,4),(A,5),(B,1),(B,2),(B,3)...이런 구조로 펼쳐진다.
  # 따라서 기존의 의도는 노드 A,B,C,D,E와 연관된 링크를 가져오는 것이 아닌
  노드 A의 5개 링크만 가져오게 되는 결과가 발생한다.
  ```
  - 해결방법 : 1.노드의 ID만 따로 페이징 -> 2. fetch join사용
  - MultipleBagFetchException문제
    - 현재 애플리케이션에는 없지만 만약 동시에 부모 1개에 2개 이상의 리스트 기반 컬렉션을 fetch join할 경우 **카르테시안 곱으로 인한 결과셋 폭증과 엔티티 매핑 혼란을 방지하기 위한 Hibernate의 보호 메커니즘**으로 예외를 발생시킨다.
    - [2단계 페이징,fetchJoin 관련링크](https://vladmihalcea.com/join-fetch-pagination-spring/?utm_source=chatgpt.com)
    - [MultipleBagFetchException 관련링크](https://thorben-janssen.com/hibernate-tips-how-to-avoid-hibernates-multiplebagfetchexception/?utm_source=chatgpt.com)
- 다중 fetch join 불가(하이버네이트 제약)
- 결과셋 폭증 위험 → 필요한 연관만 선택적으로

#### 🔧 권장 상황

- **읽기 중심 API**, 프론트 한 번의 호출로 완결되는 조회
- 목록은 `ID 페이지 → Fetch Join 2단계 조회` 패턴으로 안정화하기

```java
#실제 사용한 코드
#단건
@Query("""
select n.id
from Node n
where n.page.id = :pageId
order by n.id
""")
<Long> findIdsByPageId(Long pageId, Pageable pageable);

@Query("""
select distinct n
from Node n
left join fetch n.noteLinks l
where n.id in :ids
order by n.id
""")
List<Node> findAllWithLinksByIds(Collection<Long> ids);
```

---

### 3️. Batch Fetch (`hibernate.default_batch_fetch_size`)

| 구분      | 콜드캐시              | 웜캐시                 |
| --------- | --------------------- | ---------------------- |
| 단건 조회 | p95 **7516ms** @40RPS | p95 **1465ms** @120RPS |
| 목록 조회 | p95 **7246ms** @40RPS | p95 **1888ms** @120RPS |

#### 💡 설명

LazyLoading의 N+1 문제를 완화하기 위해 설정된 `default_batch_fetch_size`는
연관 엔티티를 **IN 쿼리(batch)** 로 묶어 한 번에 가져온다.
콜드에서는 효과 미미했지만, 웜캐시 목록에서 **2551→1888ms**로 개선되어 왕복 최소화 확인

<details>
<summary>📜 batch fetch목록 로그 결과 (클릭하여 보기)</summary>

```
#쿼리 2번 노드 + 링크배치
Hibernate:
    /* select
        n
    from
        Node n
    where
        n.page.id = :pageId
    order by
        n.id  */ select
            n1_0.id,
            n1_0.content,
            n1_0.created_date,
            n1_0.modified_date,
            n1_0.page_id,
            n1_0.record_date,
            n1_0.subject,
            n1_0.symb,
            n1_0.x,
            n1_0.y
        from
            node n1_0
        where
            n1_0.page_id=?
        order by
            n1_0.id
Hibernate:
    select
        nl1_0.node_id,
        nl1_0.id,
        nl1_0.note_id
    from
        node_note_link nl1_0
    where
        nl1_0.node_id = any (?)
```

</details>

#### ✅ 장점

- Lazy보다 **왕복 수 감소 → 성능 개선**
- **페이징과 완벽히 호환가능**
- 코드 수정 없이 설정만으로 적용 가능

#### ⚠️ 단점

- 콜드 I/O 상황에서는 Lazy와 큰 차이 없음
- 배치 사이즈 과다 시 IN 리스트 커져 플랜 비효율
- 완전한 1회 쿼리는 아니므로 fetch join보단 느림

#### 🔧 권장 상황

- **페이징 필수 + 연관 소량 접근**
- fetch join 폭발 위험이 있는 중간 복잡도 엔티티
- 일반적으로 64~256 수준 권장

```yaml
spring:
  jpa:
    properties:
      hibernate.default_batch_fetch_size: 16
```

---

## 📈 종합 비교 요약

| 전략        | 왕복 쿼리 수 | 페이징 호환 | 성능(p95, 웜) | 권장 상황                 |
| ----------- | ------------ | ----------- | ------------- | ------------------------- |
| Lazy        | 많음 (N+1)   | 호환        | 1348~2551ms   | 단건 조회, 소규모         |
| Batch Fetch | 중간         | 호환        | 1465~1888ms   | 목록(페이징 필수)         |
| Fetch Join  | 최소 (1회)   | 제한        | **413~874ms** | 읽기 집중, 즉시 응답 필요 |

---

## 💬 결론

> 동일 환경에서 JPA의 세 가지 Fetch 전략을 비교한 결과,
> **Fetch Join이 왕복 최소화로 가장 낮은 p95를 기록(412ms @120RPS)** 하였으며,
> **Batch Fetch는 페이징과 호환되면서 Lazy 대비 평균 25% 성능 개선**을 보였다.
> 단순 메모리 확장(work_mem 조정)은 효과가 없었으며, **쿼리 구조·왕복 최소화가 핵심 병목 요인**임을 확인하였다.
> 본 실험을 통해 API별 특성에 따라 Fetch 전략을 구분 적용하는 것이 대규모 트래픽 환경에서 필수적임을 확인했다.

---

### 🔁 테스트 단계 전환 안내

#### 테스트 단계 전환(1차 → 2차)

- 1차 테스트에서는 UI 요구가 없었기 때문에 노트 링크의 noteId만 반환하여 왕복 쿼리 수 최소화 전략을 검증했다.
  해당 실험을 통해 쿼리 횟수가 적을수록(p95↓) 성능이 유의미하게 개선됨을 확인하였다.
- 2차 테스트부터는 UI 요구(노드 하단에 노트 제목 표시 및 클릭 로딩)에 따라 반환 스키마를 noteId → {id,title}로 확장한다.
  스키마 변경에 따른 페이로드 증가를 감안하여, 2차에서는 Fetch Join 대신 DTO 프로젝션/네이티브 집계를 채택해 로우 폭증 없이 필드만 추가하도록 설계했다.
- 1차 테스트와 마찬가지로 동일 원칙(왕복 최소화)을 유지한 2차 실험을 설계하였다.

### 스키마 변화 (요약)

- 1차 테스트

```
{
  "id": 101,
  "subject": "…",
  "noteIds": [11, 12, 15],
  ...
}
```

- 2차 테스트

```
{
  "id": 101,
  "subject": "…",
  "notes": [{"id":11,"title":"제목A"}, …],
  ...
}
```

<br><br><br>

# 🚀-2차-성능개선점

- 프로젝트 SLO 목표 : 가벼운 쿼리의 경우 600RPS, 무거운 쿼리의 경우 300RPS p95 ~= 300ms
- 2차 튜닝 성능 목표 : 300RPS p95 ~= 300ms

<details>
<summary>📜 psql 로그 결과 (클릭하여 보기)</summary>

⚙️ Before: Node × Note = 10 × 10 = 100 rows

- 노드당 10개의 행으로 폭증된다

```sql
trader=# SELECT
trader-#   n.id AS node_id,
trader-#   n.subject AS node_subject,
trader-#   l.id AS link_id,
trader-#   l.note_id AS note_id,
trader-#   no.subject AS note_title
trader-# FROM node n
trader-# LEFT JOIN node_note_link l ON l.node_id = n.id
trader-# LEFT JOIN note no ON no.id = l.note_id
trader-# WHERE n.page_id = 200125
trader-# ORDER BY n.id, l.note_id;
 node_id |   node_subject    | link_id | note_id | note_title
---------+-------------------+---------+---------+------------
      43 | subject_200125_1  | 2000401 |      29 | 안뇽하세요
      43 | subject_200125_1  | 2000402 |      30 | 123
      43 | subject_200125_1  | 2000403 |      31 | string
      43 | subject_200125_1  | 2000404 |      32 | string
      43 | subject_200125_1  | 2000405 |      33 | TEST
      43 | subject_200125_1  | 2000406 |      34 | asd
      43 | subject_200125_1  | 2000407 |      35 | 123
      43 | subject_200125_1  | 2000408 |      36 | 123
      43 | subject_200125_1  | 2000409 |      37 | DDC
      43 | subject_200125_1  | 2000410 |      38 | note_138

...
```

✅ After: Grouped by Node → 10 rows (notes aggregated as JSON array)

- 예상대로 10개의 노트id와 노트 제목이 한 행 JSON형식으로 나온다

```sql
trader=# SELECT
trader-#   n.id,
trader-#   n.x, n.y, n.subject, n.page_id,
trader-#   COALESCE(
trader(#     json_agg(json_build_object('id', l.note_id, 'title', no.subject)
trader(#              ORDER BY l.note_id)
trader(#       FILTER (WHERE l.note_id IS NOT NULL),
trader(#     '[]'::json
trader(#   ) AS notesJson
trader-# FROM node n
trader-# LEFT JOIN node_note_link l ON l.node_id = n.id
trader-# LEFT JOIN note no ON no.id = l.note_id
trader-# WHERE n.page_id = 200125
trader-# GROUP BY n.id, n.x, n.y, n.subject, n.page_id
trader-# ORDER BY n.id;
   id    |    x    |    y    |      subject      | page_id |                                                                                                                                                            notesjson
---------+---------+---------+-------------------+---------+---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
      43 |   457.3 | -226.57 | subject_200125_1  |  200125 | [{"id" : 29, "title" : "안뇽하세요"}, {"id" : 30, "title" : "123"}, {"id" : 31, "title" : "string"}, {"id" : 32, "title" : "string"}, {"id" : 33, "title" : "TEST"}, {"id" : 34, "title" : "asd"}, {"id" : 35, "title" : "123"}, {"id" : 36, "title" : "123"}, {"id" : 37, "title" : "DDC"}, {"id" : 38, "title" : "note_138"}]
  200043 ....
```

</details>

<details>
<summary>📜 psql 로그 결과-행 수만 조회 (클릭하여 보기)</summary>

- 10개의 노드에 대해서 각 100개 10개, 결과 행의 수가 10분의 1로 줄어들어 행 폭증이 사라진 모습이다.
- ⚙️ Before: Node × Note = 10 × 10 = 100 rows

```sql
trader=# SELECT COUNT(*) AS row_count_before
trader-# FROM node n
trader-# LEFT JOIN node_note_link l ON l.node_id = n.id
trader-# LEFT JOIN note no ON no.id = l.note_id
trader-# WHERE n.page_id = 200125;
 row_count_before
------------------
              100
(1개 행)
```

✅ After: Grouped by Node → 10 rows (notes aggregated as JSON array)

```
trader=# SELECT COUNT(\*) AS row_count_after
trader-# FROM (
trader(# SELECT n.id
trader(# FROM node n
trader(# LEFT JOIN node_note_link l ON l.node_id = n.id
trader(# LEFT JOIN note no ON no.id = l.note_id
trader(# WHERE n.page_id = 200125
trader(# GROUP BY n.id
trader(# ) t;
row_count_after

---

              10

(1개 행)

```

</details>

- 결과적으로, json_agg와 GROUP BY를 이용하여  
  10배에 달하던 행 폭증이 제거되고,  
  각 노드가 단일 행으로 압축되어 조회 효율이 극적으로 개선되었다.

## 2. DTO 집계 1쿼리로 전환

## 3. 페이로드 다이어트

## 4. 드라이버/풀 자잘한 팁

- PG JDBC prepareThreshold 기본값 권장(운영에선 서버사이드 PS 이점 큼)
- 기존은 0으로 진행

```

```
