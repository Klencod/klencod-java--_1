package com.klencod.weather;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class WeatherApi {
    private static final String FORECAST_URL = "https://api.open-meteo.com/v1/forecast";
    private static final String GEOCODING_URL = "https://geocoding-api.open-meteo.com/v1/search";

    private WeatherApi() {
    }

    public static Place geocode(String city) throws Exception {
        String query = GEOCODING_URL + "?name=" + URLEncoder.encode(city, "UTF-8")
                + "&count=1&language=ar&format=json";
        JSONObject root = getJson(query);
        JSONArray results = root.optJSONArray("results");
        if (results == null || results.length() == 0) {
            throw new Exception("لم يتم العثور على المدينة");
        }
        JSONObject item = results.getJSONObject(0);
        return new Place(item.optString("name", city), item.optString("country", ""), item.getDouble("latitude"), item.getDouble("longitude"));
    }

    public static WeatherData fetch(double latitude, double longitude, String locationName,
                                    String countryName, boolean useDeviceTimezone) throws Exception {
        String query = String.format(Locale.US,
                "%s?latitude=%.6f&longitude=%.6f&current=temperature_2m,relative_humidity_2m,apparent_temperature,is_day,precipitation,weather_code,wind_speed_10m,wind_direction_10m,uv_index,visibility,surface_pressure&hourly=temperature_2m,relative_humidity_2m,precipitation_probability,weather_code,wind_speed_10m&daily=weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset,precipitation_sum,uv_index_max&forecast_days=7&timezone=auto",
                FORECAST_URL, latitude, longitude);
        JSONObject root = getJson(query);
        JSONObject currentJson = root.getJSONObject("current");
        WeatherData.Current current = new WeatherData.Current(
                currentJson.optDouble("temperature_2m"),
                currentJson.optDouble("apparent_temperature"),
                currentJson.optInt("relative_humidity_2m"),
                currentJson.optDouble("wind_speed_10m"),
                currentJson.optDouble("precipitation"),
                currentJson.optInt("weather_code"),
                currentJson.optInt("is_day", 1) == 1,
                currentJson.optDouble("uv_index"),
                currentJson.optDouble("visibility"),
                currentJson.optDouble("surface_pressure"),
                currentJson.optInt("wind_direction_10m")
        );

        List<WeatherData.Hour> hours = new ArrayList<>();
        List<WeatherData.Hour> allHours = new ArrayList<>();
        JSONObject hourlyJson = root.optJSONObject("hourly");
        if (hourlyJson != null) {
            JSONArray times = hourlyJson.optJSONArray("time");
            JSONArray temperatures = hourlyJson.optJSONArray("temperature_2m");
            JSONArray humidity = hourlyJson.optJSONArray("relative_humidity_2m");
            JSONArray rain = hourlyJson.optJSONArray("precipitation_probability");
            JSONArray codes = hourlyJson.optJSONArray("weather_code");
            JSONArray winds = hourlyJson.optJSONArray("wind_speed_10m");
            int count = times == null ? 0 : times.length();
            for (int i = 0; i < count; i++) {
                allHours.add(new WeatherData.Hour(
                        times.optString(i),
                        numberAt(temperatures, i),
                        intAt(rain, i),
                        intAt(codes, i),
                        intAt(humidity, i),
                        numberAt(winds, i)
                ));
            }
            int start = findCurrentHour(times, currentJson.optString("time"));
            int end = Math.min(allHours.size(), start + 12);
            for (int i = start; i < end; i++) hours.add(allHours.get(i));
        }

        List<WeatherData.Day> days = new ArrayList<>();
        JSONObject dailyJson = root.optJSONObject("daily");
        if (dailyJson != null) {
            JSONArray times = dailyJson.optJSONArray("time");
            JSONArray max = dailyJson.optJSONArray("temperature_2m_max");
            JSONArray min = dailyJson.optJSONArray("temperature_2m_min");
            JSONArray codes = dailyJson.optJSONArray("weather_code");
            JSONArray sunrise = dailyJson.optJSONArray("sunrise");
            JSONArray sunset = dailyJson.optJSONArray("sunset");
            JSONArray precipitation = dailyJson.optJSONArray("precipitation_sum");
            JSONArray uvMax = dailyJson.optJSONArray("uv_index_max");
            int count = times == null ? 0 : times.length();
            for (int i = 0; i < count; i++) {
                String date = times.optString(i);
                double maxTemperature = numberAt(max, i);
                double minTemperature = numberAt(min, i);
                int weatherCode = intAt(codes, i);
                DaySummary summary = summarizeDay(allHours, date, maxTemperature, minTemperature, weatherCode);
                days.add(new WeatherData.Day(
                        date, maxTemperature, minTemperature, weatherCode,
                        stringAt(sunrise, i), stringAt(sunset, i),
                        summary.dayTemperature, summary.nightTemperature,
                        summary.dayWeatherCode, summary.nightWeatherCode,
                        summary.dayHumidity, summary.nightHumidity,
                        summary.dayWindSpeed, summary.nightWindSpeed,
                        summary.dayRainProbability, summary.nightRainProbability,
                        numberAt(precipitation, i), numberAt(uvMax, i)
                ));
            }
        }
        String timezoneId = useDeviceTimezone ? "" : root.optString("timezone", "");
        return new WeatherData(locationName, countryName, timezoneId, current, hours, days);
    }

    private static final class DaySummary {
        final double dayTemperature;
        final double nightTemperature;
        final int dayWeatherCode;
        final int nightWeatherCode;
        final int dayHumidity;
        final int nightHumidity;
        final double dayWindSpeed;
        final double nightWindSpeed;
        final int dayRainProbability;
        final int nightRainProbability;

        DaySummary(double dayTemperature, double nightTemperature,
                   int dayWeatherCode, int nightWeatherCode,
                   int dayHumidity, int nightHumidity,
                   double dayWindSpeed, double nightWindSpeed,
                   int dayRainProbability, int nightRainProbability) {
            this.dayTemperature = dayTemperature;
            this.nightTemperature = nightTemperature;
            this.dayWeatherCode = dayWeatherCode;
            this.nightWeatherCode = nightWeatherCode;
            this.dayHumidity = dayHumidity;
            this.nightHumidity = nightHumidity;
            this.dayWindSpeed = dayWindSpeed;
            this.nightWindSpeed = nightWindSpeed;
            this.dayRainProbability = dayRainProbability;
            this.nightRainProbability = nightRainProbability;
        }
    }

    private static DaySummary summarizeDay(List<WeatherData.Hour> allHours, String date,
                                            double fallbackDay, double fallbackNight,
                                            int fallbackCode) {
        double dayTemp = 0.0, nightTemp = 0.0;
        double dayWind = 0.0, nightWind = 0.0;
        int dayHumidity = 0, nightHumidity = 0;
        int dayCount = 0, nightCount = 0;
        int dayRain = 0, nightRain = 0;
        int dayCode = fallbackCode, nightCode = fallbackCode;
        for (WeatherData.Hour hour : allHours) {
            if (hour.time == null || !hour.time.startsWith(date)) continue;
            if (isDayHour(hour.time)) {
                dayTemp += hour.temperature;
                dayWind += hour.windSpeed;
                dayHumidity += hour.humidity;
                dayRain = Math.max(dayRain, hour.precipitationProbability);
                if (dayCount == 0) dayCode = hour.weatherCode;
                dayCount++;
            } else {
                nightTemp += hour.temperature;
                nightWind += hour.windSpeed;
                nightHumidity += hour.humidity;
                nightRain = Math.max(nightRain, hour.precipitationProbability);
                if (nightCount == 0) nightCode = hour.weatherCode;
                nightCount++;
            }
        }
        return new DaySummary(
                dayCount == 0 ? fallbackDay : dayTemp / dayCount,
                nightCount == 0 ? fallbackNight : nightTemp / nightCount,
                dayCode, nightCode,
                dayCount == 0 ? 0 : Math.round((float) dayHumidity / dayCount),
                nightCount == 0 ? 0 : Math.round((float) nightHumidity / nightCount),
                dayCount == 0 ? 0.0 : dayWind / dayCount,
                nightCount == 0 ? 0.0 : nightWind / nightCount,
                dayRain, nightRain
        );
    }

    private static boolean isDayHour(String iso) {
        try {
            int hour = Integer.parseInt(iso.substring(11, 13));
            return hour >= 6 && hour < 18;
        } catch (Exception e) {
            return true;
        }
    }

    private static int findCurrentHour(JSONArray times, String currentTime) {
        if (times == null || times.length() == 0) return 0;
        for (int i = 0; i < times.length(); i++) {
            if (times.optString(i).compareTo(currentTime) >= 0) return i;
        }
        return 0;
    }

    private static double numberAt(JSONArray array, int index) {
        return array == null || index >= array.length() || array.isNull(index) ? 0.0 : array.optDouble(index);
    }

    private static int intAt(JSONArray array, int index) {
        return array == null || index >= array.length() || array.isNull(index) ? 0 : array.optInt(index);
    }

    private static String stringAt(JSONArray array, int index) {
        return array == null || index >= array.length() || array.isNull(index) ? "" : array.optString(index);
    }

    private static JSONObject getJson(String address) throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(address).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(20000);
            connection.setRequestProperty("Accept", "application/json");
            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
            String body = read(stream);
            if (status < 200 || status >= 300) {
                throw new Exception("خدمة الطقس غير متاحة حاليًا");
            }
            return new JSONObject(body);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static String read(InputStream stream) throws Exception {
        if (stream == null) throw new Exception("لم تصل استجابة من خدمة الطقس");
        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) result.append(line);
        }
        return result.toString();
    }

    public static final class Place {
        public final String name;
        public final String country;
        public final double latitude;
        public final double longitude;

        public Place(String name, String country, double latitude, double longitude) {
            this.name = name;
            this.country = country;
            this.latitude = latitude;
            this.longitude = longitude;
        }
    }
}
