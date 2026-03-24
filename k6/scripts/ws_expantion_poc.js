import http from 'k6/http';
import ws from 'k6/ws';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';

export const options = {
  scenarios: {
    ws_rooms_100_users: {
      executor: 'per-vu-iterations',
      vus: 100,
      iterations: 100,
      maxDuration: '1m30s',
    },
  },
};

const routeOk = new Counter('ws_route_ok');
const routeFail = new Counter('ws_route_fail');
const connectOk = new Counter('ws_connect_ok');
const connectFail = new Counter('ws_connect_fail');
const sentMessages = new Counter('ws_sent_messages');
const recvMessages = new Counter('ws_recv_messages');

const ROUTE_BASE_URL = 'http://localhost:8080';
const GRAPH_ID = 1;

const ROOM_SIZE = 50;
const SENDERS_PER_ROOM = Math.floor(ROOM_SIZE * 0.2); // 👉 10명

const CONNECT_SETTLE_SECONDS = 3;
const SEND_DURATION_SECONDS = 30;
const POST_WAIT_SECONDS = 10;
const SEND_INTERVAL_MS = 100;

// 🔥 핵심: 방 1 / 방 32
const GROUP1 = 1;
const GROUP2 = 32;

function assignUser(vu) {
  const index = vu - 1;

  // 방 1
  if (index < ROOM_SIZE) {
    const localIndex = index;
    return {
      groupId: GROUP1,
      teamId: GROUP1,
      graphId: GRAPH_ID,
      userId: 1001 + localIndex,
      nickName: `g1-u${1001 + localIndex}`,
      isSender: localIndex < SENDERS_PER_ROOM,
    };
  }

  // 방 32
  const localIndex = index - ROOM_SIZE;
  return {
    groupId: GROUP2,
    teamId: GROUP2,
    graphId: GRAPH_ID,
    userId: 3201 + localIndex,
    nickName: `g32-u${3201 + localIndex}`,
    isSender: localIndex < SENDERS_PER_ROOM,
  };
}

function buildRouteUrl(user) {
  return `${ROUTE_BASE_URL}/internal/ws-route?groupId=${user.groupId}` +
    `&teamId=${user.teamId}` +
    `&graphId=${user.graphId}` +
    `&userId=${user.userId}` +
    `&nickName=${encodeURIComponent(user.nickName)}`;
}

function fetchWsUrl(user) {
  const res = http.get(buildRouteUrl(user));

  const ok = check(res, {
    'route status 200': (r) => r.status === 200,
  });

  if (!ok) {
    routeFail.add(1);
    console.error(`[ROUTE_FAIL] vu=${__VU} body=${res.body}`);
    return null;
  }

  routeOk.add(1);
  return JSON.parse(res.body).wsUrl;
}

function makeCursorMessage(user, seq) {
  return JSON.stringify({
    type: 'CURSOR',
    teamId: user.teamId,
    graphId: user.graphId,
    userId: user.userId,
    nickName: user.nickName,
    nodeId: null,
    x: 100 + (seq % 200),
    y: 200 + ((seq * 3) % 200),
    sentAt: Date.now(),
  });
}

export default function () {
  const user = assignUser(__VU);
  const wsUrl = fetchWsUrl(user);

  if (!wsUrl) {
    sleep(1);
    return;
  }

  const totalHoldMs =
    (CONNECT_SETTLE_SECONDS + SEND_DURATION_SECONDS + POST_WAIT_SECONDS) * 1000;

  const res = ws.connect(wsUrl, {}, function (socket) {
    let seq = 0;
    let senderInterval = null;

    socket.on('open', () => {
      connectOk.add(1);

      socket.setTimeout(() => {
        if (user.isSender) {
          senderInterval = socket.setInterval(() => {
            seq++;
            socket.send(makeCursorMessage(user, seq));
            sentMessages.add(1);
          }, SEND_INTERVAL_MS);
        }
      }, CONNECT_SETTLE_SECONDS * 1000);

      socket.setTimeout(() => {
        if (senderInterval) {
          socket.clearInterval(senderInterval);
        }
      }, (CONNECT_SETTLE_SECONDS + SEND_DURATION_SECONDS) * 1000);

      socket.setTimeout(() => {
        socket.close();
      }, totalHoldMs);
    });

    socket.on('message', () => {
      recvMessages.add(1);
    });
  });

  const ok = check(res, {
    'ws status 101': (r) => r && r.status === 101,
  });

  if (!ok) {
    connectFail.add(1);
    console.error(`[CONNECT_FAIL] vu=${__VU} wsUrl=${wsUrl}`);
  }

  sleep(1);
}