import http from "k6/http";
import { check, sleep } from "k6";
import { SharedArray } from "k6/data";
import { Trend } from "k6/metrics";

/** ===== 커스텀 메트릭: 스테이지별 응답시간 ===== */
const RT_STAGE = new Trend("rt_stage", true); // tag별(submetric) 집계 허용

/** ===== 실행 파라미터 (ENV) ===== */
const BASE = __ENV.BASE_URL || "http://172.30.1.78:8080";
const VUS = Number(__ENV.VUS || 5);
const DURATION = __ENV.DURATION || "10s";
const EXECUTOR = (__ENV.EXECUTOR || "constant-vus").trim(); // "constant-vus" | "constant-arrival-rate" | "ramping-arrival-rate"
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
const VARIANTS = (__ENV.VARIANTS || "light") // 예: "light", "heavy", "light,heavy"
  .split(",")
  .map((s) => s.trim())
  .filter(Boolean);
const SUMMARY_OUT = __ENV.SUMMARY || "outputs/summary.json";
const DISTRIBUTE_RATE = String(__ENV.DISTRIBUTE_RATE || "1") === "1"; // 🔴 그룹 내 조합별 rate 분배 ON/OFF
const DROP_BODIES = String(__ENV.DROP_BODIES || "1") === "1"; // 요청 단위 바디 폐기 여부

/** ===== 엔드포인트 설정 로드 =====
 * 파일 위치: k6/data/endpoints.json
 * 실행 위치: k6/ 폴더에서 실행 (권장)
 */
const cfg = new SharedArray("endpoints", () =>
  JSON.parse(open("../data/endpoints.json"))
);

