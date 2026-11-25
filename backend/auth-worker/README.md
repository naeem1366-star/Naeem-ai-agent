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
