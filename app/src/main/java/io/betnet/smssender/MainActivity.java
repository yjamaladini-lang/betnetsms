package io.betnet.smssender;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class MainActivity extends Activity {
    private Switch switchEnabled;
    private EditText inputWebhook, inputSenderFilter, inputTextFilter, inputRetryCount, inputRetrySeconds;
    private TextView textStatus, textSelectedApps;
    private LinearLayout historyContainer;
    private HistoryDb historyDb;
    private Set<String> selectedPackages = new HashSet<>();
    private List<AppChoice> appChoices = new ArrayList<>();

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
        textStatus = findViewById(R.id.textStatus);
        textSelectedApps = findViewById(R.id.textSelectedApps);
        historyContainer = findViewById(R.id.historyContainer);

        findViewById(R.id.buttonSave).setOnClickListener(v -> saveSettings());
        findViewById(R.id.buttonPermission).setOnClickListener(v -> openNotificationAccess());
        findViewById(R.id.buttonTest).setOnClickListener(v -> testWebhook());
        findViewById(R.id.buttonRefresh).setOnClickListener(v -> refreshHistory());
        findViewById(R.id.buttonApps).setOnClickListener(v -> showAppPicker());

        loadApps(); loadSettings(); requestNotificationPermission(); refreshHistory();
    }

    @Override protected void onResume() { super.onResume(); refreshHistory(); updateAccessStatus(); }

    private void loadApps() {
        PackageManager pm = getPackageManager();
        List<ApplicationInfo> apps = pm.getInstalledApplications(0);
        for (ApplicationInfo info : apps) {
            if (info.packageName.equals(getPackageName())) continue;
            if (pm.getLaunchIntentForPackage(info.packageName) == null) continue;
            appChoices.add(new AppChoice(pm.getApplicationLabel(info).toString(), info.packageName));
        }
        Collections.sort(appChoices, Comparator.comparing(a -> a.label.toLowerCase(Locale.ROOT)));
    }

    private void loadSettings() {
        switchEnabled.setChecked(AppPrefs.isEnabled(this));
        inputWebhook.setText(AppPrefs.getWebhook(this));
        inputSenderFilter.setText(AppPrefs.getSenderFilter(this));
        inputTextFilter.setText(AppPrefs.getTextFilter(this));
        inputRetryCount.setText(String.valueOf(AppPrefs.getRetryCount(this)));
        inputRetrySeconds.setText(String.valueOf(AppPrefs.getRetrySeconds(this)));
        selectedPackages = new HashSet<>(AppPrefs.getAllowedPackages(this));
        updateSelectedAppsText(); updateAccessStatus();
    }

    private void saveSettings() {
        String webhook = inputWebhook.getText().toString().trim();
        if (switchEnabled.isChecked() && !isValidUrl(webhook)) { toast("آدرس وب‌هوک معتبر وارد کن."); return; }
        if (switchEnabled.isChecked() && selectedPackages.isEmpty()) { toast("حداقل یک برنامه برای خواندن اعلان انتخاب کن."); return; }
        int retryCount = parsePositive(inputRetryCount, 5);
        int retrySeconds = parsePositive(inputRetrySeconds, 5);
        AppPrefs.save(this, switchEnabled.isChecked(), webhook, inputSenderFilter.getText().toString(),
                inputTextFilter.getText().toString(), selectedPackages, retryCount, retrySeconds);
        toast("تنظیمات ذخیره شد.");
    }

    private void showAppPicker() {
        if (appChoices.isEmpty()) { toast("برنامه‌ای برای انتخاب پیدا نشد."); return; }
        String[] labels = new String[appChoices.size()]; boolean[] checked = new boolean[appChoices.size()];
        for (int i=0;i<appChoices.size();i++){ labels[i] = appChoices.get(i).label + "\n" + appChoices.get(i).packageName; checked[i] = selectedPackages.contains(appChoices.get(i).packageName); }
        Set<String> draft = new HashSet<>(selectedPackages);
        new AlertDialog.Builder(this).setTitle("اعلان کدام برنامه‌ها خوانده شود؟")
                .setMultiChoiceItems(labels, checked, (d, which, isChecked) -> { String p=appChoices.get(which).packageName; if(isChecked) draft.add(p); else draft.remove(p); })
                .setPositiveButton("تأیید", (d,w) -> { selectedPackages = draft; updateSelectedAppsText(); })
                .setNegativeButton("لغو", null).show();
    }

    private void updateSelectedAppsText() {
        if (selectedPackages.isEmpty()) { textSelectedApps.setText("هیچ برنامه‌ای انتخاب نشده؛ واتساپ و تلگرام ارسال نمی‌شوند مگر انتخابشان کنی."); return; }
        List<String> names = new ArrayList<>();
        for (AppChoice a : appChoices) if (selectedPackages.contains(a.packageName)) names.add(a.label);
        textSelectedApps.setText("برنامه‌های انتخاب‌شده: " + TextUtils.join("، ", names));
    }

    private void openNotificationAccess() {
        try { startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)); }
        catch (Exception e) { startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")); }
    }

    private void updateAccessStatus() { if(textStatus!=null) textStatus.setText(isNotificationAccessEnabled()?"دسترسی اعلان‌ها فعال است.":"دسترسی اعلان‌ها هنوز فعال نشده است."); }
    private boolean isNotificationAccessEnabled() { String enabled=Settings.Secure.getString(getContentResolver(),"enabled_notification_listeners"); return enabled!=null&&enabled.contains(getPackageName()); }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1201);
    }

    private void testWebhook() {
        String webhook=inputWebhook.getText().toString().trim(); if(!isValidUrl(webhook)){toast("اول آدرس وب‌هوک معتبر وارد کن.");return;}
        saveSettings(); long ts=System.currentTimeMillis(); String msg="پیام آزمایشی Betnet SMS Sender - مبلغ: 12500000 ریال";
        long id=historyDb.insertPending(ts,"BETNET-TEST",msg,getPackageName()); textStatus.setText("در حال ارسال تست...");
        WebhookSender.sendWithRetry(this,webhook,"BETNET-TEST",msg,getPackageName(),ts,true,id,
                parsePositive(inputRetryCount,5),parsePositive(inputRetrySeconds,5),(success,code,response)->runOnUiThread(()->{textStatus.setText((success?"تست موفق":"تست ناموفق")+" | HTTP "+code+" | "+shorten(response,220));refreshHistory();}));
    }

    private void refreshHistory() {
        if(historyContainer==null)return; historyContainer.removeAllViews(); List<HistoryDb.HistoryItem> items=historyDb.latest(50);
        if(items.isEmpty()){TextView e=new TextView(this);e.setText("هنوز پیامی ثبت نشده است.");e.setTextColor(Color.parseColor("#6B7280"));e.setPadding(8,18,8,18);historyContainer.addView(e);return;}
        SimpleDateFormat df=new SimpleDateFormat("yyyy/MM/dd HH:mm:ss",Locale.US);
        for(HistoryDb.HistoryItem item:items){
            LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(24,20,24,20);
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,0,0,14);card.setLayoutParams(p);card.setBackgroundColor(Color.WHITE);card.setClickable(true);
            TextView title=new TextView(this);title.setText(item.sender+"  •  "+df.format(new Date(item.createdAt)));title.setTextColor(Color.parseColor("#171717"));title.setTextSize(16);title.setTypeface(null,1);
            TextView msg=new TextView(this);msg.setText(shorten(item.message,300));msg.setTextColor(Color.parseColor("#374151"));msg.setTextSize(14);msg.setPadding(0,8,0,8);
            TextView result=new TextView(this);String st;int color;if("sent".equals(item.status)){st="ارسال شد";color=Color.parseColor("#16803C");}else if("failed".equals(item.status)){st="ناموفق";color=Color.parseColor("#C62828");}else{st="در حال تلاش";color=Color.parseColor("#B26A00");}
            result.setText(st+" | تلاش‌ها: "+item.attemptCount+" | HTTP "+item.httpCode);result.setTextColor(color);result.setTextSize(13);result.setGravity(Gravity.START);
            card.addView(title);card.addView(msg);card.addView(result);card.setOnClickListener(v->showHistoryDetail(item.id));historyContainer.addView(card);
        }
    }

    private void showHistoryDetail(long id) {
        HistoryDb.HistoryItem item=historyDb.get(id); if(item==null)return; List<HistoryDb.AttemptItem> attempts=historyDb.attempts(id);
        SimpleDateFormat df=new SimpleDateFormat("yyyy/MM/dd HH:mm:ss",Locale.US); StringBuilder b=new StringBuilder();
        b.append("فرستنده: ").append(item.sender).append("\nبرنامه: ").append(item.packageName).append("\nزمان شناسایی: ").append(df.format(new Date(item.createdAt))).append("\n\nمتن پیام:\n").append(item.message).append("\n\nتاریخچه تلاش‌ها:\n");
        if(attempts.isEmpty())b.append("هنوز تلاشی ثبت نشده.");
        for(HistoryDb.AttemptItem a:attempts)b.append("\nتلاش ").append(a.attemptNo).append(" • ").append(df.format(new Date(a.createdAt))).append("\n").append("sent".equals(a.status)?"موفق":"ناموفق").append(" • HTTP ").append(a.httpCode).append("\nپاسخ: ").append(a.response).append("\n");
        new AlertDialog.Builder(this).setTitle("جزئیات ارسال").setMessage(b.toString()).setPositiveButton("بستن",null)
                .setNeutralButton("ارسال دستی",(d,w)->manualResend(item)).show();
    }

    private void manualResend(HistoryDb.HistoryItem item) {
        String webhook=AppPrefs.getWebhook(this);if(!isValidUrl(webhook)){toast("آدرس وب‌هوک در تنظیمات معتبر نیست.");return;}
        long ts=System.currentTimeMillis();long newId=historyDb.insertPending(ts,item.sender,item.message,item.packageName);
        toast("ارسال دستی شروع شد.");
        WebhookSender.sendWithRetry(this,webhook,item.sender,item.message,item.packageName,ts,false,newId,AppPrefs.getRetryCount(this),AppPrefs.getRetrySeconds(this),(s,c,r)->runOnUiThread(this::refreshHistory));
    }

    private int parsePositive(EditText edit,int fallback){try{return Math.max(1,Integer.parseInt(edit.getText().toString().trim()));}catch(Exception e){return fallback;}}
    private boolean isValidUrl(String v){return !TextUtils.isEmpty(v)&&(v.startsWith("https://")||v.startsWith("http://"));}
    private String shorten(String v,int max){if(v==null)return"";String c=v.trim();return c.length()<=max?c:c.substring(0,max)+"…";}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    private static final class AppChoice { final String label,packageName; AppChoice(String l,String p){label=l;packageName=p;} }
}
