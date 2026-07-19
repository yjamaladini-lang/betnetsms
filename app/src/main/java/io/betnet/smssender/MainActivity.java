package io.betnet.smssender;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends Activity {
    private Switch switchEnabled;
    private EditText inputWebhook;
    private EditText inputSenderFilter;
    private EditText inputTextFilter;
    private TextView textStatus;
    private LinearLayout historyContainer;
    private HistoryDb historyDb;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        historyDb = new HistoryDb(this);
        switchEnabled = findViewById(R.id.switchEnabled);
        inputWebhook = findViewById(R.id.inputWebhook);
        inputSenderFilter = findViewById(R.id.inputSenderFilter);
        inputTextFilter = findViewById(R.id.inputTextFilter);
        textStatus = findViewById(R.id.textStatus);
        historyContainer = findViewById(R.id.historyContainer);
        Button buttonSave = findViewById(R.id.buttonSave);
        Button buttonPermission = findViewById(R.id.buttonPermission);
        Button buttonTest = findViewById(R.id.buttonTest);
        Button buttonRefresh = findViewById(R.id.buttonRefresh);

        loadSettings();
        refreshHistory();

        buttonSave.setOnClickListener(v -> saveSettings());
        buttonPermission.setOnClickListener(v -> openNotificationAccess());
        buttonTest.setOnClickListener(v -> testWebhook());
        buttonRefresh.setOnClickListener(v -> refreshHistory());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshHistory();
    }

    private void loadSettings() {
        switchEnabled.setChecked(AppPrefs.isEnabled(this));
        inputWebhook.setText(AppPrefs.getWebhook(this));
        inputSenderFilter.setText(AppPrefs.getSenderFilter(this));
        inputTextFilter.setText(AppPrefs.getTextFilter(this));
        textStatus.setText(isNotificationAccessEnabled()
                ? "دسترسی اعلان‌ها فعال است."
                : "دسترسی اعلان‌ها هنوز فعال نشده است.");
    }

    private void saveSettings() {
        String webhook = inputWebhook.getText().toString().trim();
        if (switchEnabled.isChecked() && !isValidUrl(webhook)) {
            Toast.makeText(this, "آدرس وب‌هوک معتبر وارد کن.", Toast.LENGTH_LONG).show();
            return;
        }
        AppPrefs.save(this, switchEnabled.isChecked(), webhook,
                inputSenderFilter.getText().toString(), inputTextFilter.getText().toString());
        Toast.makeText(this, "تنظیمات ذخیره شد.", Toast.LENGTH_SHORT).show();
    }

    private void openNotificationAccess() {
        try {
            startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
        } catch (Exception exception) {
            startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
        }
    }

    private boolean isNotificationAccessEnabled() {
        String enabled = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        return enabled != null && enabled.contains(getPackageName());
    }

    private void testWebhook() {
        String webhook = inputWebhook.getText().toString().trim();
        if (!isValidUrl(webhook)) {
            Toast.makeText(this, "اول آدرس وب‌هوک معتبر وارد کن.", Toast.LENGTH_LONG).show();
            return;
        }

        saveSettings();
        long timestamp = System.currentTimeMillis();
        long historyId = historyDb.insertPending(timestamp, "BETNET-TEST",
                "پیام آزمایشی Betnet SMS Sender - مبلغ: 12500000 ریال",
                getPackageName());
        textStatus.setText("در حال ارسال تست...");
        WebhookSender.send(this, webhook, "BETNET-TEST",
                "پیام آزمایشی Betnet SMS Sender - مبلغ: 12500000 ریال",
                getPackageName(), timestamp, true, historyId,
                (success, code, response) -> runOnUiThread(() -> {
                    textStatus.setText((success ? "تست موفق" : "تست ناموفق") +
                            " | HTTP " + code + " | " + shorten(response, 220));
                    refreshHistory();
                }));
    }

    private void refreshHistory() {
        if (historyContainer == null) return;
        historyContainer.removeAllViews();
        List<HistoryDb.HistoryItem> items = historyDb.latest(50);
        if (items.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("هنوز پیامی ثبت نشده است.");
            empty.setTextColor(Color.parseColor("#6B7280"));
            empty.setPadding(8, 18, 8, 18);
            historyContainer.addView(empty);
            return;
        }
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.US);
        for (HistoryDb.HistoryItem item : items) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(24, 20, 24, 20);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            params.setMargins(0, 0, 0, 14);
            card.setLayoutParams(params);
            card.setBackgroundColor(Color.WHITE);

            TextView title = new TextView(this);
            title.setText(item.sender + "  •  " + dateFormat.format(new Date(item.createdAt)));
            title.setTextColor(Color.parseColor("#171717"));
            title.setTextSize(16);
            title.setTypeface(null, android.graphics.Typeface.BOLD);

            TextView message = new TextView(this);
            message.setText(shorten(item.message, 500));
            message.setTextColor(Color.parseColor("#374151"));
            message.setTextSize(14);
            message.setPadding(0, 8, 0, 8);

            TextView result = new TextView(this);
            String statusText;
            int statusColor;
            if ("sent".equals(item.status)) {
                statusText = "ارسال شد";
                statusColor = Color.parseColor("#16803C");
            } else if ("failed".equals(item.status)) {
                statusText = "ناموفق";
                statusColor = Color.parseColor("#C62828");
            } else {
                statusText = "در حال ارسال";
                statusColor = Color.parseColor("#6B7280");
            }
            result.setText(statusText + " | HTTP " + item.httpCode + " | " + shorten(item.response, 250));
            result.setTextColor(statusColor);
            result.setTextSize(13);
            result.setGravity(Gravity.START);

            card.addView(title);
            card.addView(message);
            card.addView(result);
            historyContainer.addView(card);
        }
    }

    private boolean isValidUrl(String value) {
        return !TextUtils.isEmpty(value) && (value.startsWith("https://") || value.startsWith("http://"));
    }

    private String shorten(String value, int max) {
        if (value == null) return "";
        String clean = value.trim();
        return clean.length() <= max ? clean : clean.substring(0, max) + "…";
    }
}
