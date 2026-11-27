# قرارداد داده و مدل تحلیلی EMA Market Intelligence

## 1. قرارداد مشترک داده

تمام timestampها در ذخیره‌سازی UTC و به‌صورت epoch milliseconds یا ISO-8601 UTC نگهداری می‌شوند. نمایش به کاربر با timezone بازار یا timezone انتخابی انجام می‌شود.

هر مشاهده بازار باید provenance داشته باشد:

```json
{
  "instrument_id": "btc-usdt-binance-spot",
  "market": "crypto",
  "data_type": "quote",
  "observed_at": "2026-11-27T12:00:00Z",
  "received_at": "2026-11-27T12:00:02Z",
  "source_id": "provider-id",
  "source_timestamp": "2026-11-27T12:00:00Z",
  "currency": "USDT",
  "price": "90000.12",
  "quality": "verified",
  "freshness": "live",
  "is_market_open": true
}
```

این نمونه صرفاً ساختار است و قیمت آن داده واقعی نیست. مقدارهای مالی در لایه محاسباتی با decimal دقیق یا integer scaled units پردازش شوند؛ float دودویی برای حسابداری یا مقایسه قیمت حساس استفاده نشود.

### وضعیت کیفیت

- `verified`: منبع معتبر، schema صحیح و تازگی قابل قبول.
- `delayed`: داده معتبر اما تأخیردار.
- `stale`: از حد مجاز تازگی عبور کرده.
- `partial`: بخشی از فیلدها/منابع موجود نیست.
- `estimated`: مقدار محاسباتی مشتق‌شده، نه quote مستقیم.
- `unavailable`: داده قابل استفاده وجود ندارد.
- `rejected`: داده به دلیل اعتبارسنجی رد شده.

`freshness` و `quality` دو مفهوم جدا هستند؛ یک داده می‌تواند از منبع معتبر ولی stale باشد.

## 2. مدل‌های پایه

### Instrument
- `id`، `market`، `asset_class`، `symbol`، `name`
- `exchange`، `country`، `quote_currency`
- `timezone`، `calendar_id`
- `provider_symbol` mapping
- `active` و `metadata_json`

### MarketObservation
- `id`، `instrument_id`، `kind`
- `observed_at`، `received_at`، `source_id`
- `value_decimal` یا ستون‌های صریح quote
- `currency`، `unit`، `quality`، `freshness`
- `raw_payload_hash` و `schema_version`

### Candle
- `instrument_id`، `interval`، `open_time`، `close_time`
- `open`، `high`، `low`، `close`، `volume`
- `volume_unit`، `quote_volume`، `trade_count` در صورت وجود
- `is_final`، `source_id`، `quality`

### DerivedMetric
- `instrument_id`، `metric_key`، `window`
- `value`، `calculated_at`
- `input_snapshot_ids`، `algorithm_version`
- `quality` و `explanation`

### AnalysisReport
- `id`، `scope` (instrument/market/cross-market)
- `created_at`، `valid_until`، `horizon`
- `data_snapshot_refs`، `engine_versions`
- `summary`، `bull_case`، `bear_case`، `neutral_case`
- `confidence`، `confidence_method`
- `limitations`، `source_refs`
- `billing_usage_id` در صورت پولی بودن

### Forecast
- `id`، `instrument_id`، `target_time`، `created_at`
- `horizon`، `model_id`، `model_version`
- `baseline_value`، `quantiles` یا probability distribution
- `scenario_probabilities`، `calibration_version`
- `input_snapshot_refs`، `status`

### ForecastOutcome
- `forecast_id`، `evaluated_at`، `actual_value`
- `error_metric`، `directional_result`
- `calibration_bucket`، `evaluation_quality`

## 3. شش خانواده بازار

### Crypto
تفکیک spot، futures/perpetual و سایر بازارها الزامی است. فیلدهای اختیاری:
- bid/ask، spread، order-book depth
- funding rate، open interest، liquidation aggregates
- on-chain metrics با نام provider و روش محاسبه
- exchange و trading pair

مقادیر funding و OI بین صرافی‌ها قابل جمع ساده نیستند مگر واحد و قرارداد یکسان باشد.