export function setup() {
  const url = `${BASE}/api/login/signin`; // 로그인 엔드포인트
  const payload = JSON.stringify({
    loginId: __ENV.USER || "login_34",
    password: __ENV.PASS || "pw_e369853df766fa44e1ed0ff613f563bd",
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
    testStartTs: Date.now(), // 스테이지 경과 시간 계산용 앵커
  };
}

export function handleSummary(data) {
  // 민감정보 제거
  if (data.setup_data) data.setup_data = { redacted: true };

  // 안전한 값 추출 헬퍼
  const get = (path, fallback = undefined) =>
    path.split(".").reduce((o, k) => (o && k in o ? o[k] : undefined), data) ??
    fallback;

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
    get("metrics.http_req_failed.value");

  const toMs = (v) => (typeof v === "number" ? v.toFixed(2) : "n/a");
  const toNum = (v) => (typeof v === "number" ? v.toFixed(2) : "n/a");
  const toPct = (v) =>
    typeof v === "number" ? (v * 100).toFixed(2) + "%" : "n/a";

  const lines = [
    "=== k6 Summary (safe) ===",
    `avg latency: ${toMs(avg)} ms`,
    `p95 latency: ${toMs(p95)} ms`,
    `throughput: ${toNum(rps)} req/s`,
    `fail rate: ${toPct(failRate)}`,
    "",
  ];

  // ===== 스테이지별 p95 출력 =====
  const metrics = data.metrics || {};
  const stageKeys = Object.keys(metrics).filter((k) => {
    // 'rt_stage{stage:0,...}' 처럼 stage 태그가 있는 submetric만
    return k.startsWith("rt_stage{") && /stage:\d+/.test(k);
  });

  // stage index 기준 정렬
  stageKeys.sort((a, b) => {
    const ai = Number((a.match(/stage:(\d+)/) || [])[1] || 0);
    const bi = Number((b.match(/stage:(\d+)/) || [])[1] || 0);
    return ai - bi;
  });

  if (stageKeys.length) {
    lines.push("per-stage p95 (ms):");
    for (const k of stageKeys) {
      const idx = (k.match(/stage:(\d+)/) || [])[1] || "0";
      const p = metrics[k]?.values?.["p(95)"];
      lines.push(`  stage ${idx}: ${toMs(p)}`);
    }
    lines.push("");
  }

  // 출력 파일 경로
  const outFile = SUMMARY_OUT;

  // summary.json에는 원본 전체를 남기되 setup_data는 마스킹
  const cloned = JSON.parse(JSON.stringify(data));
  if (cloned.setup_data) cloned.setup_data = { redacted: true };

  return {
    [outFile]: JSON.stringify(cloned, null, 2),
    stdout: lines.join("\n"),
  };
}

/** ===== 템플릿/카르테시안 헬퍼 ===== */
function renderTemplate(tpl, ctx) {
  if (!tpl) return "";
  return tpl.replace(/{{\s*([\w]+)\s*}}/g, (_, k) => {
    const v = ctx[k];
    return encodeURIComponent(v == null ? "" : String(v));
  });
}

// "{{...}}" 포함 여부
function hasTemplateBraces(s) {
  return typeof s === "string" && /{{\s*[\w]+\s*}}/.test(s);
}

function cartesianParams(params) {
  const keys = Object.keys(params || {});
  if (!keys.length) return [{}];
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
  const keys = Array.isArray(order) && order.length ? order : Object.keys(ctx);
  return keys
    .map((k) => `${k}_${String(ctx[k]).replace(/[^A-Za-z0-9._-]/g, "_")}`)
    .join("__");
}

function normalizePath(p) {
  if (!p) return "";
  return p.startsWith("/") ? p : `/${p}`;
}

function toSeconds(dur) {
  if (!dur) return 0;
  const m = String(dur).match(/^(\d+)(ms|s|m|h)$/);
  if (!m) return 0;
  const n = Number(m[1]),
    u = m[2];
  if (u === "ms") return Math.floor(n / 1000);
  if (u === "s") return n;
  if (u === "m") return n * 60;
  if (u === "h") return n * 3600;
  return 0;
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
        preAllocatedVUs: ep.preAllocatedVUs, // 우선 사용
        vus: ep.vus, // 백워드 호환
        maxVUs: ep.maxVUs,
        duration: ep.duration,
        thresholds: ep.thresholds,
        headers: ep.headers,
        // ✅ ramping-arrival-rate 관련 필드도 저장
        startRate: ep.startRate,
        timeUnit: ep.timeUnit,
        stages: ep.stages,
      },
    };

    // 템플릿 필요 여부: params가 있고, qsTemplate 또는 path에 {{...}}가 있는 경우
    const needsTemplate =
      ep.params &&
      (hasTemplateBraces(ep.qsTemplate) || hasTemplateBraces(ep.path));

    const combos = needsTemplate ? cartesianParams(ep.params) : [{}];

    for (const combo of combos) {
      // path/qs 각각 템플릿 치환
      const renderedPath = normalizePath(
        hasTemplateBraces(ep.path) ? renderTemplate(ep.path, combo) : ep.path
      );

      const qs = hasTemplateBraces(ep.qsTemplate)
        ? renderTemplate(ep.qsTemplate, combo)
        : ep.qs || "";

      const url = `${BASE}${c.base}${renderedPath}${qs ? "?" + qs : ""}`;
      const comboName = needsTemplate
        ? comboLabel(combo, ep.paramLabels)
        : null;

      if (Array.isArray(ep.variants) && ep.variants.length) {
        for (const v of ep.variants) {
          if (VARIANTS.length && !VARIANTS.includes(v.name)) continue;

          const item = {
            ...baseItem,
            url,
            combo: comboName,
            variant: v.name || null,
            _overrides: {
              executor: v.executor,
              rate: v.rate,
              preAllocatedVUs: v.preAllocatedVUs,
              vus: v.vus, // 백워드 호환
              maxVUs: v.maxVUs,
              duration: v.duration,
              thresholds: v.thresholds,
              headers: v.headers,
              body: v.body,
              startTime: v.startTime,
              // ✅ ramping-arrival-rate 관련 필드 반영
              startRate: v.startRate,
              timeUnit: v.timeUnit,
              stages: v.stages,
            },
          };
          item.key = [
            `${item.controller}.${item.name}`,
            item.combo || "-",
            item.variant || "-",
          ].join("::");
          all.push(item);
        }
      } else {
        const item = {
          ...baseItem,
          url,
          combo: comboName,
          variant: null,
          _overrides: {},
        };
        item.key = [
          `${item.controller}.${item.name}`,
          item.combo || "-",
          "-",
        ].join("::");
        all.push(item);
      }
    }
  }
}

// 빠른 조회 맵
const allMap = {};
for (const it of all) allMap[it.key] = it;

