package dev.universaladmin.modules.worlds.action;

import dev.universaladmin.modules.worlds.WeatherState;

public record SetWorldWeatherInput(String worldName, WeatherState state) {
}
