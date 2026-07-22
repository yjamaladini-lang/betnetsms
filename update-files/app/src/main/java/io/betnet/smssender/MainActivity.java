package io.betnet.smssender;

import android.Manifest;
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
import android.widget.ImageButton;
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

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

public final class MainActivity extends AppCompatActivity {
    private Switch switchEnabled, switchDarkMode;
    private View buttonTopBack;
    private EditText inputWebhook, inputSenderFilter, inputTextFilter, inputRetryCount, inputRetrySeconds;
    private EditText inputManualSender, inputManualMessage;
    private TextView textStatus, textSelectedApps, textAppVersion, dashboardStatus, dashboardAppsText, dashboardLatestSender, dashboardLatestMessage, dashboardLatestResult;
    private LinearLayout historyContainer, sectionDashboard, sectionWebhook, sectionSettings, sectionHistory, sectionManual, dashboardLatestCard;
    private boolean accessDialogVisible = false;
    private boolean historySelectionMode = false;
    private final Set<Long> selectedHistoryIds = new HashSet<>();
    private HistoryDb historyDb;
    private Set<String> selectedPackages = new HashSet<>();
    private final List<AppChoice> appChoices = new ArrayList<>();

    @Override protected void onCreate(Bundle savedInstanceState) {
        AppCompatDelegate.setDefaultNightMode(
                AppPrefs.isDarkMode(this)
                        ? AppCompatDelegate.MODE_NIGHT_YES
                        : AppCompatDelegate.MODE_NIGHT_NO
        );
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
        switchDarkMode = findViewById(R.id.switchDarkMode);
        textAppVersion = findViewById(R.id.textAppVersion);
        buttonTopBack = findViewById(R.id.buttonTopBack);
        switchDarkMode.setChecked(AppPrefs.isDarkMode(this));
        textAppVersion.setText("نسخه " + BuildConfig.VERSION_NAME);

        findViewById(R.id.buttonHome).setOnClickListener(v -> showSection(sectionDashboard));
        buttonTopBack.setOnClickListener(v -> {
            historySelectionMode = false;
            selectedHistoryIds.clear();
            showSection(sectionDashboard);
        });
        findViewById(R.id.cardWebhook).setOnClickListener(v -> showSection(sectionWebhook));
        findViewById(R.id.cardSettings).setOnClickListener(v -> showSection(sectionSettings));
        findViewById(R.id.cardHistory).setOnClickListener(v -> { showSection(sectionHistory); refreshHistory(); });
        findViewById(R.id.cardManual).setOnClickListener(v -> showSection(sectionManual));
        switchDarkMode.setOnCheckedChangeListener((buttonView, dark) -> {
            if (AppPrefs.isDarkMode(this) == dark) return;
            AppPrefs.setDarkMode(this, dark);
            AppCompatDelegate.setDefaultNightMode(
                    dark
                            ? AppCompatDelegate.MODE_NIGHT_YES
                            : AppCompatDelegate.MODE_NIGHT_NO
            );
            recreate();
        });

        findViewById(R.id.buttonSaveWebhook).setOnClickListener(v -> saveWebhook());
        findViewById(R.id.buttonSave).setOnClickListener(v -> saveSettings());
        findViewById(R.id.buttonPermission).setOnClickListener(v -> openNotificationAccess());
        findViewById(R.id.buttonStartup).setOnClickListener(v -> openStartupSettings());
        findViewById(R.id.buttonTest).setOnClickListener(v -> testWebhook());
        findViewById(R.id.buttonRefresh).setOnClickListener(v -> refreshHistory());
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
        if (buttonTopBack != null) {
            buttonTopBack.setVisibility(target == sectionDashboard ? View.GONE : View.VISIBLE);
        }
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
        if (appChoices.isEmpty()) {
            toast("برنامه ارتباطی مناسبی پیدا نشد.");
            return;
        }

        Set<String> draft = new HashSet<>(selectedPackages);

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(14, 8, 14, 8);
        list.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);

