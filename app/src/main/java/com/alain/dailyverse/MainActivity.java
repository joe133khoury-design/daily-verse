package com.alain.dailyverse;

import android.app.Activity;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private static final int BG = 0xFF0B1224;
    private static final int CARD = 0xFF16203A;
    private static final int GOLD = 0xFFE8C26A;

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private ImageView preview;
    private TextView verseText;
    private Button timeButton;
    private Button photoButton;
    private TextView statusText;
    private Button batteryButton;
    private static final int PICK_PHOTO = 7;
    private Bitmap shownBitmap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        setContentView(buildUi());
        Scheduler.schedule(this);
        refresh();
        // Ask once for background permission — without it Vivo/Xiaomi phones block the morning change
        SharedPreferences sp = VerseManager.prefs(this);
        if (!isBatteryExempt() && !sp.getBoolean("asked_battery", false)) {
            sp.edit().putBoolean("asked_battery", true).apply();
            askBatteryExemption();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        worker.execute(() -> {
            VerseManager.catchUpIfMissed(getApplicationContext());
            main.post(this::refresh);
        });
    }

    @Override
    protected void onDestroy() {
        worker.shutdown();
        super.onDestroy();
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private View buildUi() {
        SharedPreferences p = VerseManager.prefs(this);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(20), dp(24), dp(20), dp(32));
        scroll.addView(col);

        TextView title = new TextView(this);
        title.setText("Daily Verse");
        title.setTextColor(GOLD);
        title.setTextSize(28);
        title.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));
        title.setGravity(Gravity.CENTER);
        col.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("A verse about Jesus Christ on your own lock screen wallpaper, new every morning");
        subtitle.setTextColor(0xFFB8C2D9);
        subtitle.setTextSize(14);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, dp(4), 0, dp(16));
        col.addView(subtitle);

        photoButton = new Button(this);
        photoButton.setAllCaps(false);
        photoButton.setTextSize(16);
        photoButton.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams pblp = fullWidth();
        pblp.bottomMargin = dp(14);
        col.addView(photoButton, pblp);
        photoButton.setOnClickListener(v -> pickPhoto());

        // Preview of the lock screen
        preview = new ImageView(this);
        preview.setAdjustViewBounds(true);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, (int) (getResources().getDisplayMetrics().heightPixels * 0.5f));
        col.addView(preview, plp);

        verseText = new TextView(this);
        verseText.setTextColor(0xFFDDE3F0);
        verseText.setTextSize(13);
        verseText.setGravity(Gravity.CENTER);
        verseText.setPadding(dp(8), dp(10), dp(8), dp(6));
        col.addView(verseText);

        // Previous / Next
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        Button prev = smallButton("◀  Previous");
        Button next = smallButton("Next  ▶");
        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        half.setMargins(dp(4), dp(4), dp(4), dp(4));
        nav.addView(prev, half);
        nav.addView(next, new LinearLayout.LayoutParams(half));
        col.addView(nav);
        prev.setOnClickListener(v -> { VerseManager.setIndex(this, VerseManager.currentIndex(this) - 1); refresh(); });
        next.setOnClickListener(v -> { VerseManager.setIndex(this, VerseManager.currentIndex(this) + 1); refresh(); });

        Button apply = new Button(this);
        apply.setText("Set on lock screen now");
        apply.setAllCaps(false);
        apply.setTextSize(17);
        apply.setTextColor(0xFF1B1405);
        apply.setTypeface(Typeface.DEFAULT_BOLD);
        apply.setBackground(rounded(GOLD));
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(54));
        alp.setMargins(dp(4), dp(10), dp(4), dp(18));
        col.addView(apply, alp);
        apply.setOnClickListener(v -> applyNow());

        // Settings card
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(12), dp(16), dp(12));
        card.setBackground(rounded(CARD));
        col.addView(card);

        Switch daily = new Switch(this);
        daily.setText("New verse every morning");
        daily.setTextColor(Color.WHITE);
        daily.setTextSize(16);
        daily.setChecked(p.getBoolean(VerseManager.K_ENABLED, true));
        daily.setPadding(0, dp(8), 0, dp(8));
        card.addView(daily);
        daily.setOnCheckedChangeListener((b, on) -> {
            VerseManager.prefs(this).edit().putBoolean(VerseManager.K_ENABLED, on).apply();
            Scheduler.schedule(this);
            toast(on ? "Daily verse turned on" : "Daily verse turned off");
        });

        timeButton = smallButton("");
        card.addView(timeButton, fullWidth());
        timeButton.setOnClickListener(v -> pickTime());

        Button restore = smallButton("Remove verse (show my photo only)");
        card.addView(restore, fullWidth());
        restore.setOnClickListener(v -> {
            daily.setChecked(false);
            worker.execute(() -> {
                boolean ok = VerseManager.restorePhoto(getApplicationContext());
                main.post(() -> toast(ok ? "Your photo is back without a verse" : "Choose your wallpaper photo first"));
            });
        });

        statusText = new TextView(this);
        statusText.setTextColor(0xFFB8C2D9);
        statusText.setTextSize(13);
        statusText.setPadding(dp(4), dp(12), dp(4), dp(4));
        card.addView(statusText);

        // Vivo / Xiaomi / Huawei stop apps in the background. These two buttons let the morning change run.
        TextView bgTitle = new TextView(this);
        bgTitle.setText("Important for the automatic morning change:");
        bgTitle.setTextColor(GOLD);
        bgTitle.setTextSize(14);
        bgTitle.setTypeface(Typeface.DEFAULT_BOLD);
        bgTitle.setPadding(0, dp(18), 0, dp(4));
        col.addView(bgTitle);

        batteryButton = smallButton("1. Allow running in background (battery)");
        col.addView(batteryButton, fullWidth());
        batteryButton.setOnClickListener(v -> askBatteryExemption());

        Button appSettings = smallButton("2. Open app settings → allow Auto-start / High background power use");
        col.addView(appSettings, fullWidth());
        appSettings.setOnClickListener(v -> {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()));
            startActivity(i);
        });

        Button share = smallButton("Share this verse");
        LinearLayout.LayoutParams slp = fullWidth();
        slp.topMargin = dp(16);
        col.addView(share, slp);
        share.setOnClickListener(v -> {
            int i = VerseManager.currentIndex(this);
            Intent s = new Intent(Intent.ACTION_SEND);
            s.setType("text/plain");
            s.putExtra(Intent.EXTRA_TEXT, "\"" + Verses.text(i) + "\"\n— " + Verses.ref(i));
            startActivity(Intent.createChooser(s, "Share verse"));
        });

        TextView footer = new TextView(this);
        footer.setText("Scripture: World English Bible · " + Verses.count() + " verses");
        footer.setTextColor(0xFF6F7A94);
        footer.setTextSize(12);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, dp(18), 0, 0);
        col.addView(footer);

        return scroll;
    }

    private LinearLayout.LayoutParams fullWidth() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(4), 0, dp(4));
        return lp;
    }

    private Button smallButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(15);
        b.setBackground(rounded(0xFF24304F));
        b.setPadding(dp(12), dp(10), dp(12), dp(10));
        return b;
    }

    private GradientDrawable rounded(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(14));
        return d;
    }

    private void refresh() {
        SharedPreferences p = VerseManager.prefs(this);
        int i = VerseManager.currentIndex(this);
        verseText.setText(Verses.ref(i) + "   ·   verse " + (i + 1) + " of " + Verses.count());
        timeButton.setText(String.format(Locale.getDefault(), "Change every morning at  %02d:%02d",
            p.getInt(VerseManager.K_HOUR, 6), p.getInt(VerseManager.K_MINUTE, 0)));
        long last = p.getLong(VerseManager.K_LAST_AUTO, 0);
        statusText.setText(last == 0
            ? "Last automatic change: not yet — it will happen at the time above"
            : "Last automatic change: " + new SimpleDateFormat("EEE d MMM, HH:mm", Locale.getDefault()).format(new Date(last)));
        boolean exempt = isBatteryExempt();
        batteryButton.setText(exempt ? "1. Background running allowed ✓" : "1. Allow running in background (battery)");
        batteryButton.setBackground(rounded(exempt ? 0xFF1F4A33 : 0xFF7A2E2E));

        boolean has = VerseManager.hasPhoto(this);
        photoButton.setText(has ? "Change my wallpaper photo" : "Step 1: Choose my wallpaper photo");
        photoButton.setTextColor(has ? Color.WHITE : 0xFF1B1405);
        photoButton.setBackground(rounded(has ? 0xFF24304F : GOLD));

        worker.execute(() -> {
            int[] size = VerseManager.screenSize(getApplicationContext());
            // Render at half size for the preview — same look, less memory
            Bitmap full = VerseManager.render(getApplicationContext(), i);
            Bitmap small = Bitmap.createScaledBitmap(full, size[0] / 2, size[1] / 2, true);
            full.recycle();
            main.post(() -> {
                if (shownBitmap != null) preview.setImageDrawable(null);
                shownBitmap = small;
                preview.setImageBitmap(small);
            });
        });
    }

    private void applyNow() {
        final int i = VerseManager.currentIndex(this);
        if (!VerseManager.hasPhoto(this)) {
            toast("First choose your wallpaper photo");
            pickPhoto();
            return;
        }
        toast("Setting your lock screen…");
        worker.execute(() -> {
            boolean ok = VerseManager.applyIndex(getApplicationContext(), i);
            if (ok) VerseManager.markAppliedToday(getApplicationContext());
            main.post(() -> toast(ok ? "Done! Lock your phone to see it." : "Couldn't set the wallpaper on this phone."));
        });
    }

    private boolean isBatteryExempt() {
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        return pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
    }

    private void askBatteryExemption() {
        if (isBatteryExempt()) {
            toast("Already allowed");
            return;
        }
        try {
            Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        }
    }

    private void pickPhoto() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        startActivityForResult(i, PICK_PHOTO);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_PHOTO || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        final android.net.Uri uri = data.getData();
        toast("Saving your photo…");
        worker.execute(() -> {
            boolean ok = VerseManager.savePhoto(getApplicationContext(), uri);
            main.post(() -> {
                toast(ok ? "Photo saved. Tap \"Set on lock screen now\"" : "Couldn't open that photo, try another one");
                refresh();
            });
        });
    }

    private void pickTime() {
        SharedPreferences p = VerseManager.prefs(this);
        new TimePickerDialog(this, (view, hour, minute) -> {
            p.edit().putInt(VerseManager.K_HOUR, hour).putInt(VerseManager.K_MINUTE, minute).apply();
            Scheduler.schedule(this);
            refresh();
            toast(String.format(Locale.getDefault(), "New verse every day at %02d:%02d", hour, minute));
        }, p.getInt(VerseManager.K_HOUR, 6), p.getInt(VerseManager.K_MINUTE, 0), true).show();
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
