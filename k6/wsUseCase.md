RAW BASELINE 연결만
k6 run -e BASE_URL=http://localhost:8080 -e MODE=baseline -e HOLD_MS=30000 -e TEAM_ID=1 -e GRAPH_ID=1 -e SUMMARY=outputs/raw_baseline.json --vus 200 --iterations 200 scripts/ws_raw_cursor.js

k6 run -e BASE_URL=http://localhost:8080 -e MODE=baseline -e VUS=200 -e TEST_DURATION_S=60 -e TEAM_ID=1 -e GRAPH_ID=1 -e HOLD_MS=30000 -e SUMMARY=outputs/raw_baseline.json scripts/ws_raw_cursor.js

k6 run \
  -e BASE_URL=http://localhost:8080 \
  -e MODE=baseline \
  -e VUS=200 -e TEST_DURATION_S=60 \
  -e TEAM_ID=1 -e GRAPH_ID=1 \
  -e SUMMARY=outputs/raw_baseline.json \
  scripts/ws_raw_cursor.js

  STOMP BASELINE (연결+subscribe만)
k6 run -e BASE_URL=http://localhost:8080 -e MODE=baseline -e HOLD_MS=30000 -e TEAM_ID=1 -e GRAPH_ID=1 -e SUMMARY=outputs/stomp_baseline.json --vus 200 --iterations 200 scripts/ws_stomp_cursor.js


k6 run -e BASE_URL=http://localhost:8080 -e MODE=baseline -e VUS=200 -e TEST_DURATION_S=60 -e TEAM_ID=1 -e GRAPH_ID=1 -e HOLD_MS=30000 -e SUMMARY=outputs/stomp_baseline.json scripts/ws_stomp_cursor.js 
k6 run \
  -e BASE_URL=http://localhost:8080 \
  -e MODE=baseline \
  -e VUS=200 -e TEST_DURATION_S=60 \
  -e TEAM_ID=1 -e GRAPH_ID=1 \
  -e SUMMARY=outputs/raw_baseline.json \
  scripts/ws_raw_cursor.js


RAW CURSOR (sender 10%, 20Hz, 30초)
k6 run -e BASE_URL=http://localhost:8080 -e USERS_CSV=../data/users_200.csv -e MODE=cursor -e VUS=200 -e TEST_DURATION_S=60 -e HOLD_MS=60000 -e TEAM_ID=1 -e GRAPH_ID=1 -e SENDER_RATIO=0.1 -e RATE=20 -e DURATION_S=30 -e PAD=200 -e SUMMARY=outputs/raw_cursor.json scripts/ws_raw_cursor.js

k6 run `
  -e BASE_URL=http://localhost:8080 `
  -e USERS_CSV=../data/users_200.csv `
  -e MODE=cursor `
  -e VUS=200 -e TEST_DURATION_S=60 -e HOLD_MS=60000 `
  -e TEAM_ID=1 -e GRAPH_ID=1 `
  -e SENDER_RATIO=0.1 -e RATE=20 -e DURATION_S=30 `
  -e PAD=200 `
  -e LAT_OK_MS=200 -e LAT_WARN_MS=1000 `
  -e RT_OK_200_MIN=0.90 -e RT_OK_1S_MIN=0.99 `
  -e RT_OK_200_SEND_MIN=0.90 -e RT_OK_200_AFTER_MIN=0.90 `
  -e SUMMARY=outputs/raw_cursor.json `
  scripts/ws_raw_cursor.js


개발서버

k6 run -e BASE_URL=http://123.143.98.5:8080 -e USERS_CSV=../data/users_200_server.csv -e MODE=cursor -e VUS=200 -e TEST_DURATION_S=60 -e HOLD_MS=60000 -e TEAM_ID=1 -e GRAPH_ID=1 -e SENDER_RATIO=0.1 -e RATE=20 -e DURATION_S=30 -e PAD=200 -e SUMMARY=outputs/raw_cursor.json scripts/ws_raw_cursor.js


k6 run `
  -e BASE_URL=http://123.143.98.5:8080 `
  -e USERS_CSV=../data/users_200_server.csv `
  -e MODE=cursor `
  -e VUS=200 -e TEST_DURATION_S=60 -e HOLD_MS=60000 `
  -e TEAM_ID=1 -e GRAPH_ID=1 `
  -e SENDER_RATIO=0.1 -e RATE=20 -e DURATION_S=30 `
  -e PAD=200 `
  -e LAT_OK_MS=200 -e LAT_WARN_MS=1000 `
  -e RT_OK_200_MIN=0.90 -e RT_OK_1S_MIN=0.99 `
  -e RT_OK_200_SEND_MIN=0.90 -e RT_OK_200_AFTER_MIN=0.90 `
  -e SUMMARY=outputs/raw_cursor.json `
  scripts/ws_raw_cursor.js

