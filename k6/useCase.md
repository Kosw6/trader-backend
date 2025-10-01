### 조합 × 변형 전부 동시 실행

k6 run `  -e BASE_URL=http://localhost:8080`
-e CONTROLLERS=StockController `  -e DURATION=30s`
scripts/api.js

### 특정 endpoint만, variants 포함해서

k6 run -e BASE_URL=http://localhost:8080 \
 -e ENDPOINTS=range \
 scripts/api.js
