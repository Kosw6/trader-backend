import http from 'k6/http';

export const options = {
  scenarios: {
    load_test: {
      executor: 'constant-arrival-rate',
      rate: 10,
      timeUnit: '1s',
      duration: '3m',
      preAllocatedVUs: 50,
      maxVUs: 200,
    },
  },
};

const targets = [
  'http://localhost:8080/internal/test/stock',
  'http://3.36.56.159:8080/internal/test/stock', // 두 번째 인스턴스
];

export default function () {
  const idx = __ITER % targets.length;
  http.get(targets[idx]);
}