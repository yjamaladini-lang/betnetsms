package io.betnet.smssender;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.ComponentName;
import android.service.notification.NotificationListenerService;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
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

import org.json.JSONObject;

public final class MainActivity extends Activity {
    private Switch switchEnabled;
    private EditText inputWebhook, inputSenderFilter, inputTextFilter, inputRetryCount, inputRetrySeconds;
    private EditText inputManualSender, inputManualMessage;
    private TextView textStatus, textSelectedApps, dashboardStatus, dashboardAppsText, dashboardLatestSender, dashboardLatestMessage, dashboardLatestResult;
    private LinearLayout historyContainer, sectionDashboard, sectionWebhook, sectionSettings, sectionHistory, sectionManual, dashboardLatestCard;
    private boolean accessDialogVisible = false;
    private boolean historySelectionMode = false;
    private final Set<Long> selectedHistoryIds = new HashSet<>();
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
        findViewById(R.id.buttonHistoryBack).setOnClickListener(v -> {
            historySelectionMode = false;
            selectedHistoryIds.clear();
            showSection(sectionDashboard);
        });
        findViewById(R.id.buttonSelectHistory).setOnClickListener(v -> {
            historySelectionMode = !historySelectionMode;
            if (!historySelectionMode) selectedHistoryIds.clear();
            refreshHistory();
        });
        findViewById(R.id.buttonDeleteSelected).setOnClickListener(v -> deleteSelectedHistory());
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
        if (historyContainer == null) return;

        historyContainer.removeAllViews();
        List<HistoryDb.HistoryItem> items = historyDb.latest(80);
        updateLatestDashboard(items);
        updateHistoryToolbar();

        if (items.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("هنوز پیامی ثبت نشده است.");
            empty.setTextColor(Color.parseColor("#6B7280"));
            empty.setTextSize(12);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(8, 30, 8, 30);
            historyContainer.addView(empty);
            return;
        }

