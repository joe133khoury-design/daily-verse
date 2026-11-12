package com.alain.dailyverse;

import android.app.WallpaperManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.hardware.display.DisplayManager;
import android.media.ExifInterface;
import android.net.Uri;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.DisplayMetrics;
import android.view.Display;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Calendar;
import java.util.Random;

/** Holds the app's settings, picks the verse of the day, draws it and sets the wallpaper. */
public final class VerseManager {
    private VerseManager() {}

    private static final String PREFS = "daily_verse";
    static final String K_INDEX = "index";
    static final String K_LAST_DAY = "last_day";
    static final String K_HOUR = "hour";
    static final String K_MINUTE = "minute";
    static final String K_ENABLED = "enabled";
    static final String K_HOME = "also_home";
    static final String K_STYLE = "style"; // -1 = auto (changes every day)

    /** {name, top colour, bottom colour, accent colour} */
    static final String[] STYLE_NAMES = {"Night Blue", "Sunrise", "Forest", "Royal Purple", "Ocean", "Charcoal"};
    private static final int[][] STYLES = {
        {0xFF0F1A33, 0xFF2B3F6B, 0xFFE8C26A},
        {0xFF3A1C4A, 0xFFD9765F, 0xFFFFE2A8},
        {0xFF0E2A22, 0xFF2F5D4A, 0xFFD8E8B0},
        {0xFF1E0F3A, 0xFF5B2C83, 0xFFF2C9FF},
        {0xFF062B3D, 0xFF1C6E8C, 0xFFB8F1FF},
        {0xFF111111, 0xFF3A3A3A, 0xFFE8C26A},
    };

