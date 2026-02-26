
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

=== RAW Summary (MODE=cursor) ===
duration: 61.93s
open: 200 / close: 200 / errors: 0
sent: 12000 / received: 9004
sent/s: 193.78 / recv/s: 145.40
connect(ms) count=0 avg=211.8 p50=215.0 p95=373.6 p99=491.0
latency buckets(ms): <=200=2507 (27.84%) / <=1000=6497 (72.16%) / >1000=0 (0.00%)
realtime rates: ok<=200=27.84% (min 90.00%) / ok<=1000=100.00% (min 99.00%)
realtime rates by phase: during-send ok<=200=27.84% (min 90.00%), after-send ok<=200=0.00% (min 90.00%)


=== RAW Summary (MODE=cursor) ===
duration: 64.45s
open: 200 / close: 200 / errors: 0
sent: 12000 / received: 5134
sent/s: 186.19 / recv/s: 79.66
connect(ms) count=0 avg=221.5 p50=230.0 p95=304.0 p99=311.0
latency buckets(ms): <=200=13 (0.25%) / <=1000=5121 (99.75%) / >1000=0 (0.00%)
realtime rates: ok<=200=0.25% (min 90.00%) / ok<=1000=100.00% (min 99.00%)
realtime rates by phase: during-send ok<=200=0.25% (min 90.00%), after-send ok<=200=0.00% (min 90.00%)

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

  
=== STOMP Summary (MODE=cursor) ===
duration: 62.53s
open: 200 / close: 200 / errors: 0
sent: 11970 / received: 1948286
sent/s: 191.42 / recv/s: 31156.87
CONNECTED frames: 200
ws-open(ms) count=0 avg=163.1 p50=158.0 p95=296.1 p99=425.1
ready(ms)   count=0 avg=330.4 p50=344.5 p95=539.6 p99=629.3
latency buckets(ms): <=200=168 (0.01%) / <=1000=8749 (0.45%) / >1000=1939369 (99.54%)
realtime rates: ok<=200=0.01% (min 90.00%) / ok<=1000=0.46% (min 99.00%)
realtime rates by phase: during-send ok<=200=0.02% (min 90.00%), after-send ok<=200=0.00% (min 90.00%)

=== STOMP Summary (MODE=cursor) ===
duration: 62.66s
open: 200 / close: 200 / errors: 0
sent: 11991 / received: 1799164
sent/s: 191.37 / recv/s: 28713.92
CONNECTED frames: 200
ws-open(ms) count=0 avg=137.1 p50=118.0 p95=253.3 p99=385.1
ready(ms)   count=0 avg=195.0 p50=188.0 p95=326.3 p99=448.1
latency buckets(ms): <=200=5454 (0.30%) / <=1000=15973 (0.89%) / >1000=1777737 (98.81%)
realtime rates: ok<=200=0.30% (min 90.00%) / ok<=1000=1.19% (min 99.00%)
realtime rates by phase: during-send ok<=200=0.63% (min 90.00%), after-send ok<=200=0.00% (min 90.00%)



=== RAW Summary (MODE=cursor) ===
duration: 64.61s
open: 200 / close: 200 / errors: 0
sent: 11999 / received: 2399800
sent/s: 185.70 / recv/s: 37140.33
connect(ms) count=0 avg=334.5 p50=326.0 p95=430.0 p99=447.0
latency buckets(ms): <=200=75 (0.00%) / <=1000=9230 (0.38%) / >1000=2390495 (99.61%)
realtime rates: ok<=200=0.00% (min 90.00%) / ok<=1000=0.39% (min 99.00%)
realtime rates by phase: during-send ok<=200=0.00% (min 90.00%), after-send ok<=200=0.00% (min 90.00%)


=== RAW Summary (MODE=cursor) ===
duration: 62.90s
open: 200 / close: 200 / errors: 0
sent: 12000 / received: 2396399
sent/s: 190.79 / recv/s: 38100.29
connect(ms) count=0 avg=251.2 p50=258.5 p95=507.7 p99=670.3
latency buckets(ms): <=200=8999 (0.38%) / <=1000=41563 (1.73%) / >1000=2345837 (97.89%)
realtime rates: ok<=200=0.38% (min 90.00%) / ok<=1000=2.11% (min 99.00%)
realtime rates by phase: during-send ok<=200=0.55% (min 90.00%), after-send ok<=200=0.00% (min 90.00%)