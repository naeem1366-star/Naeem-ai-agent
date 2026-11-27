# EMA public market feeds — no account, no API key

This implementation deliberately uses read-only public endpoints. It does not ask the user to create provider accounts, request API keys, configure secrets, or subscribe to data services.

## Connected in the Android market dashboard

| Market | Public source | Data | Important caveat |
|---|---|---|---|
| Crypto | Binance market-data-only REST | BTC, ETH, BNB, SOL, XRP, DOGE tickers and 24h change | Exchange-specific prices; not a consolidated global price |
| Iran FX and gold | TGJU public JSON feed | Free-market USD, 18K gold, Emami coin, world gold ounce | Unofficial/undocumented endpoint; may change or block requests. TGJU local prices are Rial and are converted to Toman by dividing by 10 |
| Global stocks | Yahoo Finance public chart endpoint | S&P 500, NASDAQ, Dow Jones, Nikkei | Unofficial public chart endpoint; may be delayed, throttled or changed |
| Commodities | Yahoo Finance public chart endpoint | Gold, silver, WTI, Brent, copper futures | Futures quotes, not local cash-market prices |
| Iran stock market | TSETMC public market overview | TEDPIX index snapshot | Unofficial endpoint; may be blocked outside Iran or during maintenance |
| FX reference | Frankfurter / ECB reference rates | USD against EUR, GBP, CHF, JPY | Reference rates, not the Iranian free-market USD rate |

## Worker endpoint

`GET /v1/market/public-snapshot?market=`

Supported values: `crypto`, `fx_reference`, `global_stock`, `commodities`, `iran_stock`, `iran_local`.

Every quote includes its source, source URL, observation timestamp and a conservative freshness label. The response never describes a quote as live solely because a request just completed. Provider failures are isolated where possible and are surfaced in `source_status`.

## Market analyst

The Android dashboard adds a simple market breadth readout based on the available percentage changes: positive count, negative count and average reported move. It is descriptive, not a buy/sell recommendation and not a price forecast.

The technical-indicator engine already in the Worker remains available for stored candles (EMA, RSI, MACD, Bollinger Bands, ATR, volatility and drawdown). No historical candles are fabricated; technical analysis is not marked ready until sufficient real candles exist.

## Explicit non-goals

- No API keys, user accounts, sign-up flows, paid provider plans or user-managed secrets.
- No order placement, brokerage login, wallet connection or trading.
- No claim that all sources are official, licensed for unrestricted redistribution, guaranteed uptime or guaranteed real-time.
- No conflation of ECB reference FX with Iran's free-market rate.
- No conflation of global futures with local spot prices.

These endpoints are public and unauthenticated, but public access does not automatically mean an unrestricted commercial redistribution license. The app therefore attributes sources, uses low request frequency, handles failure, and avoids claiming contractual service guarantees. If a source blocks or changes its public endpoint, that market is shown as unavailable rather than replaced with invented values.