서버 jfr
  k6 run `
  -e BASE_URL=http://123.143.98.5:8080 `
  -e USERS_CSV=../data/users_200_server.csv `
  -e MODE=cursor `
  -e VUS=200 -e TEST_DURATION_S=60 -e HOLD_MS=60000 `
  -e TEAM_ID=1 -e GRAPH_ID=1 `
  -e SENDER_RATIO=0.1 -e RATE=10 -e DURATION_S=30 `
  -e PAD=200 `
  -e LAT_OK_MS=200 -e LAT_WARN_MS=1000 `
  -e RT_OK_200_MIN=0.90 -e RT_OK_1S_MIN=0.99 `
  -e RT_OK_200_SEND_MIN=0.90 -e RT_OK_200_AFTER_MIN=0.90 `
  -e SUMMARY=outputs/raw_cursor.json `
  scripts/ws_raw_cursor.js
로컬 jfr
k6 run `
  -e BASE_URL=http://localhost:8080 `
  -e USERS_CSV=../data/users_200.csv `
  -e MODE=cursor `
  -e VUS=200 -e TEST_DURATION_S=60 -e HOLD_MS=60000 `
  -e TEAM_ID=1 -e GRAPH_ID=1 `
  -e SENDER_RATIO=0.1 -e RATE=10 -e DURATION_S=30 `
  -e PAD=200 `
  -e LAT_OK_MS=200 -e LAT_WARN_MS=1000 `
  -e RT_OK_200_MIN=0.90 -e RT_OK_1S_MIN=0.99 `
  -e RT_OK_200_SEND_MIN=0.90 -e RT_OK_200_AFTER_MIN=0.90 `
  -e SUMMARY=outputs/raw_cursor.json `
  scripts/ws_raw_cursor.js

k6 run `
  -e BASE_URL=http://localhost:8080 `
  -e USERS_CSV=../data/users_200.csv `
  -e MODE=cursor `
  -e VUS=200 -e TEST_DURATION_S=60 -e HOLD_MS=60000 `
  -e TEAM_ID=1 -e GRAPH_ID=1 `
  -e SENDER_RATIO=0.1 -e RATE=20 -e DURATION_S=30 `
  -e PAD=200 `
  -e LAT_OK_MS=200 -e LAT_WARN_MS=1000 `
  -e RT_OK_200_MIN=0.90 -e RT_OK_1S_MIN=0.99 `
  -e RT_OK_200_SEND_MIN=0.90 -e RT_OK_200_AFTER_MIN=0.90 `
  -e SUMMARY=outputs/raw_cursor.json `
  scripts/ws_raw_batch.js


  k6 run `
  -e BASE_URL=http://localhost:8080 `
  -e USERS_CSV=../data/users_200.csv `
  -e MODE=cursor `
  -e VUS=200 -e TEST_DURATION_S=60 -e HOLD_MS=60000 `
  -e TEAM_ID=1 -e GRAPH_ID=1 `
  -e SENDER_RATIO=0.2 -e RATE=20 -e DURATION_S=30 `
  -e PAD=200 `
  -e LAT_OK_MS=200 -e LAT_WARN_MS=1000 `
  -e RT_OK_200_MIN=0.90 -e RT_OK_1S_MIN=0.99 `
  -e RT_OK_200_SEND_MIN=0.90 -e RT_OK_200_AFTER_MIN=0.90 `
  -e SUMMARY=outputs/raw_cursor.json `
  scripts/ws_raw_batch.js

STOMP CURSOR (동일 조건)
k6 run -e BASE_URL=http://localhost:8080 -e USERS_CSV=../data/users_200.csv -e MODE=cursor -e VUS=200 -e TEST_DURATION_S=60 -e HOLD_MS=60000 -e TEAM_ID=1 -e GRAPH_ID=1 -e SENDER_RATIO=0.1 -e RATE=20 -e DURATION_S=30 -e PAD=200 -e SUMMARY=outputs/stomp_cursor.json scripts/ws_stomp_cursor.js

