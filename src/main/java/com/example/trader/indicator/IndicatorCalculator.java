package com.example.trader.indicator;

/** 순수 계산: RSI Wilder(14), SMA 시드 EMA MACD(12,26,9). 날짜/DB/HTTP는 모른다. */
public final class IndicatorCalculator {
    private IndicatorCalculator() {}
    public record Values(Double rsi, Double macd, Double signal, Double histogram) {}

    public static Values[] calculate(double[] close) {
        for (double value : close) {
            if (!Double.isFinite(value) || value <= 0) {
                throw new IllegalArgumentException("종가는 양수인 유한한 값이어야 합니다.");
            }
        }
        Double[] fast = ema(close, 12), slow = ema(close, 26);
        double gain = 0, loss = 0, signalSeed = 0;
        Double rsi = null, signal = null;
        int macdCount = 0;
        Values[] result = new Values[close.length];
        for (int i = 0; i < close.length; i++) {
            if (i > 0) {
                double delta = close[i] - close[i - 1];
                double up = Math.max(delta, 0), down = Math.max(-delta, 0);
                if (i <= 14) { gain += up / 14; loss += down / 14; }
                else { gain = (gain * 13 + up) / 14; loss = (loss * 13 + down) / 14; }
                if (i >= 14) {
                    // 0/0은 중립 50으로 명시한다. 다른 플랫폼의 시드/평탄 구간 정책과 다를 수 있다.
                    rsi = gain == 0 && loss == 0 ? 50.0 : loss == 0 ? 100.0
                            : gain == 0 ? 0.0 : 100 - 100 / (1 + gain / loss);
                }
            }
            Double macd = slow[i] == null ? null : fast[i] - slow[i];
            if (macd != null) {
                macdCount++;
                if (macdCount <= 9) signalSeed += macd / 9;
                if (macdCount == 9) signal = signalSeed;
                else if (macdCount > 9) signal = signal + .2 * (macd - signal);
            }
            result[i] = new Values(rsi, macd, signal,
                    macd == null || signal == null ? null : macd - signal);
        }
        return result;
    }

    private static Double[] ema(double[] values, int period) {
        Double[] result = new Double[values.length];
        double sum = 0, previous = 0, alpha = 2.0 / (period + 1);
        for (int i = 0; i < values.length; i++) {
            if (i < period) sum += values[i] / period;
            if (i == period - 1) previous = sum;
            else if (i >= period) previous += alpha * (values[i] - previous);
            if (i >= period - 1) result[i] = previous;
        }
        return result;
    }
}
