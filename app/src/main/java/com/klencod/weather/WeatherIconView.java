package com.klencod.weather;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

/**
 * رسام أيقونات الطقس داخل Java فقط.
 * تعتمد الأيقونات على طبقات ولون وعمق بسيط لتبقى واضحة فوق خلفيات الفيديو.
 */
public final class WeatherIconView extends View {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int weatherCode;
    private boolean day = true;

    public WeatherIconView(Context context) {
        super(context);
        setWillNotDraw(false);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        shadow.setStyle(Paint.Style.FILL);
    }

    public void setWeather(int code, boolean isDay) {
        weatherCode = code;
        day = isDay;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float size = Math.min(getWidth(), getHeight());
        if (size <= 0) return;

        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float scale = size / 100f;
        canvas.save();
        canvas.translate(cx, cy);
        canvas.scale(scale, scale);

        if (weatherCode == -1 || weatherCode == 0) {
            if (day) drawSun(canvas, 0f, 0f, 22f);
            else drawMoon(canvas, 0f, 0f, 23f);
        } else if (weatherCode == -2) {
            drawMoon(canvas, 0f, 0f, 23f);
        } else if (weatherCode == -3 || weatherCode == 1) {
            drawSunOrMoonBehindCloud(canvas, -20f, -16f, 16f);
            drawCloud(canvas, 9f, 11f, 0.95f);
        } else if (weatherCode == -4 || weatherCode == 2) {
            drawCloud(canvas, -9f, 8f, 0.82f);
            drawCloud(canvas, 15f, 12f, 0.78f);
        } else if (weatherCode == -5 || weatherCode == 3) {
            drawCloud(canvas, 0f, -7f, 1.05f);
        } else if (weatherCode == -6 || (weatherCode >= 4 && weatherCode <= 48)) {
            drawFog(canvas);
        } else if (weatherCode == -7 || (weatherCode >= 51 && weatherCode <= 57)) {
            drawCloud(canvas, 0f, -10f, 1f);
            drawRain(canvas, -14f, 25f, 3, 11f, false);
        } else if (weatherCode == -8 || (weatherCode >= 61 && weatherCode <= 67)
                || (weatherCode >= 80 && weatherCode <= 82)) {
            drawCloud(canvas, 0f, -11f, 1f);
            drawRain(canvas, -17f, 24f, 4, 10f, false);
        } else if (weatherCode == -9 || (weatherCode >= 71 && weatherCode <= 77)) {
            drawCloud(canvas, 0f, -11f, 1f);
            drawSnow(canvas);
        } else if (weatherCode == -10 || weatherCode >= 95) {
            drawCloud(canvas, 0f, -12f, 1f);
            drawRain(canvas, -17f, 23f, 3, 11f, true);
            drawLightning(canvas);
        } else if (weatherCode == -11) {
            drawDust(canvas);
        } else if (weatherCode == -12) {
            drawSandstorm(canvas);
        } else {
            drawCloud(canvas, 0f, -11f, 1f);
        }

        canvas.restore();
    }

    private void drawSunOrMoonBehindCloud(Canvas canvas, float x, float y, float radius) {
        if (day) drawSun(canvas, x, y, radius);
        else drawMoon(canvas, x, y, radius);
    }