개발서버

k6 run -e BASE_URL=http://123.143.98.5:8080 -e USERS_CSV=../data/users_200_server.csv -e MODE=cursor -e VUS=200 -e TEST_DURATION_S=180 -e HOLD_MS=180000 -e TEAM_ID=1 -e GRAPH_ID=1 -e SENDER_RATIO=0.1 -e RATE=20 -e DURATION_S=30 -e PAD=200 -e SUMMARY=outputs/stomp_cursor.json scripts/ws_stomp_cursor.js

k6 run -e BASE_URL=http://localhost:8080 -e USERS_CSV=../data/users_200_server.csv -e MODE=cursor -e VUS=200 -e TEST_DURATION_S=180 -e HOLD_MS=180000 -e TEAM_ID=1 -e GRAPH_ID=1 -e SENDER_RATIO=0.1 -e RATE=20 -e DURATION_S=30 -e PAD=200 -e SUMMARY=outputs/stomp_cursor.json scripts/ws_stomp_cursor.js

k6 run -e BASE_URL=http://localhost:8080 -e USERS_CSV=../data/users_200_server.csv -e MODE=cursor -e VUS=200 -e TEST_DURATION_S=60 -e HOLD_MS=60000 -e TEAM_ID=1 -e GRAPH_ID=1 -e SENDER_RATIO=0.1 -e RATE=20 -e DURATION_S=30 -e PAD=200 -e SUMMARY=outputs/stomp_cursor.json scripts/ws_stomp_cursor.js

k6 run -e BASE_URL=http://localhost:8080 -e USERS_CSV=../data/users_200_server.csv -e MODE=cursor -e VUS=200 -e TEST_DURATION_S=60 -e HOLD_MS=60000 -e TEAM_ID=1 -e GRAPH_ID=1 -e SENDER_RATIO=0.1 -e RATE=20 -e DURATION_S=30 -e PAD=200 -e SUMMARY=outputs/raw_cursor.json scripts/ws_raw_cursor.js


k6 run `
  -e BASE_URL=http://localhost:8080 `
  -e USERS_CSV=../data/users_200.csv `
  -e MODE=cursor `
  -e VUS=200 -e TEST_DURATION_S=60 -e HOLD_MS=60000 `
  -e TEAM_ID=1 -e GRAPH_ID=1 `
  -e SENDER_RATIO=0.1 -e RATE=20 -e DURATION_S=30 `
  -e PAD=200 `
  -e LAT_OK_MS=200 -e LAT_WARN_MS=1000 `
  -e RT_OK_200_MIN=0.90 -e RT_OK_1S_MIN=0.99 `
  -e RT_OK_200_SEND_MIN=0.90 -e RT_OK_200_AFTER_MIN=0.90 `
  -e SUMMARY=outputs/stomp_cursor.json `
  scripts/ws_stomp_cursor.js



로컬jfr stomp
  k6 run `
  -e BASE_URL=http://localhost:8080 `
  -e USERS_CSV=../data/users_200.csv `
  -e MODE=cursor `
  -e VUS=200 -e TEST_DURATION_S=60 -e HOLD_MS=60000 `
  -e TEAM_ID=1 -e GRAPH_ID=1 `
  -e SENDER_RATIO=0.1 -e RATE=10 -e DURATION_S=30 `
  -e PAD=200 `
  -e LAT_OK_MS=200 -e LAT_WARN_MS=1000 `
  -e RT_OK_200_MIN=0.90 -e RT_OK_1S_MIN=0.99 `
  -e RT_OK_200_SEND_MIN=0.90 -e RT_OK_200_AFTER_MIN=0.90 `
  -e SUMMARY=outputs/stomp_cursor.json `
  scripts/ws_stomp_cursor.js

    k6 run `
  -e BASE_URL=http://localhost:8080 `
  -e USERS_CSV=../data/users_200.csv `
  -e MODE=cursor `
  -e VUS=200 -e TEST_DURATION_S=60 -e HOLD_MS=60000 `
  -e TEAM_ID=1 -e GRAPH_ID=1 `
  -e SENDER_RATIO=0.2 -e RATE=20 -e DURATION_S=30 `
  -e PAD=200 `
  -e LAT_OK_MS=200 -e LAT_WARN_MS=1000 `
  -e RT_OK_200_MIN=0.90 -e RT_OK_1S_MIN=0.99 `
  -e RT_OK_200_SEND_MIN=0.90 -e RT_OK_200_AFTER_MIN=0.90 `
  -e SUMMARY=outputs/stomp_cursor.json `
  scripts/ws_stomp_batch.js

