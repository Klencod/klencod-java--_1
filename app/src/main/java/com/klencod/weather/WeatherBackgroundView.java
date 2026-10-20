package com.klencod.weather;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.net.Uri;
import android.view.Gravity;
import android.view.TextureView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;

import java.util.Calendar;
import java.util.TimeZone;

/** خلفية الطقس: الصور الثابتة هي الوضع الافتراضي، والفيديو اختياري من الإعدادات. */
public final class WeatherBackgroundView extends FrameLayout {
    private final GradientFallback fallback;
    private final ImageView staticView;
    private final TextureView videoView;
    private final TextView errorLabel;
    private ExoPlayer player;
    private int requestToken;
    private String currentResourceName = "";
    private int lastCode;
    private boolean lastIsDay = true;
    private boolean videoModeEnabled;
    /** معرف منطقة المدينة؛ فارغ يعني استخدام المنطقة الزمنية الافتراضية للهاتف. */
    private String timezoneId = "";

    public WeatherBackgroundView(Context context) {
        super(context);
        setClipChildren(false);
        setClipToPadding(false);
        setBackgroundColor(Color.TRANSPARENT);

        fallback = new GradientFallback(context);
        addView(fallback, new FrameLayout.LayoutParams(-1, -1));

        staticView = new ImageView(context);
        staticView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        staticView.setVisibility(View.VISIBLE);
        addView(staticView, new FrameLayout.LayoutParams(-1, -1));

        videoView = new TextureView(context);
        videoView.setOpaque(false);
        videoView.setAlpha(1f);
        videoView.setVisibility(View.INVISIBLE);
        addView(videoView, new FrameLayout.LayoutParams(-1, -1));

        errorLabel = new TextView(context);
        errorLabel.setText("تعذّر تشغيل فيديو حالة الطقس");
        errorLabel.setTextColor(Color.WHITE);
        errorLabel.setTextSize(16f);
        errorLabel.setGravity(Gravity.CENTER);
        errorLabel.setPadding(28, 18, 28, 18);
        errorLabel.setBackgroundColor(Color.argb(190, 0, 0, 0));
        errorLabel.setVisibility(View.INVISIBLE);
        FrameLayout.LayoutParams errorParams = new FrameLayout.LayoutParams(-2, -2);
        errorParams.gravity = Gravity.CENTER;
        addView(errorLabel, errorParams);

        videoModeEnabled = false;
    }

    /** يبدّل بين الصور الثابتة الافتراضية والفيديوهات الموجودة في المشروع. */
    public void setVideoModeEnabled(boolean enabled) {
        if (videoModeEnabled == enabled && currentResourceName.length() > 0) return;
        videoModeEnabled = enabled;
        if (lastCode != 0 || currentResourceName.length() > 0) {
            setWeatherCode(lastCode, lastIsDay);
        }
    }

    public boolean isVideoModeEnabled() {
        return videoModeEnabled;
    }

    /** يضبط المنطقة الزمنية المستخدمة لاختيار صورة الصباح أو بعد الظهر أو الليل. */
    public void setTimeZoneId(String id) {
        timezoneId = id == null ? "" : id.trim();
    }

    public void setWeatherCode(int code, boolean isDay) {
        lastCode = code;
        lastIsDay = isDay;
        fallback.setState(code, isDay);

        if (!videoModeEnabled) {
            showStatic(code, isDay);
            return;
        }

        String resourceName = videoResourceNameFor(code, isDay);
        int token = ++requestToken;
        if (resourceName == null) {
            currentResourceName = "";
            releasePlayer();
            showGradientFallback();
            return;
        }

        int resourceId = getResources().getIdentifier(
                resourceName, "raw", getContext().getPackageName());
        if (resourceId == 0) {
            currentResourceName = resourceName;
            releasePlayer();
            showPlaybackError();
            return;
        }

        if (("video:" + resourceName).equals(currentResourceName) && player != null) {
            showVideo();
            return;
        }

        currentResourceName = "video:" + resourceName;
        releasePlayer();
        errorLabel.setVisibility(View.INVISIBLE);
        fallback.setVisibility(View.INVISIBLE);
        staticView.setVisibility(View.INVISIBLE);
        videoView.setVisibility(View.INVISIBLE);
        videoView.setAlpha(1f);
        setBackgroundColor(Color.TRANSPARENT);

        final Uri uri = Uri.parse("android.resource://"
                + getContext().getPackageName() + "/" + resourceId);
        player = new ExoPlayer.Builder(getContext()).build();
        player.setVideoTextureView(videoView);
        player.setRepeatMode(Player.REPEAT_MODE_ONE);
        player.setPlayWhenReady(true);
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                if (token != requestToken || player == null) return;
                if (state == Player.STATE_READY) showVideo();
            }