    private void drawSun(Canvas canvas, float x, float y, float radius) {
        stroke.setShader(null);
        stroke.setColor(Color.argb(210, 255, 221, 126));
        stroke.setStrokeWidth(4.2f);
        for (int i = 0; i < 8; i++) {
            double angle = Math.PI * 2d * i / 8d;
            float x1 = x + (float) Math.cos(angle) * (radius + 9f);
            float y1 = y + (float) Math.sin(angle) * (radius + 9f);
            float x2 = x + (float) Math.cos(angle) * (radius + 17f);
            float y2 = y + (float) Math.sin(angle) * (radius + 17f);
            canvas.drawLine(x1, y1, x2, y2, stroke);
        }

        fill.setStyle(Paint.Style.FILL);
        fill.setShader(new RadialGradient(x - radius * 0.28f, y - radius * 0.32f,
                radius * 1.25f, new int[]{Color.rgb(255, 244, 190), Color.rgb(255, 186, 64)},
                new float[]{0f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawCircle(x, y, radius, fill);
        fill.setShader(null);
    }

    private void drawMoon(Canvas canvas, float x, float y, float radius) {
        fill.setStyle(Paint.Style.FILL);
        fill.setShader(null);
        fill.setColor(Color.rgb(246, 224, 157));
        Path crescent = new Path();
        crescent.moveTo(x + radius * 0.30f, y - radius * 0.96f);
        crescent.cubicTo(x - radius * 0.55f, y - radius * 0.72f,
                x - radius * 0.55f, y + radius * 0.68f,
                x + radius * 0.35f, y + radius * 0.94f);
        crescent.cubicTo(x - radius * 0.08f, y + radius * 0.32f,
                x - radius * 0.06f, y - radius * 0.37f,
                x + radius * 0.30f, y - radius * 0.96f);
        crescent.close();
        canvas.drawPath(crescent, fill);

        stroke.setShader(null);
        stroke.setColor(Color.argb(175, 230, 239, 255));
        stroke.setStrokeWidth(2.2f);
        canvas.drawCircle(x + 27f, y - 23f, 1.8f, stroke);
        canvas.drawCircle(x + 31f, y + 1f, 1.2f, stroke);
    }

    private void drawCloud(Canvas canvas, float x, float y, float factor) {
        shadow.setShader(null);
        shadow.setColor(Color.argb(90, 0, 15, 34));
        shadow.setShadowLayer(5f, 0f, 4f, Color.argb(100, 0, 15, 34));
        RectF shadowBase = new RectF(x - 36f * factor, y + 1f * factor,
                x + 36f * factor, y + 24f * factor);
        canvas.drawRoundRect(shadowBase, 13f * factor, 13f * factor, shadow);
        shadow.clearShadowLayer();

        fill.setStyle(Paint.Style.FILL);
        fill.setShader(new LinearGradient(0f, y - 25f * factor, 0f, y + 25f * factor,
                Color.rgb(255, 255, 255), Color.rgb(192, 215, 232), Shader.TileMode.CLAMP));
        RectF base = new RectF(x - 36f * factor, y - 1f * factor,
                x + 36f * factor, y + 22f * factor);
        canvas.drawRoundRect(base, 13f * factor, 13f * factor, fill);
        canvas.drawCircle(x - 19f * factor, y + 1f * factor, 15f * factor, fill);
        canvas.drawCircle(x + 1f * factor, y - 11f * factor, 22f * factor, fill);
        canvas.drawCircle(x + 22f * factor, y + 2f * factor, 15f * factor, fill);
        fill.setShader(null);
    }

    private void drawRain(Canvas canvas, float x, float y, int count, float gap, boolean heavy) {
        stroke.setShader(null);
        stroke.setColor(heavy ? Color.rgb(121, 205, 232) : Color.rgb(104, 196, 221));
        stroke.setStrokeWidth(heavy ? 3.4f : 3.1f);
        for (int i = 0; i < count; i++) {
            float dx = x + i * gap;
            float length = heavy ? 14f : 11f;
            Path drop = new Path();
            drop.moveTo(dx, y);
            drop.lineTo(dx - 4f, y + length - 3f);
            drop.quadTo(dx - 4.5f, y + length + 2f, dx, y + length + 1f);
            drop.quadTo(dx + 4.5f, y + length - 1f, dx, y);
            canvas.drawPath(drop, stroke);
        }
    }

    private void drawSnow(Canvas canvas) {
        stroke.setShader(null);
        stroke.setColor(Color.rgb(198, 237, 249));
        stroke.setStrokeWidth(2.6f);
        float[] xs = {-18f, 0f, 18f};
        for (float x : xs) {
            float y = 30f;
            canvas.drawLine(x - 6f, y, x + 6f, y, stroke);
            canvas.drawLine(x, y - 6f, x, y + 6f, stroke);
            canvas.drawLine(x - 4.5f, y - 4.5f, x + 4.5f, y + 4.5f, stroke);
            canvas.drawLine(x - 4.5f, y + 4.5f, x + 4.5f, y - 4.5f, stroke);
            fill.setShader(null);
            fill.setColor(Color.rgb(224, 247, 255));
            canvas.drawCircle(x, y, 1.8f, fill);
        }
    }

    private void drawFog(Canvas canvas) {
        stroke.setShader(null);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setColor(Color.argb(210, 226, 239, 247));
        stroke.setStrokeWidth(5f);
        canvas.drawLine(-35f, -18f, 31f, -18f, stroke);
        canvas.drawLine(-26f, -1f, 37f, -1f, stroke);
        stroke.setColor(Color.argb(145, 194, 219, 235));
        canvas.drawLine(-37f, 16f, 23f, 16f, stroke);
    }

    private void drawLightning(Canvas canvas) {
        fill.setStyle(Paint.Style.FILL);
        fill.setShader(null);
        fill.setColor(Color.argb(100, 255, 207, 74));
        Path glow = new Path();
        glow.moveTo(5f, 4f);
        glow.lineTo(-10f, 29f);
        glow.lineTo(0f, 27f);
        glow.lineTo(-6f, 46f);
        glow.lineTo(19f, 16f);
        glow.lineTo(8f, 19f);
        glow.close();
        canvas.drawPath(glow, fill);

        fill.setColor(Color.rgb(255, 216, 82));
        Path bolt = new Path();
        bolt.moveTo(3f, 5f);
        bolt.lineTo(-8f, 28f);
        bolt.lineTo(2f, 26f);
        bolt.lineTo(-3f, 43f);
        bolt.lineTo(16f, 17f);
        bolt.lineTo(7f, 19f);
        bolt.close();
        canvas.drawPath(bolt, fill);
    }

    private void drawDust(Canvas canvas) {
        stroke.setShader(null);
        stroke.setColor(Color.argb(180, 224, 205, 160));
        stroke.setStrokeWidth(3.8f);
        canvas.drawArc(new RectF(-38f, -18f, 28f, 12f), 200f, 145f, false, stroke);
        canvas.drawArc(new RectF(-29f, 0f, 39f, 28f), 200f, 145f, false, stroke);
        fill.setShader(null);
        fill.setColor(Color.rgb(232, 207, 147));
        float[] dots = {-29f, -11f, 4f, 20f, 33f};
        for (int i = 0; i < dots.length; i++) {
            canvas.drawCircle(dots[i], 31f - (i % 2) * 7f, 2.2f + (i % 3), fill);
        }
    }

    private void drawSandstorm(Canvas canvas) {
        stroke.setShader(null);
        stroke.setColor(Color.argb(210, 237, 190, 111));
        stroke.setStrokeWidth(4.5f);
        canvas.drawArc(new RectF(-45f, -27f, 25f, 6f), 205f, 140f, false, stroke);
        canvas.drawArc(new RectF(-37f, -7f, 42f, 27f), 205f, 140f, false, stroke);
        canvas.drawArc(new RectF(-44f, 11f, 27f, 39f), 205f, 140f, false, stroke);
        fill.setShader(null);
        fill.setColor(Color.rgb(244, 194, 111));
        for (int i = 0; i < 10; i++) {
            float x = -35f + i * 7f;
            float y = 28f + (i % 3) * 4f;
            canvas.drawCircle(x, y, 1.5f + (i % 2), fill);
        }
    }
}
