### 조합 × 변형 전부 동시 실행

k6 run -e BASE_URL=http://172.30.1.78:8080 -e CONTROLLERS=StockController -e DURATION=30s scripts/api.js

k6 run -e BASE_URL=http://172.30.1.78:8080 -e CONTROLLERS=NodeController -e DURATION=30s scripts/api.js

### 특정 endpoint만, variants 포함해서

k6 run -e BASE_URL=http://172.30.1.78:8080 -e ENDPOINTS=range scripts/api.js

k6 run -e BASE_URL=http://172.30.1.78:8080 -e ENDPOINTS=range -e EXECUTOR=constant-arrival-rate scripts/api.js

### constant-arrival-rate사용

k6 run -e BASE_URL=http://172.30.1.78:8080 -e VARIANTS=light -e CONTROLLERS=GraphController -e EXECUTOR=constant-arrival-rate scripts/api.js
k6 run -e BASE_URL=http://172.30.1.78:8080 -e VARIANTS=heavy -e CONTROLLERS=GraphController -e EXECUTOR=constant-arrival-rate scripts/api.js
k6 run -e BASE_URL=http://172.30.1.78:8080 -e VARIANTS=heavy -e CONTROLLERS=StockController -e ENDPOINTS=range -e EXECUTOR=constant-arrival-rate scripts/api.js

### 안정임계값 테스트

k6 run -e BASE_URL=http://172.30.1.78:8080 -e CONTROLLERS=TickerController -e VARIANTS=find-limit scripts/api.js
k6 run -e BASE_URL=http://172.30.1.78:8080 -e CONTROLLERS=StockController -e ENDPOINTS=range -e VARIANTS=find-limit scripts/api.js
k6 run -e BASE_URL=http://172.30.1.78:8080 -e CONTROLLERS=ContentController -e VARIANTS=find-limit scripts/api.js
k6 run -e BASE_URL=http://172.30.1.78:8080 -e CONTROLLERS=NodeController -e VARIANTS=find-limit scripts/api.js
k6 run -e BASE_URL=http://172.30.1.78:8080 -e CONTROLLERS=PageController -e VARIANTS=find-limit scripts/api.js
k6 run -e BASE_URL=http://172.30.1.78:8080 -e CONTROLLERS=GraphController -e VARIANTS=find-limit scripts/api.js
티커
, "AA", "AD", "AAC"
노트
, "3248", "3255", "3263"
노드
, "400067", "600067", "800067"
엣지
, "53163", "845763", "1199762"
그래프
, "210149", "220149", "290149"
페이지
, "210149", "220149", "290149"
스톡 -스톡
, "MSFT" -엔드
, "2025-03-10T00:00:00"

k6 run -e BASE_URL=http://172.30.1.78:8080 -e DROP_BODIES=0 -e CONTROLLERS=StockController -e ENDPOINTS=range -e VARIANTS=find-limit scripts/api.js
k6 run -e BASE_URL=http://localhost:8080 -e CONTROLLERS=StockController -e ENDPOINTS=range -e VARIANTS=find-limit scripts/api.js
k6 run -e BASE_URL=http://172.30.1.78:8080 -e CONTROLLERS=StockController -e ENDPOINTS=range -e VARIANTS=find-limit scripts/api.js
k6 run -e BASE_URL=http://172.30.1.78:8080 -e CONTROLLERS=GraphController -e VARIANTS=find-limit scripts/api.js

k6 run -e BASE_URL=http://172.30.1.78:8080 -e CONTROLLERS=StockController -e ENDPOINTS=range -e VARIANTS=find-limit -e ENDPOINTS_PARAMS='{"start":["2024-09-01T00:00:00Z"],"end":["2024-10-01T00:00:00Z"],"stock":["TSLA"]}' scripts/api.js
