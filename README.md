# Betnet SMS Sender

برنامه اختصاصی ارسال متن اعلان پیامک به Webhook.

## امکانات

- دریافت متن اعلان‌ها با Notification Access
- فیلتر عنوان/فرستنده با چند عبارت جداشده با ویرگول
- فیلتر متن پیام با چند عبارت جداشده با ویرگول
- ارسال JSON با POST
- تست Webhook
- تاریخچه ۲۰۰ پیام آخر
- نمایش وضعیت ارسال، HTTP Code و پاسخ سرور
- بدون دسترسی مستقیم SMS و بدون قابلیت OTP

## JSON ارسالی

```json
{
  "sender": "Bank",
  "message": "متن اعلان پیامک",
  "timestamp": 1784420000000,
  "package_name": "com.google.android.apps.messaging",
  "source": "betnet-sms-sender",
  "test": false
}
```

## ساخت APK در GitHub

پس از Push، Workflow با نام `Build Betnet APK` خودکار اجرا می‌شود. پس از سبزشدن Build، فایل APK از بخش Artifacts قابل دریافت است.

## نکته امنیتی

پیشنهاد می‌شود Webhook فقط با HTTPS و یک Token محرمانه استفاده شود.
