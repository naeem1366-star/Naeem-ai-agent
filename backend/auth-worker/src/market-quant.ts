export type CandleInput = {
  open_time: number;
  high_text: string;
  low_text: string;
  close_text: string;
};

type Point = { time: number; value: number };

function numeric(value: string): number {
  const parsed = Number(value);
  if (!Number.isFinite(parsed)) throw new Error("INVALID_CANDLE_VALUE");
  return parsed;
}

function ema(values: number[], period: number): number[] {
  const result = Array(values.length).fill(null) as (number | null)[];
  if (values.length < period) return result as number[];
  const alpha = 2 / (period + 1);
  let current = values.slice(0, period).reduce((a, b) => a + b, 0) / period;
  result[period - 1] = current;
  for (let i = period; i < values.length; i++) {
    current = values[i] * alpha + current * (1 - alpha);
    result[i] = current;
  }
  return result as number[];
}

function rsi(values: number[], period = 14): number | null {
  if (values.length <= period) return null;
  let gains = 0;
  let losses = 0;
  for (let i = 1; i <= period; i++) {
    const delta = values[i] - values[i - 1];
    gains += Math.max(0, delta);
    losses += Math.max(0, -delta);
  }
  let averageGain = gains / period;
  let averageLoss = losses / period;
  for (let i = period + 1; i < values.length; i++) {
    const delta = values[i] - values[i - 1];
    averageGain = (averageGain * (period - 1) + Math.max(0, delta)) / period;
    averageLoss = (averageLoss * (period - 1) + Math.max(0, -delta)) / period;
  }
  if (averageLoss === 0) return averageGain === 0 ? 50 : 100;
  const relativeStrength = averageGain / averageLoss;
  return 100 - 100 / (1 + relativeStrength);
}

function standardDeviation(values: number[]): number {
  if (!values.length) return 0;
  const mean = values.reduce((a, b) => a + b, 0) / values.length;
  const variance = values.reduce((sum, value) => sum + (value - mean) ** 2, 0) / values.length;
  return Math.sqrt(variance);
}

export function buildTechnicalSnapshot(rows: CandleInput[]) {
  if (rows.length < 60) {
    return { ready: false, required_candles: 60, available_candles: rows.length, reason: "INSUFFICIENT_HISTORY" as const };
  }
  const candles = rows.map(row => ({
    time: Number(row.open_time),
    high: numeric(row.high_text),
    low: numeric(row.low_text),
    close: numeric(row.close_text),
  }));
  if (candles.some(c => c.low <= 0 || c.high < c.low || c.close <= 0 || c.close > c.high || c.close < c.low)) {
    throw new Error("INVALID_OHLC_RELATIONSHIP");
  }
  const closes = candles.map(c => c.close);
  const ema20 = ema(closes, 20);
  const ema50 = ema(closes, 50);
  const latest = closes[closes.length - 1];
  const previous = closes[closes.length - 2];
  const returns: number[] = [];
  for (let i = 1; i < closes.length; i++) returns.push(closes[i] / closes[i - 1] - 1);
  const last20 = closes.slice(-20);
  const middle = last20.reduce((a, b) => a + b, 0) / last20.length;
  const deviation = standardDeviation(last20);
  const macdFast = ema(closes, 12);
  const macdSlow = ema(closes, 26);
  const macdLine = closes.map((_, i) => macdFast[i] == null || macdSlow[i] == null ? null : macdFast[i] - macdSlow[i]);
  const macdValues = macdLine.filter((x): x is number => x !== null);
  const macdSignalSeries = ema(macdValues, 9);
  const macdCurrent = macdValues[macdValues.length - 1];
  const signalCurrent = macdSignalSeries[macdSignalSeries.length - 1];
  let peak = closes[0];
  let maxDrawdown = 0;
  for (const close of closes) {
    peak = Math.max(peak, close);
    maxDrawdown = Math.min(maxDrawdown, close / peak - 1);
  }
  const trueRanges: number[] = [];
  for (let i = 1; i < candles.length; i++) {
    const current = candles[i];
    const priorClose = candles[i - 1].close;
    trueRanges.push(Math.max(current.high - current.low, Math.abs(current.high - priorClose), Math.abs(current.low - priorClose)));
  }
  const atr14 = trueRanges.slice(-14).reduce((a, b) => a + b, 0) / Math.min(14, trueRanges.length);
  const trend = latest > ema20[ema20.length - 1] && ema20[ema20.length - 1] > ema50[ema50.length - 1]
    ? "bullish"
    : latest < ema20[ema20.length - 1] && ema20[ema20.length - 1] < ema50[ema50.length - 1]
      ? "bearish" : "mixed";
  const rsiValue = rsi(closes);
  return {
    ready: true,
    sample_size: candles.length,
    as_of: candles[candles.length - 1].time,
    trend,
    price: latest,
    change_last_candle_pct: previous === 0 ? null : (latest / previous - 1) * 100,
    indicators: {
      ema_20: ema20[ema20.length - 1],
      ema_50: ema50[ema50.length - 1],
      rsi_14: rsiValue,
      macd: { line: macdCurrent, signal: signalCurrent, histogram: macdCurrent - signalCurrent },
      bollinger_20: { middle, upper: middle + 2 * deviation, lower: middle - 2 * deviation },
      atr_14: atr14,
      atr_pct: latest === 0 ? null : atr14 / latest * 100,
      realized_return_stddev: standardDeviation(returns.slice(-20)),
      max_drawdown_sample: maxDrawdown * 100,
    },
    methodology: {
      trend: "EMA20/EMA50 alignment and latest close",
      rsi: "Wilder RSI, 14 periods",
      macd: "EMA12 minus EMA26; EMA9 signal",
      bollinger: "20-period SMA plus/minus two population standard deviations",
      atr: "14-period simple average true range",
      realized_return_stddev: "Standard deviation of last 20 close-to-close simple returns; not annualized",
      max_drawdown_sample: "Maximum peak-to-trough drawdown in supplied candle sample",
    },
    limitations: [
      "Indicators describe historical price behavior and are not a price forecast.",
      "No market-session or corporate-action adjustment is inferred by this calculation.",
      "Interpretation depends on candle interval, source quality and instrument-specific conventions."
    ]
  };
}
