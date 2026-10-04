package com.cineverse.app.data.places

import android.content.Context
import androidx.compose.runtime.Immutable
import com.cineverse.app.core.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder

/** Somewhere a title was filmed. */
@Immutable
data class Place(val name: String, val lat: Double, val lon: Double) {
    /** A country or a state is a region, not a place you could stand: drawn smaller. */
    val broad: Boolean get() = (lat * 10).rem(10.0) == 0.0 && (lon * 10).rem(10.0) == 0.0
}

/**
 * Where a film or series was shot, from Wikidata: its "filming location"
 * statements, each with the place's coordinates. Keyed by IMDb id, kept on
 * the device for a month, and quietly nothing when Wikidata has no record -
 * a map with no pins is not shown at all.
 */
class FilmingLocations(context: Context, private val http: OkHttpClient) {

    private val prefs = context.getSharedPreferences("filming_locations", Context.MODE_PRIVATE)

    suspend fun of(imdbId: String): List<Place> {
        if (!imdbId.matches(Regex("^tt\\d+$"))) return emptyList()
        prefs.getString(imdbId, null)?.let { raw ->
            val at = raw.substringBefore('\n').toLongOrNull() ?: 0
            if (System.currentTimeMillis() - at < 30 * 86_400_000L) return decode(raw.substringAfter('\n'))
        }
        val query = "SELECT ?locLabel ?coord WHERE { ?item wdt:P345 \"$imdbId\". ?item wdt:P915 ?loc. " +
            "?loc wdt:P625 ?coord. SERVICE wikibase:label { bd:serviceParam wikibase:language \"en\". } } LIMIT 40"
        val places = withTimeoutOrNull(25_000) {
            withContext(Dispatchers.IO) {
                runCatching {
                    val request = Request.Builder()
                        .url("https://query.wikidata.org/sparql?format=json&query=" + URLEncoder.encode(query, "UTF-8"))
                        .header("Accept", "application/sparql-results+json")
                        .build()
                    http.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) return@use null
                        val rows = Http.json.parseToJsonElement(response.body.string()).jsonObject["results"]
                            ?.jsonObject?.get("bindings")?.jsonArray.orEmpty()
                        rows.mapNotNull { row ->
                            val fields = row.jsonObject
                            val name = fields["locLabel"]?.jsonObject?.get("value")?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                            val point = fields["coord"]?.jsonObject?.get("value")?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                            val (lon, lat) = Regex("""Point\(([-\d.eE]+) ([-\d.eE]+)\)""").find(point)?.destructured ?: return@mapNotNull null
                            // An unlabelled item comes back as its own Q-number.
                            if (name.matches(Regex("Q\\d+"))) return@mapNotNull null
                            Place(name, lat.toDouble(), lon.toDouble())
                        }.distinctBy { it.name }
                    }
                }.getOrNull()
            }
        } ?: return emptyList()
        prefs.edit().putString(imdbId, "${System.currentTimeMillis()}\n" + encode(places)).apply()
        return places
    }

    private fun encode(places: List<Place>) = places.joinToString("\n") { "${it.lat}\t${it.lon}\t${it.name}" }

    private fun decode(raw: String) = raw.lines().mapNotNull { line ->
        val parts = line.split('\t')
        if (parts.size < 3) null else Place(parts[2], parts[0].toDoubleOrNull() ?: return@mapNotNull null, parts[1].toDoubleOrNull() ?: return@mapNotNull null)
    }
}
