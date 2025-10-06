import http from "k6/http";
import { check, sleep } from "k6";
import { SharedArray } from "k6/data";

/** ===== 실행 파라미터 (ENV) ===== */
const BASE = __ENV.BASE_URL || "http://localhost:8080";
const VUS = Number(__ENV.VUS || 5);
const DURATION = __ENV.DURATION || "10s";
const EXECUTOR = (__ENV.EXECUTOR || "constant-vus").trim(); // "constant-vus" | "constant-arrival-rate"
const CONTROLLERS = (__ENV.CONTROLLERS || "")
  .split(",")
  .map((s) => s.trim())
  .filter(Boolean);
const ENDPOINTS = (__ENV.ENDPOINTS || "")
  .split(",")
  .map((s) => s.trim())
  .filter(Boolean);
const RATE = Number(__ENV.RATE || 20);
const MAX_VUS = Number(__ENV.MAX_VUS || VUS * 5);

/** ===== 엔드포인트 설정 로드 =====
 * 파일 위치: k6/data/endpoints.json
 * 실행 위치: k6/ 폴더에서 실행 (권장)
 */
const cfg = new SharedArray(
  "endpoints",
  () => JSON.parse(open("../data/endpoints.json")) // 스크립트가 k6/scripts/api.js에 있을 때 ../data가 맞음
);
export function setup() {
  const url = `${BASE}/api/login/signin`; // 로그인 엔드포인트
  const payload = JSON.stringify({
    loginId: __ENV.USER || "test1",
    password: __ENV.PASS || "test1",
  });
  const res = http.post(url, payload, {
    headers: { "Content-Type": "application/json" },
  });
  if (res.status !== 200) {
    throw new Error(`로그인 실패: ${res.status} ${res.body}`);
  }
  const token = JSON.parse(res.body).accessToken; // 응답 키 이름은 서버 응답에 맞춰 수정
  return {
    token,
    headers: { Authorization: `Bearer ${token}` },
  };
}

export function handleSummary(data) {
  // 민감정보 제거
  if (data.setup_data) data.setup_data = { redacted: true };

  // 안전한 값 추출 헬퍼
  const get = (path, fallback = undefined) =>
    path.split(".").reduce((o, k) => (o && k in o ? o[k] : undefined), data) ??
    fallback;

  // 다양한 포맷(예: --summary-export vs handleSummary 내부 표현) 대비한 다중 시도
  const p95 =
    get("metrics.http_req_duration.values.p(95)") ??
    get("metrics.http_req_duration.percentiles.95") ??
    get("metrics.http_req_duration.p(95)");

  const avg =
    get("metrics.http_req_duration.values.avg") ??
    get("metrics.http_req_duration.avg");

  const rps =
    get("metrics.http_reqs.values.rate") ?? get("metrics.http_reqs.rate");

  const failRate =
    get("metrics.http_req_failed.values.rate") ??
    get("metrics.http_req_failed.value"); // 0~1일 수도, 절대값일 수도 있음

  const toMs = (v) => (typeof v === "number" ? v.toFixed(2) : "n/a");
  const toNum = (v) => (typeof v === "number" ? v.toFixed(2) : "n/a");
  const toPct = (v) =>
    typeof v === "number" ? (v * 100).toFixed(2) + "%" : "n/a";

  const text = [
    "=== k6 Summary (safe) ===",
    `avg latency: ${toMs(avg)} ms`,
    `p95 latency: ${toMs(p95)} ms`,
    `throughput: ${toNum(rps)} req/s`,
    `fail rate: ${toPct(failRate)}`,
    "",
  ].join("\n");

  // 출력 파일 경로 (원하면 -e SUMMARY=... 로 덮어쓰기)
  const outFile = "outputs/summary.json";

  // summary.json에는 원본 전체를 남기되 setup_data는 마스킹
  const cloned = JSON.parse(JSON.stringify(data));
  if (cloned.setup_data) cloned.setup_data = { redacted: true };

  return {
    [outFile]: JSON.stringify(cloned, null, 2),
    stdout: text,
  };
}

/** ===== 템플릿/카르테시안 헬퍼 ===== */
function renderTemplate(tpl, ctx) {
  return tpl.replace(/{{\s*([\w]+)\s*}}/g, (_, k) => {
    const v = ctx[k];
    return encodeURIComponent(v == null ? "" : String(v));
  });
}

