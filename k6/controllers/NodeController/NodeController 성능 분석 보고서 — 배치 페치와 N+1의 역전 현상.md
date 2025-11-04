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

### 1차 테스트 문제확인 및 원인분석(N+1)

- Node API 성능부하 테스트를 진행중 Edge API에 비해 동일한 응답 지연구간에서 처리량이 낮음을 확인

#### 1차 테스트 문제확인 및 원인분석(fetch join)

- 노드의 경우 Note와 다대다 연관관계로 맺어져 있으며 중간의 node_note_link 테이블로 연관관계를 설정해두었다.
- Node DTO 변환 과정에서 각 노드에 포함된 Note ID 목록을 반환해야 하므로, node.getNoteLinks() 접근 시 Lazy 로딩이 발생해 연관된 노트 엔티티가 추가 조회되었다.
- 단건 조회의 경우 1번 조회시 10건의 노트가 조회되는 N+1문제가 발생한다.

#### 문제해결 접근방법

- 초기에는 fetch join을 통해 N+1을 제거하고 실험을 해보았으며 이 경우 오히려 사용하지 않는 것보다 성능저하가 발생하였다.

#### 성능저하 원인

- left join fetch n.noteLinks l left join fetch l.note로 세 테이블을 한 번에 로딩 → 결과 행이 N×K로 팽창(단건도 1×K) → 네트워크/직렬화/GC + Hibernate 중복 제거/객체 조립 비용 증가.

  - 또한 본 테스트는 300 RPS 수준의 부하 환경에서 진행되었기 때문에, 행 수가 적어도 요청 수 × 조인 비용이 누적되어 결과적으로 행 폭증과 유사한 효과(조인 경쟁 및 조립 오버헤드)가 발생하였다.
  - 요약
    - 조인비용증가
    - 행 폭증
    - 네트워크 전송량
    - Hibernate에서 중복 제거 및 객체 조립 비용 증가

- 이에 따라 fetch join 대신 Hibernate의 `default_batch_fetch_size` 옵션(100)을 적용하여, 연관 엔티티를 개별 쿼리 대신 일정 단위로 배치 조회하도록 설정하였다.

### 1차 테스트 문제확인 및 원인분석(배치조회)

- N+1문제와 fetch join의 카디널리티 폭증을 경험 후 그 중간 합의지점인 Hibernate의 `default_batch_fetch_size`옵션을 이용하여 같은 부하의 테스트 진행

- 배치는 Lazy 초기화 시 여러 부모의 자식을 IN (…)으로 묶어 가져오는 방식

| 항목            | RPS | P95      | throughput |
| --------------- | --- | -------- | ---------- |
| 단건조회(배치O) | 30  | 369.72ms | 37.23req/s |
| 단건조회(배치X) | 30  | 17.30ms  | 37.23req/s |
| 다건조회(배치O) | 30  | 344.38ms | 35.01req/s |
| 다건조회(배치X) | 30  | 65.83ms  | 35.01req/s |

#### 원인분석

- `default_batch_fetch_size`의 경우 Lazy로딩시에 가져올 데이터를 묶어서 한번에 조회하는 방식이다.
  <br>예시로 노드1건을 반환하기 위해 DTO로 변환과정에서 노트의 강제초기화가 일어날 경우 10개의 노트 조회 대신 SQL의 IN절을 사용하여 한번에 가져오게 된다.

- 다만 WHERE node_id IN ( ids...)형태의 큰 쿼리로 던지기에

  1. 파싱/플래너의 오버헤드가 발생
  2. 데이터베이스의 해시,비트맵 연산과정에서 많은 리소스 사용한다.

- 결과
  | 항목 | RPS | P95 | throughput |
  | --------------- | --- | -------- | ---------- |
  | 단건조회(배치O) | 30 | 369.72ms | 37.23req/s |
  | 단건조회(배치O)&&<br>`work_mem` 8->128변경 | 30 | 18.97 ms | 37.23req/s|
  | 단건조회(배치X) | 30 | 17.30ms | 37.23req/s |

#### 해결방법

- `work_mem`이 너무 작을 경우 임시파일이 스필되어서 디스크 I/O가 발생합니다.
- postgreSQL의 `work_mem`속성을 기존 8MB -> 128MB로 증가시켰고 낮은 RPS인 30에서 매우 큰 성능 향상을 확인하였다.
- 해당 향상 수치는 높은 RPS로 갈 수록 적어지는 것 또한 확인하였지만 진짜 파일 스필이 문제인지 확인할 필요성을 느꼈다.