    static SharedPreferences prefs(Context c) {
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!p.contains(K_INDEX)) {
            p.edit()
                .putInt(K_INDEX, new Random().nextInt(Verses.count()))
                .putInt(K_HOUR, 6)
                .putInt(K_MINUTE, 0)
                .putBoolean(K_ENABLED, true)
                .putBoolean(K_HOME, false)
                .putInt(K_STYLE, -1)
                .apply();
        }
        return p;
    }

    static int todayKey() {
        Calendar c = Calendar.getInstance();
        return c.get(Calendar.YEAR) * 1000 + c.get(Calendar.DAY_OF_YEAR);
    }

    static int currentIndex(Context c) {
        return prefs(c).getInt(K_INDEX, 0);
    }

    static void setIndex(Context c, int index) {
        prefs(c).edit().putInt(K_INDEX, Math.floorMod(index, Verses.count())).apply();
    }

    static int styleFor(Context c, int index) {
        int s = prefs(c).getInt(K_STYLE, -1);
        if (s < 0) s = Math.floorMod(index, STYLES.length);
        return s;
    }

    /** Called by the morning alarm: moves to a new verse once per day and sets it. */
    static synchronized boolean advanceAndApply(Context c) {
        SharedPreferences p = prefs(c);
        int today = todayKey();
        if (!hasPhoto(c)) return false; // never replace the wallpaper before the user picks their photo
        if (p.getInt(K_LAST_DAY, 0) == today) return false;
        int index = p.getInt(K_INDEX, 0);
        if (p.getInt(K_LAST_DAY, 0) != 0) index = Math.floorMod(index + 1, Verses.count());
        p.edit().putInt(K_INDEX, index).apply();
        if (applyIndex(c, index)) {
            p.edit().putInt(K_LAST_DAY, today).apply();
            return true;
        }
        return false;
    }

    /** If the phone missed this morning's alarm (switched off, battery saver...), catch up now. */
    static void catchUpIfMissed(Context c) {
        SharedPreferences p = prefs(c);
        if (!p.getBoolean(K_ENABLED, true)) return;
        if (p.getInt(K_LAST_DAY, 0) == todayKey()) return;
        Calendar now = Calendar.getInstance();
        int minutesNow = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        int minutesSet = p.getInt(K_HOUR, 6) * 60 + p.getInt(K_MINUTE, 0);
        if (minutesNow >= minutesSet) advanceAndApply(c);
    }

    /** Sets the given verse on the lock screen (and home screen if chosen). */
    static boolean applyIndex(Context c, int index) {
        try {
            Bitmap bmp = render(c, index);
            WallpaperManager wm = WallpaperManager.getInstance(c);
            // Lock screen only — the home screen wallpaper is never touched.
            wm.setBitmap(bmp, null, true, WallpaperManager.FLAG_LOCK);
            bmp.recycle();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    static void markAppliedToday(Context c) {
        prefs(c).edit().putInt(K_LAST_DAY, todayKey()).apply();
    }

    static int[] screenSize(Context c) {
        DisplayMetrics dm = new DisplayMetrics();
        DisplayManager dmg = (DisplayManager) c.getSystemService(Context.DISPLAY_SERVICE);
        Display d = dmg.getDisplay(Display.DEFAULT_DISPLAY);
        d.getRealMetrics(dm);
        int w = Math.min(dm.widthPixels, dm.heightPixels);
        int h = Math.max(dm.widthPixels, dm.heightPixels);
        return new int[]{w, h};
    }

    // ---------- The user's own wallpaper photo ----------

    static File photoFile(Context c) {
        return new File(c.getFilesDir(), "my_wallpaper.jpg");
    }

    static boolean hasPhoto(Context c) {
        return photoFile(c).exists();
    }

    /** Copies the chosen photo into the app (rotated upright, resized to the screen) so the
     *  verse is always drawn on the clean original — verses never pile up on each other. */
    static boolean savePhoto(Context c, Uri uri) {
        try {
            int[] size = screenSize(c);
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            try (InputStream in = c.getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(in, null, o);
            }
            int sample = 1;
            while (o.outWidth / (sample * 2) >= size[0] && o.outHeight / (sample * 2) >= size[1]) sample *= 2;
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = sample;
            Bitmap bmp;
            try (InputStream in = c.getContentResolver().openInputStream(uri)) {
                bmp = BitmapFactory.decodeStream(in, null, o2);
            }
            if (bmp == null) return false;

            int rotate = 0;
            try (InputStream in = c.getContentResolver().openInputStream(uri)) {
                int ori = new ExifInterface(in).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                if (ori == ExifInterface.ORIENTATION_ROTATE_90) rotate = 90;
                else if (ori == ExifInterface.ORIENTATION_ROTATE_180) rotate = 180;
                else if (ori == ExifInterface.ORIENTATION_ROTATE_270) rotate = 270;
            } catch (Exception ignored) {}
            if (rotate != 0) {
                Matrix m = new Matrix();
                m.postRotate(rotate);
                Bitmap r = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), m, true);
                bmp.recycle();
                bmp = r;
            }

            Bitmap fitted = centerCrop(bmp, size[0], size[1]);
            bmp.recycle();
            try (FileOutputStream out = new FileOutputStream(photoFile(c))) {
                fitted.compress(Bitmap.CompressFormat.JPEG, 95, out);
            }
            fitted.recycle();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    static Bitmap centerCrop(Bitmap src, int w, int h) {
        float scale = Math.max(w / (float) src.getWidth(), h / (float) src.getHeight());
        int cw = Math.round(w / scale), ch = Math.round(h / scale);
        int x = Math.max(0, (src.getWidth() - cw) / 2), y = Math.max(0, (src.getHeight() - ch) / 2);
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        new Canvas(out).drawBitmap(src, new Rect(x, y, x + Math.min(cw, src.getWidth()), y + Math.min(ch, src.getHeight())),
            new Rect(0, 0, w, h), new Paint(Paint.FILTER_BITMAP_FLAG));
        return out;
    }

    /** Puts the user's photo back on the lock screen without any verse. */
    static boolean restorePhoto(Context c) {
        try {
            if (!hasPhoto(c)) return false;
            Bitmap bmp = BitmapFactory.decodeFile(photoFile(c).getAbsolutePath());
            WallpaperManager.getInstance(c).setBitmap(bmp, null, true, WallpaperManager.FLAG_LOCK);
            bmp.recycle();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    static Bitmap render(Context c, int index) {
        int[] size = screenSize(c);
        Bitmap base = null;
        if (hasPhoto(c)) {
            Bitmap photo = BitmapFactory.decodeFile(photoFile(c).getAbsolutePath());
            if (photo != null) {
                base = centerCrop(photo, size[0], size[1]);
                photo.recycle();
            }
        }
        return render(size[0], size[1], Verses.text(index), Verses.ref(index), STYLES[styleFor(c, index)], base);
    }

    static Bitmap render(int w, int h, String text, String ref, int[] style, Bitmap photo) {
        Bitmap bmp = photo != null ? photo : Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bmp);
        int top = style[0], bottom = style[1];
        int accent = photo != null ? 0xFFFFE6A8 : style[2];

        if (photo != null) {
            // Your own wallpaper stays as it is; only a soft shade behind the words so they are readable.
            Paint shade = new Paint();
            shade.setShader(new LinearGradient(0, h * 0.30f, 0, h * 0.90f,
                new int[]{0x00000000, 0x8C000000, 0x8C000000, 0x00000000},
                new float[]{0f, 0.25f, 0.75f, 1f}, Shader.TileMode.CLAMP));
            cv.drawRect(0, 0, w, h, shade);
        } else {
            // No photo chosen yet: plain colour background
            Paint bg = new Paint();
            bg.setShader(new LinearGradient(0, 0, w * 0.4f, h, top, bottom, Shader.TileMode.CLAMP));
            cv.drawRect(0, 0, w, h, bg);
            Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
            int glowColor = (accent & 0x00FFFFFF) | 0x30000000;
            glow.setShader(new RadialGradient(w / 2f, h * 0.58f, w * 0.85f, glowColor, 0x00000000, Shader.TileMode.CLAMP));
            cv.drawRect(0, 0, w, h, glow);
        }

        // Verse text
        TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        tp.setColor(0xFFFFFFFF);
        tp.setTypeface(Typeface.create(Typeface.SERIF, Typeface.NORMAL));
        float textSize = w * 0.045f;
        int textWidth = (int) (w * 0.80f);
        StaticLayout layout;
        while (true) {
            tp.setTextSize(textSize);
            tp.setShadowLayer(textSize * 0.35f, 0, 2, 0xCC000000);
            layout = StaticLayout.Builder.obtain(text, 0, text.length(), tp, textWidth)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setLineSpacing(0f, 1.2f)
                .build();
            if (layout.getHeight() <= h * 0.30f || textSize < w * 0.030f) break;
            textSize *= 0.93f;
        }

        float refSize = Math.max(textSize * 0.68f, w * 0.026f);
        float crossSize = textSize * 0.9f;
        float gap = textSize * 0.9f;
        float block = crossSize + gap + layout.getHeight() + gap + refSize * 1.4f;
        // Centred a little below the middle so the lock-screen clock stays clear
        float y = h * 0.58f - block / 2f;

        // Small cross
        Paint ap = new Paint(Paint.ANTI_ALIAS_FLAG);
        ap.setColor(accent);
        float cx = w / 2f, bar = crossSize * 0.14f;
        cv.drawRect(cx - bar / 2f, y, cx + bar / 2f, y + crossSize, ap);
        cv.drawRect(cx - crossSize * 0.32f, y + crossSize * 0.28f, cx + crossSize * 0.32f, y + crossSize * 0.28f + bar, ap);
        y += crossSize + gap;

        cv.save();
        cv.translate((w - textWidth) / 2f, y);
        layout.draw(cv);
        cv.restore();
        y += layout.getHeight() + gap * 0.7f;

        // Divider line + reference
        ap.setStrokeWidth(Math.max(2f, w * 0.0025f));
        cv.drawLine(cx - w * 0.07f, y, cx + w * 0.07f, y, ap);
        Paint rp = new Paint(Paint.ANTI_ALIAS_FLAG);
        rp.setColor(accent);
        rp.setTextSize(refSize);
        rp.setTextAlign(Paint.Align.CENTER);
        rp.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        rp.setLetterSpacing(0.12f);
        cv.drawText(ref.toUpperCase(), cx, y + refSize * 1.5f, rp);

        return bmp;
    }
}