### Currency
هر نرخ باید نوع نرخ داشته باشد:
- official/reference
- negotiated/market
- free-market quote
- exchange midpoint/bid/ask

نرخ‌های مختلف دلار را در یک سری واحد ترکیب نکنید. منبع، زمان و نوع نرخ همیشه کنار قیمت قابل مشاهده باشد.

### Gold
تفکیک:
- XAU spot / futures
- طلای داخلی بر حسب واحد و عیار
- سکه بهار آزادی و انواع قطعات
- نرخ ارز استفاده‌شده در محاسبه
- premium/bubble estimate

حباب سکه یک metric مشتق‌شده است و باید فرمول، وزن/عیار مرجع و timestamp ورودی‌هایش را نشان دهد.

### Iran Stock
- نماد، بازار/تابلو، صنعت، وضعیت توقف
- قیمت پایانی و آخرین معامله به‌صورت جدا
- حجم، ارزش، تعداد معاملات و محدودیت دامنه
- شاخص کل/هم‌وزن و سایر شاخص‌ها
- حقیقی/حقوقی و جریان پول فقط در صورت تعریف منبع معتبر

تقویم و ساعت معاملاتی ایران باید مستقل از timezone دستگاه باشد.

### Global Stock
- exchange، ticker، currency، session
- adjusted/unadjusted prices
- corporate actions، earnings, fundamentals
- index/ETF/equity types

داده‌های split/dividend باید به روش مشخص تعدیل شوند و داده تعدیل‌شده با خام مخلوط نشود.

### Commodities
تفکیک spot، futures و benchmark:
- contract month، expiry، roll methodology
- unit (barrel, troy ounce, metric ton, bushel...)
- quote currency
- continuous futures adjustment method

قیمت قرارداد آتی بدون ماه سررسید و روش roll نباید به‌عنوان قیمت spot معرفی شود.

## 4. قرارداد خروجی تحلیل

هر تحلیل استاندارد باید چنین ساختاری داشته باشد:

```json
{
  "analysis_id": "opaque-id",
  "instrument_id": "canonical-id",
  "as_of": "2026-11-27T12:00:00Z",
  "horizon": "1d",
  "regime": {
    "label": "range",
    "probability": 0.61,
    "method": "regime-model-v1"
  },
  "scenarios": [
    {"name": "bull", "probability": 0.28, "conditions": [], "invalidation": []},
    {"name": "base", "probability": 0.51, "conditions": [], "invalidation": []},
    {"name": "bear", "probability": 0.21, "conditions": [], "invalidation": []}
  ],
  "evidence": [],
  "counter_evidence": [],
  "risk_flags": [],
  "data_quality": {"status": "verified", "missing_fields": []},
  "model_versions": [],
  "limitations": []
}
```

اعداد داخل این نمونه ساختاری و فرضی‌اند، نه تحلیل یا پیش‌بینی بازار.

## 5. استانداردهای ارزیابی پیش‌بینی

- Direction: balanced accuracy، precision/recall و baseline comparison.
- Point forecast: MAE، RMSE و خطای نسبی با توجه به scale.
- Probabilistic forecast: pinball loss، CRPS در صورت پشتیبانی و calibration/reliability curves.
- Scenario probabilities: Brier score و calibration.
- Trading strategy backtest: net return، max drawdown، turnover، exposure، Sharpe/Sortino با توضیح فرض‌ها و benchmark.
- نتایج باید بر اساس market regime، asset class و horizon تفکیک شوند.
- داده آموزش، validation و test زمانی جدا باشد. Walk-forward لازم است.
- survivorship bias، look-ahead bias، leakage، corporate actions، slippage، spread و fee باید بررسی شوند.
- عملکرد گذشته تضمین‌کننده نتیجه آینده نیست.

## 6. نگهداری و حریم خصوصی

- فقط داده بازار عمومی/دارای مجوز و metadata لازم نگهداری شود.
- متن چت و رسانه کاربر به event analytics وارد نشود.
- برای گزارش مصرف، feature/model/unit/usage/cost/status کافی است.
- retention برای raw data تابع مجوز provider و سیاست حذف باشد.
- هر snapshot قابل ردیابی باید شناسه نسخه و زمان داشته باشد.