개발서버-받은 시간에 따라 처리
k6 run `
  -e BASE_URL=http://123.143.98.5:8080 `
  -e USERS_CSV=../data/users_200_server.csv `
  -e MODE=cursor `
  -e VUS=200 -e TEST_DURATION_S=60 -e HOLD_MS=60000 `
  -e TEAM_ID=1 -e GRAPH_ID=1 `
  -e SENDER_RATIO=0.1 -e RATE=20 -e DURATION_S=30 `
  -e PAD=200 `
  -e LAT_OK_MS=200 -e LAT_WARN_MS=1000 `
  -e RT_OK_200_MIN=0.90 -e RT_OK_1S_MIN=0.99 `
  -e RT_OK_200_SEND_MIN=0.90 -e RT_OK_200_AFTER_MIN=0.90 `
  -e SUMMARY=outputs/stomp_cursor.json `
  scripts/ws_stomp_cursor.js

로그출력까지
k6 run -e BASE_URL=http://localhost:8080 -e MODE=cursor -e VUS=200 -e TEST_DURATION_S=60 -e TEAM_ID=1 -e GRAPH_ID=1 -e SENDER_RATIO=0.1 -e RATE=20 -e DURATION_S=30 -e DUMP_RECV=1 -e DUMP_LIMIT=20 -e DUMP_SAMPLE=1 -e DUMP_RAW=1 -e PAD=200 -e SUMMARY=outputs/stomp_cursor.json scripts/ws_stomp_cursor.js

  k6 run \
  -e BASE_URL=http://localhost:8080 \
  -e MODE=cursor \
  -e VUS=200 -e TEST_DURATION_S=60 \
  -e TEAM_ID=1 -e GRAPH_ID=1 \
  -e SENDER_RATIO=0.1 -e RATE=20 -e DURATION_S=30 \
  -e PAD=200 \
  -e SUMMARY=outputs/stomp_cursor.json \
  scripts/ws_stomp_cursor.js



비교항목

ΔCPU = CPU(cursor) - CPU(baseline)

ΔAlloc = Alloc(cursor) - Alloc(baseline)

ΔGC = GC(cursor) - GC(baseline)


- raw 멀티룸

- 룸 다섯개 고정분산, 개중 센더 각 방마다 20프로
k6 run `
  -e VUS=200 -e ROOMS=5 `
  -e BASE_URL=http://localhost:8080 `
  -e USERS_CSV=../data/users_200.csv `
  -e RATE=20 -e DURATION_S=30 `
  -e SENDER_RATIO=0.2 `
  -e PAD=200 `
-e TEAM_ID=1 `
-e GRAPH_ID_BASE=1 `
-e TEST_DURATION_S=60 -e HOLD_MS=60000 `
-e LAT_OK_MS=200 -e LAT_WARN_MS=1000 `
-e RT_OK_200_MIN=0.90 -e RT_OK_1S_MIN=0.99 `
-e RT_OK_200_SEND_MIN=0.90 -e RT_OK_200_AFTER_MIN=0.90`
  scripts/ws_multiroom_raw_batch.js

- 룸 1갶, 개중 센더 각 방마다 20프로

k6 run `
  -e VUS=200 `
  -e ROOMS=1 `
  -e ROOM_METRICS=0 `
  -e BASE_URL=http://localhost:8080 `
  -e USERS_CSV=../data/users_200.csv `
  -e RATE=20 `
  -e DURATION_S=30 `
  -e SENDER_RATIO=0.2 `
  -e PAD=200 `
  -e TEAM_ID=1 `
  -e GRAPH_ID_BASE=1 `
  -e TEST_DURATION_S=60 `
  -e HOLD_MS=60000 `
  -e LAT_OK_MS=200 `
  -e LAT_WARN_MS=1000 `
  -e SUMMARY=outputs/raw_multi_1room_light.json `
  scripts/ws_multiroom_raw_batch.js

  k6 run `
  -e VUS=200 `
  -e ROOMS=1 `
  -e ROOM_METRICS=0 `
  -e BASE_URL=http://localhost:8080 `
  -e USERS_CSV=../data/users_200.csv `
  -e RATE=20 `
  -e DURATION_S=30 `
  -e SENDER_RATIO=0.2 `
  -e PAD=200 `
  -e TEAM_ID=1 `
  -e GRAPH_ID_BASE=1 `
  -e TEST_DURATION_S=60 `
  -e HOLD_MS=60000 `
  -e LAT_OK_MS=200 `
  -e LAT_WARN_MS=1000 `
  -e SUMMARY=outputs/raw_multi_1room_light.json `
  scripts/ws_multiroom_raw_batch.js