            @Override
            public void onRenderedFirstFrame() {
                if (token != requestToken || player == null) return;
                videoView.setVisibility(View.VISIBLE);
                showVideo();
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                if (token != requestToken) return;
                releasePlayer();
                showPlaybackError();
            }
        });
        player.setMediaItem(MediaItem.fromUri(uri));
        player.prepare();
    }

    private void showStatic(int code, boolean isDay) {
        requestToken++;
        releasePlayer();
        String name = staticResourceNameFor(code, isDay);
        int resourceId = getResources().getIdentifier(
                name, "drawable", getContext().getPackageName());
        if (resourceId == 0) {
            currentResourceName = name;
            showGradientFallback();
            return;
        }
        currentResourceName = "static:" + name;
        staticView.setImageResource(resourceId);
        fallback.setVisibility(View.INVISIBLE);
        staticView.setVisibility(View.VISIBLE);
        videoView.setVisibility(View.INVISIBLE);
        errorLabel.setVisibility(View.INVISIBLE);
        setBackgroundColor(Color.TRANSPARENT);
    }

    private String staticResourceNameFor(int code, boolean isDay) {
        if (code == -1) return "weather_clear_morning";
        if (code == -2) return "weather_clear_night";

        String state;
        if (code == 0) state = "clear";
        else if (code == -3 || code == 1) state = "partly_cloudy";
        else if (code == -4 || code == 2) state = "mostly_cloudy";
        else if (code == -5 || code == 3) state = "overcast";
        else if (code == -6 || (code >= 4 && code <= 48)) state = "fog";
        else if (code == -7 || (code >= 49 && code <= 57)) state = "drizzle";
        else if (code == -8 || (code >= 58 && code <= 67) || (code >= 78 && code <= 82)) state = "rain";
        else if (code == -9 || (code >= 68 && code <= 77)) state = "snow";
        else if (code == -10 || code == 95 || code == 96 || code == 99) state = "thunderstorm";
        else if (code == -11) state = "dust";
        else if (code == -12) state = "sandstorm";
        else state = "clear";
        return "weather_" + state + "_" + timeSegment();
    }

    private String timeSegment() {
        TimeZone zone = timezoneId.length() == 0
                ? TimeZone.getDefault()
                : TimeZone.getTimeZone(timezoneId);
        int hour = Calendar.getInstance(zone).get(Calendar.HOUR_OF_DAY);
        if (hour >= 7 && hour < 14) return "morning";
        if (hour >= 14 && hour < 19) return "afternoon";
        return "night";
    }

    private String videoResourceNameFor(int code, boolean isDay) {
        if (code == -1) return "sunny_day";
        if (code == -2) return "clear_night";
        if (code == -3) return "partly_cloudy";
        if (code == -4) return "mostly_cloudy";
        if (code == -5) return "overcast";
        if (code == -6) return "fog";
        if (code == -7) return "drizzle";
        if (code == -8) return "rain";
        if (code == -9) return "snow";
        if (code == -10) return "thunderstorm";
        if (code == -11) return "dust";
        if (code == -12) return "sandstorm";
        if (code == 0) return isDay ? "sunny_day" : "clear_night";
        if (code == 1) return "partly_cloudy";
        if (code == 2) return "mostly_cloudy";
        if (code == 3) return "overcast";
        if (code <= 48) return "fog";
        if (code <= 57) return "drizzle";
        if (code <= 67) return "rain";
        if (code <= 77) return "snow";
        if (code <= 82) return "rain";
        if (code == 95 || code == 96 || code == 99) return "thunderstorm";
        return null;
    }

    private void showVideo() {
        fallback.setVisibility(View.INVISIBLE);
        staticView.setVisibility(View.INVISIBLE);
        errorLabel.setVisibility(View.INVISIBLE);
        videoView.setVisibility(View.VISIBLE);
        videoView.setAlpha(1f);
        setBackgroundColor(Color.TRANSPARENT);
    }

    private void showGradientFallback() {
        fallback.setVisibility(View.VISIBLE);
        staticView.setVisibility(View.INVISIBLE);
        videoView.setVisibility(View.INVISIBLE);
        errorLabel.setVisibility(View.INVISIBLE);
        setBackgroundColor(Color.TRANSPARENT);
    }

    private void showPlaybackError() {
        fallback.setVisibility(View.INVISIBLE);
        staticView.setVisibility(View.INVISIBLE);
        videoView.setVisibility(View.INVISIBLE);
        errorLabel.setVisibility(View.VISIBLE);
        setBackgroundColor(Color.rgb(12, 16, 24));
    }

    private void releasePlayer() {
        ExoPlayer old = player;
        player = null;
        videoView.setVisibility(View.INVISIBLE);
        if (old != null) {
            try {
                old.clearVideoTextureView(videoView);
                old.stop();
                old.clearMediaItems();
                old.release();
            } catch (Exception ignored) {
                // منع خروج استثناء من دورة حياة العرض.
            }
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        requestToken++;
        currentResourceName = "";
        releasePlayer();
        super.onDetachedFromWindow();
    }

    private static final class GradientFallback extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int code;
        private boolean day;

        GradientFallback(Context context) { super(context); }

        void setState(int code, boolean day) {
            this.code = code;
            this.day = day;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int top = day ? Color.rgb(74, 139, 188) : Color.rgb(20, 37, 70);
            int bottom = day ? Color.rgb(169, 187, 205) : Color.rgb(40, 61, 94);
            if (code >= 95 || code == -10) {
                top = day ? Color.rgb(48, 66, 101) : Color.rgb(18, 25, 49);
                bottom = day ? Color.rgb(89, 103, 132) : Color.rgb(42, 43, 72);
            } else if (code >= 71 && code <= 77) {
                top = day ? Color.rgb(107, 157, 193) : Color.rgb(31, 52, 83);
                bottom = day ? Color.rgb(196, 216, 228) : Color.rgb(76, 94, 119);
            }
            paint.setShader(new LinearGradient(0, 0, 0, getHeight(), top, bottom, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
            paint.setShader(null);
        }
    }
}
