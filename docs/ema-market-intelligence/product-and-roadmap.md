# تجربه کاربری، مدل درآمد و نقشه اجرای نبض بازار

## 1. ساختار تجربه کاربری

ورودی اصلی از فضای برنامه EMA با عنوان «نبض بازار» باز می‌شود.

### تب اول: داشبورد

- Market Pulse: وضعیت کلی و regime هر بازار؛ فقط وقتی داده کافی است.
- شش کارت بازار با آخرین زمان داده و برچسب زنده/تأخیری/قدیمی.
- نقشه حرارتی عملکرد دارایی‌ها.
- جریان سرمایه و روابط بین بازارها، در صورت وجود داده قابل مقایسه.
- خلاصه هوشمند روز با سه بخش: چه تغییر کرد، چرا مهم است، چه چیزی را زیر نظر بگیریم.
- رویدادهای مهم اقتصادی و شرکتی.

### تب دوم: بازارها

- شش دسته اصلی بازار.
- جست‌وجوی دارایی و watchlist.
- صفحه دارایی: quote، نمودار، حجم، شاخص‌ها، خبرها، تحلیل چندافقی و ریسک.
- انتخاب بازه زمانی، مقایسه دارایی‌ها و overlay شاخص‌ها.
- source badge و data freshness همیشه قابل مشاهده.

### تب سوم: رادار

- ناهنجاری حجم و نوسان.
- تغییر regime.
- divergence بین قیمت و sentiment.
- تغییر همبستگی و روابط cross-market.
- liquidity/derivatives signals فقط برای بازار دارای داده.
- هر هشدار با علت، شدت، شواهد، timestamp، منبع و شرایط ابطال.

### تب چهارم: آزمایشگاه

- Market DNA و جست‌وجوی دوره تاریخی مشابه.
- Market Time Machine.
- Digital Twin و سناریوهای What-if.
- AI Analyst Arena.
- Backtesting و Prediction Scorecard.
- ابزارهای حرفه‌ای با محدودیت مصرف و نمایش هزینه پیش از اجرا.

## 2. صفحه تحلیل یک دارایی

ترتیب پیشنهادی:
1. نام دارایی، بازار، قیمت و زمان/منبع داده.
2. وضعیت کیفیت داده و session.
3. نمودار قیمت و حجم با انتخاب timeframe.
4. وضعیت روند و رژیم بازار.
5. شواهد تکنیکال و بنیادی.
6. جریان سرمایه و روابط مرتبط.
7. سناریوهای صعودی، پایه و نزولی.
8. عوامل ابطال و ریسک.
9. اختلاف نظر موتورهای تحلیلی.
10. تاریخچه تحلیل‌ها و Prediction Scorecard.

هر خروجی باید تفاوت «واقعیت مشاهده‌شده»، «محاسبه»، «تفسیر مدل» و «سناریوی فرضی» را با برچسب بصری نشان دهد.

## 3. نمودارهای ویژه

- Market heatmap: رنگ‌بندی بر اساس بازده/نوسان، با انتخاب universe و timeframe.
- Correlation network: گره دارایی، یال همبستگی، وزن و بازه محاسبه.
- Sankey/capital flow: فقط اگر جریان سرمایه واقعاً اندازه‌گیری شده باشد؛ در غیر این صورت از واژه «جریان پول» استفاده نشود.
- Liquidity heatmap: فقط با داده عمق بازار/لیکوییدیشن معتبر.
- Forecast fan chart: quantile bands و سناریوها؛ نه یک مسیر قطعی.
- Regime timeline: تغییر حالت بازار و احتمال هر رژیم.
- Market DNA map: نمایش دوره‌های مشابه و فاصله ویژگی‌ها.
- Digital Twin sensitivity chart: اثر تغییر فرض‌ها بر خروجی سناریو.
- Prediction reliability chart: کالیبراسیون و خطا بر اساس افق و رژیم.

نمودارهای سه‌بعدی صرفاً وقتی استفاده شوند که بعد سوم اطلاعات قابل فهم و مفید داشته باشد؛ تزئین سه‌بعدی نباید خوانایی و دقت را کم کند.

## 4. لحن و نمایش تحلیل

قالب گزارش:
- **خلاصه:** یک پاراگراف کوتاه و بدون اغراق.
- **شواهد:** داده و metric با زمان و منبع.
- **سناریوها:** صعودی/پایه/نزولی با احتمال و شروط.
- **مخالفان تحلیل:** شواهدی که نتیجه را تضعیف می‌کنند.
- **ریسک و ابطال:** چه چیزی این تحلیل را نامعتبر می‌کند.
- **کیفیت:** کیفیت داده، تاریخچه و مدل.
- **محدودیت:** شکاف داده یا نبود اطلاعات.

از عبارات قطعی مانند «حتماً رشد می‌کند»، «سود تضمینی» یا «سیگنال قطعی» استفاده نشود. این محصول تحلیل اطلاعاتی است، نه تضمین بازده یا جایگزین مشاور مالی مجاز.

## 5. مدل درآمدی

با مدل کیف پول EMA:
- سطح رایگان دائمی: قیمت‌ها/نمودارهای پایه و سهمیه محدود گزارش ساده.
- تحلیل استاندارد: مصرف اعتبار برای هر گزارش.
- تحلیل حرفه‌ای چندبازاری: مصرف بیشتر بر اساس تعداد دارایی، ماژول و افق.
- Simulation/Backtest/Arena: محاسبه هزینه بر اساس compute budget و مدل‌های استفاده‌شده.

