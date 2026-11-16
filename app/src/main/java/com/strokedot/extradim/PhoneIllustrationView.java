package com.strokedot.extradim;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

/** A decorative, continuously breathing brightness preview: no system setting writes. */
public class PhoneIllustrationView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private ValueAnimator animator;
    private float phase = .5f;
    private boolean warm;

    public PhoneIllustrationView(Context c) { super(c); }
    public void setWarmthEnabled(boolean value) {
        if (warm != value) { warm = value; invalidate(); }
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        animator=ValueAnimator.ofFloat(0f,1f);
        animator.setDuration(2100L);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setRepeatMode(ValueAnimator.REVERSE);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        animator.addUpdateListener(a -> {
            phase=(float)a.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }
    @Override protected void onDetachedFromWindow() {
        if (animator!=null) { animator.cancel(); animator=null; }
        super.onDetachedFromWindow();
    }

    @Override protected void onMeasure(int w,int h) {
        super.onMeasure(w,MeasureSpec.makeMeasureSpec(dp(256),MeasureSpec.EXACTLY));
    }
    private int dp(float v) {
        return (int)(v * getResources().getDisplayMetrics().density + .5f);
    }
    private void round(Canvas c,float l,float t,float r,float b,float radius,int color) {
        p.setStyle(Paint.Style.FILL);p.setColor(color);
        c.drawRoundRect(new RectF(l,t,r,b),radius,radius,p);
    }
    private int blend(int from,int to,float progress) {
        return Color.rgb(
            Math.round(Color.red(from)+(Color.red(to)-Color.red(from))*progress),
            Math.round(Color.green(from)+(Color.green(to)-Color.green(from))*progress),
            Math.round(Color.blue(from)+(Color.blue(to)-Color.blue(from))*progress));
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.save();
        float scale=dp(140)/140f;
        canvas.translate((getWidth()-dp(140))/2f,dp(5));
        canvas.scale(scale,scale);
        round(canvas,0,0,140,240,17,0xFFDCE5F4);
        int dark=warm?0xFF37281C:0xFF1C2539;
        int light=warm?0xFFF6C794:0xFFE1F0FF;
        round(canvas,3,3,137,237,15,blend(dark,light,phase*.78f));
        round(canvas,38,23,122,55,6,blend(0xFF2B3241,0xFF95AEC4,phase*.8f));
        round(canvas,38,62,122,95,6,blend(0xFF32394A,0xFFB4C6D3,phase*.76f));
        round(canvas,18,108,106,139,6,blend(0xFF353D51,0xFFABBFD2,phase*.83f));
        round(canvas,18,147,106,195,6,blend(0xFF323A4C,0xFFC1CEDC,phase*.9f));
        round(canvas,138,63,143,77,3,0xFF7E8C9D);
        round(canvas,138,91,143,123,3,0xFF7E8C9D);
        p.setColor(warm?0xFFE5A654:0xFF7A9CFF);
        canvas.drawCircle(111,210,12,p);
        canvas.restore();
    }
}
