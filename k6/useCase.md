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

k6 run -e BASE_URL=http://172.30.1.78:8080 -e CONTROLLERS=TickerController -e VARIANTS=find-limit scripts/apiAuto.js
k6 run -e BASE_URL=http://172.30.1.78:8080 -e CONTROLLERS=StockController -e ENDPOINTS=range -e VARIANTS=find-limit scripts/apiAuto.js
k6 run -e BASE_URL=http://172.30.1.78:8080 -e CONTROLLERS=ContentController -e VARIANTS=find-limit scripts/apiAuto.js
k6 run -e BASE_URL=http://172.30.1.78:8080 -e CONTROLLERS=NodeController -e VARIANTS=find-limit scripts/apiAuto.js
k6 run -e BASE_URL=http://172.30.1.78:8080 -e CONTROLLERS=PageController -e VARIANTS=find-limit scripts/apiAuto.js
k6 run -e BASE_URL=http://172.30.1.78:8080 -e CONTROLLERS=GraphController -e VARIANTS=find-limit scripts/apiAuto.js
k6 run -e BASE_URL=http://172.30.1.78:8080 -e CONTROLLERS=UserController -e VARIANTS=find-limit scripts/apiAuto.js

k6 run -e BASE_URL=http://172.30.1.78:8080 -e CONTROLLERS=NodeController -e VARIANTS=light scripts/apiAuto.js

k6 run -e BASE_URL=http://trading-replay.duckdns.org:8080 -e CONTROLLERS=NodeController -e VARIANTS=light scripts/apiAuto.js
k6 run -e BASE_URL=http://172.30.1.78:8080 -e CONTROLLERS=NodeController -e ENDPOINTS=list -e VARIANTS=heavy scripts/apiAuto.js

### 웜업

k6 run -e BASE_URL=http://172.30.1.78:8080 -e CONTROLLERS=NodeController -e VARIANTS=warmup scripts/apiAuto.js
k6 run -e BASE_URL=http://172.30.1.78:8080 -e CONTROLLERS=StockController -e ENDPOINTS=range -e VARIANTS=warmup scripts/apiAuto.js
k6 run -e BASE_URL=http://172.30.1.78:8080 -e CONTROLLERS=GraphController -e VARIANTS=heavy scripts/apiAuto.js

k6 run -e BASE_URL=http://123.143.98.9:8080 -e CONTROLLERS=StockController -e ENDPOINTS=range -e VARIANTS=warmup scripts/apiAuto.js

### 웜업없이

k6 run -e BASE_URL=http://172.30.1.78:8080 -e CONTROLLERS=NodeController -e VARIANTS=light -e NO_WARMUP=1 scripts/apiAuto.js
k6 run -e BASE_URL=http://172.30.1.78:8080 -e CONTROLLERS=StockController -e ENDPOINTS=range -e VARIANTS=warmup -e NO_WARMUP=1 scripts/apiAuto.js

k6 run -e BASE_URL=http://172.30.1.78:8080 -e CONTROLLERS=NodeController -e VARIANTS=heavy -e NO_WARMUP=1 \scripts/apiAuto.js
k6 run -e BASE_URL=http://172.30.1.78:8080 -e CONTROLLERS=GraphController -e VARIANTS=heavy -e NO_WARMUP=1 \scripts/apiAuto.js

k6 run -e BASE_URL=http://172.30.1.78:8080 -e DROP_BODIES=0 -e CONTROLLERS=StockController -e ENDPOINTS=range -e VARIANTS=find-limit scripts/api.js
k6 run -e BASE_URL=http://localhost:8080 -e CONTROLLERS=StockController -e ENDPOINTS=range -e VARIANTS=find-limit scripts/api.js
k6 run -e BASE_URL=http://172.30.1.78:8080 -e CONTROLLERS=StockController -e ENDPOINTS=range -e VARIANTS=find-limit scripts/api.js
k6 run -e BASE_URL=http://172.30.1.78:8080 -e CONTROLLERS=GraphController -e VARIANTS=find-limit scripts/api.js