        for (AppChoice app : appChoices) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(8, 10, 8, 10);
            row.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);

            LinearLayout texts = new LinearLayout(this);
            texts.setOrientation(LinearLayout.VERTICAL);
            texts.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
            texts.setGravity(Gravity.LEFT);
            texts.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));

            TextView name = new TextView(this);
            name.setText(app.label);
            name.setGravity(Gravity.LEFT);
            name.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);
            name.setTextDirection(View.TEXT_DIRECTION_LTR);
            name.setTextSize(15);
            name.setTextColor(getColor(R.color.ui_text));
            name.setTypeface(null, 1);

            TextView pkg = new TextView(this);
            pkg.setText(app.packageName);
            pkg.setGravity(Gravity.LEFT);
            pkg.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);
            pkg.setTextDirection(View.TEXT_DIRECTION_LTR);
            pkg.setTextSize(10);
            pkg.setTextColor(getColor(R.color.ui_muted));

            texts.addView(name);
            texts.addView(pkg);

            ImageView icon = new ImageView(this);
            icon.setImageDrawable(app.icon);
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(48, 48);
            iconParams.setMargins(10, 0, 10, 0);
            icon.setLayoutParams(iconParams);

            CheckBox check = new CheckBox(this);
            check.setChecked(draft.contains(app.packageName));

            row.addView(texts);
            row.addView(icon);
            row.addView(check);

            View.OnClickListener toggle = view -> check.setChecked(!check.isChecked());
            row.setOnClickListener(toggle);

            check.setOnCheckedChangeListener((button, isChecked) -> {
                if (isChecked) {
                    draft.add(app.packageName);
                } else {
                    draft.remove(app.packageName);
                }
            });

            list.addView(row);
        }

        ScrollView scroll = new ScrollView(this);
        scroll.addView(list);

        new AlertDialog.Builder(this)
                .setTitle("برنامه‌های ارتباطی مجاز")
                .setView(scroll)
                .setPositiveButton("تأیید", (dialog, which) -> {
                    selectedPackages = draft;
                    updateSelectedAppsText();
                })
                .setNegativeButton("لغو", null)
                .show();
    }

    private void updateSelectedAppsText() {
        if (selectedPackages.isEmpty()) {
            textSelectedApps.setGravity(Gravity.RIGHT);
            textSelectedApps.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);
            textSelectedApps.setTextDirection(View.TEXT_DIRECTION_RTL);
            textSelectedApps.setText("هیچ برنامه‌ای انتخاب نشده است.");

            if (dashboardAppsText != null) {
                dashboardAppsText.setText("هیچ برنامه‌ای انتخاب نشده است");
            }
            return;
        }

        List<String> names = new ArrayList<>();
        for (AppChoice app : appChoices) {
            if (selectedPackages.contains(app.packageName)) {
                names.add(app.label);
            }
        }

        String joined = TextUtils.join(", ", names);
        textSelectedApps.setGravity(Gravity.LEFT);
        textSelectedApps.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);
        textSelectedApps.setTextDirection(View.TEXT_DIRECTION_LTR);
        textSelectedApps.setText(joined);

        if (dashboardAppsText != null) {
            dashboardAppsText.setText(joined);
        }
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
        persistAll(webhook); long ts=System.currentTimeMillis(); String msg="پیام آزمایشی نوتیفر هرمز - مبلغ: 12500000 ریال";
        long id=historyDb.insertPending(ts,"NOTIFIER-HORMOZ",msg,getPackageName());
        toast("در حال ارسال پیام آزمایشی...");
        WebhookSender.sendWithRetry(this,webhook,"NOTIFIER-HORMOZ",msg,getPackageName(),ts,true,id,
                parsePositive(inputRetryCount,5),parsePositive(inputRetrySeconds,5),(success,code,response)->runOnUiThread(()->{
                    toast((success ? "تست موفق" : "تست ناموفق") + " - HTTP " + code);
                    updateAccessStatus();
                    refreshHistory();
                }));
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
            empty.setTextColor(getColor(R.color.premium_muted));
            empty.setTextSize(12);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(8), dp(36), dp(8), dp(36));
            historyContainer.addView(empty);
            return;
        }

        final String webhookLabel = historyWebhookLabel();

        for (HistoryDb.HistoryItem item : items) {
            final int state = historyVisualState(item);
            final View card = getLayoutInflater().inflate(
                    R.layout.item_history_premium,
                    historyContainer,
                    false
            );

            LinearLayout root = card.findViewById(R.id.historyCardRoot);
            CheckBox check = card.findViewById(R.id.historyCheck);
            TextView sender = card.findViewById(R.id.historySender);
            TextView time = card.findViewById(R.id.historyTime);
            TextView messageTitle = card.findViewById(R.id.historyMessageTitle);
            TextView messageBody = card.findViewById(R.id.historyMessageBody);
            LinearLayout messageSummary = card.findViewById(R.id.historyMessageSummary);
            TextView webhook = card.findViewById(R.id.historyWebhook);
            LinearLayout statusChip = card.findViewById(R.id.historyStatusChip);
            ImageView statusIcon = card.findViewById(R.id.historyStatusIcon);
            TextView statusText = card.findViewById(R.id.historyStatusText);

            boolean selected = selectedHistoryIds.contains(item.id);
            root.setBackgroundResource(
                    selected
                            ? R.drawable.bg_premium_card_selected
                            : R.drawable.bg_premium_card
            );
            // Some Android versions clear XML padding after replacing background.
            root.setPadding(dp(13), dp(13), dp(13), dp(13));

            sender.setText(
                    item.sender == null || item.sender.trim().isEmpty()
                            ? "فرستنده نامشخص"
                            : item.sender.trim()
            );
            time.setText(JalaliDate.format(item.createdAt));
            webhook.setText(webhookLabel);

            String[] parts = splitHistoryMessage(item.message);
            if (parts[0].isEmpty() || "متن پیام".equals(parts[0])) {
                messageTitle.setVisibility(View.GONE);
            } else {
                messageTitle.setVisibility(View.VISIBLE);
                messageTitle.setText(parts[0]);
            }
            messageBody.setText(parts[1].isEmpty() ? parts[0] : parts[1]);

            applyPremiumStatus(statusChip, statusIcon, statusText, state, item);
            applyHistoryMessageStyle(messageSummary, state);

            check.setVisibility(historySelectionMode ? View.VISIBLE : View.GONE);
            check.setChecked(selected);
            check.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) selectedHistoryIds.add(item.id);
                else selectedHistoryIds.remove(item.id);

                root.setBackgroundResource(
                        isChecked
                                ? R.drawable.bg_premium_card_selected
                                : R.drawable.bg_premium_card
                );
                // Keep identical spacing in selected and unselected states.
                root.setPadding(dp(13), dp(13), dp(13), dp(13));
                updateHistoryToolbar();
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
        int visualState = historyVisualState(item);
        if (visualState == 1) {
            dashboardLatestResult.setText("بسته داده شد");
            dashboardLatestResult.setTextColor(Color.parseColor("#16803C"));
            dashboardLatestResult.setBackgroundResource(R.drawable.bg_status_success);
        } else if (visualState == 2) {
            dashboardLatestResult.setText("رسید؛ اعمال نشد");
            dashboardLatestResult.setTextColor(Color.parseColor("#A16207"));
            dashboardLatestResult.setBackgroundResource(R.drawable.bg_status_warning);
        } else {
            dashboardLatestResult.setText("ارسال ناموفق");
            dashboardLatestResult.setTextColor(Color.parseColor("#B91C1C"));
            dashboardLatestResult.setBackgroundResource(R.drawable.bg_status_error);
        }
        dashboardLatestCard.setOnClickListener(v -> showHistoryDetail(item.id));
    }

    private void showHistoryDetail(long id) {
        HistoryDb.HistoryItem item = historyDb.get(id);
        if (item == null) return;

        List<HistoryDb.AttemptItem> attempts = historyDb.attempts(id);
        View dialogView = getLayoutInflater().inflate(
                R.layout.dialog_history_detail_premium,
                null,
                false
        );

        TextView sender = dialogView.findViewById(R.id.detailSenderValue);
        TextView app = dialogView.findViewById(R.id.detailAppValue);
        TextView time = dialogView.findViewById(R.id.detailTimeValue);
        TextView message = dialogView.findViewById(R.id.detailMessageValue);
        LinearLayout attemptsContainer =
                dialogView.findViewById(R.id.detailAttemptsContainer);
        View closeTop = dialogView.findViewById(R.id.detailCloseTop);
        Button resend = dialogView.findViewById(R.id.detailResendButton);

        sender.setText(
                item.sender == null || item.sender.trim().isEmpty()
                        ? "—"
                        : item.sender.trim()
        );
        app.setText(getAppLabel(item.packageName));
        time.setText(JalaliDate.format(item.createdAt));
        message.setText(item.message == null ? "" : item.message);
        sender.setGravity(Gravity.LEFT);
        sender.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);
        app.setGravity(Gravity.LEFT);
        app.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);
        time.setGravity(Gravity.LEFT);
        time.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);
        message.setGravity(Gravity.RIGHT);
        message.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);
        message.setTextDirection(View.TEXT_DIRECTION_RTL);

        if (attempts.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("هنوز تلاشی ثبت نشده است.");
            empty.setTextColor(getColor(R.color.premium_muted));
            empty.setTextSize(11);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(12), dp(22), dp(12), dp(22));
            empty.setBackgroundResource(R.drawable.bg_premium_inner_card);
            attemptsContainer.addView(empty);
        } else {
            for (HistoryDb.AttemptItem attempt : attempts) {
                View attemptView = getLayoutInflater().inflate(
                        R.layout.item_attempt_premium,
                        attemptsContainer,
                        false
                );

                TextView number = attemptView.findViewById(R.id.attemptNumber);
                TextView attemptTime = attemptView.findViewById(R.id.attemptTime);
                TextView http = attemptView.findViewById(R.id.attemptHttp);
                TextView result = attemptView.findViewById(R.id.attemptResult);
                TextView response = attemptView.findViewById(R.id.attemptResponse);

                int state = attemptVisualState(attempt);
                number.setText("تلاش " + attempt.attemptNo);
                attemptTime.setText(JalaliDate.format(attempt.createdAt));
                http.setText("HTTP " + attempt.httpCode);
                result.setText(
                        state == 1
                                ? "موفق"
                                : state == 2 ? "رسید" : "ناموفق"
                );
                response.setText(prettyJson(attempt.response));

                applyPremiumAttemptStyle(http, result, state);
                attemptsContainer.addView(attemptView);
            }
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        closeTop.setOnClickListener(v -> dialog.dismiss());
        resend.setOnClickListener(v -> {
            dialog.dismiss();
            manualResend(item);
        });

        dialog.setOnShowListener(d -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawableResource(
                        android.R.color.transparent
                );
                int width = (int) (
                        getResources().getDisplayMetrics().widthPixels * 0.94f
                );
                int height = (int) (
                        getResources().getDisplayMetrics().heightPixels * 0.90f
                );
                dialog.getWindow().setLayout(width, height);
                dialog.getWindow().setGravity(Gravity.CENTER);
            }
        });

        dialog.show();
    }

    private void applyHistoryMessageStyle(LinearLayout summary, int state) {
        if (summary == null) return;
        if (state == 1) {
            summary.setBackgroundResource(R.drawable.bg_history_message_success);
        } else if (state == 2) {
            summary.setBackgroundResource(R.drawable.bg_history_message_warning);
        } else {
            summary.setBackgroundResource(R.drawable.bg_history_message_error);
        }
        summary.setPadding(dp(11), dp(11), dp(11), dp(11));
    }

    private void applyPremiumStatus(
            LinearLayout chip,
            ImageView icon,
            TextView text,
            int state,
            HistoryDb.HistoryItem item
    ) {
        if (state == 1) {
            chip.setBackgroundResource(R.drawable.bg_status_success);
            icon.setImageResource(R.drawable.ic_check_premium);
            text.setTextColor(getColor(R.color.premium_green));
            text.setText(
                    "بسته داده شد • HTTP "
                            + item.httpCode
                            + " • "
                            + item.attemptCount
                            + " تلاش"
            );
        } else if (state == 2) {
            chip.setBackgroundResource(R.drawable.bg_status_warning);
            icon.setImageResource(R.drawable.ic_warning_premium);
            text.setTextColor(getColor(R.color.premium_yellow));
            text.setText(
                    "بسته داده نشد • HTTP "
                            + item.httpCode
                            + " • "
                            + item.attemptCount
                            + " تلاش"
            );
        } else {
            chip.setBackgroundResource(R.drawable.bg_status_error);
            icon.setImageResource(R.drawable.ic_error_premium);
            text.setTextColor(getColor(R.color.premium_red));
            text.setText(
                    "به سایت نرفت • HTTP "
                            + item.httpCode
                            + " • "
                            + item.attemptCount
                            + " تلاش"
            );
        }
    }

    private void applyPremiumAttemptStyle(
            TextView http,
            TextView result,
            int state
    ) {
        int background;
        int color;

        if (state == 1) {
            background = R.drawable.bg_status_success;
            color = getColor(R.color.premium_green);
        } else if (state == 2) {
            background = R.drawable.bg_status_warning;
            color = getColor(R.color.premium_yellow);
        } else {
            background = R.drawable.bg_status_error;
            color = getColor(R.color.premium_red);
        }

        http.setBackgroundResource(background);
        result.setBackgroundResource(background);
        http.setTextColor(color);
        result.setTextColor(color);
    }

    private int dp(int value) {
        return Math.round(
                getResources().getDisplayMetrics().density * value
        );
    }

    private String historyWebhookLabel() {
        String webhook = AppPrefs.getWebhook(this);

        if (webhook == null || webhook.trim().isEmpty()) {
            return "وب‌هوک";
        }

        try {
            Uri uri = Uri.parse(webhook.trim());
            String host = uri.getHost();
            String path = uri.getPath();

            String label = host == null || host.trim().isEmpty()
                    ? webhook.trim()
                    : host.trim();

            if (path != null
                    && !path.trim().isEmpty()
                    && !"/".equals(path.trim())) {
                label += path.trim();
            }

            return label;
        } catch (Exception ignored) {
            return webhook.trim();
        }
    }

    private String[] splitHistoryMessage(String raw) {
        String message = raw == null ? "" : raw.trim();

        if (message.isEmpty()) {
            return new String[]{"", "پیام بدون متن"};
        }

        String[] lines = message.split("\\r?\\n", 2);

        if (lines.length == 2 && !lines[0].trim().isEmpty()) {
            return new String[]{
                    lines[0].trim(),
                    lines[1].trim()
            };
        }

        return new String[]{"", message};
    }

    private int attemptVisualState(HistoryDb.AttemptItem attempt) {
        if (attempt == null) {
            return 3;
        }

        if (attempt.httpCode < 200 || attempt.httpCode >= 300) {
            return 3;
        }

        try {
            JSONObject json = new JSONObject(
                    attempt.response == null
                            ? ""
                            : attempt.response.trim()
            );

            if (json.optBoolean("matched", false)) {
                return 1;
            }

            if (json.optBoolean("success", false)) {
                return 2;
            }
        } catch (Exception ignored) {
        }

        return 2;
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