قبل از اجرا باید قیمت تخمینی، سقف قطعی رزرو، واحد مصرف و موجودی bonus/purchased نشان داده شود. Bonus و Purchased جدا باقی می‌مانند و free quota پول نیست.

هیچ قابلیت پولی تا زمانی که هزینه داده، هزینه مدل، هزینه محاسبات، FX و margin مشخص و server-side فعال نشده، قابل خرید نیست.

## 6. نقشه پیاده‌سازی

### فاز 0 — تأیید منابع و قراردادها
Deliverables:
- provider matrix برای هر شش بازار؛
- بررسی API رسمی، مجوز بازنشر/ذخیره، نرخ، پوشش ایران و SLA؛
- instrument taxonomy و calendar;
- تصمیم درباره storage و هزینه.

Exit criteria:
- برای هر بازار حداقل یک منبع قابل استفاده و مسیر fallback یا اعلام عدم دسترسی مشخص باشد.
- منبع با مجوز نامشخص وارد production نشود.

### فاز 1 — Data foundation
Deliverables:
- migration افزایشی بازار؛
- instrument registry؛
- provider adapter interface؛
- ingestion queue؛
- quote/candle normalization؛
- data-quality endpoints؛
- cache و provider health.

Exit criteria:
- unit/integration tests برای mapping، timezone، stale data، market close و duplicate observations.
- هیچ مقدار stale با برچسب live نمایش داده نشود.

### فاز 2 — Market UI و نمودارهای پایه
Deliverables:
- dashboard؛
- market selector؛
- instrument search؛
- asset detail؛
- candles/volume/heatmap؛
- source/freshness labels.

Exit criteria:
- loading/empty/error/offline states؛
- نمودار با timestamp و currency درست؛
- accessibility و عملکرد روی دستگاه میان‌رده.

### فاز 3 — Quant engine
Deliverables:
- technical indicators؛
- regime detection؛
- volatility/risk metrics؛
- correlation matrix؛
- anomaly radar؛
- cross-market dashboard.

Exit criteria:
- deterministic fixtures؛
- مقایسه با محاسبات مرجع؛
- versioning و reproducibility؛
- missing data handling.

### فاز 4 — AI analysis
Deliverables:
- evidence pack؛
- schema-constrained report generation؛
- bull/base/bear scenarios؛
- analyst arena؛
- news/sentiment integration با منبع و deduplication.

Exit criteria:
- اعداد خروجی دقیقاً از engine باشند؛
- هر claim به evidence reference متصل باشد؛
- LLM در نبود داده کافی abstain کند.

### فاز 5 — Forecast lab
Deliverables:
- historical regime similarity؛
- walk-forward forecasts؛
- backtest engine؛
- prediction scorecard؛
- time machine؛
- digital twin.

Exit criteria:
- baseline comparison؛
- leakage tests؛
- هزینه معاملات و bias controls؛
- calibration و out-of-sample evaluation.

### فاز 6 — Wallet and commercial launch
Deliverables:
- قیمت‌گذاری feature/model/unit؛
- estimate/maximum reservation؛
- source-aware wallet reservation/settlement/refund؛
- free quota؛
- usage analytics؛
- admin pricing controls.

Exit criteria:
- concurrency/idempotency tests؛
- failure release/refund tests؛
- هیچ operation پولی بدون price فعال اجرا نشود؛
- گزارش مالی bonus و purchased مستقل باشد.

### فاز 7 — Hardening and release
- security review، provider terms review و privacy review؛
- load/performance tests؛
- alerting و incident runbooks؛
- Android QA؛
- staged rollout و rollback plan.

## 7. اولویت واقعی برای ساخت

ترتیب توصیه‌شده:
1. منبع داده و کیفیت آن؛
2. داده استاندارد و نمودار قابل اعتماد؛
3. تحلیل‌های کمی و رادار ناهنجاری؛
4. گزارش AI مستند؛
5. پیش‌بینی با کارنامه؛
6. قابلیت‌های نمایشی و سنگین Digital Twin/Arena؛
7. صورتحساب و عرضه تجاری.

نباید اول سراغ مدل زبانی یا نمودارهای عجیب برویم و بعد دنبال داده بگردیم. مزیت پایدار ایما از داده معتبر و سنجش عملکرد تحلیل‌ها می‌آید.

## 8. Test matrix

### Data
- malformed provider payload؛
- duplicate/out-of-order observations؛
- stale quote؛
- timezone and DST boundary؛
- exchange holiday/market close؛
- currency/unit mismatch؛
- adjusted vs unadjusted price؛
- missing candle intervals.

### Analytics
- known indicator fixtures؛
- zero-volume and outlier data؛
- correlation with missing values؛
- regime change simulation؛
- no-look-ahead checks؛
- forecast calibration fixtures؛
- backtest fee/slippage checks.

### API/security
- unauthorized and cross-user access؛
- admin permission enforcement؛
- rate-limit and oversized range؛
- provider timeout/circuit breaker؛
- job retry/idempotency؛
- queue exhaustion؛
- secret redaction.

### Billing
- estimate mismatch؛
- insufficient funds؛
- free quota exhaustion؛
- concurrent reservation؛
- provider failure release؛
- partial settlement؛
- duplicate callback؛
- unknown usage reconciliation؛
- bonus/purchased bucket refund.

### UX
- live/delayed/stale/unavailable labels؛
- empty state؛
- provider outage؛
- partial market availability؛
- low-end Android memory and rendering؛
- RTL Persian and English labels.
