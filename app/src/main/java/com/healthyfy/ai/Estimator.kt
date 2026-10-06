package com.healthyfy.ai

import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

data class Nutrition(
    val name: String,
    val calories: Int,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val source: String,
)

private val http = OkHttpClient.Builder()
    .callTimeout(40, TimeUnit.SECONDS)
    .build()
private val json = Json { ignoreUnknownKeys = true }

/**
 * Google Gemini free tier (get a key at https://aistudio.google.com/apikey).
 * Handles free-text ("2 rotis and dal") and photos.
 */
class GeminiEstimator(private val apiKey: String) {
    private val model = "gemini-2.5-flash"

    suspend fun estimate(text: String?, photo: Bitmap?): List<Nutrition> = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank()) { "Add a Gemini API key in Settings" }
        val parts = buildJsonArray {
            add(buildJsonObject {
                put("text", "Identify each food item in this meal and estimate nutrition for the portion " +
                    "described/shown. Be realistic about portion sizes. ${text.orEmpty()}")
            })
            if (photo != null) add(buildJsonObject {
                put("inline_data", buildJsonObject {
                    put("mime_type", "image/jpeg")
                    put("data", photo.toBase64())
                })
            })
        }
        val schema = buildJsonObject {
            put("type", "ARRAY")
            put("items", buildJsonObject {
                put("type", "OBJECT")
                put("properties", buildJsonObject {
                    put("name", buildJsonObject { put("type", "STRING") })
                    put("calories", buildJsonObject { put("type", "INTEGER") })
                    put("protein_g", buildJsonObject { put("type", "NUMBER") })
                    put("carbs_g", buildJsonObject { put("type", "NUMBER") })
                    put("fat_g", buildJsonObject { put("type", "NUMBER") })
                })
                put("required", buildJsonArray { listOf("name", "calories", "protein_g", "carbs_g", "fat_g").forEach { add(it) } })
            })
        }
        val body = buildJsonObject {
            put("contents", buildJsonArray { add(buildJsonObject { put("parts", parts) }) })
            put("generationConfig", buildJsonObject {
                put("responseMimeType", "application/json")
                put("responseSchema", schema)
            })
        }.toString()

        val req = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
            .header("x-goog-api-key", apiKey)
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("Gemini error ${resp.code}: ${raw.take(200)}")
            val out = json.parseToJsonElement(raw).jsonObject["candidates"]!!.jsonArray[0]
                .jsonObject["content"]!!.jsonObject["parts"]!!.jsonArray[0]
                .jsonObject["text"]!!.jsonPrimitive.content
            json.parseToJsonElement(out).jsonArray.map {
                val o = it.jsonObject
                Nutrition(
                    o["name"]!!.jsonPrimitive.content,
                    o["calories"]!!.jsonPrimitive.int,
                    o["protein_g"]!!.jsonPrimitive.double,
                    o["carbs_g"]!!.jsonPrimitive.double,
                    o["fat_g"]!!.jsonPrimitive.double,
                    "gemini",
                )
            }
        }
    }

    private fun Bitmap.toBase64(): String {
        val scaled = if (width > 1024) Bitmap.createScaledBitmap(this, 1024, height * 1024 / width, true) else this
        val bos = ByteArrayOutputStream()
        scaled.compress(CompressFormat.JPEG, 85, bos)
        return Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
    }
}

/** Open Food Facts: free, no key. Values are per 100 g. */
object OpenFoodFacts {
    suspend fun search(query: String): List<Nutrition> = withContext(Dispatchers.IO) {
        val url = "https://world.openfoodfacts.org/cgi/search.pl".toHttpUrlOrNull()!!.newBuilder()
            .addQueryParameter("search_terms", query)
            .addQueryParameter("search_simple", "1")
            .addQueryParameter("action", "process")
            .addQueryParameter("json", "1")
            .addQueryParameter("page_size", "10")
            .addQueryParameter("fields", "product_name,nutriments")
            .build()
        val req = Request.Builder().url(url).header("User-Agent", "Healthyfy/0.1 (personal app)").build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("OpenFoodFacts error ${resp.code}")
            val products = json.parseToJsonElement(resp.body!!.string()).jsonObject["products"]?.jsonArray.orEmpty()
            products.mapNotNull {
                val o = it.jsonObject
                val n = o["nutriments"]?.jsonObject ?: return@mapNotNull null
                val name = o["product_name"]?.jsonPrimitive?.contentOrNull?.takeIf { s -> s.isNotBlank() } ?: return@mapNotNull null
                val kcal = n["energy-kcal_100g"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
                Nutrition(
                    "$name (per 100 g)", kcal.toInt(),
                    n["proteins_100g"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                    n["carbohydrates_100g"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                    n["fat_100g"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                    "openfoodfacts",
                )
            }
        }
    }
}
