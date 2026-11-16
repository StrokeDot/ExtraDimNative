package com.strokedot.extradim;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

public class PhoneIllustrationView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    public PhoneIllustrationView(Context c) { super(c); }
    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(dp(284), MeasureSpec.EXACTLY));
    }
    private int dp(float v) { return (int)(v * getResources().getDisplayMetrics().density + .5f); }
    private void round(Canvas c, float l, float t, float r, float b, float radius, int color) {
        p.setColor(color); p.setStyle(Paint.Style.FILL);
        c.drawRoundRect(new RectF(l,t,r,b),radius,radius,p);
    }
    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        c.save();
        c.translate((getWidth() - dp(142)) / 2f, dp(8));
        float scale = dp(142)/142f;
        c.scale(scale,scale);
        round(c,0,0,142,270,12,0xFFE5E5E5);
        round(c,2,2,140,268,11,0xFF686868);
        round(c,45,25,125,54,4,0xFF505050);
        round(c,45,59,125,100,4,0xFF505050);
        round(c,16,114,98,143,5,0xFF444444);
        round(c,16,148,98,191,5,0xFF444444);
        round(c,140,64,145,78,2,0xFFBABABA);
        round(c,140,92,145,125,2,0xFFBABABA);
        p.setColor(0xFF174A87); c.drawCircle(110,240,13,p);
        c.restore();
    }
}
