# سرویس حساب کاربری Naeem AI

ورود برنامه بر پایه **Supabase Auth با ایمیل و رمز عبور** است. ورود با شماره موبایل و کد پیامکی بازنشسته شده و اپ اندروید مستقیماً از Supabase Auth استفاده می‌کند.

## تنظیمات Supabase

- آدرس پروژه: `https://ifloyzpvgjgmneqgcggu.supabase.co`
- اپ از کلید Publishable استفاده می‌کند؛ کلید Service Role هرگز نباید داخل اپ یا مخزن قرار بگیرد.
- در Supabase Dashboard بخش Authentication، ثبت‌نام با Email را فعال کنید.
- برای فعال‌بودن تأیید ایمیل، Email provider و قالب تأیید را تنظیم کنید. در این حالت کاربر بعد از ثبت‌نام باید ایمیلش را تأیید کند و سپس وارد شود.
- برای بازیابی رمز، قالب Reset Password و نشانی Redirect مجاز را در تنظیمات Auth تنظیم کنید.

## احراز هویت API

اپ با این مسیرهای رسمی Supabase کار می‌کند:

- `POST /auth/v1/signup` برای ثبت‌نام ایمیل و رمز عبور
- `POST /auth/v1/token?grant_type=password` برای ورود
- `POST /auth/v1/token?grant_type=refresh_token` برای تمدید نشست
- `POST /auth/v1/recover` برای ارسال لینک بازیابی رمز
- `POST /auth/v1/logout` برای خروج

نشست و توکن‌ها در حافظه رمزگذاری‌شده اندروید نگه‌داری می‌شوند. هیچ رمز عبوری در برنامه ذخیره نمی‌شود.

## اتصال Worker به هویت Supabase

Worker برای APIهای کیف پول و حساب، توکن Bearer را با درخواست `/auth/v1/user` در Supabase اعتبارسنجی می‌کند. تنظیمات Worker در `wrangler.toml` شامل آدرس پروژه و کلید Publishable است. کلید Publishable محرمانه نیست؛ کلیدهای مدیریتی سرویس‌دهنده باید فقط به‌صورت Cloudflare Secret نگه‌داری شوند.

## پایگاه داده D1

برای پایگاه داده‌ای که قبلاً با `schema.sql` و مهاجرت کیف پول ساخته شده، مهاجرت جدید را اجرا کنید:

```bash
npx wrangler d1 execute naeem-ai-auth --remote --file=./migrations/0003_supabase_email_auth.sql
```

این مهاجرت ستون ایمیل و ایندکس یکتا را اضافه می‌کند و اطلاعات کیف پول و حساب‌های قبلی را حذف نمی‌کند. کاربران قدیمی شماره‌موبایلی به‌صورت خودکار به حساب Supabase جدید متصل نمی‌شوند؛ اتصال سوابق قدیمی به ایمیل نیازمند تطبیق و مهاجرت کنترل‌شده است.

## تنظیم مدیر

ایمیل مدیر را در متغیر `ADMIN_EMAILS` در `wrangler.toml` وارد کنید؛ برای چند مدیر از کاما استفاده کنید. سپس Worker را منتشر کنید:

```bash
npx wrangler deploy
```

## بازنشستگی ورود پیامکی

مسیرهای قدیمی `/v1/auth/otp/request` و `/v1/auth/otp/verify` پاسخ `410` می‌دهند. برای احراز هویت فقط توکن Supabase پذیرفته می‌شود.

## درگاه امن چت ایما (نسخه آزمایشی)

درگاه جدید مسیر چت متنی و تحلیل تصویر در گفتگو را از برنامه به Worker منتقل می‌کند. کلیدهای سرویس‌دهنده فقط در Cloudflare Secrets قرار می‌گیرند و داخل APK قرار نمی‌گیرند. در صورت خطای شبکه، پاسخ 429 یا خطای 5xx در سرویس اصلی، Worker سرویس پشتیبان را امتحان می‌کند؛ خطاهای احراز هویت و درخواست نامعتبر باعث جابه‌جایی بی‌مورد نمی‌شوند.

