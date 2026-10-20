package com.klencod.weather;

import java.util.ArrayList;
import java.util.List;

public final class WeatherData {
    public final String locationName;
    public final String countryName;
    /** فارغ للموقع الحالي كي تُستخدم منطقة الهاتف الزمنية، ومعبأ للمدن التي تم البحث عنها. */
    public final String timezoneId;
    public final Current current;
    public final List<Hour> hourly;
    public final List<Day> daily;

    public WeatherData(String locationName, String countryName, Current current, List<Hour> hourly, List<Day> daily) {
        this(locationName, countryName, "", current, hourly, daily);
    }

    public WeatherData(String locationName, String countryName, String timezoneId,
                       Current current, List<Hour> hourly, List<Day> daily) {
        this.locationName = locationName == null ? "" : locationName;
        this.countryName = countryName == null ? "" : countryName;
        this.timezoneId = timezoneId == null ? "" : timezoneId;
        this.current = current;
        this.hourly = hourly == null ? new ArrayList<>() : hourly;
        this.daily = daily == null ? new ArrayList<>() : daily;
    }

    public static final class Current {
        public final double temperature;
        public final double apparentTemperature;
        public final int humidity;
        public final double windSpeed;
        public final double precipitation;
        public final int weatherCode;
        public final boolean isDay;
        public final double uvIndex;
        public final double visibility;
        public final double pressure;
        public final int windDirection;

        public Current(double temperature, double apparentTemperature, int humidity,
                       double windSpeed, double precipitation, int weatherCode,
                       boolean isDay, double uvIndex, double visibility,
                       double pressure, int windDirection) {
            this.temperature = temperature;
            this.apparentTemperature = apparentTemperature;
            this.humidity = humidity;
            this.windSpeed = windSpeed;
            this.precipitation = precipitation;
            this.weatherCode = weatherCode;
            this.isDay = isDay;
            this.uvIndex = uvIndex;
            this.visibility = visibility;
            this.pressure = pressure;
            this.windDirection = windDirection;
        }
    }

    public static final class Hour {
        public final String time;
        public final double temperature;
        public final int precipitationProbability;
        public final int weatherCode;
        public final int humidity;
        public final double windSpeed;

        public Hour(String time, double temperature, int precipitationProbability, int weatherCode) {
            this(time, temperature, precipitationProbability, weatherCode, 0, 0.0);
        }

        public Hour(String time, double temperature, int precipitationProbability,
                    int weatherCode, int humidity, double windSpeed) {
            this.time = time;
            this.temperature = temperature;
            this.precipitationProbability = precipitationProbability;
            this.weatherCode = weatherCode;
            this.humidity = humidity;
            this.windSpeed = windSpeed;
        }
    }

    public static final class Day {
        public final String time;
        public final double maxTemperature;
        public final double minTemperature;
        public final int weatherCode;
        public final String sunrise;
        public final String sunset;
        public final double dayTemperature;
        public final double nightTemperature;
        public final int dayWeatherCode;
        public final int nightWeatherCode;
        public final int dayHumidity;
        public final int nightHumidity;
        public final double dayWindSpeed;
        public final double nightWindSpeed;
        public final int dayRainProbability;
        public final int nightRainProbability;
        public final double totalPrecipitation;
        public final double maxUvIndex;

        public Day(String time, double maxTemperature, double minTemperature,
                   int weatherCode, String sunrise, String sunset) {
            this(time, maxTemperature, minTemperature, weatherCode, sunrise, sunset,
                    maxTemperature, minTemperature, weatherCode, weatherCode,
                    0, 0, 0.0, 0.0, 0, 0, 0.0, 0.0);
        }

        public Day(String time, double maxTemperature, double minTemperature,
                   int weatherCode, String sunrise, String sunset,
                   double dayTemperature, double nightTemperature,
                   int dayWeatherCode, int nightWeatherCode,
                   int dayHumidity, int nightHumidity,
                   double dayWindSpeed, double nightWindSpeed,
                   int dayRainProbability, int nightRainProbability) {
            this(time, maxTemperature, minTemperature, weatherCode, sunrise, sunset,
                    dayTemperature, nightTemperature, dayWeatherCode, nightWeatherCode,
                    dayHumidity, nightHumidity, dayWindSpeed, nightWindSpeed,
                    dayRainProbability, nightRainProbability, 0.0, 0.0);
        }

        public Day(String time, double maxTemperature, double minTemperature,
                   int weatherCode, String sunrise, String sunset,
                   double dayTemperature, double nightTemperature,
                   int dayWeatherCode, int nightWeatherCode,
                   int dayHumidity, int nightHumidity,
                   double dayWindSpeed, double nightWindSpeed,
                   int dayRainProbability, int nightRainProbability,
                   double totalPrecipitation, double maxUvIndex) {
            this.time = time;
            this.maxTemperature = maxTemperature;
            this.minTemperature = minTemperature;
            this.weatherCode = weatherCode;
            this.sunrise = sunrise == null ? "" : sunrise;
            this.sunset = sunset == null ? "" : sunset;
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
            this.totalPrecipitation = totalPrecipitation;
            this.maxUvIndex = maxUvIndex;
        }
    }
}
