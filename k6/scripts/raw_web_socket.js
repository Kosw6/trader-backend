import ws from 'k6/ws';
import { check, sleep } from 'k6';
import { Trend, Counter } from 'k6/metrics';

const e2eMs = new Trend('ws_e2e_ms');
const sent = new Counter('ws_sent');
const recv = new Counter('ws_recv');

export const options = {
  scenarios: {
    raw_cursor: {
      executor: 'constant-vus',
      vus: 20,            // 동시 접속 클라 수
      duration: '30s',
    },
  },
};

function nowMs() { return Date.now(); }
function rand01() { return Math.random(); }

export default function () {
  const teamId = 1;
  const graphId = 1;
  const url = `ws://localhost:8080/ws/canvas-raw?teamId=${teamId}&graphId=${graphId}`;

  // 60Hz(16ms)로 보내고 싶으면 0.016, 30Hz면 0.033
  const periodSec = 1 / 60;

  const res = ws.connect(url, {}, function (socket) {
    socket.on('open', () => {
      // 연결 성공 확인
      // (raw는 subscribe 개념이 없을 수 있으니 서버 구현에 따라 다름)
    });

    socket.on('message', (data) => {
      recv.add(1);

      // 서버가 echo/broadcast 해주는 구조라면 sentAt으로 e2e 측정 가능
      try {
        const msg = JSON.parse(data);
        if (msg.sentAt) {
          e2eMs.add(nowMs() - msg.sentAt);
        }
      } catch (_) {}
    });

    socket.on('close', () => {});
    socket.on('error', (e) => {});

    // 일정 시간 동안 주기적으로 send
    const endAt = nowMs() + 25_000; // 25초만 송신하고 종료
    while (nowMs() < endAt) {
      const payload = JSON.stringify({
        type: 'cursor',
        x: rand01() * 1920,
        y: rand01() * 1080,
        sentAt: nowMs(),
      });

      socket.send(payload);
      sent.add(1);
      sleep(periodSec);
    }

    socket.close();
  });

  check(res, { 'raw ws connected': (r) => r && r.status === 101 });
}