import ws from 'k6/ws';
import { check, sleep } from 'k6';
import { Trend, Counter } from 'k6/metrics';

const e2eMs = new Trend('stomp_e2e_ms');
const sent = new Counter('stomp_sent');
const recv = new Counter('stomp_recv');

export const options = {
  scenarios: {
    stomp_cursor: {
      executor: 'constant-vus',
      vus: 20,
      duration: '30s',
    },
  },
};

function nowMs() { return Date.now(); }
function rand01() { return Math.random(); }

function stompFrame(command, headers = {}, body = '') {
  let out = command + '\n';
  for (const [k, v] of Object.entries(headers)) out += `${k}:${v}\n`;
  out += '\n';
  out += body;
  out += '\x00'; // STOMP frame terminator
  return out;
}

export default function () {
  const teamId = 1;
  const graphId = 1;

  // STOMP endpoint = ws://localhost:8080/ws/canvas
  const url = `ws://localhost:8080/ws/canvas`;

  const sendDest = `/app/teams/${teamId}/graphs/${graphId}/presence/cursor`;
  const subDest  = `/topic/teams/${teamId}/graphs/${graphId}/presence`;

  const periodSec = 1 / 60;

  const res = ws.connect(url, {}, function (socket) {
    let connected = false;

    socket.on('open', () => {
      // CONNECT
      socket.send(stompFrame('CONNECT', {
        'accept-version': '1.2',
        'heart-beat': '0,0',
        // Authorization을 여기 넣고 싶으면 가능하지만,
        // k6는 브라우저 제약이 없어서 헤더/프레임 둘 다 가능.
        // 'Authorization': 'Bearer ...'
      }));
    });

    socket.on('message', (data) => {
      // data 안에 CONNECTED / MESSAGE / ERROR 프레임이 섞여 옴
      recv.add(1);

      if (data.startsWith('CONNECTED')) {
        connected = true;

        // SUBSCRIBE (id는 고유하면 됨)
        socket.send(stompFrame('SUBSCRIBE', {
          id: 'sub-0',
          destination: subDest,
        }));
        return;
      }

      // MESSAGE 프레임에서 body 파싱 (아주 단순 파서)
      if (data.startsWith('MESSAGE')) {
        const idx = data.indexOf('\n\n');
        if (idx !== -1) {
          const bodyWithNull = data.substring(idx + 2);
          const body = bodyWithNull.replace('\x00', '');
          try {
            const msg = JSON.parse(body);
            if (msg.sentAt) e2eMs.add(nowMs() - msg.sentAt);
          } catch (_) {}
        }
      }
    });

    socket.on('error', () => {});
    socket.on('close', () => {});

    // CONNECTED 될 때까지 잠깐 대기 (바쁜 루프 방지)
    const waitUntil = nowMs() + 3000;
    while (!connected && nowMs() < waitUntil) sleep(0.05);

    if (!connected) {
      socket.close();
      return;
    }

    // SEND 반복
    const endAt = nowMs() + 25_000;
    while (nowMs() < endAt) {
      const body = JSON.stringify({
        type: 'cursor',
        x: rand01() * 1920,
        y: rand01() * 1080,
        sentAt: nowMs(),
      });

      socket.send(stompFrame('SEND', {
        destination: sendDest,
        'content-type': 'application/json',
      }, body));

      sent.add(1);
      sleep(periodSec);
    }

    // DISCONNECT (선택)
    socket.send(stompFrame('DISCONNECT', {}));
    socket.close();
  });

  check(res, { 'stomp ws connected': (r) => r && r.status === 101 });
}