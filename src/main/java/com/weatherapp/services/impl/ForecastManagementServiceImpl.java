package com.weatherapp.services.impl;

import com.weatherapp.configs.OpenWeatherConfig;
import com.weatherapp.entities.City;
import com.weatherapp.entities.Forecast;
import com.weatherapp.repositories.CityRepository;
import com.weatherapp.repositories.ForecastRepository;
import com.weatherapp.services.ForecastManagementService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Implementation of {@link ForecastManagementService} that manages the retrieval,
 * parsing, and storage of weather forecast data for cities.
 */
@RequiredArgsConstructor
@Service
public class ForecastManagementServiceImpl implements ForecastManagementService {

    private final ForecastRepository forecastRepository;
    private final CityRepository cityRepository;
    private final OpenWeatherConfig openWeatherConfig;

    private final RestTemplate restTemplate = new RestTemplate();
    private static final Logger log = LoggerFactory.getLogger(ForecastManagementServiceImpl.class);

    /**
     * Scheduled method that fetches weather forecasts for cities from the database from the OpenWeatherMap API,
     * clears existing forecasts, and saves the new forecasts in the database.
     * <p>
     * This method is executed daily at midnight (00:00:00) server time.
     */
    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    @Override
    public void insertForecastsIntoDatabase() {
        forecastRepository.deleteAll();
        forecastRepository.flush();

        List<City> cities = cityRepository.findAll();
        List<Forecast> allForecasts = new ArrayList<>();
        for (City city : cities) {
            String apiUrl = String.format(
                    "%s?lat=%.5f&lon=%.5f&cnt=16&units=metric&appid=%s",
                    openWeatherConfig.getUrl(), city.getLat(), city.getLon(), openWeatherConfig.getKey()
            );

            try {
                ResponseEntity<String> response = restTemplate.exchange(apiUrl, HttpMethod.GET, null, String.class);
                List<Forecast> forecastsForCity = forecastJSONParser(response.getBody(), city);
                allForecasts.addAll(forecastsForCity);
            } catch (Exception e) {
                log.error("Failed to fetch forecast for city {}: {}", city.getName(), e.getMessage());
            }
        }

        forecastRepository.saveAll(allForecasts);
    }

    /**
     * Helper method that parses the JSON response from the OpenWeatherMap API to a list of {@link Forecast} entities.
     *
     * @param json The JSON response body from the OpenWeatherMap API
     * @param city The {@link City} entity the forecast is associated with
     * @return a list of {@link Forecast} entities parsed from the response
     */
    private List<Forecast> forecastJSONParser(String json, City city) {
        List<Forecast> forecasts = new LinkedList<>();
        JSONObject root = new JSONObject(json);

        JSONArray forecastList = root.getJSONArray("list");

        for (int i = 0; i < forecastList.length(); i++) {
            JSONObject day = forecastList.getJSONObject(i);

            long timestamp = day.getLong("dt");
            LocalDate date = Instant.ofEpochSecond(timestamp)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate();
            double tempMax = day.getJSONObject("temp").getDouble("max");
            String weatherType = day.getJSONArray("weather").getJSONObject(0).getString("main");

            Forecast forecast = new Forecast();
            forecast.setCity(city);
            forecast.setForecastDate(date);
            forecast.setTempMax(tempMax);
            forecast.setWeatherMain(weatherType);

            forecasts.add(forecast);
        }

        return forecasts;
    }
}