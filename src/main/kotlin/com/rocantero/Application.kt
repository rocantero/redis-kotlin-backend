package com.rocantero

import com.rocantero.plugins.configureHTTP
import com.rocantero.plugins.configureSerialization
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import io.ktor.client.*;
import io.netty.handler.codec.http.HttpResponse
import kotlinx.io.IOException

val cacheService = CacheService()

data class Location(val id: String, val name: String, val lat: Double, val lon: Double)

object Constants {
  const val API_URL = "https://api.tomorrow.io/v4/weather/realtime"
  const val FETCH_INTERVAL = 5 * 60 * 1000L // 5 minutos
  const val API_KEY = "wV3NipHcDxhxQlUdNeyo5ahinRWe3hot"
  // Tomorrow.io es muy flexible con el input de Location pero uso coordenadas por tranquilidad
  val LOCATIONS = mapOf(
    "CL" to Location("CL", "Santiago (CL)", -33.437774658203125, -70.65045166015625),
    "CH" to Location("CH", "Zúrich (CH)", 47.41330337524414,  8.656394004821777),
    "NZ" to Location("NZ", "Auckland (NZ)", -36.541282653808594,  174.5506134033203),
    "AU" to Location("AU", "Sídney (AU)", -33.869842529296875,  151.20828247070312),
    "UK" to Location("UK", "Londres (UK)", 51.51561737060547, -0.09199830144643784),
    "USA" to Location("USA", "Georgia (USA)", 32.32938003540039, -83.11373901367188)
  )
  // Redis
  const val REDIS_HOST = "localhost"
  const val REDIS_PORT = 6379
}

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
  configureSerialization()
  configureHTTP()


  routing {
    get("/") {
        call.respondText("Hello World!")
    }

    get("/locations") {
        call.respond(Constants.LOCATIONS.values)
    }

    get("/locations/{id}") {
        val id = call.parameters["id"] ?: return@get call.respondText("Bad Request", status = HttpStatusCode.BadRequest)
        val location = Constants.LOCATIONS[id] ?: return@get call.respondText("Not Found", status = HttpStatusCode.NotFound)

        val cachedLocation = cacheService.get(id)
        call.respond(cachedLocation ?: "No data")
    }      
  }

  val scheduler = Executors.newScheduledThreadPool(1)
  scheduler.scheduleAtFixedRate({
    GlobalScope.launch {
      fetchAllWeatherData()
      println("Get location weather data - ${System.currentTimeMillis()}")
    }
  }, 0, 5, TimeUnit.MINUTES)


    startScheduledTask()
}

fun Application.startScheduledTask() {
  val scope = CoroutineScope(Dispatchers.Default)

  scope.launch {
      while (true) {
          fetchAllWeatherData()
          delay(Constants.FETCH_INTERVAL) 
      }
  }
}

fun fetchAllWeatherData() {
  Constants.LOCATIONS.forEach { (id, location) ->
    val weather = getWeather(location.lat, location.lon)
    cacheService.put(id, weather)
  }
}

fun getWeather(lat: Double, lon: Double): String {
  val apiUrl = Constants.API_URL
  val apiKey = Constants.API_KEY
  val location = "$lat,$lon"
  val requestUrl = "$apiUrl?apikey=$apiKey&location=$location"

  val client = HttpClient(CIO)
  val canFetch = Math.random() > 0.2
  if (!canFetch) {
      throw IOException("The API Request Failed")
  }
  val response = client.get<HttpResponse>(requestUrl)
  return response.readText()
}

