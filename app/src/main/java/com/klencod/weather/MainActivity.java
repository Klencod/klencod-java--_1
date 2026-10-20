package com.klencod.weather;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.SharedPreferences;
import android.location.Address;
import android.location.Geocoder;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int LOCATION_REQUEST = 410;
    private static final String DEFAULT_CITY = "Cairo";
    private static final String PREFS_NAME = "weather_preferences";
    private static final String PREF_ANIMATED_BACKGROUND = "animated_background_enabled";

    private WeatherBackgroundView backgroundView;
    private LinearLayout content;
    private TextView locationText;
    private TextView updateText;
    private ProgressBar progressBar;
    private WeatherData lastData;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private LocationManager locationManager;
    private boolean waitingForLocation;
    private boolean animatedBackgroundEnabled;
    private LocationListener pendingLocationListener;
    private boolean demoMode;
    private String demoStateLabel = "";

    private final int ink = Color.rgb(238, 246, 255);
    private final int muted = Color.rgb(190, 211, 232);
    private final int accent = Color.rgb(255, 202, 104);
    private final int panel = Color.argb(80, 18, 38, 68);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        window.setStatusBarColor(Color.rgb(16, 32, 58));
        window.setNavigationBarColor(Color.rgb(12, 25, 45));
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            window.getDecorView().setSystemUiVisibility(0);
        }
        animatedBackgroundEnabled = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getBoolean(PREF_ANIMATED_BACKGROUND, false);
        buildUi();
        requestOrUseLocation();
    }

    private void buildUi() {
        android.widget.FrameLayout root = new android.widget.FrameLayout(this);
        backgroundView = new WeatherBackgroundView(this);
        root.addView(backgroundView, new android.widget.FrameLayout.LayoutParams(-1, -1));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        content.setPadding(dp(18), dp(16), dp(18), dp(30));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new android.widget.FrameLayout.LayoutParams(-1, -1));
        setContentView(root);

        LinearLayout top = row();
        top.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout brandColumn = new LinearLayout(this);
        brandColumn.setOrientation(LinearLayout.VERTICAL);
        TextView eyebrow = text("توقعات مباشرة", 11, true, accent);
        brandColumn.addView(eyebrow, lp(-1, -2));
        TextView brand = text("Weather", 29, true, ink);
        brandColumn.addView(brand, margins(-1, -2, 0, 0));
        top.addView(brandColumn, weight(1, -2));

        Button settings = actionButton("⚙", 44);
        settings.setContentDescription("إعدادات الخلفية");
        settings.setOnClickListener(v -> showBackgroundSettingsDialog());
        top.addView(settings, margins(dp(44), dp(44), 8, 0));

        Button demo = actionButton("تجربة", 0);
        demo.setContentDescription("تجربة حالات الطقس");
        demo.setOnClickListener(v -> showWeatherDemoDialog());
        top.addView(demo, lp(dp(78), 44));
        Button refresh = actionButton("⟳", 44);
        refresh.setContentDescription("تحديث الطقس");
        refresh.setOnClickListener(v -> loadDefaultLocation());
        top.addView(refresh, margins(dp(44), dp(44), 8, 0));
        backgroundView.setVideoModeEnabled(animatedBackgroundEnabled);
        content.addView(top, margins(-1, -2, 0, 14));

        LinearLayout locationCard = card();
        locationCard.setPadding(dp(15), dp(13), dp(15), dp(13));
        LinearLayout placeRow = row();
        placeRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView pin = plainText("⌖", 27, true, accent);
        pin.setGravity(Gravity.CENTER);
        placeRow.addView(pin, lp(dp(34), dp(40)));
        LinearLayout placeColumn = new LinearLayout(this);
        placeColumn.setOrientation(LinearLayout.VERTICAL);
        TextView placeCaption = plainText("الموقع الحالي", 10, true, muted);
        placeColumn.addView(placeCaption, lp(-1, -2));
        locationText = plainText("جارٍ تحديد الموقع…", 17, true, ink);
        placeColumn.addView(locationText, margins(-1, -2, 0, 0));
        placeRow.addView(placeColumn, weight(1, -2));
        Button cityButton = actionButton("تغيير", 0);
        cityButton.setOnClickListener(v -> showCityDialog());
        placeRow.addView(cityButton, lp(dp(78), 40));
        locationCard.addView(placeRow, lp(-1, -2));
        updateText = plainText("جارٍ تحديث البيانات…", 11, false, muted);
        locationCard.addView(updateText, margins(-1, -2, dp(34), 0));
        content.addView(locationCard, margins(-1, -2, 0, 14));

        progressBar = new ProgressBar(this);
        progressBar.setIndeterminate(true);
        content.addView(progressBar, centered(-2, 48));
    }

    private void renderWeather(WeatherData data) {
        lastData = data;
        backgroundView.setTimeZoneId(data.timezoneId);
        backgroundView.setWeatherCode(data.current.weatherCode, data.current.isDay);
        String placeText = data.locationName == null ? "" : data.locationName.trim();
        String countryText = data.countryName == null ? "" : data.countryName.trim();
        if (placeText.length() == 0) placeText = "موقعك الحالي";
        locationText.setText(placeText + (countryText.length() > 0 ? "، " + countryText : ""));
        updateText.setText(demoMode
                ? "وضع التجربة: " + demoStateLabel
                : "آخر تحديث: " + new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()));
        progressBar.setVisibility(View.GONE);
        while (content.getChildCount() > 3) {
            content.removeViewAt(3);
        }

        LinearLayout current = heroCard();
        LinearLayout heroTop = row();
        LinearLayout tempCol = new LinearLayout(this);
        tempCol.setOrientation(LinearLayout.VERTICAL);
        TextView nowLabel = plainText("الطقس الآن", 12, true, accent);
        tempCol.addView(nowLabel, lp(-1, -2));
        TextView temp = plainText(formatTemp(data.current.temperature), 62, true, ink);
        tempCol.addView(temp, margins(-1, -2, 0, 0));
        TextView condition = plainText(weatherName(data.current.weatherCode), 19, true, ink);
        tempCol.addView(condition, margins(-1, -2, 0, 2));
        TextView feel = plainText("الإحساس " + formatTemp(data.current.apparentTemperature), 13, false, muted);
        tempCol.addView(feel, lp(-1, -2));
        heroTop.addView(tempCol, weight(1, -2));
        WeatherIconView icon = new WeatherIconView(this);
        icon.setWeather(data.current.weatherCode, data.current.isDay);
        heroTop.addView(icon, lp(dp(118), dp(112)));
        current.addView(heroTop, lp(-1, -2));

        View divider = new View(this);
        divider.setBackgroundColor(Color.argb(45, 255, 255, 255));
        current.addView(divider, margins(-1, 1, 0, 12));
        LinearLayout factsTop = row();
        factsTop.addView(fact("الرطوبة", data.current.humidity + "%"), weight(1, -2));
        factsTop.addView(fact("الرياح", formatNumber(data.current.windSpeed) + " كم/س"), weight(1, -2));
        LinearLayout factsBottom = row();
        factsBottom.addView(fact("الأمطار", formatNumber(data.current.precipitation) + " مم"), weight(1, -2));
        factsBottom.addView(fact("الاتجاه", windDirectionLabel(data.current.windDirection)), weight(1, -2));
        current.addView(factsTop, lp(-1, -2));
        current.addView(factsBottom, margins(-1, -2, 0, 0));
        content.addView(current, margins(-1, -2, 0, 18));

        content.addView(sectionTitle("الساعات القادمة"), margins(-1, -2, 0, 8));
        HorizontalScrollView hourlyScroll = new HorizontalScrollView(this);
        hourlyScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout hourly = row();
        hourly.setPadding(0, dp(2), dp(2), dp(2));
        for (int i = 0; i < Math.min(12, data.hourly.size()); i++) {
            hourly.addView(hourCard(data.hourly.get(i)), margins(0, 0, 8, 0));
        }
        hourlyScroll.addView(hourly, new HorizontalScrollView.LayoutParams(-2, -2));
        content.addView(hourlyScroll, margins(-1, -2, 0, 20));

        content.addView(sectionTitle("مؤشرات الطقس"), margins(-1, -2, 0, 8));
        LinearLayout details = card();
        LinearLayout detailsTop = row();
        detailsTop.addView(fact("مؤشر UV", formatUv(data.current.uvIndex)), weight(1, -2));
        detailsTop.addView(fact("الرؤية", formatVisibility(data.current.visibility)), weight(1, -2));
        LinearLayout detailsBottom = row();
        detailsBottom.addView(fact("الضغط", formatPressure(data.current.pressure)), weight(1, -2));
        detailsBottom.addView(fact("اتجاه الرياح", windDirectionLabel(data.current.windDirection)), weight(1, -2));
        details.addView(detailsTop, lp(-1, -2));
        details.addView(detailsBottom, margins(-1, -2, 0, 0));
        content.addView(details, margins(-1, -2, 0, 18));

        if (!data.daily.isEmpty()) {
            WeatherData.Day today = data.daily.get(0);
            content.addView(sectionTitle("مسار الشمس"), margins(-1, -2, 0, 8));
            LinearLayout sunCard = card();
            LinearLayout sunRow = row();
            sunRow.addView(fact("الشروق", shortTime(today.sunrise)), weight(1, -2));
            sunRow.addView(fact("الغروب", shortTime(today.sunset)), weight(1, -2));
            sunCard.addView(sunRow, lp(-1, -2));
            content.addView(sunCard, margins(-1, -2, 0, 18));
        }

        content.addView(sectionTitle("التوقعات اليومية"), margins(-1, -2, 0, 8));
        LinearLayout daily = card();
        for (int i = 0; i < data.daily.size(); i++) {
            daily.addView(dayRow(data.daily.get(i), i), lp(-1, -2));
            if (i < data.daily.size() - 1) {
                View dayDivider = new View(this);
                dayDivider.setBackgroundColor(Color.argb(45, 255, 255, 255));
                daily.addView(dayDivider, margins(-1, 1, 0, 1));
            }
        }
        content.addView(daily, margins(-1, -2, 0, 16));

        TextView source = text("بيانات الطقس من Open-Meteo", 11, false, Color.argb(180, 220, 232, 246));
        source.setGravity(Gravity.CENTER);
        content.addView(source, lp(-1, -2));
    }

    private void loadDefaultLocation() {
        demoMode = false;
        demoStateLabel = "";
        progressBar.setVisibility(View.VISIBLE);
        locationText.setText("جارٍ تحديث الطقس…");
        if (checkSelfPermissionCompat(Manifest.permission.ACCESS_FINE_LOCATION) || checkSelfPermissionCompat(Manifest.permission.ACCESS_COARSE_LOCATION)) {
            try {
                locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
                Location last = null;
                if (locationManager != null) {
                    if (checkSelfPermissionCompat(Manifest.permission.ACCESS_FINE_LOCATION)) {
                        last = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                    }
                    if (last == null && checkSelfPermissionCompat(Manifest.permission.ACCESS_COARSE_LOCATION)) {
                        last = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                    }
                }
                if (last != null) {
                    loadWeather(last.getLatitude(), last.getLongitude(), "", "");
                    return;
                }
                requestFreshLocation();
                return;
            } catch (Exception ignored) {
            }
        }
        loadCity(DEFAULT_CITY);
    }

    private void requestFreshLocation() {
        if (locationManager == null) {
            loadCity(DEFAULT_CITY);
            return;
        }
        try {
            waitingForLocation = true;
            locationText.setText("جارٍ تحديد موقعك…");
            pendingLocationListener = new LocationListener() {
                @Override
                public void onLocationChanged(Location location) {
                    if (!waitingForLocation) return;
                    waitingForLocation = false;
                    try {
                        locationManager.removeUpdates(this);
                    } catch (Exception ignored) {
                    }
                    loadWeather(location.getLatitude(), location.getLongitude(), "", "");
                }

                @Override
                public void onStatusChanged(String provider, int status, android.os.Bundle extras) {
                }

                @Override
                public void onProviderEnabled(String provider) {
                }

                @Override
                public void onProviderDisabled(String provider) {
                }
            };
            locationManager.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, pendingLocationListener, Looper.getMainLooper());
            new android.os.Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (!waitingForLocation) return;
                waitingForLocation = false;
                try {
                    locationManager.removeUpdates(pendingLocationListener);
                } catch (Exception ignored) {
                }
                loadCity(DEFAULT_CITY);
            }, 8000L);
        } catch (Exception ignored) {
            waitingForLocation = false;
            loadCity(DEFAULT_CITY);
        }
    }

    private void requestOrUseLocation() {
        if (checkSelfPermissionCompat(Manifest.permission.ACCESS_COARSE_LOCATION) || checkSelfPermissionCompat(Manifest.permission.ACCESS_FINE_LOCATION)) {
            loadDefaultLocation();
            return;
        }
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_REQUEST);
        } else {
            loadDefaultLocation();
        }
    }

    private boolean checkSelfPermissionCompat(String permission) {
        return android.os.Build.VERSION.SDK_INT < 23 || checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == LOCATION_REQUEST) {
            loadDefaultLocation();
        }
    }

    private void loadCity(String city) {
        demoMode = false;
        demoStateLabel = "";
        progressBar.setVisibility(View.VISIBLE);
        locationText.setText("جارٍ البحث عن " + city + "…");
        executor.execute(() -> {
            try {
                WeatherApi.Place place = WeatherApi.geocode(city);
                WeatherData data = WeatherApi.fetch(place.latitude, place.longitude, place.name, place.country, false);
                runOnUiThread(() -> renderWeather(data));
            } catch (Exception e) {
                runOnUiThread(() -> showError(e.getMessage()));
            }
        });
    }

    private void loadWeather(double latitude, double longitude, String name, String country) {
        demoMode = false;
        demoStateLabel = "";
        executor.execute(() -> {
            try {
                String resolvedName = name == null ? "" : name.trim();
                String resolvedCountry = country == null ? "" : country.trim();
                if (resolvedName.length() == 0) {
                    String[] place = reverseGeocode(latitude, longitude);
                    resolvedName = place[0];
                    resolvedCountry = place[1];
                }
                WeatherData data = WeatherApi.fetch(latitude, longitude, resolvedName, resolvedCountry, true);
                runOnUiThread(() -> renderWeather(data));
            } catch (Exception e) {
                runOnUiThread(() -> showError(e.getMessage()));
            }
        });
    }

    @SuppressWarnings("deprecation")
    private String[] reverseGeocode(double latitude, double longitude) {
        String[] result = {"", ""};
        try {
            if (!Geocoder.isPresent()) return result;
            Geocoder geocoder = new Geocoder(this, Locale.getDefault());
            List<Address> addresses = geocoder.getFromLocation(latitude, longitude, 1);
            if (addresses == null || addresses.isEmpty()) return result;
            Address address = addresses.get(0);
            String name = firstNonEmpty(
                    address.getLocality(),
                    address.getSubAdminArea(),
                    address.getAdminArea(),
                    address.getFeatureName()
            );
            String country = address.getCountryName();
            result[0] = name == null ? "" : name;
            result[1] = country == null ? "" : country;
        } catch (Exception ignored) {
            // يبقى الاسم الاحتياطي «موقعك الحالي» إذا لم يوفر الجهاز الترميز العكسي.
        }
        return result;
    }

    private String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && value.trim().length() > 0) return value.trim();
        }
        return "";
    }

    private void showWeatherDemoDialog() {
        final String[] labels = {
                "مشمس / صافٍ نهارًا",
                "صافٍ ليلًا",
                "غائم جزئيًا",
                "غائم غالبًا",
                "غائم كليًا",
                "ضباب",
                "رذاذ",
                "مطر",
                "زخات مطر",
                "ثلوج",
                "عاصفة رعدية",
                "غبار",
                "عاصفة رملية",
                "حالة غير متوفرة - خلفية متدرجة"
        };
        // Negative values are demo-only codes mapped to the prepared video assets.
        final int[] codes = {-1, -2, -3, -4, -5, -6, -7, -8, 80, -9, -10, -11, -12, 999};
        final boolean[] day = {true, false, true, true, true, true, true, true, true, true, false, true, true, true};
        new android.app.AlertDialog.Builder(this)
                .setTitle("تجربة حالات الطقس")
                .setItems(labels, (dialog, which) -> renderDemoWeather(labels[which], codes[which], day[which]))
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private void renderDemoWeather(String label, int code, boolean isDay) {
        demoMode = true;
        demoStateLabel = label;
        renderWeather(createDemoData(label, code, isDay));
    }

    private WeatherData createDemoData(String label, int code, boolean isDay) {
        double baseTemperature;
        if (code == -9 || code == 71) baseTemperature = 1;
        else if ((code >= 63 && code <= 82) || code == -7 || code == -8) baseTemperature = 17;
        else if (code >= 95 || code == -10 || code == -12) baseTemperature = 21;
        else if (code == 45 || code == -6) baseTemperature = 19;
        else baseTemperature = isDay ? 28 : 22;

        int humidity = (code == 0 || code == -1 || code == -2) ? 42 : (code == 45 || code == -6 ? 88 : 76);
        double wind = (code >= 95 || code == -10 || code == -12) ? 32 : ((code >= 51 || code <= -7) ? 18 : 11);
        double precipitation = (code >= 51 || (code <= -7 && code >= -12)) ? 2.8 : 0.0;
        WeatherData.Current current = new WeatherData.Current(
                baseTemperature,
                baseTemperature - (code >= 51 ? 1 : 2),
                humidity,
                wind,
                precipitation,
                code,
                isDay,
                isDay ? 5.8 : 0.0,
                10000.0,
                1012.0,
                code >= 95 ? 245 : 135
        );

        ArrayList<WeatherData.Hour> hours = new ArrayList<>();
        Calendar hourCalendar = Calendar.getInstance();
        hourCalendar.set(Calendar.MINUTE, 0);
        hourCalendar.set(Calendar.SECOND, 0);
        hourCalendar.set(Calendar.MILLISECOND, 0);
        SimpleDateFormat hourFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US);
        for (int i = 0; i < 12; i++) {
            Calendar item = (Calendar) hourCalendar.clone();
            item.add(Calendar.HOUR_OF_DAY, i);
            int probability = (code == 0 || code == -1 || code == -2) ? 0 : (code >= 95 || code <= -10 ? 85 : 65);
            double temperature = baseTemperature + (isDay ? Math.max(0, 3 - i * 0.35) : -i * 0.25);
            hours.add(new WeatherData.Hour(hourFormat.format(item.getTime()), temperature, probability, code));
        }

        ArrayList<WeatherData.Day> days = new ArrayList<>();
        Calendar dayCalendar = Calendar.getInstance();
        SimpleDateFormat dayFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        for (int i = 0; i < 7; i++) {
            Calendar item = (Calendar) dayCalendar.clone();
            item.add(Calendar.DAY_OF_YEAR, i);
            double max = baseTemperature + (i % 3) - (code == 71 ? 1 : 0);
            double min = max - (code == 71 ? 4 : 7);
            int dayCode = (i == 2 && code == 2) ? 0 : code;
            int nightCode = (dayCode == -1 || dayCode == 0) ? -2 : dayCode;
            String date = dayFormat.format(item.getTime());
            int dayHumidity = Math.max(30, humidity - 8 + (i % 3));
            int nightHumidity = Math.min(98, humidity + 10 + (i % 3));
            double dayWind = Math.max(2.0, wind + (i % 2));
            double nightWind = Math.max(1.0, wind - 2.0 + (i % 2));
            int dayRain = Math.min(100, probabilityForDemo(code));
            int nightRain = Math.min(100, dayRain + (dayRain == 0 ? 0 : 8));
            days.add(new WeatherData.Day(date, max, min, dayCode,
                    date + "T05:50", date + "T18:25",
                    max - 1.0, min + 1.0, dayCode, nightCode,
                    dayHumidity, nightHumidity, dayWind, nightWind,
                    dayRain, nightRain,
                    dayRain > 0 ? 3.2 : 0.0, isDay ? 6.4 : 0.0));
        }
        return new WeatherData("معاينة: " + label, "", current, hours, days);
    }

    private int probabilityForDemo(int code) {
        if (code == 0 || code == -1 || code == -2) return 0;
        if (code >= 95 || code <= -10) return 85;
        return 65;
    }

    private void showCityDialog() {
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setSingleLine(true);
        input.setHint("مثال: Amman أو القاهرة");
        input.setPadding(dp(14), dp(4), dp(14), dp(4));
        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(this)
                .setTitle("اختر مدينة")
                .setMessage("اكتب اسم المدينة باللغة العربية أو الإنجليزية")
                .setView(input)
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("عرض الطقس", null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String city = input.getText().toString().trim();
            if (city.length() < 2) {
                input.setError("اكتب اسم مدينة صحيحًا");
                return;
            }
            dialog.dismiss();
            loadCity(city);
        }));
        dialog.show();
    }

    private void showError(String message) {
        progressBar.setVisibility(View.GONE);
        locationText.setText("تعذر تحديث الطقس");
        Toast.makeText(this, message == null ? "تحقق من اتصال الإنترنت أو اسم المدينة" : message, Toast.LENGTH_LONG).show();
    }

    private LinearLayout sectionTitle(String value) {
        LinearLayout heading = row();
        heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text(value, 18, true, ink);
        title.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        heading.addView(title, weight(1, dp(30)));
        View marker = new View(this);
        GradientDrawable markerBg = new GradientDrawable();
        markerBg.setColor(accent);
        markerBg.setCornerRadius(dp(3));
        marker.setBackground(markerBg);
        heading.addView(marker, lp(dp(4), dp(22)));
        return heading;
    }

    private LinearLayout hourCard(WeatherData.Hour hour) {
        LinearLayout box = card();
        box.setPadding(dp(12), dp(12), dp(12), dp(12));
        box.setGravity(Gravity.CENTER);
        TextView time = plainText(hourLabel(hour.time), 12, true, muted);
        time.setGravity(Gravity.CENTER);
        box.addView(time, lp(dp(72), -2));
        WeatherIconView icon = new WeatherIconView(this);
        icon.setWeather(hour.weatherCode, isDayTime(hour.time));
        box.addView(icon, margins(dp(72), dp(52), 0, 2));
        TextView temp = plainText(formatTemp(hour.temperature), 16, true, ink);
        temp.setGravity(Gravity.CENTER);
        box.addView(temp, lp(dp(72), -2));
        TextView rain = plainText(hour.precipitationProbability + "% مطر", 10, false, muted);
        rain.setGravity(Gravity.CENTER);
        box.addView(rain, margins(dp(72), -2, 0, 0));
        return box;
    }

    private LinearLayout dayRow(WeatherData.Day day, int index) {
        LinearLayout row = row();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(5), 0, dp(5));
        row.setClickable(true);
        row.setFocusable(true);
        row.setContentDescription("عرض تفاصيل " + (index == 0 ? "اليوم" : dayLabel(day.time)));
        row.setOnClickListener(v -> showDayDetailsDialog(day, index));
        TextView dayName = plainText(index == 0 ? "اليوم" : dayLabel(day.time), 15, true, ink);
        row.addView(dayName, weight(1, -2));
        WeatherIconView icon = new WeatherIconView(this);
        icon.setWeather(day.weatherCode, isDayTime(day.time + "T12:00"));
        row.addView(icon, lp(dp(58), dp(52)));
        LinearLayout temperatures = new LinearLayout(this);
        temperatures.setOrientation(LinearLayout.VERTICAL);
        temperatures.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView high = plainText(formatTemp(day.maxTemperature), 14, true, accent);
        high.setGravity(Gravity.CENTER);
        temperatures.addView(high, lp(dp(58), -2));
        TextView low = plainText(formatTemp(day.minTemperature), 12, false, muted);
        low.setGravity(Gravity.CENTER);
        temperatures.addView(low, lp(dp(58), -2));
        row.addView(temperatures, lp(dp(64), -2));
        return row;
    }

    private void showDayDetailsDialog(WeatherData.Day day, int index) {
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setNestedScrollingEnabled(true);
        scroll.setBackgroundColor(Color.TRANSPARENT);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        panel.setPadding(dp(19), dp(18), dp(19), dp(14));
        GradientDrawable background = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.argb(230, 27, 57, 96), Color.argb(220, 8, 23, 48)});
        background.setCornerRadius(dp(30));
        background.setStroke(dp(1), Color.argb(125, 255, 202, 104));
        panel.setBackground(background);

        TextView title = plainText(index == 0 ? "تفاصيل اليوم" : "تفاصيل " + dayLabel(day.time),
                22, true, ink);
        title.setGravity(Gravity.RIGHT);
        panel.addView(title, lp(-1, -2));
        TextView subtitle = plainText(dayLongLabel(day.time) + "  •  معلومات اليوم كاملة", 12, false, muted);
        subtitle.setGravity(Gravity.RIGHT);
        panel.addView(subtitle, margins(-1, -2, 0, 14));

        panel.addView(detailHeading("ملخص اليوم"), margins(-1, -2, 0, 8));
        panel.addView(detailText(daySummaryText(day)), margins(-1, -2, 0, 14));
        panel.addView(detailDivider(), margins(-1, dp(1), 0, 14));

        panel.addView(detailHeading("النهار"), margins(-1, -2, 0, 8));
        panel.addView(detailText(periodDetailsText("النهار", day.dayWeatherCode,
                day.dayTemperature, day.dayHumidity, day.dayWindSpeed,
                day.dayRainProbability)), margins(-1, -2, 0, 14));
        panel.addView(detailDivider(), margins(-1, dp(1), 0, 14));

        panel.addView(detailHeading("الليل"), margins(-1, -2, 0, 8));
        panel.addView(detailText(periodDetailsText("الليل", day.nightWeatherCode,
                day.nightTemperature, day.nightHumidity, day.nightWindSpeed,
                day.nightRainProbability)), margins(-1, -2, 0, 14));
        panel.addView(detailDivider(), margins(-1, dp(1), 0, 14));

        panel.addView(detailHeading("الشروق والغروب"), margins(-1, -2, 0, 8));
        panel.addView(detailText("الشروق: " + shortTime(day.sunrise)
                + "\nالغروب: " + shortTime(day.sunset)), margins(-1, -2, 0, 14));

        TextView close = plainText("إغلاق", 14, true, accent);
        close.setGravity(Gravity.CENTER);
        close.setPadding(0, dp(12), 0, dp(4));
        close.setOnClickListener(v -> dialog.dismiss());
        panel.addView(close, lp(-1, -2));

        scroll.addView(panel, new ScrollView.LayoutParams(-1, -2));
        android.widget.FrameLayout dialogRoot = new android.widget.FrameLayout(this);
        dialogRoot.setBackgroundColor(Color.TRANSPARENT);
        dialogRoot.addView(scroll, new android.widget.FrameLayout.LayoutParams(-1, -1));
        dialog.setContentView(dialogRoot);
        dialog.setCancelable(true);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                window.setBackgroundBlurRadius(dp(22));
            }
            android.view.WindowManager.LayoutParams params = window.getAttributes();
            params.dimAmount = 0.54f;
            window.setAttributes(params);
            window.setLayout((int) (getResources().getDisplayMetrics().widthPixels * 0.96f),
                    (int) (getResources().getDisplayMetrics().heightPixels * 0.88f));
        }
    }

    private String daySummaryText(WeatherData.Day day) {
        return "الحالة: " + weatherName(day.weatherCode)
                + "\nالعظمى: " + formatTemp(day.maxTemperature)
                + "\nالصغرى: " + formatTemp(day.minTemperature)
                + "\nالمدى الحراري: " + formatTemp(day.maxTemperature - day.minTemperature)
                + "\nإجمالي المطر: " + formatNumber(day.totalPrecipitation) + " مم"
                + "\nأقصى مؤشر UV: " + formatUv(day.maxUvIndex);
    }

    private String periodDetailsText(String label, int weatherCode,
                                     double temperature, int humidity,
                                     double windSpeed, int rainProbability) {
        return "الحالة: " + weatherName(weatherCode)
                + "\nدرجة الحرارة: " + formatTemp(temperature)
                + "\nالرطوبة: " + (humidity > 0 ? humidity + "%" : "—")
                + "\nسرعة الرياح: " + (windSpeed > 0 ? formatNumber(windSpeed) + " كم/س" : "—")
                + "\nاحتمال المطر: " + rainProbability + "%";
    }

    private TextView detailHeading(String value) {
        TextView heading = plainText(value, 18, true, accent);
        heading.setGravity(Gravity.RIGHT);
        heading.setSingleLine(false);
        heading.setMaxLines(Integer.MAX_VALUE);
        heading.setIncludeFontPadding(true);
        return heading;
    }

    private TextView detailText(String value) {
        TextView body = plainText(value, 15, false, Color.rgb(232, 240, 248));
        body.setGravity(Gravity.RIGHT);
        body.setSingleLine(false);
        body.setMaxLines(Integer.MAX_VALUE);
        body.setHorizontallyScrolling(false);
        body.setIncludeFontPadding(true);
        body.setLineSpacing(dp(4), 1.0f);
        body.setPadding(dp(2), dp(2), dp(2), dp(2));
        return body;
    }

    private View detailDivider() {
        View divider = new View(this);
        divider.setBackgroundColor(Color.argb(105, 215, 235, 255));
        return divider;
    }

    private LinearLayout fact(String label, String value) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(4), dp(4), dp(4), dp(2));
        TextView v = plainText(value, 14, true, ink);
        v.setGravity(Gravity.CENTER);
        v.setSingleLine(false);
        v.setMaxLines(Integer.MAX_VALUE);
        v.setHorizontallyScrolling(false);
        v.setIncludeFontPadding(true);
        v.setLineSpacing(dp(1), 1.0f);
        box.addView(v, lp(-1, -2));
        TextView l = plainText(label, 11, false, Color.rgb(213, 229, 244));
        l.setGravity(Gravity.CENTER);
        l.setSingleLine(false);
        l.setMaxLines(Integer.MAX_VALUE);
        l.setHorizontallyScrolling(false);
        l.setIncludeFontPadding(true);
        l.setLineSpacing(dp(1), 1.0f);
        box.addView(l, margins(-1, 4, 0, 0));
        return box;
    }

    private LinearLayout card() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(17), dp(16), dp(17), dp(16));
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.argb(125, 23, 52, 88), Color.argb(72, 9, 23, 48)});
        bg.setCornerRadius(dp(24));
        bg.setStroke(dp(1), Color.argb(58, 215, 235, 255));
        box.setBackground(bg);
        return box;
    }

    private LinearLayout heroCard() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(19), dp(18), dp(19), dp(18));
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.argb(180, 45, 91, 143), Color.argb(104, 9, 25, 55)});
        bg.setCornerRadius(dp(28));
        bg.setStroke(dp(1), Color.argb(105, 255, 224, 151));
        box.setBackground(bg);
        return box;
    }

    private Button actionButton(String label, int width) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(ink);
        b.setTextSize(width > 0 ? 20 : 12);
        b.setAllCaps(false);
        applyTextContrast(b, width > 0);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(8), 0, dp(8), 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(70, 255, 255, 255));
        bg.setCornerRadius(dp(16));
        b.setBackground(bg);
        return b;
    }

    private void applyTextContrast(TextView view, boolean bold) {
        // يحافظ على لون النص الأبيض، ويضيف هالة داكنة حول الحروف فوق الفيديو الفاتح.
        view.setShadowLayer(dp(bold ? 3 : 2), 0f, dp(1), Color.argb(225, 0, 0, 0));
        view.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        return r;
    }

    private TextView text(String value, float size, boolean bold, int color) {
        TextView t = plainText(value, size, bold, color);
        applyTextContrast(t, bold);
        return t;
    }

    private TextView plainText(String value, float size, boolean bold, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setTypeface(Typeface.create("sans", bold ? Typeface.BOLD : Typeface.NORMAL));
        t.setIncludeFontPadding(true);
        return t;
    }

    private TextView text(String value, float size, boolean bold, int color, int gravity) {
        TextView t = text(value, size, bold, color);
        t.setGravity(gravity);
        return t;
    }

    private LinearLayout.LayoutParams lp(int width, int height) {
        return new LinearLayout.LayoutParams(width, height);
    }

    private LinearLayout.LayoutParams weight(float weight, int height) {
        return new LinearLayout.LayoutParams(0, height, weight);
    }

    private LinearLayout.LayoutParams margins(int width, int height, int left, int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(width, height);
        p.setMargins(dp(left), 0, 0, dp(bottom));
        return p;
    }

    private LinearLayout.LayoutParams centered(int width, int height) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(width, height);
        p.gravity = Gravity.CENTER_HORIZONTAL;
        return p;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private String formatTemp(double value) {
        return String.format(Locale.getDefault(), "%.0f°", value);
    }

    private String formatNumber(double value) {
        return String.format(Locale.getDefault(), "%.1f", value);
    }

    private String formatUv(double value) {
        return value <= 0 ? "—" : String.format(Locale.getDefault(), "%.1f", value);
    }

    private String formatVisibility(double meters) {
        if (meters <= 0) return "—";
        return String.format(Locale.getDefault(), "%.1f كم", meters / 1000d);
    }

    private String formatPressure(double pressure) {
        if (pressure <= 0) return "—";
        return String.format(Locale.getDefault(), "%.0f hPa", pressure);
    }

    private String windDirectionLabel(int degrees) {
        if (degrees < 0) return "—";
        String[] directions = {"شمال", "شمال شرقي", "شرق", "جنوب شرقي",
                "جنوب", "جنوب غربي", "غرب", "شمال غربي"};
        return directions[(int) Math.floor((degrees + 22.5) / 45.0) % 8];
    }

    private boolean isDayTime(String iso) {
        try {
            Date d = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US).parse(iso);
            int hour = Integer.parseInt(new SimpleDateFormat("HH", Locale.US).format(d));
            return hour >= 6 && hour < 19;
        } catch (Exception e) {
            return true;
        }
    }

    private String shortTime(String iso) {
        if (iso == null || iso.length() == 0) return "—";
        try {
            Date d = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US).parse(iso);
            return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(d);
        } catch (Exception e) {
            return iso.length() > 15 ? iso.substring(11, 16) : iso;
        }
    }

    private String hourLabel(String iso) {
        try {
            Date d = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US).parse(iso);
            return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(d);
        } catch (Exception e) {
            return iso.length() > 11 ? iso.substring(11, 16) : iso;
        }
    }

    private String dayLabel(String iso) {
        try {
            Date d = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(iso);
            return new SimpleDateFormat("EEEE", new Locale("ar")).format(d);
        } catch (Exception e) {
            return iso;
        }
    }

    private String dayLongLabel(String iso) {
        try {
            Date d = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(iso);
            return new SimpleDateFormat("EEEE، d MMMM", new Locale("ar")).format(d);
        } catch (Exception e) {
            return iso == null ? "" : iso;
        }
    }

    private String weatherName(int code) {
        if (code == 0 || code == -1 || code == -2) return "سماء صافية";
        if (code == 1 || code == -3) return "غائم جزئيًا";
        if (code == 2 || code == -4) return "غائم غالبًا";
        if (code == 3 || code == -5) return "غائم كليًا";
        if (code == -6) return "ضباب";
        if (code == -7) return "رذاذ";
        if (code == -8) return "مطر";
        if (code == -9) return "ثلوج";
        if (code == -10) return "عاصفة رعدية";
        if (code == -11) return "غبار";
        if (code == -12) return "عاصفة رملية";
        if (code == 999) return "حالة غير متوفرة";
        if (code <= 48) return "ضباب";
        if (code <= 57) return "رذاذ";
        if (code <= 67) return "مطر";
        if (code <= 77) return "ثلوج";
        if (code <= 82) return "زخات مطر";
        return "عاصفة رعدية";
    }

    private void showBackgroundSettingsDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(22), dp(4), dp(22), dp(4));
        box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        TextView description = plainText(
                "استخدم صور الطقس الثابتة افتراضيًا، أو فعّل الخلفيات المتحركة إذا كانت ملفات الفيديو متوفرة.",
                14, false, muted);
        description.setLineSpacing(0f, 1.15f);
        box.addView(description, lp(-1, -2));

        Switch animatedSwitch = new Switch(this);
        animatedSwitch.setText("الخلفيات المتحركة");
        animatedSwitch.setTextColor(ink);
        animatedSwitch.setTextSize(16f);
        animatedSwitch.setGravity(Gravity.CENTER_VERTICAL);
        animatedSwitch.setChecked(animatedBackgroundEnabled);
        box.addView(animatedSwitch, margins(-1, dp(14), 0, 0));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("إعدادات الخلفية")
                .setView(box)
                .setNegativeButton("إغلاق", null)
                .create();

        animatedSwitch.setOnCheckedChangeListener((buttonView, checked) -> {
            animatedBackgroundEnabled = checked;
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .edit()
                    .putBoolean(PREF_ANIMATED_BACKGROUND, checked)
                    .apply();
            if (backgroundView != null) {
                backgroundView.setVideoModeEnabled(checked);
            }
            Toast.makeText(this,
                    checked ? "تم تفعيل الخلفيات المتحركة" : "تم تفعيل الخلفيات الثابتة",
                    Toast.LENGTH_SHORT).show();
        });
        dialog.show();
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}