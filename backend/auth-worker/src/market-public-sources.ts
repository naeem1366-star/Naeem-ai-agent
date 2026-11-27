export type PublicMarket = "crypto" | "fx_reference" | "global_stock" | "commodities" | "iran_stock" | "iran_local";

type Quote = {
  symbol: string;
  name: string;
  market: PublicMarket;
  price: number;
  currency: string;
  changePercent: number | null;
  observedAt: number;
  source: string;
  sourceUrl: string;
  freshness: "recently_observed" | "historical_or_delayed";
};

const nowSeconds = () => Math.floor(Date.now() / 1000);
const safeNumber = (value: unknown): number | null => {
  const n = Number(value);
  return Number.isFinite(n) ? n : null;
};

async function getJson(url: string, headers: Record<string, string> = {}): Promise<any> {
  const response = await fetch(url, {
    headers: { "accept": "application/json", "user-agent": "EMA-Market-Research/1.0", ...headers },
    signal: AbortSignal.timeout(9000),
  });
  if (!response.ok) throw new Error("SOURCE_HTTP_" + response.status);
  return response.json();
}

function quote(
  symbol: string, name: string, market: PublicMarket, price: number,
  currency: string, changePercent: number | null, observedAt: number,
  source: string, sourceUrl: string,
): Quote {
  const age = Math.max(0, nowSeconds() - observedAt);
  return {
    symbol, name, market, price, currency, changePercent, observedAt, source, sourceUrl,
    freshness: age <= 15 * 60 ? "recently_observed" : "historical_or_delayed",
  };
}

async function yahooQuotes(symbols: Array<{ symbol: string; name: string; market: PublicMarket }>): Promise<Quote[]> {
  const results = await Promise.allSettled(symbols.map(async item => {
    const url = "https://query1.finance.yahoo.com/v8/finance/chart/" + encodeURIComponent(item.symbol) + "?range=5d&interval=15m";
    const data = await getJson(url);
    const result = data?.chart?.result?.[0];
    const meta = result?.meta;
    const price = safeNumber(meta?.regularMarketPrice);
    if (price === null) throw new Error("SOURCE_PRICE_MISSING");
    const previous = safeNumber(meta?.chartPreviousClose ?? meta?.previousClose);
    const observedAt = safeNumber(meta?.regularMarketTime) ?? nowSeconds();
    const change = previous && previous !== 0 ? ((price - previous) / previous) * 100 : null;
    return quote(item.symbol, item.name, item.market, price, String(meta?.currency || "USD"), change, observedAt, "Yahoo Finance public chart", url);
  }));
  return results.flatMap(x => x.status === "fulfilled" ? [x.value] : []);
}

async function binanceQuotes(): Promise<Quote[]> {
  const symbols = ["BTCUSDT", "ETHUSDT", "BNBUSDT", "SOLUSDT", "XRPUSDT", "DOGEUSDT"];
  const url = "https://data-api.binance.vision/api/v3/ticker/24hr";
  const data = await getJson(url);
  if (!Array.isArray(data)) throw new Error("BINANCE_BAD_RESPONSE");
  return data.filter((x: any) => symbols.includes(String(x.symbol))).map((x: any) =>
    quote(String(x.symbol), String(x.symbol).replace("USDT", ""), "crypto",
      Number(x.lastPrice), "USDT", safeNumber(x.priceChangePercent), nowSeconds(),
      "Binance public market-data API", url)
  );
}

async function frankfurterQuotes(): Promise<Quote[]> {
  const url = "https://api.frankfurter.dev/v1/latest?base=USD&symbols=EUR,GBP,CHF,JPY";
  const data = await getJson(url);
  const rates = data?.rates || {};
  const observedAt = Math.floor(new Date(String(data.date) + "T12:00:00Z").getTime() / 1000);
  return Object.entries(rates).flatMap(([currency, value]) => {
    const price = safeNumber(value);
    return price === null ? [] : [quote("USD/" + currency, "US dollar to " + currency, "fx_reference",
      price, currency, null, observedAt, "Frankfurter (ECB reference rates)", url)];
  });
}


async function tgjuQuotes(): Promise<Quote[]> {
  const url = "https://call1.tgju.org/ajax.json";
  const data = await getJson(url);
  const current = data?.current || {};
  const items: Array<[string, string, number]> = [
    ["price_dollar_rl", "USD free market (Iran)", 10],
    ["geram18", "18K gold gram (Iran)", 10],
    ["sekee", "Emami coin (Iran)", 10],
    ["ons", "Gold ounce", 1],
  ];
  return items.flatMap(([key, name, divisor]) => {
    const raw = safeNumber(current?.[key]?.p);
    if (raw === null) return [];
    const isOunce = key === "ons";
    return [quote(key, name, "iran_local", raw / divisor, isOunce ? "USD/oz" : "Toman",
      null, nowSeconds(), "TGJU public feed (unofficial)", url)];
  });
}

