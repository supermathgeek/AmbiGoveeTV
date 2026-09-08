package fr.ambigovee.tv;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.SoundEffectConstants;
import android.widget.TextView;

/**
 * Bouton pense pour Android/Google TV : gros focus visible, animation legere
 * et activation fiable avec DPAD_CENTER/ENTER.
 */
public class TvButton extends TextView {
    private int normalColor = 0xff1a2030;
    private int focusedColor = 0xff6d4aff;
    private int selectedColor = 0xff3b2f63;
    private boolean selectedStyle = false;

    public TvButton(Context context) { super(context); init(); }
    public TvButton(Context context, AttributeSet attrs) { super(context, attrs); init(); }
    public TvButton(Context context, AttributeSet attrs, int defStyleAttr) { super(context, attrs, defStyleAttr); init(); }

    private void init() {
        setFocusable(true);
        setFocusableInTouchMode(true);
        setClickable(true);
        setGravity(Gravity.CENTER);
        setTextColor(Color.WHITE);
        setTextSize(16);
        setTypeface(Typeface.DEFAULT_BOLD);
        setIncludeFontPadding(false);
        setPadding(dp(18), dp(10), dp(18), dp(10));
        if (Build.VERSION.SDK_INT >= 21) setElevation(dp(2));
        setOnFocusChangeListener((v, hasFocus) -> {
            animate().cancel();
            animate().scaleX(hasFocus ? 1.055f : 1f).scaleY(hasFocus ? 1.055f : 1f).setDuration(115).start();
            if (Build.VERSION.SDK_INT >= 21) setTranslationZ(hasFocus ? dp(8) : 0);
            updateBackground(hasFocus);
        });
        updateBackground(false);
    }

    public TvButton colors(int normal, int focused) {
        normalColor = normal;
        focusedColor = focused;
        updateBackground(hasFocus());
        return this;
    }

    public void setSelectedStyle(boolean selected) {
        selectedStyle = selected;
        updateBackground(hasFocus());
    }

    private void updateBackground(boolean focused) {
        int fill = focused ? focusedColor : (selectedStyle ? selectedColor : normalColor);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{lighten(fill, focused ? 0.06f : 0f), fill});
        bg.setCornerRadius(dp(18));
        bg.setStroke(dp(focused ? 2 : 1), focused ? 0xffd8ccff : 0xff30384a);
        setBackground(bg);
        setAlpha(isEnabled() ? 1f : 0.42f);
    }

    @Override public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        updateBackground(hasFocus());
    }

    @Override public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (isEnabled() && (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER || keyCode == KeyEvent.KEYCODE_BUTTON_A)) {
            playSoundEffect(SoundEffectConstants.CLICK);
            return performClick();
        }
        return super.onKeyUp(keyCode, event);
    }

    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + .5f); }

    private static int lighten(int color, float amount) {
        int a = Color.alpha(color);
        int r = Math.min(255, (int)(Color.red(color) + 255 * amount));
        int g = Math.min(255, (int)(Color.green(color) + 255 * amount));
        int b = Math.min(255, (int)(Color.blue(color) + 255 * amount));
        return Color.argb(a, r, g, b);
    }
}
