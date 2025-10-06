### 조합 × 변형 전부 동시 실행

k6 run -e BASE_URL=http://localhost:8080 -e CONTROLLERS=StockController -e DURATION=30s scripts/api.js

### 특정 endpoint만, variants 포함해서

k6 run -e BASE_URL=http://localhost:8080 -e ENDPOINTS=range scripts/api.js

### constant-arrival-rate사용

k6 run -e BASE_URL=http://localhost:8080 -e CONTROLLERS=PageController -e ENDPOINTS=list -e EXECUTOR=constant-arrival-rate -e RATE=100 -e DURATION=30s -e VUS=50 -e MAX_VUS=200 scripts/api.js