k6 run -e BASE_URL=http://172.30.1.78:8080 -e CONTROLLERS=StockController -e ENDPOINTS=range -e VARIANTS=find-limit -e ENDPOINTS_PARAMS='{"start":["2024-09-01T00:00:00Z"],"end":["2024-10-01T00:00:00Z"],"stock":["TSLA"]}' scripts/api.js

본부하시에 시드 고정해서 비교
k6 run -e BASE_URL=http://172.30.1.78:8080 -e CONTROLLERS=StockController -e ENDPOINTS=range -e VARIANTS=heavy -e MAIN_SEED=777 scripts/apiAuto.js

k6 run -e BASE_URL=http://172.30.1.78:8080 -e CONTROLLERS=EdgeController -e DEBUG=1 -e VARIANTS=heavy -e MAIN_SEED=777 scripts/apiAuto.js
k6 run -e BASE_URL=http://172.30.1.78:8080 -e CONTROLLERS=NodeController -e VARIANTS=heavy -e MAIN_SEED=777 scripts/apiAuto.js
k6 run -e BASE_URL=http://172.30.1.78:8080 -e CONTROLLERS=ContentController -e VARIANTS=heavy -e MAIN_SEED=777 scripts/apiAuto.js
k6 run -e BASE_URL=http://172.30.1.78:8080 -e CONTROLLERS=DirectoryController -e VARIANTS=heavy -e MAIN_SEED=777 scripts/apiAuto.js
k6 run -e BASE_URL=http://172.30.1.78:8080 -e CONTROLLERS=TickerController -e VARIANTS=heavy -e MAIN_SEED=777 scripts/apiAuto.js

k6 run -e BASE_URL=http://172.30.1.78:8080 -e CONTROLLERS=NodeController -e ENDPOINTS=list -e VARIANTS=heavy -e MAIN_SEED=777 scripts/apiAuto.js

k6 run -e BASE_URL=http://172.30.1.78:8080 -e CONTROLLERS=NodeController -e ENDPOINTS=single -e VARIANTS=heavy -e MAIN_SEED=777 scripts/apiAuto.js


k6 run -e BASE_URL=http://192.168.60.49:8080 -e CONTROLLERS=StockController -e ENDPOINTS=range -e VARIANTS=heavy -e MAIN_SEED=777 scripts/apiAuto.js

k6 run -e BASE_URL=http://192.168.8.49:8080 -e CONTROLLERS=NodeController -e ENDPOINTS=list -e VARIANTS=heavy -e MAIN_SEED=777 scripts/apiAuto.js

k6 run -e BASE_URL=http://172.30.1.78:8080 -e CONTROLLERS=NodeController -e ENDPOINTS=list -e VARIANTS=heavy -e MAIN_SEED=777 scripts/apiAuto.js


k6 run -e BASE_URL=http://3.36.125.180:8080 -e CONTROLLERS=NodeController -e ENDPOINTS=list -e VARIANTS=heavy -e MAIN_SEED=777 scripts/apiAuto.js

k6 run -e BASE_URL=http://3.34.145.209:8080 -e CONTROLLERS=StockController -e ENDPOINTS=range -e VARIANTS=heavy -e MAIN_SEED=777 scripts/apiAuto.js

k6 run -e BASE_URL=http://192.168.8.49:8080 -e CONTROLLERS=StockController -e ENDPOINTS=range -e VARIANTS=heavy -e MAIN_SEED=777 scripts/apiAuto.js

k6 run -e BASE_URL=http://43.201.69.40:8080 -e CONTROLLERS=NodeController -e ENDPOINTS=list -e VARIANTS=heavy -e MAIN_SEED=777 scripts/apiAuto.js

k6 run -e BASE_URL=http://43.201.69.40:8080 -e CONTROLLERS=StockController -e ENDPOINTS=range -e VARIANTS=heavy -e MAIN_SEED=777 scripts/apiAuto.js

k6 run -e BASE_URL=http://43.201.6.155:8080 -e CONTROLLERS=NodeController -e ENDPOINTS=list -e VARIANTS=heavy -e MAIN_SEED=777 scripts/apiAuto.js

k6 run -e BASE_URL=http://43.201.6.155:8080 -e CONTROLLERS=StockController -e ENDPOINTS=range -e VARIANTS=heavy -e MAIN_SEED=777 scripts/apiAuto.js