- 따라서 fetch,배치의 성능 저하가 발생한 원인을 확인하기 위해 아래의 쿼리나 컨테이너의 임시 파일 발생 로그를 확인한 결과 스필은 관측되지 않았으며(pg_stat_database.temp_bytes=0),
  <br> 성능 향상은 스필 해소가 아니라 해시/비트맵 버퍼 여유 증가 및 플래너 선택 개선의 효과로 판단했다.

```
SELECT datname, temp_files, pg_size_pretty(temp_bytes) AS temp_bytes
FROM pg_stat_database
WHERE datname = current_database();

```

- 결론: 내부 해시·비트맵 버퍼 크기가 확장되며 플래너의 조인·비트맵 선택이 효율적으로 변경되었다.
  이에 따라 CPU 캐시 미스 및 해시 충돌률이 감소하여 270RPS기준 평균 지연이 개선되었다.

#### work_mem의 위험성

- 본 실험은 원인 확인 목적의 테스트 단계로, work_mem을 최소 8MB와 최대 128MB로 각각 설정하여 비교했다
- work_mem은 쿼리의 각 실행 노드 및 병렬 워커 단위로 독립적으로 할당한다.
- 이로 인해 서버 메모리 한계(OOM, Out of Memory) 또는 스왑 과다 사용으로 인한 시스템 다운이 발생할 수 있다.

- 따라서 실제 운영에서는 특정 연산(정렬·해시·비트맵 등) 부하가 집중되는 구간에 한정하여 `SET LOCAL work_mem`을 적용하는 국소적 조정 방식을 기본 원칙으로 한다.

#### 요약

- `work_mem`수치 향상으로 내부 해시·비트맵 버퍼 크기가 확장되며 플래너의 조인·비트맵 선택이 효율적으로 변경되어 성능향상
- 해당 용량 설정의 중요성을 인지하고 초기에 진행했던 fetch join테스트도 동시에 진행하였다.
- 셋 중 하나라도 목표 P95 300부근에 도달하면 해당 RPS로 동일 조건 테스트 진행하였다.
- 추가로 다건 조회와 단건 조회의 경우 흐름이 비슷하고 현재 방향성을 고려하는 단계이기에 단건 조회로만 진행 -> 추후 방향성 확정 후 다건 조회 안정성 테스트 진행 예정이다.

### 최종 결과

| 항목                        | RPS | P95        | throughput   |
| --------------------------- | --- | ---------- | ------------ |
| 단건 `work_mem=128MB`       | 270 | 271.76 ms  | 277.23 req/s |
| 배치 `work_mem=128MB`       | 270 | 1824.30 ms | 277.23 req/s |
| fetch join `work_mem=128MB` | 270 | 1841.11 ms | 277.23 req/s |

#### 요약 결론

- fetch join → 조인 카디널리티 폭증, Hibernate 조립 비용 → 성능 저하

- batch fetch → 해시/비트맵 연산 부하, work_mem 의존도 ↑ → 성능 저하

- Lazy (기본) → 쿼리 수는 많지만 단건 쿼리가 짧고 CPU 캐시 효율 ↑ → 최고 성능

### 2차 문제

1. 서비스는 Node와 Note의 다대다 연관관계로써 Node-NodeNoteLink-Note엔티티 구조이며<br>
   Node엔티티를 조회 후 반환할 시에 노드에 속한 노트id값을 같이 포함하여 dto로 변환하여 반환하게 된다<br>
   이 경우에 추가 쿼리가 나가게 되므로 fetch join을 통해 N+1문제를 해결하려고 하였다.

```java
#반환되는 DTO, Node조회시에 연관 Note조회 후 id값 추출과정을 거친다.
public class ResponseNodeDto {
    private Long id;
    private double x;
    private double y;
    private String subject;
    private String content;
    private String symb;
    private LocalDate recordDate;
    private Set<Long> noteIds;
    private Long pageId;
    private LocalDateTime createdAt;
    private LocalDateTime modifiedAt;

    public static ResponseNodeDto toResponseDto(Node node) {
        Long pageId = (node.getPage() != null) ? node.getPage().getId() : null;

        Set<Long> noteIds = node.getNoteLinks().stream()
                .map(link -> link.getNote().getId())
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));

        return ResponseNodeDto.builder()
                .id(node.getId())
                .x(node.getX())
                .y(node.getY())
                .subject(node.getSubject())
                .content(node.getContent())
                .symb(node.getSymb())
                .recordDate(node.getRecordDate())
                .pageId(pageId)
                .noteIds(noteIds)
                .build();
    }
}
```

### 2차 문제 해결 방안

1. 해당 노드 엔드포인트에서 결국 node와 연결된 note의 id값을 내려주어야 한다.
2. 이를 위해 Lazy로딩, batch, fetch join을 비교하였으며 Lazy로딩을 방향성을 잡았다.
