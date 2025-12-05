package com.example.android.notepad;

import android.app.Activity;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.TextView;

public class ThemeManager {
    private static final String KEY_BACKGROUND_COLOR = "background_color";
    private static final String KEY_FONT_SIZE = "font_size";

    public static final int BACKGROUND_WHITE = 0;
    public static final int BACKGROUND_BLUE = 1;
    public static final int BACKGROUND_YELLOW = 2;
    public static final int BACKGROUND_PINK = 3;
    public static final int BACKGROUND_GREEN = 4;

    public static final int FONT_SIZE_SMALL = 0;
    public static final int FONT_SIZE_MEDIUM = 1;
    public static final int FONT_SIZE_LARGE = 2;
    public static final int FONT_SIZE_XLARGE = 3;

    private static final float[] FONT_SIZES = {14f, 16f, 18f, 20f};

    public static int getBackgroundColorMode(Activity activity) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(activity);
        return preferences.getInt(KEY_BACKGROUND_COLOR, BACKGROUND_WHITE);
    }

    public static void setBackgroundColorMode(Activity activity, int mode) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(activity);
        preferences.edit().putInt(KEY_BACKGROUND_COLOR, mode).apply();
        applyBackgroundColor(activity);
    }

    public static int getFontSizeMode(Activity activity) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(activity);
        return preferences.getInt(KEY_FONT_SIZE, FONT_SIZE_MEDIUM);
    }

    public static void setFontSizeMode(Activity activity, int mode) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(activity);
        preferences.edit().putInt(KEY_FONT_SIZE, mode).apply();
    }

    public static float getFontSizeValue(Activity activity) {
        int mode = getFontSizeMode(activity);
        return FONT_SIZES[mode];
    }

    public static void applyBackgroundColor(Activity activity) {
        activity.setTheme(R.style.NotePadTheme);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            activity.getWindow().setStatusBarColor(activity.getResources().getColor(R.color.primary_dark));
        }

        View rootView = activity.getWindow().getDecorView().getRootView();
        if (rootView != null) {
            int backgroundColor = getBackgroundColorResId(activity);
            rootView.setBackgroundColor(activity.getResources().getColor(backgroundColor));
        }
    }

    public static void applyFontSize(EditText editText, Activity activity) {
        float fontSize = getFontSizeValue(activity);
        editText.setTextSize(fontSize);
    }

    public static void applyFontSize(TextView textView, Activity activity) {
        float fontSize = getFontSizeValue(activity);
        textView.setTextSize(fontSize);
    }

    private static int getBackgroundColorResId(Activity activity) {
        int mode = getBackgroundColorMode(activity);

        switch (mode) {
            case BACKGROUND_WHITE:
                return R.color.background_white;
            case BACKGROUND_BLUE:
                return R.color.background_blue;
            case BACKGROUND_YELLOW:
                return R.color.background_yellow;
            case BACKGROUND_PINK:
                return R.color.background_pink;
            case BACKGROUND_GREEN:
                return R.color.background_green;
            default:
                return R.color.background_white;
        }
    }

    public static void applyTheme(Activity activity) {
        applyBackgroundColor(activity);
    }
}