### مهاجرت سهمیه رایگان

پس از اطمینان از اتصال D1، مهاجرت را اجرا کنید:

```bash
npx wrangler d1 execute naeem-ai-auth --remote --file=./migrations/0004_chat_gateway_quota.sql
```

سهمیه پیش‌فرض ۵۰ درخواست چت در ماه برای هر حساب است و با متغیر `CHAT_FREE_MONTHLY_REQUESTS` قابل تنظیم است. این سهمیه فعلاً رایگان است؛ پرداخت و شارژ کیف پول هنوز به درگاه چت متصل نشده و نباید به کاربر وعده خرید اعتبار فعال داده شود.

### تنظیم کلیدهای سرویس‌دهنده در Cloudflare

از مسیر Cloudflare Worker → Settings → Variables and Secrets، این مقادیر را به‌صورت **Secrets** اضافه کنید:

- `CHAT_PRIMARY_API_KEY`: کلید اصلی NetArz
- `CHAT_SECONDARY_API_KEY`: کلید پشتیبان GapGPT
- `CHAT_PRIMARY_BASE_URL`: مقدار پیش‌فرض `https://netarz.ir/api/ai/v1`
- `CHAT_SECONDARY_BASE_URL`: مقدار پیش‌فرض `https://api.gapgpt.ir/v1`
- `CHAT_PRIMARY_MODEL`: مقدار پیش‌فرض `gpt-4o-mini`
- `CHAT_SECONDARY_MODEL`: مقدار پیش‌فرض `gpt-4o-mini`
- `CHAT_FREE_MONTHLY_REQUESTS`: مقدار پیشنهادی `50` درخواست برای هر کاربر در ماه
- `CHAT_GLOBAL_MONTHLY_REQUESTS`: سقف کلی اولیه `5000` درخواست در ماه برای کنترل هزینه کل؛ در صورت نیاز قابل افزایش است

کلیدها را در مخزن GitHub، فایل wrangler.toml یا کد اندروید قرار ندهید. پس از تنظیم Secretها، Worker را با `npx wrangler deploy` منتشر کنید.

### تنظیم نشانی Worker برای APK

پس از انتشار Worker، نشانی HTTPS آن را در GitHub Repository → Settings → Secrets and variables → Actions → Variables با نام `CHAT_GATEWAY_URL` ثبت کنید. مقدار باید فقط ریشه نشانی Worker باشد، برای مثال `https://<worker-name>.<account-subdomain>.workers.dev` و نباید مسیر `/v1/chat/completions` داشته باشد.

سپس workflow اندروید را اجرا کنید تا نشانی عمومی در BuildConfig نسخه APK قرار بگیرد. اگر این متغیر تنظیم نشده باشد، APK به مسیر قدیمی کلید API روی دستگاه برمی‌گردد؛ بنابراین تا زمان تنظیم نشانی Worker و کلیدهای سرور، این نسخه را برای انتشار عمومی آماده تلقی نکنید.

### محدودیت‌های فعلی

- این درگاه فقط چت متنی و پیام تصویری در خود چت را پوشش می‌دهد؛ تصویرساز مستقل، ویدیو و تبدیل متن به گفتار همچنان تنظیمات جداگانه دارند.
- سهمیه فعلی بر اساس تعداد درخواست است، نه تعداد توکن یا هزینه واقعی.
- شارژ کیف پول، درگاه پرداخت، صورتحساب و پنل نمایش سهمیه در برنامه هنوز پیاده‌سازی نشده‌اند.
- Failover فقط برای خطای شبکه، HTTP 429 و HTTP 5xx انجام می‌شود. خطاهای 4xx دیگر به همان شکل به برنامه بازگردانده می‌شوند.