async function iranStockQuotes(): Promise<Quote[]> {
  const url = "https://cdn.tsetmc.com/api/MarketData/GetMarketOverview/0";
  const data = await getJson(url, { "referer": "https://www.tsetmc.com/", "origin": "https://www.tsetmc.com" });
  const overview = data?.marketOverview?.[0] ?? data?.marketOverview;
  const indexValue = safeNumber(overview?.indexLastValue);
  const change = safeNumber(overview?.indexChange);
  if (indexValue === null) throw new Error("TSETMC_INDEX_MISSING");
  return [quote("TEDPIX", "شاخص کل بورس تهران", "iran_stock", indexValue, "IRR-index-points",
    change, nowSeconds(), "TSETMC public endpoint (unofficial)", url)];
}

const MARKET_SYMBOLS: Record<"global_stock" | "commodities", Array<{ symbol: string; name: string; market: PublicMarket }>> = {
  global_stock: [
    { symbol: "^GSPC", name: "S&P 500", market: "global_stock" },
    { symbol: "^IXIC", name: "NASDAQ Composite", market: "global_stock" },
    { symbol: "^DJI", name: "Dow Jones", market: "global_stock" },
    { symbol: "^FTSE", name: "FTSE 100", market: "global_stock" },
    { symbol: "^N225", name: "Nikkei 225", market: "global_stock" },
  ],
  commodities: [
    { symbol: "GC=F", name: "Gold futures", market: "commodities" },
    { symbol: "SI=F", name: "Silver futures", market: "commodities" },
    { symbol: "CL=F", name: "WTI crude oil futures", market: "commodities" },
    { symbol: "BZ=F", name: "Brent crude oil futures", market: "commodities" },
    { symbol: "HG=F", name: "Copper futures", market: "commodities" },
  ],
};

export async function publicMarketSnapshot(market: string): Promise<Response> {
  const allowed: PublicMarket[] = ["crypto", "fx_reference", "global_stock", "commodities", "iran_stock", "iran_local"];
  if (!allowed.includes(market as PublicMarket)) {
    return Response.json({ error: "UNSUPPORTED_PUBLIC_MARKET", supported: allowed }, { status: 422 });
  }
  try {
    let quotes: Quote[] = [];
    let sourceStatus: Record<string, string> = {};
    if (market === "crypto") {
      try { quotes = await binanceQuotes(); sourceStatus.binance = "ok"; }
      catch (error) { sourceStatus.binance = error instanceof Error ? error.message : "unavailable"; }
    } else if (market === "fx_reference") {
      try { quotes = await frankfurterQuotes(); sourceStatus.frankfurter = "ok"; }
      catch (error) { sourceStatus.frankfurter = error instanceof Error ? error.message : "unavailable"; }
    } else if (market === "iran_local") {
      try { quotes = await tgjuQuotes(); sourceStatus.tgju = "ok"; }
      catch (error) { sourceStatus.tgju = error instanceof Error ? error.message : "unavailable"; }
    } else if (market === "iran_stock") {
      try { quotes = await iranStockQuotes(); sourceStatus.tsetmc = "ok"; }
      catch (error) { sourceStatus.tsetmc = error instanceof Error ? error.message : "unavailable"; }
    } else {
      const symbols = MARKET_SYMBOLS[market as "global_stock" | "commodities"];
      quotes = await yahooQuotes(symbols);
      sourceStatus.yahooFinance = quotes.length ? "partial_or_ok" : "unavailable";
    }
    return Response.json({
      market,
      quotes,
      source_status: sourceStatus,
      count: quotes.length,
      fetched_at: nowSeconds(),
      notice: market === "fx_reference"
        ? "این نرخ‌ها مرجع بین‌المللی USD هستند و نرخ آزاد دلار ایران نیستند."
        : market === "iran_stock"
          ? "منبع TSETMC عمومی و غیررسمی است؛ در صورت مسدودشدن یا تأخیر، داده ممکن است در دسترس نباشد."
          : "داده از endpoint عمومی بدون کلید دریافت شده؛ زمان مشاهده و منبع برای هر ردیف ثبت شده است.",
    }, { headers: { "cache-control": "public, max-age=30" } });
  } catch {
    return Response.json({ market, quotes: [], source_status: { provider: "unavailable" }, error: "PUBLIC_SOURCE_UNAVAILABLE" }, { status: 503 });
  }
}