/** ===== (신규) 그룹별 조합 수/기준 rate 집계 & 분배 =====
 * 그룹 키: controller.name + variant (콤보들은 같은 그룹으로 간주)
 * - 같은 그룹 내 여러 combo가 있으면 per-combo rate로 나눠 총 RPS 유지
 * - baseRate < combos면 perRate=1로 올리므로 총합이 baseRate보다 커질 수 있음(간단 전략)
 *   → 엄밀 분배(총합 동일) 원하면 remainder 분배 방식으로 교체 가능
 */
const groupCounts = {};
const groupBaseRate = {};
for (const ep of all) {
  const g = `${ep.controller}.${ep.name}::${ep.variant || "-"}`;
  groupCounts[g] = (groupCounts[g] || 0) + 1;
  const baseRate = Number(ep._overrides?.rate ?? ep._defaults.rate ?? RATE);
  groupBaseRate[g] = baseRate; // 같은 그룹이면 동일하다고 가정
}

const perRateMap = {};
if (DISTRIBUTE_RATE) {
  for (const ep of all) {
    const g = `${ep.controller}.${ep.name}::${ep.variant || "-"}`;
    const baseRate = Number(groupBaseRate[g] || 0);
    const count = Number(groupCounts[g] || 1);
    // 간단 분배: Math.max(1, floor(base/count))
    // (주의: base<count면 총합이 base보다 커질 수 있음)
    perRateMap[ep.key] = Math.max(1, Math.floor(baseRate / count));
  }
}

/** ===== 시나리오 생성 + per-scenario thresholds 병합 ===== */
const scenarios = {};
const thresholds = {
  http_req_failed: ["rate<0.01"],
  http_req_duration: ["p(95)<800"],
};

function mergeThresholds(thrObj, scenarioName, thrSpec) {
  if (!thrSpec || typeof thrSpec !== "object") return;
  for (const [metric, arr] of Object.entries(thrSpec)) {
    const key = metric.includes("{")
      ? metric
      : `${metric}{scenario:${scenarioName}}`;
    thrObj[key] = arr;
  }
}

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

  const baseRate = Number(ep._overrides?.rate ?? ep._defaults.rate ?? RATE);
  const rate =
    execType === "constant-arrival-rate" && DISTRIBUTE_RATE
      ? Number(perRateMap[ep.key] ?? baseRate)
      : baseRate;

  // preAllocatedVUs 우선 -> 없으면 vus 사용 -> 마지막으로 글로벌 VUS
  const pre = Number(
    ep._overrides?.preAllocatedVUs ??
      ep._defaults.preAllocatedVUs ??
      ep._overrides?.vus ??
      ep._defaults.vus ??
      VUS
  );
  const maxVUs = Number(
    ep._overrides?.maxVUs ?? ep._defaults.maxVUs ?? MAX_VUS
  );
  const duration = ep._overrides?.duration || ep._defaults.duration || DURATION;
  const startTime = ep._overrides?.startTime;

  const env = {
    KEY: ep.key, // 🔴 유니크 매핑 키
    TARGET: `${ep.controller}.${ep.name}`,
    VARIANT: ep.variant || "",
    COMBO: ep.combo || "",
  };

  if (execType === "constant-arrival-rate") {
    scenarios[scenarioName] = {
      executor: "constant-arrival-rate",
      rate,
      timeUnit: "1s",
      duration, // CAR에서는 duration 사용
      preAllocatedVUs: pre,
      maxVUs,
      exec: "dispatch",
      env,
      ...(startTime ? { startTime } : {}),
    };
  } else if (execType === "ramping-arrival-rate") {
    // ✅ JSON에 넣은 stages/startRate/timeUnit을 최우선 사용
    const rawStages = ep._overrides?.stages ??
      ep._defaults?.stages ?? [{ target: rate, duration: "1m" }]; // 최후 fallback

    const startRate = Number(
      ep._overrides?.startRate ?? ep._defaults?.startRate ?? 10
    );

    const timeUnit = ep._overrides?.timeUnit ?? ep._defaults?.timeUnit ?? "1s";

    // 스테이지 정보 env로 내려보내기 (내장 보고서용)
    const stageDurS = rawStages.map((s) => toSeconds(s.duration));
    const stageTargets = rawStages.map((s) => Number(s.target));

    scenarios[scenarioName] = {
      executor: "ramping-arrival-rate",
      startRate,
      timeUnit,
      preAllocatedVUs: pre,
      maxVUs,
      stages: rawStages, // 길이는 stages로만 제어 (duration 넣지 말 것)
      exec: "dispatch",
      env: {
        ...env,
        STAGE_DURS: stageDurS.join(","), // "30,30,30,30,30"
        STAGE_TARGETS: stageTargets.join(","), // "40,80,120,160,200"
        START_OFFSET_S: String(startTime ? toSeconds(startTime) : 0),
      },
      ...(startTime ? { startTime } : {}),
    };
    // ✅ 여기 추가: 스테이지별 더미 threshold를 자동 주입해 summary에 서브메트릭 노출
    for (let i = 0; i < rawStages.length; i++) {
      // mergeThresholds는 태그가 없는 키에만 {scenario:...}를 붙입니다.
      // 여기서는 태그를 명시하지 않고 넣어도 되고, 명시하고 싶다면 아래처럼:
      const key = `rt_stage{stage:${i}}`; // 또는 `rt_stage{stage:${i},scenario:${scenarioName}}`
      thresholds[key] = ["p(95)<100000"]; // 아주 느슨한 더미 기준
    }
  } else {
    scenarios[scenarioName] = {
      executor: "constant-vus",
      vus: pre,
      duration, // CV에서는 duration 사용
      exec: "dispatch",
      env,
      ...(startTime ? { startTime } : {}),
    };
  }

  // per-variant/endpoint thresholds 적용 (scenario 태그로 scope 좁힘)
  mergeThresholds(thresholds, scenarioName, ep._defaults.thresholds);
  mergeThresholds(thresholds, scenarioName, ep._overrides.thresholds);
}

