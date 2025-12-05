package com.example.android.notepad;

import android.app.Activity;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.TextView;

/**
 * 主题管理器，负责管理应用的背景颜色切换和字体大小调整
 */
public class ThemeManager {
    private static final String KEY_BACKGROUND_COLOR = "background_color";
    private static final String KEY_FONT_SIZE = "font_size";
    
    // 背景颜色常量
    public static final int BACKGROUND_WHITE = 0;
    public static final int BACKGROUND_BLUE = 1;
    public static final int BACKGROUND_YELLOW = 2;
    public static final int BACKGROUND_PINK = 3;
    public static final int BACKGROUND_GREEN = 4;
    
    // 字体大小常量
    public static final int FONT_SIZE_SMALL = 0;
    public static final int FONT_SIZE_MEDIUM = 1;
    public static final int FONT_SIZE_LARGE = 2;
    public static final int FONT_SIZE_XLARGE = 3;
    
    // 字体大小值（单位：sp）
    private static final float[] FONT_SIZES = {14f, 16f, 18f, 20f};
    
    /**
     * 获取当前背景颜色模式
     */
    public static int getBackgroundColorMode(Activity activity) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(activity);
        return preferences.getInt(KEY_BACKGROUND_COLOR, BACKGROUND_WHITE);
    }
    
    /**
     * 设置背景颜色模式
     */
    public static void setBackgroundColorMode(Activity activity, int mode) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(activity);
        preferences.edit().putInt(KEY_BACKGROUND_COLOR, mode).apply();
        applyBackgroundColor(activity);
    }
    
    /**
     * 获取当前字体大小模式
     */
    public static int getFontSizeMode(Activity activity) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(activity);
        return preferences.getInt(KEY_FONT_SIZE, FONT_SIZE_MEDIUM);
    }
    
    /**
     * 设置字体大小模式
     */
    public static void setFontSizeMode(Activity activity, int mode) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(activity);
        preferences.edit().putInt(KEY_FONT_SIZE, mode).apply();
    }
    
    /**
     * 获取当前字体大小值（单位：sp）
     */
    public static float getFontSizeValue(Activity activity) {
        int mode = getFontSizeMode(activity);
        return FONT_SIZES[mode];
    }
    
    /**
     * 应用当前背景颜色
     */
    public static void applyBackgroundColor(Activity activity) {
        // 始终使用默认浅色主题
        activity.setTheme(R.style.NotePadTheme);
        
        // 设置状态栏颜色
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            activity.getWindow().setStatusBarColor(activity.getResources().getColor(R.color.primary_dark));
        }
        
        // 设置Activity的背景颜色
        View rootView = activity.getWindow().getDecorView().getRootView();
        if (rootView != null) {
            int backgroundColor = getBackgroundColorResId(activity);
            rootView.setBackgroundColor(activity.getResources().getColor(backgroundColor));
        }
    }
    
    /**
     * 应用当前字体大小到指定的EditText
     */
    public static void applyFontSize(EditText editText, Activity activity) {
        float fontSize = getFontSizeValue(activity);
        editText.setTextSize(fontSize);
    }
    
    /**
     * 应用当前字体大小到指定的TextView
     */
    public static void applyFontSize(TextView textView, Activity activity) {
        float fontSize = getFontSizeValue(activity);
        textView.setTextSize(fontSize);
    }
    
    /**
     * 获取当前背景颜色的资源ID
     */
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
    
    /**
     * 应用背景颜色（兼容旧方法名，确保原有功能正常）
     */
    public static void applyTheme(Activity activity) {
        applyBackgroundColor(activity);
    }
}