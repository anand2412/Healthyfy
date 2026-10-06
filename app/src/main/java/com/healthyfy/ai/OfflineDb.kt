package com.healthyfy.ai

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class Item(
    val name: String,
    val serving: String,
    val kcal: Int,
    val p: Double,
    val c: Double,
    val f: Double,
    val aliases: List<String> = emptyList(),
)

/**
 * Fully offline fallback: a small bundled table (assets/foods.json) matched by name,
 * with a leading quantity ("2 roti") multiplying the serving.
 */
class OfflineDb(ctx: Context) {
    private val items: List<Item> = ctx.assets.open("foods.json").bufferedReader().use {
        Json.decodeFromString(it.readText())
    }

    fun estimate(query: String): List<Nutrition> {
        // split "2 roti and 1 cup dal" into parts
        return query.lowercase().split(",", " and ", "+", "\n").mapNotNull { part ->
            val p = part.trim()
            if (p.isEmpty()) return@mapNotNull null
            val qty = Regex("^(\\d+(?:\\.\\d+)?)\\s*").find(p)?.groupValues?.get(1)?.toDoubleOrNull() ?: 1.0
            val text = p.replace(Regex("^(\\d+(?:\\.\\d+)?)\\s*(x|pcs|piece|pieces|cup|cups|bowl|bowls)?\\s*"), "")
            val hit = items.firstOrNull { i -> (listOf(i.name) + i.aliases).any { text.contains(it.lowercase()) } }
                ?: return@mapNotNull null
            Nutrition(
                "${if (qty == 1.0) "" else "${qty.toString().removeSuffix(".0")} x "}${hit.name} (${hit.serving})",
                (hit.kcal * qty).toInt(), hit.p * qty, hit.c * qty, hit.f * qty, "offline",
            )
        }
    }
}