export const options = {
  scenarios,
  thresholds,
};

/** ===== 공통 검증 ===== */
function ok(res) {
  return check(res, { "status is 200": (r) => r.status === 200 });
}

/** ===== 실행 함수(모든 시나리오가 이걸 호출) ===== */
export function dispatch(data) {
  const key = __ENV.KEY;
  const ep = allMap[key];
  if (!ep) throw new Error(`No endpoint matched KEY=${key}`);
  // dispatch 맨 위 근처
  // console.log(
  //   `[REQ] ${ep.method} ${ep.url}  combo=${__ENV.COMBO || ""} variant=${
  //     __ENV.VARIANT || ""
  //   }`
  // );

  // 현재 시나리오의 경과 시간(초) → 스테이지 인덱스 계산
  const testStartTs = data?.testStartTs || 0;
  const startOffset = Number(__ENV.START_OFFSET_S || 0);
  const elapsedS = Math.max(
    0,
    Math.floor((Date.now() - testStartTs) / 1000) - startOffset
  );
  const dursStr = __ENV.STAGE_DURS || "";
  const durs = dursStr ? dursStr.split(",").map(Number) : [];
  let stageIdx = 0;
  if (durs.length) {
    let acc = 0;
    for (let i = 0; i < durs.length; i++) {
      acc += durs[i];
      if (elapsedS < acc) {
        stageIdx = i;
        break;
      }
      stageIdx = i; // 마지막 스테이지 이후면 최종 인덱스로
    }
  }

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
      stage: String(stageIdx), // ← 중요: 스테이지 태그
    },
    responseType: DROP_BODIES ? "none" : "text", // 요청 단위 바디 폐기
  };

  const bodyToUse = ep._overrides?.body ?? ep.body;
  let res;
  switch (ep.method) {
    case "GET":
      res = http.get(ep.url, params);
      break;
    case "POST":
      res = http.post(ep.url, bodyToUse, params);
      break;
    case "PUT":
      res = http.put(ep.url, bodyToUse, params);
      break;
    case "DELETE":
      res = http.del(ep.url, null, params);
      break;
    default:
      throw new Error(`Unsupported method: ${ep.method}`);
  }

  // per-stage 응답시간 수집
  RT_STAGE.add(res.timings.duration, params.tags);

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
    // console.log(`[BODY] ${String(res.body).slice(0, 200)}`);/
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
