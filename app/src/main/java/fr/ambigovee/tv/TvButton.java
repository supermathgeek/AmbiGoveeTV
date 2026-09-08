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
 * Bouton Android TV :
 * - focus net à plusieurs mètres ;
 * - animation légère qui ne fait pas "sauter" la grille ;
 * - sélection persistante pour les modes / états ;
 * - compatible DPAD_CENTER, ENTER et bouton A.
 */
public class TvButton extends TextView {
    private int normalColor = 0xff151b27;
    private int focusedColor = 0xff7357ff;
    private int selectedColor = 0xff292044;
    private boolean selectedStyle = false;

    public TvButton(Context context) {
        super(context);
        init();
    }

    public TvButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public TvButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setFocusable(true);
        setFocusableInTouchMode(true);
        setClickable(true);
        setGravity(Gravity.CENTER);
        setTextColor(0xfff6f7fb);
        setTextSize(15);
        setTypeface(Typeface.DEFAULT_BOLD);
        setIncludeFontPadding(false);
        setPadding(dp(18), dp(10), dp(18), dp(10));

        if (Build.VERSION.SDK_INT >= 21) {
            setElevation(dp(2));
        }

        setOnFocusChangeListener((v, hasFocus) -> {
            animate().cancel();
            animate()
                    .scaleX(hasFocus ? 1.028f : 1f)
                    .scaleY(hasFocus ? 1.028f : 1f)
                    .setDuration(110)
                    .start();

            if (Build.VERSION.SDK_INT >= 21) {
                setTranslationZ(hasFocus ? dp(9) : 0);
            }

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

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        updateBackground(hasFocus());
    }

    private void updateBackground(boolean focused) {
        int fill = focused ? focusedColor : (selectedStyle ? selectedColor : normalColor);
        int top = lighten(fill, focused ? 0.07f : 0.025f);

        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{top, fill}
        );

        bg.setCornerRadius(dp(16));

        if (focused) {
            bg.setStroke(dp(2), 0xffded7ff);
        } else if (selectedStyle) {
            bg.setStroke(dp(1), 0xff6955a8);
        } else {
            bg.setStroke(dp(1), 0xff2b3446);
        }

        setBackground(bg);
        setTextColor(isEnabled() ? 0xfff6f7fb : 0xff8992a2);
        setAlpha(isEnabled() ? 1f : 0.48f);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (isEnabled()
                && (keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
                || keyCode == KeyEvent.KEYCODE_BUTTON_A)) {

            playSoundEffect(SoundEffectConstants.CLICK);
            return performClick();
        }

        return super.onKeyUp(keyCode, event);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static int lighten(int color, float amount) {
        int a = Color.alpha(color);
        int r = Math.min(255, (int) (Color.red(color) + 255 * amount));
        int g = Math.min(255, (int) (Color.green(color) + 255 * amount));
        int b = Math.min(255, (int) (Color.blue(color) + 255 * amount));
        return Color.argb(a, r, g, b);
    }
}
