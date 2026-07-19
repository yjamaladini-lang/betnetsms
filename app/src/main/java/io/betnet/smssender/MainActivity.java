package io.betnet.smssender;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.ComponentName;
import android.service.notification.NotificationListenerService;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.provider.Telephony;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class MainActivity extends Activity {
    private Switch switchEnabled;
    private EditText inputWebhook, inputSenderFilter, inputTextFilter, inputRetryCount, inputRetrySeconds;
    private EditText inputManualSender, inputManualMessage;
    private TextView textStatus, textSelectedApps, dashboardStatus, dashboardAppsText, dashboardLatestSender, dashboardLatestMessage, dashboardLatestResult;
    private LinearLayout historyContainer, sectionDashboard, sectionWebhook, sectionSettings, sectionHistory, sectionManual, dashboardLatestCard;
    private boolean accessDialogVisible = false;
    private HistoryDb historyDb;
    private Set<String> selectedPackages = new HashSet<>();
    private final List<AppChoice> appChoices = new ArrayList<>();

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        historyDb = new HistoryDb(this);

        switchEnabled = findViewById(R.id.switchEnabled);
        inputWebhook = findViewById(R.id.inputWebhook);
        inputSenderFilter = findViewById(R.id.inputSenderFilter);
        inputTextFilter = findViewById(R.id.inputTextFilter);
        inputRetryCount = findViewById(R.id.inputRetryCount);
        inputRetrySeconds = findViewById(R.id.inputRetrySeconds);
        inputManualSender = findViewById(R.id.inputManualSender);
        inputManualMessage = findViewById(R.id.inputManualMessage);
        textStatus = findViewById(R.id.textStatus);
        textSelectedApps = findViewById(R.id.textSelectedApps);
        historyContainer = findViewById(R.id.historyContainer);
        sectionDashboard = findViewById(R.id.sectionDashboard);
        sectionWebhook = findViewById(R.id.sectionWebhook);
        sectionSettings = findViewById(R.id.sectionSettings);
        sectionHistory = findViewById(R.id.sectionHistory);
        sectionManual = findViewById(R.id.sectionManual);
        dashboardStatus = findViewById(R.id.dashboardStatus);
        dashboardAppsText = findViewById(R.id.dashboardAppsText);
        dashboardLatestCard = findViewById(R.id.dashboardLatestCard);
        dashboardLatestSender = findViewById(R.id.dashboardLatestSender);
        dashboardLatestMessage = findViewById(R.id.dashboardLatestMessage);
        dashboardLatestResult = findViewById(R.id.dashboardLatestResult);

        findViewById(R.id.buttonHome).setOnClickListener(v -> showSection(sectionDashboard));
        findViewById(R.id.cardWebhook).setOnClickListener(v -> showSection(sectionWebhook));
        findViewById(R.id.cardSettings).setOnClickListener(v -> showSection(sectionSettings));
        findViewById(R.id.cardHistory).setOnClickListener(v -> { showSection(sectionHistory); refreshHistory(); });
        findViewById(R.id.cardManual).setOnClickListener(v -> showSection(sectionManual));

        findViewById(R.id.buttonSaveWebhook).setOnClickListener(v -> saveWebhook());
        findViewById(R.id.buttonSave).setOnClickListener(v -> saveSettings());
        findViewById(R.id.buttonPermission).setOnClickListener(v -> openNotificationAccess());
        findViewById(R.id.buttonStartup).setOnClickListener(v -> openStartupSettings());
        findViewById(R.id.buttonTest).setOnClickListener(v -> testWebhook());
        findViewById(R.id.buttonRefresh).setOnClickListener(v -> refreshHistory());
        findViewById(R.id.buttonApps).setOnClickListener(v -> showAppPicker());
        findViewById(R.id.buttonManualSend).setOnClickListener(v -> sendManualText());
        switchEnabled.setOnCheckedChangeListener((buttonView, isChecked) -> {
            AppPrefs.setEnabled(this, isChecked);
            if (isChecked) {
                try { NotificationListenerService.requestRebind(new ComponentName(this, SmsNotificationListener.class)); }
                catch (Exception ignored) {}
            }
            updateDashboardStatus();
            toast(isChecked ? "ارسال خودکار فعال شد." : "ارسال خودکار غیرفعال شد.");
        });

        loadCommunicationApps();
        loadSettings();
        requestNotificationPermission();
        refreshHistory();
        showSection(sectionDashboard);
        long detailId = getIntent().getLongExtra("history_id", -1L);
        if (detailId > 0) {
            showSection(sectionHistory);
            refreshHistory();
            historyContainer.post(() -> showHistoryDetail(detailId));
        }
    }

    @Override protected void onResume() {
        super.onResume();
        refreshHistory();
        updateAccessStatus();
    }

    private void showSection(View target) {
        sectionDashboard.setVisibility(target == sectionDashboard ? View.VISIBLE : View.GONE);
        sectionWebhook.setVisibility(target == sectionWebhook ? View.VISIBLE : View.GONE);
        sectionSettings.setVisibility(target == sectionSettings ? View.VISIBLE : View.GONE);
        sectionHistory.setVisibility(target == sectionHistory ? View.VISIBLE : View.GONE);
        sectionManual.setVisibility(target == sectionManual ? View.VISIBLE : View.GONE);
    }

    private void loadCommunicationApps() {
        appChoices.clear();
        PackageManager pm = getPackageManager();
        Set<String> candidates = new HashSet<>();

        try {
            String defaultSms = Telephony.Sms.getDefaultSmsPackage(this);
            if (defaultSms != null) candidates.add(defaultSms);
        } catch (Exception ignored) {}

        String[] schemes = {"smsto:", "sms:", "mms:"};
        for (String scheme : schemes) {
            Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.parse(scheme));
            for (ResolveInfo r : pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)) {
                if (r.activityInfo != null) candidates.add(r.activityInfo.packageName);
            }
        }

        String[] knownPackages = {
                "com.samsung.android.messaging", "com.google.android.apps.messaging",
                "com.android.mms", "com.android.messaging", "org.telegram.messenger",
                "org.thunderdog.challegram", "com.whatsapp", "com.whatsapp.w4b",
                "org.thoughtcrime.securesms", "com.facebook.orca", "com.instagram.android",
                "ir.eitaa.messenger", "ir.bale.app", "com.lenoshki.rubika",
                "mobi.mmdt.ottplus", "com.imo.android.imoim", "com.discord"
        };
        Collections.addAll(candidates, knownPackages);

        for (ApplicationInfo info : pm.getInstalledApplications(PackageManager.MATCH_ALL)) {
            if (info.packageName.equals(getPackageName())) continue;
            String label = pm.getApplicationLabel(info).toString();
            if (candidates.contains(info.packageName) || isCommunicationApp(info, label, candidates)) {
                try {
                    appChoices.add(new AppChoice(label, info.packageName, pm.getApplicationIcon(info)));
                } catch (Exception ignored) {}
            }
        }

        Collections.sort(appChoices, Comparator.comparing(a -> a.label.toLowerCase(Locale.ROOT)));
    }

    private boolean isCommunicationApp(ApplicationInfo info, String label, Set<String> candidates) {
        if (candidates.contains(info.packageName)) return true;
        if (Build.VERSION.SDK_INT >= 26 && info.category == ApplicationInfo.CATEGORY_SOCIAL) return true;
        String value = (info.packageName + " " + label).toLowerCase(Locale.ROOT);
        String[] keys = {"message", "messaging", "messages", "sms", "mms", "telegram", "whatsapp",
                "signal", "bale", "eitaa", "rubika", "soroush", "gap", "imo", "skype",
                "discord", "messenger", "chat", "پیام", "گفتگو"};
        for (String key : keys) if (value.contains(key)) return true;
        return false;
    }

    private void loadSettings() {
        switchEnabled.setChecked(AppPrefs.isEnabled(this));
        inputWebhook.setText(AppPrefs.getWebhook(this));
        inputSenderFilter.setText(AppPrefs.getSenderFilter(this));
        inputTextFilter.setText(AppPrefs.getTextFilter(this));
        inputRetryCount.setText(String.valueOf(AppPrefs.getRetryCount(this)));
        inputRetrySeconds.setText(String.valueOf(AppPrefs.getRetrySeconds(this)));
        selectedPackages = new HashSet<>(AppPrefs.getAllowedPackages(this));
        updateSelectedAppsText(); updateAccessStatus(); updateDashboardStatus();
    }

    private void saveWebhook() {
        String webhook = inputWebhook.getText().toString().trim();
        if (!isValidUrl(webhook)) { toast("آدرس وب‌هوک معتبر وارد کن."); return; }
        persistAll(webhook);
        toast("وب‌هوک ذخیره شد."); updateDashboardStatus();
    }

    private void saveSettings() {
        String webhook = inputWebhook.getText().toString().trim();
        if (switchEnabled.isChecked() && !isValidUrl(webhook)) { toast("اول آدرس وب‌هوک معتبر وارد کن."); return; }
        if (switchEnabled.isChecked() && selectedPackages.isEmpty()) { toast("حداقل یک برنامه ارتباطی انتخاب کن."); return; }
        persistAll(webhook);
        toast("تنظیمات ذخیره شد."); updateDashboardStatus();
    }

    private void persistAll(String webhook) {
        AppPrefs.save(this, switchEnabled.isChecked(), webhook, inputSenderFilter.getText().toString(),
                inputTextFilter.getText().toString(), selectedPackages,
                parsePositive(inputRetryCount, 5), parsePositive(inputRetrySeconds, 5));
    }

    private void showAppPicker() {
        if (appChoices.isEmpty()) { toast("برنامه ارتباطی مناسبی پیدا نشد."); return; }
        Set<String> draft = new HashSet<>(selectedPackages);
        LinearLayout list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); list.setPadding(14, 8, 14, 8); list.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        for (AppChoice app : appChoices) {
            LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(8, 10, 8, 10);
            CheckBox check = new CheckBox(this); check.setChecked(draft.contains(app.packageName));
            ImageView icon = new ImageView(this); icon.setImageDrawable(app.icon); LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(54, 54); ip.setMargins(10,0,12,0); icon.setLayoutParams(ip);
            LinearLayout texts = new LinearLayout(this); texts.setOrientation(LinearLayout.VERTICAL); texts.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
            TextView name = new TextView(this); name.setText(app.label); name.setGravity(Gravity.START); name.setTextDirection(View.TEXT_DIRECTION_RTL); name.setTextSize(16); name.setTextColor(Color.parseColor("#171717")); name.setTypeface(null, 1);
            TextView pkg = new TextView(this); pkg.setText(app.packageName); pkg.setTextSize(11); pkg.setTextColor(Color.parseColor("#6B7280")); pkg.setTextDirection(View.TEXT_DIRECTION_LTR);
            texts.addView(name); texts.addView(pkg); row.addView(check); row.addView(icon); row.addView(texts);
            View.OnClickListener toggle = v -> { check.setChecked(!check.isChecked()); if(check.isChecked()) draft.add(app.packageName); else draft.remove(app.packageName); };
            row.setOnClickListener(toggle);
            check.setOnCheckedChangeListener((b, isChecked) -> { if(isChecked) draft.add(app.packageName); else draft.remove(app.packageName); });
            list.addView(row);
        }
        ScrollView scroll = new ScrollView(this); scroll.addView(list);
        new AlertDialog.Builder(this).setTitle("برنامه‌های ارتباطی مجاز")
                .setView(scroll).setPositiveButton("تأیید", (d,w) -> { selectedPackages = draft; updateSelectedAppsText(); })
                .setNegativeButton("لغو", null).show();
    }

    private void updateSelectedAppsText() {
        if (selectedPackages.isEmpty()) {
            textSelectedApps.setText("هیچ برنامه‌ای انتخاب نشده است.");
            if (dashboardAppsText != null) dashboardAppsText.setText("هیچ برنامه‌ای انتخاب نشده است");
            return;
        }
        List<String> names = new ArrayList<>();
        for (AppChoice a : appChoices) if (selectedPackages.contains(a.packageName)) names.add(a.label);
        String joined = TextUtils.join("، ", names);
        textSelectedApps.setText("انتخاب‌شده: " + joined);
        if (dashboardAppsText != null) dashboardAppsText.setText(joined);
    }

    private void openNotificationAccess() {
        try { startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)); }
        catch (Exception e) { startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")); }
    }

    private void openStartupSettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
            return;
        } catch (Exception ignored) {}
        try {
            Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
            startActivity(intent);
            return;
        } catch (Exception ignored) {}
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (Exception e) {
            toast("تنظیمات اجرای پس‌زمینه در این گوشی پیدا نشد.");
        }
    }

    private void updateAccessStatus() {
        boolean enabled = isNotificationAccessEnabled();
        if (textStatus != null) textStatus.setText(enabled ? "دسترسی اعلان‌ها فعال است." : "دسترسی اعلان‌ها هنوز فعال نشده است.");
        updateDashboardStatus();
    }

    private void updateDashboardStatus() {
        if (dashboardStatus == null) return;
        boolean hasAccess = isNotificationAccessEnabled();
        boolean hasWebhook = isValidUrl(AppPrefs.getWebhook(this));
        boolean autoEnabled = AppPrefs.isEnabled(this);
        boolean hasApps = !AppPrefs.getAllowedPackages(this).isEmpty();
        boolean ready = hasAccess && hasWebhook && autoEnabled && hasApps;

        if (ready) {
            dashboardStatus.setText("●  برنامه برای اجرای عملیات مسلح است");
            dashboardStatus.setTextColor(Color.WHITE);
        } else if (!autoEnabled) {
            dashboardStatus.setText("●  ارسال خودکار غیرفعال است");
            dashboardStatus.setTextColor(Color.parseColor("#FDE68A"));
        } else if (!hasApps) {
            dashboardStatus.setText("●  برنامه پیامک در تنظیمات انتخاب نشده است");
            dashboardStatus.setTextColor(Color.parseColor("#FDE68A"));
        } else if (!hasAccess && !hasWebhook) {
            dashboardStatus.setText("●  دسترسی اعلان و وب‌هوک تنظیم نشده است");
            dashboardStatus.setTextColor(Color.parseColor("#FDE68A"));
        } else if (!hasAccess) {
            dashboardStatus.setText("●  دسترسی اعلان‌ها باید فعال شود");
            dashboardStatus.setTextColor(Color.parseColor("#FDE68A"));
        } else {
            dashboardStatus.setText("●  آدرس وب‌هوک باید تنظیم شود");
            dashboardStatus.setTextColor(Color.parseColor("#FDE68A"));
        }
    }

    private void enforceRequiredAccess() {
        if (isNotificationAccessEnabled() || accessDialogVisible || isFinishing()) return;
        accessDialogVisible = true;
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("دسترسی اعلان‌ها ضروری است")
                .setMessage("برای شناسایی پیامک‌ها از اعلان برنامه پیامک، باید دسترسی خواندن اعلان‌ها را فعال کنی. بدون این دسترسی برنامه قادر به کار نیست.")
                .setCancelable(false)
                .setPositiveButton("فعال‌کردن دسترسی", (d, w) -> {
                    accessDialogVisible = false;
                    openNotificationAccess();
                })
                .setNegativeButton("خروج", (d, w) -> {
                    accessDialogVisible = false;
                    finish();
                })
                .create();
        dialog.setOnDismissListener(d -> accessDialogVisible = false);
        dialog.show();
    }
    private boolean isNotificationAccessEnabled() { String enabled=Settings.Secure.getString(getContentResolver(),"enabled_notification_listeners"); return enabled!=null&&enabled.contains(getPackageName()); }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1201);
    }

    private void testWebhook() {
        String webhook=inputWebhook.getText().toString().trim(); if(!isValidUrl(webhook)){toast("اول آدرس وب‌هوک معتبر وارد کن.");return;}
        persistAll(webhook); long ts=System.currentTimeMillis(); String msg="پیام آزمایشی Betnet SMS Sender - مبلغ: 12500000 ریال";
        long id=historyDb.insertPending(ts,"BETNET-TEST",msg,getPackageName()); textStatus.setText("در حال ارسال تست...");
        WebhookSender.sendWithRetry(this,webhook,"BETNET-TEST",msg,getPackageName(),ts,true,id,
                parsePositive(inputRetryCount,5),parsePositive(inputRetrySeconds,5),(success,code,response)->runOnUiThread(()->{textStatus.setText((success?"تست موفق":"تست ناموفق")+" | HTTP "+code+" | "+shorten(response,220));refreshHistory();}));
    }

    private void sendManualText() {
        String webhook = inputWebhook.getText().toString().trim();
        String sender = inputManualSender.getText().toString().trim();
        String message = inputManualMessage.getText().toString().trim();
        if (!isValidUrl(webhook)) { toast("اول وب‌هوک معتبر ذخیره کن."); showSection(sectionWebhook); return; }
        if (message.isEmpty()) { toast("متن پیام را وارد کن."); return; }
        if (sender.isEmpty()) sender = "MANUAL";
        persistAll(webhook);
        long ts = System.currentTimeMillis();
        long id = historyDb.insertPending(ts, sender, message, getPackageName());
        final String finalSender = sender;
        toast("ارسال دستی شروع شد.");
        WebhookSender.sendWithRetry(this, webhook, finalSender, message, getPackageName(), ts, false, id,
                AppPrefs.getRetryCount(this), AppPrefs.getRetrySeconds(this),
                (s,c,r) -> runOnUiThread(() -> { refreshHistory(); toast(s ? "ارسال دستی موفق بود." : "ارسال دستی ناموفق بود."); }));
    }

    private void refreshHistory() {
        if(historyContainer==null)return; historyContainer.removeAllViews(); List<HistoryDb.HistoryItem> items=historyDb.latest(80);
        updateLatestDashboard(items);
        if(items.isEmpty()){TextView e=new TextView(this);e.setText("هنوز پیامی ثبت نشده است.");e.setTextColor(Color.parseColor("#6B7280"));e.setPadding(8,18,8,18);historyContainer.addView(e);return;}
        for(HistoryDb.HistoryItem item:items){
            LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(24,20,24,20);
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,0,0,14);card.setLayoutParams(p);card.setBackgroundResource(R.drawable.bg_card);card.setElevation(2f);card.setClickable(true);
            TextView title=new TextView(this);title.setGravity(Gravity.START);title.setTextDirection(View.TEXT_DIRECTION_RTL);title.setText(item.sender+"  •  "+JalaliDate.format(item.createdAt));title.setTextColor(Color.parseColor("#171717"));title.setTextSize(16);title.setTypeface(null,1);
            TextView msg=new TextView(this);msg.setGravity(Gravity.START);msg.setTextDirection(View.TEXT_DIRECTION_RTL);msg.setText(shorten(item.message,300));msg.setTextColor(Color.parseColor("#374151"));msg.setTextSize(14);msg.setPadding(0,8,0,8);
            TextView result=new TextView(this);String st;int color;if("sent".equals(item.status)){st="ارسال شد";color=Color.parseColor("#16803C");}else if("failed".equals(item.status)){st="ناموفق";color=Color.parseColor("#C62828");}else{st="در حال تلاش";color=Color.parseColor("#B26A00");}
            result.setText(st+" | تلاش‌ها: "+item.attemptCount+" | HTTP "+item.httpCode);result.setTextColor(color);result.setTextSize(13);result.setGravity(Gravity.START);
            card.addView(title);card.addView(msg);card.addView(result);card.setOnClickListener(v->showHistoryDetail(item.id));historyContainer.addView(card);
        }
    }

    private void updateLatestDashboard(List<HistoryDb.HistoryItem> items) {
        if (dashboardLatestCard == null) return;
        if (items == null || items.isEmpty()) {
            dashboardLatestCard.setVisibility(View.GONE);
            return;
        }
        HistoryDb.HistoryItem item = items.get(0);
        dashboardLatestCard.setVisibility(View.VISIBLE);
        dashboardLatestSender.setText(item.sender == null || item.sender.trim().isEmpty() ? "پیام جدید" : item.sender);
        dashboardLatestMessage.setText(shorten(item.message, 180));
        if ("sent".equals(item.status)) {
            dashboardLatestResult.setText("موفق");
            dashboardLatestResult.setTextColor(Color.parseColor("#16803C"));
        } else if ("failed".equals(item.status)) {
            dashboardLatestResult.setText("ناموفق");
            dashboardLatestResult.setTextColor(Color.parseColor("#C62828"));
        } else {
            dashboardLatestResult.setText("در حال تلاش");
            dashboardLatestResult.setTextColor(Color.parseColor("#B26A00"));
        }
        dashboardLatestCard.setOnClickListener(v -> showHistoryDetail(item.id));
    }

    private void showHistoryDetail(long id) {
        HistoryDb.HistoryItem item=historyDb.get(id); if(item==null)return; List<HistoryDb.AttemptItem> attempts=historyDb.attempts(id);
        StringBuilder b=new StringBuilder();
        b.append("فرستنده: ").append(item.sender).append("\nبرنامه: ").append(getAppLabel(item.packageName)).append("\nزمان شناسایی: ").append(JalaliDate.format(item.createdAt)).append("\n\nمتن پیام:\n").append(item.message).append("\n\nتاریخچه تلاش‌ها:\n");
        if(attempts.isEmpty())b.append("هنوز تلاشی ثبت نشده.");
        for(HistoryDb.AttemptItem a:attempts)b.append("\nتلاش ").append(a.attemptNo).append(" • ").append(JalaliDate.format(a.createdAt)).append("\n").append("sent".equals(a.status)?"موفق":"ناموفق").append(" • HTTP ").append(a.httpCode).append("\nپاسخ: ").append(a.response).append("\n");
        new AlertDialog.Builder(this).setTitle("جزئیات پیام و ارسال").setMessage(b.toString()).setPositiveButton("بستن",null)
                .setNeutralButton("ارسال مجدد",(d,w)->manualResend(item)).show();
    }

    private String getAppLabel(String packageName) {
        try { return getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(packageName,0)).toString(); }
        catch (Exception e) { return packageName; }
    }

    private void manualResend(HistoryDb.HistoryItem item) {
        String webhook=AppPrefs.getWebhook(this);if(!isValidUrl(webhook)){toast("آدرس وب‌هوک در تنظیمات معتبر نیست.");return;}
        long ts=System.currentTimeMillis();long newId=historyDb.insertPending(ts,item.sender,item.message,item.packageName);
        toast("ارسال مجدد شروع شد.");
        WebhookSender.sendWithRetry(this,webhook,item.sender,item.message,item.packageName,ts,false,newId,AppPrefs.getRetryCount(this),AppPrefs.getRetrySeconds(this),(s,c,r)->runOnUiThread(this::refreshHistory));
    }

    private int parsePositive(EditText edit,int fallback){try{return Math.max(1,Integer.parseInt(edit.getText().toString().trim()));}catch(Exception e){return fallback;}}
    private boolean isValidUrl(String v){return !TextUtils.isEmpty(v)&&(v.startsWith("https://")||v.startsWith("http://"));}
    private String shorten(String v,int max){if(v==null)return"";String c=v.trim();return c.length()<=max?c:c.substring(0,max)+"…";}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    private static final class AppChoice { final String label,packageName; final Drawable icon; AppChoice(String l,String p,Drawable i){label=l;packageName=p;icon=i;} }
}