k6 run `
  -e VUS=400 `
  -e ROOMS=5 `
  -e ROOM_METRICS=0 `
  -e BASE_URL=http://localhost:8080 `
  -e USERS_CSV=../data/users_200.csv `
  -e RATE=20 `
  -e DURATION_S=30 `
  -e SENDER_RATIO=0.2 `
  -e PAD=200 `
  -e TEAM_ID=1 `
  -e GRAPH_ID_BASE=1 `
  -e TEST_DURATION_S=60 `
  -e HOLD_MS=60000 `
  -e LAT_OK_MS=200 `
  -e LAT_WARN_MS=1000 `
  -e SUMMARY=outputs/raw_multi_1room_light.json `
  scripts/ws_multiroom_raw_batch.js

  k6 run `
  -e VUS=200 `
  -e ROOMS=5 `
  -e ROOM_METRICS=0 `
  -e BASE_URL=http://localhost:8080 `
  -e USERS_CSV=../data/users_200.csv `
  -e RATE=20 `
  -e DURATION_S=30 `
  -e SENDER_RATIO=0.2 `
  -e PAD=200 `
  -e TEAM_ID=1 `
  -e GRAPH_ID_BASE=1 `
  -e TEST_DURATION_S=60 `
  -e HOLD_MS=60000 `
  -e LAT_OK_MS=200 `
  -e LAT_WARN_MS=1000 `
  -e SUMMARY=outputs/raw_multi_5room_diag.json `
  scripts/ws_multiroom_raw_batch.js



  k6 run `
  -e BASE_URL=http://123.143.98.7:8080 `
  -e USERS_CSV=../data/users_200_server.csv `
  -e MODE=cursor `
  -e VUS=200 -e TEST_DURATION_S=60 -e HOLD_MS=60000 `
  -e TEAM_ID=1 -e GRAPH_ID=1 `
  -e SENDER_RATIO=0.1 -e RATE=20 -e DURATION_S=30 `
  -e PAD=200 `
  -e LAT_OK_MS=200 -e LAT_WARN_MS=1000 `
  -e RT_OK_200_MIN=0.90 -e RT_OK_1S_MIN=0.99 `
  -e RT_OK_200_SEND_MIN=0.90 -e RT_OK_200_AFTER_MIN=0.90 `
  -e SUMMARY=outputs/raw_cursor.json `
  scripts/ws_raw_batch.js

  k6 run `
  -e BASE_URL=http://123.143.98.7:8080 `
  -e USERS_CSV=../data/users_200_server.csv `
  -e MODE=cursor `
  -e VUS=100 -e TEST_DURATION_S=60 -e HOLD_MS=60000 `
  -e TEAM_ID=1 -e GRAPH_ID=1 `
  -e SENDER_RATIO=0.1 -e RATE=20 -e DURATION_S=30 `
  -e PAD=200 `
  -e LAT_OK_MS=200 -e LAT_WARN_MS=1000 `
  -e RT_OK_200_MIN=0.90 -e RT_OK_1S_MIN=0.99 `
  -e RT_OK_200_SEND_MIN=0.90 -e RT_OK_200_AFTER_MIN=0.90 `
  -e SUMMARY=outputs/raw_cursor.json `
  scripts/ws_raw_batch.js

  === RAW Summary (MODE=cursor) ===
duration: 64.04s
open: 100 / close: 100 / errors: 0
sent: 6000 / received(events): 547592 / received(frames): 59264
sent/s: 93.70 / recv_events/s: 8551.36 / recv_frames/s: 925.48
connect(ms) count=0 avg=113.1 p50=101.0 p95=190.3 p99=206.2
latency buckets(ms): <=200=539199 (98.47%) / <=1000=8362 (1.53%) / >1000=31 (0.01%)
phase samples: during=547526 / after=66
realtime rates: ok<=200=98.47% (min 90.00%) / ok<=1000=99.99% (min 99.00%)
realtime rates by phase: during-send ok<=200=98.47% (min 90.00%), after-send ok<=200=100.00% (min 90.00%)