// params 객체(ex: {stock: [...], start: [...], end: [...]})를 카르테시안 조합 배열로 펼침
function cartesianParams(params) {
  const keys = Object.keys(params || {});
  if (!keys.length) return [{}];
  // 각 키는 배열이어야 함(단일값이면 배열로 감싸기)
  const values = keys.map((k) =>
    Array.isArray(params[k]) ? params[k] : [params[k]]
  );

  const out = [];
  function dfs(i, acc) {
    if (i === keys.length) {
      out.push({ ...acc });
      return;
    }
    const k = keys[i];
    for (const v of values[i]) {
      acc[k] = v;
      dfs(i + 1, acc);
    }
  }
  dfs(0, {});
  return out;
}

function comboLabel(ctx, order) {
  // order(예: ["stock","end"]) 기준으로 "stock_AAPL__end_2025-03-01" 생성
  const keys = Array.isArray(order) && order.length ? order : Object.keys(ctx);
  return keys
    .map((k) => `${k}_${String(ctx[k]).replace(/[^A-Za-z0-9._-]/g, "_")}`)
    .join("__");
}

/** ===== 선택된 엔드포인트 평탄화 ===== */
const all = [];
for (const c of cfg) {
  if (CONTROLLERS.length && !CONTROLLERS.includes(c.controller)) continue;

  for (const ep of c.endpoints) {
    if (ENDPOINTS.length && !ENDPOINTS.includes(ep.name)) continue;

    const baseItem = {
      controller: c.controller,
      name: ep.name,
      method: (ep.method || "GET").toUpperCase(),
      body: ep.body || null,
      _defaults: {
        executor: ep.executor,
        rate: ep.rate,
        vus: ep.vus,
        maxVUs: ep.maxVUs,
        duration: ep.duration,
        thresholds: ep.thresholds,
        headers: ep.headers,
      },
    };

    const hasTemplate = !!ep.qsTemplate && !!ep.params;
    const combos = hasTemplate ? cartesianParams(ep.params) : [{}];

    for (const combo of combos) {
      const qs = hasTemplate
        ? renderTemplate(ep.qsTemplate, combo)
        : ep.qs || "";
      const url = `${BASE}${c.base}${ep.path || ""}${qs ? "?" + qs : ""}`;
      const comboName = hasTemplate ? comboLabel(combo, ep.paramLabels) : null;

      // variants(부하 변형) 있으면 중첩 확장, 없으면 단일
      if (Array.isArray(ep.variants) && ep.variants.length) {
        for (const v of ep.variants) {
          all.push({
            ...baseItem,
            url,
            combo: comboName, // 조합 라벨
            variant: v.name || null, // 부하 변형 라벨
            _overrides: {
              executor: v.executor,
              rate: v.rate,
              vus: v.vus,
              maxVUs: v.maxVUs,
              duration: v.duration,
              thresholds: v.thresholds,
              headers: v.headers,
              body: v.body,
            },
          });
        }
      } else {
        all.push({
          ...baseItem,
          url,
          combo: comboName,
          variant: null,
          _overrides: {},
        });
      }
    }
  }
}

//** ===== 시나리오 생성 ===== */
const scenarios = {};
for (const ep of all) {
  const baseName = sanitize(`${ep.controller}_${ep.name}`);
  const parts = [baseName];
  if (ep.combo) parts.push(ep.combo);
  if (ep.variant) parts.push(ep.variant);
  const scenarioName = sanitize(parts.join("__"));

  const execType = (
    ep._overrides?.executor ||
    ep._defaults.executor ||
    EXECUTOR
  ).trim();
  const rate = Number(ep._overrides?.rate ?? ep._defaults.rate ?? RATE);
  const vus = Number(ep._overrides?.vus ?? ep._defaults.vus ?? VUS);
  const maxVUs = Number(
    ep._overrides?.maxVUs ?? ep._defaults.maxVUs ?? MAX_VUS
  );
  const duration = ep._overrides?.duration || ep._defaults.duration || DURATION;

  const env = {
    TARGET: `${ep.controller}.${ep.name}`,
    VARIANT: ep.variant || "",
    COMBO: ep.combo || "",
  };
  //고정 도착률 -> RPS를 직접 제어, RPS 기반 부하 모델
  if (execType === "constant-arrival-rate") {
    scenarios[scenarioName] = {
      executor: "constant-arrival-rate",
      rate,
      timeUnit: "1s",
      duration,
      preAllocatedVUs: vus,
      maxVUs,
      exec: "dispatch",
      env,
    };
  } else {
    scenarios[scenarioName] = {
      executor: "constant-vus", //사용자 수 기반 부하 모델, VU(가상 유저)의 수를 고정, 실제 유저 시뮬레이션에 근접
      vus,
      duration,
      exec: "dispatch",
      env,
    };
  }
}