        for (HistoryDb.HistoryItem item : items) {
            final int state = historyVisualState(item);

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(16, 12, 16, 12);
            card.setClickable(true);
            card.setFocusable(true);
            card.setElevation(1f);
            card.setBackground(makeHistoryCardBackground(
                    state,
                    selectedHistoryIds.contains(item.id)
            ));

            LinearLayout.LayoutParams cardParams =
                    new LinearLayout.LayoutParams(-1, -2);
            cardParams.setMargins(0, 0, 0, 9);
            card.setLayoutParams(cardParams);

            /* ردیف اول: انتخاب + فرستنده + زمان */
            LinearLayout top = new LinearLayout(this);
            top.setOrientation(LinearLayout.HORIZONTAL);
            top.setGravity(Gravity.CENTER_VERTICAL);

            CheckBox check = new CheckBox(this);
            check.setButtonTintList(android.content.res.ColorStateList.valueOf(
                    Color.parseColor("#2563EB")
            ));
            check.setVisibility(historySelectionMode ? View.VISIBLE : View.GONE);
            check.setChecked(selectedHistoryIds.contains(item.id));
            check.setPadding(0, 0, 6, 0);
            check.setOnCheckedChangeListener((buttonView, checked) -> {
                if (checked) selectedHistoryIds.add(item.id);
                else selectedHistoryIds.remove(item.id);

                updateHistoryToolbar();
                card.setBackground(makeHistoryCardBackground(state, checked));
            });
            top.addView(check, new LinearLayout.LayoutParams(-2, -2));

            TextView sender = new TextView(this);
            sender.setText(
                    item.sender == null || item.sender.trim().isEmpty()
                            ? "فرستنده نامشخص"
                            : item.sender
            );
            sender.setTextColor(Color.parseColor("#111827"));
            sender.setTextSize(14);
            sender.setTypeface(null, 1);
            sender.setSingleLine(true);
            sender.setEllipsize(android.text.TextUtils.TruncateAt.END);
            sender.setGravity(Gravity.START);
            sender.setTextDirection(View.TEXT_DIRECTION_RTL);
            top.addView(sender, new LinearLayout.LayoutParams(0, -2, 1f));

            TextView time = new TextView(this);
            time.setText(JalaliDate.format(item.createdAt));
            time.setTextColor(Color.parseColor("#64748B"));
            time.setTextSize(10);
            time.setSingleLine(true);
            time.setGravity(Gravity.END);
            top.addView(time);

            /* فقط خلاصه کوتاه پیام */
            TextView message = new TextView(this);
            message.setText(item.message == null ? "" : item.message.trim());
            message.setTextColor(Color.parseColor("#334155"));
            message.setTextSize(12);
            message.setLineSpacing(2f, 1.08f);
            message.setMaxLines(2);
            message.setEllipsize(android.text.TextUtils.TruncateAt.END);
            message.setGravity(Gravity.START);
            message.setTextDirection(View.TEXT_DIRECTION_RTL);
            message.setPadding(0, 7, 0, 8);

            /* ردیف پایین: وضعیت + HTTP + دکمه جزئیات */
            LinearLayout bottom = new LinearLayout(this);
            bottom.setOrientation(LinearLayout.HORIZONTAL);
            bottom.setGravity(Gravity.CENTER_VERTICAL);

            TextView result = new TextView(this);
            result.setText(
                    state == 1
                            ? "بسته داده شد"
                            : state == 2
                            ? "رسید؛ اعمال نشد"
                            : "ارسال نشد"
            );
            result.setTextColor(historyStateColor(state));
            result.setTextSize(10);
            result.setTypeface(null, 1);
            result.setPadding(11, 5, 11, 5);
            result.setBackground(makeStatusPill(state));
            bottom.addView(result);

            TextView meta = new TextView(this);
            meta.setText("HTTP " + item.httpCode + "  •  " + item.attemptCount + " تلاش");
            meta.setTextColor(Color.parseColor("#64748B"));
            meta.setTextSize(9);
            meta.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams metaLp =
                    new LinearLayout.LayoutParams(0, -2, 1f);
            metaLp.setMargins(8, 0, 8, 0);
            bottom.addView(meta, metaLp);

            Button detail = compactActionButton("جزئیات", "#FFFFFF", "#2563EB");
            detail.setTextSize(10);
            detail.setMinHeight(0);
            detail.setMinimumHeight(0);
            detail.setMinWidth(0);
            detail.setMinimumWidth(0);
            detail.setPadding(13, 0, 13, 0);
            LinearLayout.LayoutParams detailLp =
                    new LinearLayout.LayoutParams(-2, 42);
            bottom.addView(detail, detailLp);

            card.addView(top);
            card.addView(message);
            card.addView(bottom);

            detail.setOnClickListener(v -> {
                if (historySelectionMode) {
                    check.setChecked(!check.isChecked());
                } else {
                    showHistoryDetail(item.id);
                }
            });

            card.setOnClickListener(v -> {
                if (historySelectionMode) {
                    check.setChecked(!check.isChecked());
                } else {
                    showHistoryDetail(item.id);
                }
            });

            card.setOnLongClickListener(v -> {
                if (!historySelectionMode) {
                    historySelectionMode = true;
                    selectedHistoryIds.clear();
                    selectedHistoryIds.add(item.id);
                    refreshHistory();
                }
                return true;
            });

            historyContainer.addView(card);
        }
    }

    private void updateHistoryToolbar() {
        View deleteButton = findViewById(R.id.buttonDeleteSelected);
        Button selectButton = findViewById(R.id.buttonSelectHistory);
        if (deleteButton != null) deleteButton.setVisibility(historySelectionMode ? View.VISIBLE : View.GONE);
        if (selectButton != null) {
            selectButton.setText(historySelectionMode ? "پایان انتخاب" : "انتخاب پیام‌ها");
        }
        if (deleteButton instanceof Button) {
            ((Button) deleteButton).setText(selectedHistoryIds.isEmpty() ? "حذف" : "حذف " + selectedHistoryIds.size() + " پیام");
            deleteButton.setEnabled(!selectedHistoryIds.isEmpty());
        }
    }

    private void deleteSelectedHistory() {
        if (selectedHistoryIds.isEmpty()) {
            toast("پیامی انتخاب نشده است.");
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("حذف تاریخچه")
                .setMessage(selectedHistoryIds.size() + " پیام انتخاب‌شده حذف شود؟")
                .setNegativeButton("انصراف", null)
                .setPositiveButton("حذف", (d, w) -> {
                    historyDb.delete(selectedHistoryIds);
                    selectedHistoryIds.clear();
                    historySelectionMode = false;
                    refreshHistory();
                    toast("تاریخچه انتخاب‌شده حذف شد.");
                }).show();
    }

    private int historyVisualState(HistoryDb.HistoryItem item) {
        if (item == null) return 3;
        if (item.httpCode <= 0 || "failed".equals(item.status) || item.httpCode < 200 || item.httpCode >= 300) return 3;
        try {
            JSONObject json = new JSONObject(item.response == null ? "" : item.response.trim());
            if (json.optBoolean("matched", false)) return 1;
            if (json.optBoolean("success", false)) return 2;
        } catch (Exception ignored) {}
        return "sent".equals(item.status) ? 2 : 3;
    }

    private String historyStateLabel(HistoryDb.HistoryItem item) {
        int state = historyVisualState(item);
        if (state == 1) return "بسته با موفقیت داده شد";
        if (state == 2) return "به سایت رسید؛ بسته داده نشد";
        return "به سایت ارسال نشد";
    }

    private int historyStateColor(int state) {
        if (state == 1) return Color.parseColor("#15803D");
        if (state == 2) return Color.parseColor("#A16207");
        return Color.parseColor("#B91C1C");
    }

    private GradientDrawable makeHistoryCardBackground(int state, boolean selected) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(34f);
        if (selected) {
            bg.setColor(Color.parseColor("#EEF4FF"));
            bg.setStroke(3, Color.parseColor("#3B82F6"));
        } else if (state == 1) {
            bg.setColor(Color.parseColor("#F0FDF4"));
            bg.setStroke(2, Color.parseColor("#86EFAC"));
        } else if (state == 2) {
            bg.setColor(Color.parseColor("#FFFBEB"));
            bg.setStroke(2, Color.parseColor("#FDE68A"));
        } else {
            bg.setColor(Color.parseColor("#FEF2F2"));
            bg.setStroke(2, Color.parseColor("#FCA5A5"));
        }
        return bg;
    }

    private GradientDrawable makeStatusPill(int state) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(999f);
        if (state == 1) bg.setColor(Color.parseColor("#DCFCE7"));
        else if (state == 2) bg.setColor(Color.parseColor("#FEF3C7"));
        else bg.setColor(Color.parseColor("#FEE2E2"));
        return bg;
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
        HistoryDb.HistoryItem item = historyDb.get(id);
        if (item == null) return;

        List<HistoryDb.AttemptItem> attempts = historyDb.attempts(id);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(22, 18, 22, 18);
        root.setTextDirection(View.TEXT_DIRECTION_RTL);
        root.setBackground(makeRoundedBackground("#FFFFFF", "#E5E7EB", 28f));

        TextView title = new TextView(this);
        title.setText("جزئیات پیام و ارسال");
        title.setTextColor(Color.parseColor("#111827"));
        title.setTextSize(22);
        title.setTypeface(null, 1);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, 14);
        root.addView(title);

        LinearLayout infoCard = new LinearLayout(this);
        infoCard.setOrientation(LinearLayout.VERTICAL);
        infoCard.setPadding(14, 6, 14, 6);
        infoCard.setBackground(makeRoundedBackground("#FFFFFF", "#E5E7EB", 18f));
        infoCard.addView(simpleDetailRow("فرستنده:", item.sender == null ? "—" : item.sender));
        infoCard.addView(infoDivider());
        infoCard.addView(simpleDetailRow("برنامه:", getAppLabel(item.packageName)));
        infoCard.addView(infoDivider());
        infoCard.addView(simpleDetailRow("زمان شناسایی:", JalaliDate.format(item.createdAt)));
        root.addView(infoCard);

        root.addView(simpleSectionTitle("متن پیام:"));
        TextView message = new TextView(this);
        message.setText(item.message == null ? "" : item.message);
        message.setTextColor(Color.parseColor("#111827"));
        message.setTextSize(13);
        message.setLineSpacing(4f, 1.18f);
        message.setGravity(Gravity.START);
        message.setTextDirection(View.TEXT_DIRECTION_RTL);
        message.setPadding(16, 14, 16, 14);
        message.setBackground(makeRoundedBackground("#FFFFFF", "#E5E7EB", 16f));
        root.addView(message);

        root.addView(simpleSectionTitle("تاریخچه تلاش‌ها:"));

        LinearLayout attemptsBox = new LinearLayout(this);
        attemptsBox.setOrientation(LinearLayout.VERTICAL);
        attemptsBox.setPadding(12, 10, 12, 10);
        attemptsBox.setBackground(makeRoundedBackground("#FFFFFF", "#E5E7EB", 18f));

        if (attempts.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("تلاشی ثبت نشده است.");
            empty.setTextColor(Color.parseColor("#6B7280"));
            empty.setTextSize(11);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(8, 14, 8, 14);
            attemptsBox.addView(empty);
        } else {
            for (HistoryDb.AttemptItem a : attempts) {
                int attemptState = a.httpCode >= 200 && a.httpCode < 300 ? 2 : 3;
                try {
                    JSONObject json = new JSONObject(a.response == null ? "" : a.response.trim());
                    if (json.optBoolean("matched", false)) {
                        attemptState = 1;
                    }
                } catch (Exception ignored) {}

                LinearLayout attempt = new LinearLayout(this);
                attempt.setOrientation(LinearLayout.VERTICAL);
                attempt.setPadding(10, 8, 10, 8);

                LinearLayout line = new LinearLayout(this);
                line.setOrientation(LinearLayout.HORIZONTAL);
                line.setGravity(Gravity.CENTER_VERTICAL);

                TextView attemptNo = new TextView(this);
                attemptNo.setText("تلاش " + a.attemptNo);
                attemptNo.setTextColor(Color.parseColor("#111827"));
                attemptNo.setTextSize(11);
                attemptNo.setTypeface(null, 1);
                line.addView(attemptNo, new LinearLayout.LayoutParams(0, -2, 1f));

                TextView time = new TextView(this);
                time.setText(JalaliDate.format(a.createdAt));
                time.setTextColor(Color.parseColor("#374151"));
                time.setTextSize(9);
                time.setPadding(8, 0, 8, 0);
                line.addView(time);

                TextView http = new TextView(this);
                http.setText("HTTP " + a.httpCode);
                http.setTextColor(attemptState == 1
                        ? Color.parseColor("#15803D")
                        : attemptState == 2
                        ? Color.parseColor("#A16207")
                        : Color.parseColor("#B91C1C"));
                http.setTextSize(9);
                http.setTypeface(null, 1);
                http.setPadding(10, 4, 10, 4);
                http.setBackground(makeRoundedBackground(
                        attemptState == 1 ? "#ECFDF5" : attemptState == 2 ? "#FFFBEB" : "#FEF2F2",
                        attemptState == 1 ? "#BBF7D0" : attemptState == 2 ? "#FDE68A" : "#FECACA",
                        14f
                ));
                line.addView(http);

                TextView result = new TextView(this);
                result.setText(attemptState == 1 ? "موفق" : attemptState == 2 ? "رسید" : "ناموفق");
                result.setTextColor(attemptState == 1
                        ? Color.parseColor("#15803D")
                        : attemptState == 2
                        ? Color.parseColor("#A16207")
                        : Color.parseColor("#B91C1C"));
                result.setTextSize(9);
                result.setTypeface(null, 1);
                result.setPadding(10, 4, 10, 4);
                result.setBackground(makeRoundedBackground(
                        attemptState == 1 ? "#ECFDF5" : attemptState == 2 ? "#FFFBEB" : "#FEF2F2",
                        attemptState == 1 ? "#BBF7D0" : attemptState == 2 ? "#FDE68A" : "#FECACA",
                        14f
                ));
                LinearLayout.LayoutParams resultLp = new LinearLayout.LayoutParams(-2, -2);
                resultLp.setMargins(6, 0, 0, 0);
                line.addView(result, resultLp);

                attempt.addView(line);

                TextView responseLabel = new TextView(this);
                responseLabel.setText("پاسخ:");
                responseLabel.setTextColor(Color.parseColor("#111827"));
                responseLabel.setTextSize(11);
                responseLabel.setTypeface(null, 1);
                responseLabel.setPadding(0, 10, 0, 6);
                attempt.addView(responseLabel);

                TextView response = new TextView(this);
                response.setText(prettyJson(a.response));
                response.setTextColor(Color.parseColor("#111827"));
                response.setTextSize(10);
                response.setTypeface(android.graphics.Typeface.MONOSPACE);
                response.setTextDirection(View.TEXT_DIRECTION_LTR);
                response.setGravity(Gravity.START);
                response.setPadding(12, 10, 12, 10);
                response.setBackground(makeRoundedBackground("#F8FAFC", "#E5E7EB", 12f));
                attempt.addView(response);

                attemptsBox.addView(attempt);

                if (a != attempts.get(attempts.size() - 1)) {
                    attemptsBox.addView(infoDivider());
                }
            }
        }

        root.addView(attemptsBox);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER);
        buttons.setPadding(0, 14, 0, 0);

        Button resend = compactActionButton("ارسال مجدد", "#16A34A", "#FFFFFF");
        Button close = compactActionButton("بستن", "#F8FAFC", "#111827");

        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(0, 58, 1f);
        btnLp.setMargins(5, 0, 5, 0);
        buttons.addView(resend, btnLp);
        buttons.addView(close, btnLp);
        root.addView(buttons);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(root);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(scroll)
                .create();

        close.setOnClickListener(v -> dialog.dismiss());
        resend.setOnClickListener(v -> {
            dialog.dismiss();
            manualResend(item);
        });

        dialog.setOnShowListener(d -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
                int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.72f);
                int maxHeight = (int) (getResources().getDisplayMetrics().heightPixels * 0.82f);
                dialog.getWindow().setLayout(width, maxHeight);
                dialog.getWindow().setGravity(Gravity.CENTER);
            }
        });

        dialog.show();
    }

    private TextView simpleSectionTitle(String text) {
        TextView title = new TextView(this);
        title.setText(text);
        title.setTextColor(Color.parseColor("#111827"));
        title.setTextSize(14);
        title.setTypeface(null, 1);
        title.setGravity(Gravity.START);
        title.setPadding(0, 16, 0, 8);
        return title;
    }

    private View simpleDetailRow(String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(6, 10, 6, 10);

        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextColor(Color.parseColor("#374151"));
        labelView.setTextSize(11);
        labelView.setGravity(Gravity.START);
        row.addView(labelView, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView valueView = new TextView(this);
        valueView.setText(value == null || value.trim().isEmpty() ? "—" : value);
        valueView.setTextColor(Color.parseColor("#111827"));
        valueView.setTextSize(11);
        valueView.setTextDirection(View.TEXT_DIRECTION_LTR);
        valueView.setGravity(Gravity.END);
        row.addView(valueView);

        return row;
    }

    private Button compactActionButton(String text, String background, String foreground) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(12);
        button.setTypeface(null, 1);
        button.setTextColor(Color.parseColor(foreground));
        button.setBackground(makeRoundedBackground(background, background, 20f));
        button.setPadding(12, 0, 12, 0);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        return button;
    }

    private GradientDrawable makeRoundedBackground(
            String fill,
            String stroke,
            float radius
    ) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.parseColor(fill));
        background.setCornerRadius(radius);
        background.setStroke(1, Color.parseColor(stroke));
        return background;
    }

    private String prettyJson(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return "بدون پاسخ";
        }

        try {
            return new JSONObject(raw.trim()).toString(2);
        } catch (Exception ignored) {
            return raw.trim();
        }
    }

    private TextView sectionTitle(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(Color.parseColor("#111827"));
        t.setTextSize(16);
        t.setTypeface(null, 1);
        t.setGravity(Gravity.START);
        t.setPadding(0, 22, 0, 8);
        return t;
    }

    private TextView detailBox(String text, String color) {
        TextView t = new TextView(this);
        t.setText(text == null ? "" : text);
        t.setTextColor(Color.parseColor("#1F2937"));
        t.setTextSize(13);
        t.setGravity(Gravity.START);
        t.setTextDirection(View.TEXT_DIRECTION_RTL);
        t.setLineSpacing(4f, 1.18f);
        t.setPadding(20, 18, 20, 18);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor(color));
        bg.setCornerRadius(26f);
        bg.setStroke(1, Color.parseColor("#E5E7EB"));
        t.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, 10);
        t.setLayoutParams(lp);
        return t;
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
