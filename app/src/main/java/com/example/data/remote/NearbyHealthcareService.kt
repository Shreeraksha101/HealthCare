package com.example.data.remote

import com.example.data.model.HealthcarePlace
import com.example.data.model.HealthcarePlaceType
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

@JsonClass(generateAdapter = true)
data class OverpassResponse(
    val elements: List<OverpassElement> = emptyList()
)

@JsonClass(generateAdapter = true)
data class OverpassElement(
    val id: Long = 0L,
    val lat: Double? = null,
    val lon: Double? = null,
    val center: OverpassCenter? = null,
    val tags: Map<String, String>? = null
)

@JsonClass(generateAdapter = true)
data class OverpassCenter(
    val lat: Double = 0.0,
    val lon: Double = 0.0
)

@JsonClass(generateAdapter = true)
data class NominatimResult(
    val lat: String = "0.0",
    val lon: String = "0.0",
    @Json(name = "display_name") val displayName: String = ""
)

interface OverpassApi {
    @POST("api/interpreter")
    suspend fun queryNearby(@Body queryBody: RequestBody): OverpassResponse
}

interface NominatimApi {
    @GET("search")
    suspend fun searchLocation(
        @Query("q") query: String,
        @Query("format") format: String = "json",
        @Query("limit") limit: Int = 1
    ): List<NominatimResult>
}

class NearbyHealthcareService {

    private val okHttp = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header("User-Agent", "HealthCareCompanionAndroid/1.0")
                .build()
            chain.proceed(req)
        }
        .build()

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val overpassApi: OverpassApi = Retrofit.Builder()
        .baseUrl("https://overpass-api.de/")
        .client(okHttp)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(OverpassApi::class.java)

    private val nominatimApi: NominatimApi = Retrofit.Builder()
        .baseUrl("https://nominatim.openstreetmap.org/")
        .client(okHttp)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(NominatimApi::class.java)

    suspend fun geocodeCityOrAddress(query: String): Pair<Double, Double>? = withContext(Dispatchers.IO) {
        val results = nominatimApi.searchLocation(query)
        val first = results.firstOrNull() ?: return@withContext null
        val lat = first.lat.toDoubleOrNull() ?: return@withContext null
        val lon = first.lon.toDoubleOrNull() ?: return@withContext null
        lat to lon
    }

    suspend fun fetchNearbyPlaces(
        latitude: Double,
        longitude: Double,
        radiusMeters: Int = 6000,
        filterType: HealthcarePlaceType = HealthcarePlaceType.ALL
    ): List<HealthcarePlace> = withContext(Dispatchers.IO) {
        val amenityFilter = filterType.osmAmenity
        val query = """
            [out:json][timeout:20];
            (
              node["amenity"~"$amenityFilter"](around:$radiusMeters,$latitude,$longitude);
              way["amenity"~"$amenityFilter"](around:$radiusMeters,$latitude,$longitude);
            );
            out center 40;
        """.trimIndent()
        val body = "data=$query".toRequestBody("application/x-www-form-urlencoded".toMediaType())
        val response = overpassApi.queryNearby(body)

        response.elements.mapNotNull { el ->
            val lat = el.lat ?: el.center?.lat ?: return@mapNotNull null
            val lon = el.lon ?: el.center?.lon ?: return@mapNotNull null
            val tags = el.tags ?: emptyMap()
            val name = tags["name"]?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val amenity = tags["amenity"].orEmpty()
            val placeType = when (amenity) {
                "hospital" -> HealthcarePlaceType.HOSPITAL
                "pharmacy" -> HealthcarePlaceType.PHARMACY
                else -> HealthcarePlaceType.CLINIC
            }
            val street = listOfNotNull(tags["addr:housenumber"], tags["addr:street"])
                .joinToString(" ")
                .trim()
            val city = tags["addr:city"].orEmpty()
            val address = listOf(street, city)
                .filter { it.isNotBlank() }
                .joinToString(", ")
                .ifBlank { tags["addr:full"] ?: "Nearby ${placeType.label}" }

            val phone = tags["phone"] ?: tags["contact:phone"] ?: "Not listed"
            val hours = tags["opening_hours"] ?: if (placeType == HealthcarePlaceType.HOSPITAL) "24/7 Emergency" else "Call for hours"
            val distKm = haversineKm(latitude, longitude, lat, lon)

            HealthcarePlace(
                id = el.id.toString(),
                name = name,
                type = placeType,
                address = address,
                phone = phone,
                openingHours = hours,
                latitude = lat,
                longitude = lon,
                distanceKm = distKm
            )
        }.sortedBy { it.distanceKm }
    }

    private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
}