export const options = {
  scenarios,
  thresholds: {
    http_req_failed: ["rate<0.01"],
    http_req_duration: ["p(95)<800"],
    // 예) 특정 조합/변형에만 임계치 강화:
    // "http_req_duration{endpoint:StockController.range,combo:stock_AAPL__end_2025-03-01,variant:heavy}": ["p(95)<600"]
  },
};

/** ===== 공통 검증 ===== */
function ok(res) {
  return check(res, { "status is 200": (r) => r.status === 200 });
}

/** ===== 실행 함수(모든 시나리오가 이걸 호출) ===== */
export function dispatch(data) {
  const target = __ENV.TARGET; // 예: "StockController.range"
  const ep = all.find((e) => `${e.controller}.${e.name}` === target);
  if (!ep) throw new Error(`No endpoint matched TARGET=${target}`);

  const params = {
    headers: {
      ...(data?.headers || {}),
      ...(ep._defaults.headers || {}),
      ...(ep._overrides?.headers || {}),
    },
    tags: {
      controller: ep.controller,
      endpoint: `${ep.controller}.${ep.name}`,
      combo: __ENV.COMBO || "",
      variant: __ENV.VARIANT || "",
    },
  };
  const bodyToUse = ep._overrides?.body ?? ep.body;
  let res;
  switch (ep.method) {
    case "GET":
      res = http.get(ep.url, params);
      if (res.status >= 400) {
        console.error(
          `[FAIL] ${ep.controller}.${ep.name} status=${
            res.status
          } body=${res.body?.slice(0, 200)}`
        );
      }
      break;
    case "POST":
      res = http.post(ep.url, bodyToUse, params);
      if (res.status >= 400) {
        console.error(
          `[FAIL] ${ep.controller}.${ep.name} status=${
            res.status
          } body=${res.body?.slice(0, 200)}`
        );
      }
      break;
    case "PUT":
      res = http.put(ep.url, bodyToUse, params);
      if (res.status >= 400) {
        console.error(
          `[FAIL] ${ep.controller}.${ep.name} status=${
            res.status
          } body=${res.body?.slice(0, 200)}`
        );
      }
      break;
    case "DELETE":
      res = http.del(ep.url, null, params);
      if (res.status >= 400) {
        console.error(
          `[FAIL] ${ep.controller}.${ep.name} status=${
            res.status
          } body=${res.body?.slice(0, 200)}`
        );
      }
      break;
    default:
      throw new Error(`Unsupported method: ${ep.method}`);
  }

  check(res, { "status is 200": (r) => r.status === 200 });
  if (res.status >= 400) {
    const e = extractError(res);
    const comboStr = params.tags.combo || "";
    const variantStr = params.tags.variant ? ` / ${params.tags.variant}` : "";
    const meta = [
      `status=${res.status}`,
      e.path ? `path=${e.path}` : null,
      e.ts ? `ts=${e.ts}` : null,
      e.trace ? `reqId=${e.trace}` : null,
    ]
      .filter(Boolean)
      .join(" ");

    console.error(
      `[FAIL] ${params.tags.endpoint}${
        comboStr || variantStr ? ` [${comboStr}${variantStr}]` : ""
      } ${meta}\n` + `→ ${e.msg}`
    );
  }
  // sleep(1);
}

/** ===== 에러 메시지/메타 추출 ===== */
function short(s, n = 400) {
  if (!s) return "";
  const str = String(s);
  return str.length > n ? str.slice(0, n) + "…" : str;
}
function extractError(res) {
  let json = null;
  try {
    json = JSON.parse(res.body);
  } catch (_) {}
  // 가장 흔한 필드 우선순위
  const msg =
    (json &&
      (json.message ||
        json.error_description ||
        json.error ||
        json.detail ||
        json.title ||
        json.reason)) ||
    (json && json.errors && short(JSON.stringify(json.errors), 300)) ||
    short(res.body, 300);

  const path = json && (json.path || json.instance);
  const ts = json && (json.timestamp || json.time);
  const trace =
    (json && (json.traceId || json.trace || json.errorId)) ||
    res.headers["x-request-id"] ||
    res.headers["x-amzn-requestid"] ||
    res.headers["x-correlation-id"];

  return { msg, path, ts, trace };
}

/** ===== 유틸 ===== */
function sanitize(s) {
  // k6 시나리오 이름 허용 문자만 남기기 (숫자/영문/언더스코어/대시)
  return s.replace(/[^A-Za-z0-9_-]/g, "_");
}
