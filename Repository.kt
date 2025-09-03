package com.natinf.searchpro.data

import android.content.Context
import android.net.Uri
import android.util.Log
import com.ibm.icu.text.Normalizer2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

object Normalizer {
    private val nfkd = Normalizer2.getNFKDInstance()
    fun normalizeForSearch(input: String): String {
        val lower = input.lowercase()
        val decomp = nfkd.normalize(lower)
        val stripped = decomp.replace(Regex("\\p{M}+"), "") // remove diacritics
        return stripped.replace(Regex("[^a-z0-9./-]+"), " ").trim()
    }
}

data class DownloadResult(val count: Int, val fromUrl: String)

class NATRepository(private val context: Context = AppCtx.appContext) {
    private val db by lazy { AppDb.get(context) }
    private val client by lazy { OkHttpClient() }
    private var synonyms: Map<String, List<String>> = emptyMap()

    suspend fun ensureSeed() {
        if (db.dao().count() == 0) seedFromAssets()
        loadSynonyms()
    }

    private suspend fun seedFromAssets() = withContext(Dispatchers.IO) {
        context.assets.open("natinf_sample.csv").use { s ->
            parseCsvAndUpsert(s.bufferedReader(Charsets.UTF_8).readText())
        }
    }

    private suspend fun loadSynonyms() = withContext(Dispatchers.IO) {
        try {
            val text = context.assets.open("synonyms_fr.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
            val obj = JSONObject(text)
            val map = mutableMapOf<String, MutableList<String>>()
            for (key in obj.keys()) {
                val arr = obj.getJSONArray(key)
                val list = mutableListOf<String>()
                for (i in 0 until arr.length()) list += arr.getString(i)
                map[key] = list
            }
            synonyms = map
        } catch (e: Exception) {
            Log.e("NATRepo", "Synonyms load error", e)
        }
    }

    // ---- Update from data.gouv API ----
    suspend fun updateFromDataGouv(): DownloadResult = withContext(Dispatchers.IO) {
        val api = "https://www.data.gouv.fr/api/1/datasets/liste-des-infractions-en-vigueur-de-la-nomenclature-natinf/"
        val req = Request.Builder().url(api).get().build()
        val resp = client.newCall(req).execute()
        if (!resp.isSuccessful) throw Exception("HTTP " + resp.code)
        val body = resp.body?.string() ?: throw Exception("Empty body")
        val json = JSONObject(body)
        val resources = json.getJSONArray("resources")
        // choose latest CSV
        var bestUrl: String? = null
        var bestDate: String? = null
        for (i in 0 until resources.length()) {
            val r = resources.getJSONObject(i)
            val fmt = r.optString("format").lowercase()
            if (fmt == "csv") {
                val u = r.optString("url").ifBlank { r.optString("latest") }
                val up = r.optString("last_modified") // ISO date
                if (bestDate == null || up > bestDate!!) {
                    bestDate = up
                    bestUrl = u
                }
            }
        }
        if (bestUrl == null) throw Exception("No CSV resource found")
        val dlReq = Request.Builder().url(bestUrl!!).get().build()
        val dlResp = client.newCall(dlReq).execute()
        if (!dlResp.isSuccessful) throw Exception("Download failed: HTTP " + dlResp.code)
        val csv = dlResp.body?.string() ?: throw Exception("Empty CSV")
        val count = parseCsvAndUpsert(csv)
        DownloadResult(count, bestUrl!!)
    }

    private suspend fun parseCsvAndUpsert(csv: String): Int = withContext(Dispatchers.IO) {
        val lines = csv.split('\n')
        if (lines.isEmpty()) return@withContext 0
        // find columns by header names (robust against order changes)
        val header = lines.first().split(';', ',')
        fun idx(name: String): Int = header.indexOfFirst { it.trim().equals(name, ignoreCase = true) }
        val idxNat = idx("natinf")
        val idxQual = idx("qualification") // sometimes "qualification_simplifiee"
        val idxQualAlt = idx("qualification_simplifiee")
        val idxNature = idx("nature")
        val idxDef = idx("articles_def") // variations: "articles_de_definition"
        val idxDefAlt = idx("articles_de_definition")
        val idxPeine = idx("articles_peine") // variations: "articles_de_peine"
        val idxPeineAlt = idx("articles_de_peine")

        val list = mutableListOf<Infraction>()
        for (i in 1 until lines.size) {
            val line = lines[i].strip()
            if (line.isEmpty()) continue
            val parts = line.split(';', ',')
            fun safe(i: Int): String = if (i >= 0 && i < parts.size) parts[i].trim() else ""
            val natStr = safe(idxNat)
            val nat = natStr.toIntOrNull() ?: continue
            val qual = safe(idxQual).ifBlank { safe(idxQualAlt) }
            val nature = safe(idxNature)
            val def = safe(idxDef).ifBlank { safe(idxDefAlt) }
            val peine = safe(idxPeine).ifBlank { safe(idxPeineAlt) }
            val norm = Normalizer.normalizeForSearch("$qual $nature $def $peine")
            list += Infraction(natinf = nat, qualification = qual, nature = nature, articlesDef = def, articlesPeine = peine, normalized = norm)
        }
        db.dao().upsertAll(list)
        return@withContext list.size
    }

    // ---- Search ----
    suspend fun search(query: String, nature: String?): List<Infraction> = withContext(Dispatchers.IO) {
        val qNum = query.trim().toIntOrNull()?.toString()
        val norm = Normalizer.normalizeForSearch(query)
        val expanded = expandQuery(norm)
        val res = db.dao().search(norm, expanded, qNum, nature)
        return@withContext res.sortedByDescending { score(it, norm, qNum) }.take(500)
    }

    private fun expandQuery(norm: String): String {
        val terms = norm.split(Regex("\\s+"))
        val out = mutableSetOf<String>()
        for (t in terms) {
            if (t.isBlank()) continue
            out += t
            synonyms[t]?.let { out.addAll(it.map { s -> Normalizer.normalizeForSearch(s) }) }
        }
        return out.joinToString(" ")
    }

    private fun score(item: Infraction, q: String, qNum: String?): Double {
        var s = 0.0
        if (qNum != null && item.natinf.toString() == qNum) s += 10.0
        val normText = item.normalized
        q.split(" ").forEach { term -> if (term.isNotBlank() && normText.contains(term)) s += 1.0 }
        s += jaroWinkler(q, Normalizer.normalizeForSearch(item.qualification)) * 2.0
        return s
    }

    // Jaro-Winkler
    private fun jaroWinkler(s1: String, s2: String): Double {
        fun jaro(a: String, b: String): Double {
            if (a == b) return 1.0
            val maxDist = (maxOf(a.length, b.length) / 2) - 1
            val aMatches = BooleanArray(a.length)
            val bMatches = BooleanArray(b.length)
            var matches = 0
            var transpositions = 0
            for (i in a.indices) {
                val start = maxOf(0, i - maxDist)
                val end = minOf(i + maxDist + 1, b.length)
                for (j in start until end) {
                    if (bMatches[j]) continue
                    if (a[i] != b[j]) continue
                    aMatches[i] = true
                    bMatches[j] = true
                    matches++
                    break
                }
            }
            if (matches == 0) return 0.0
            var k = 0
            for (i in a.indices) if (aMatches[i]) {
                while (!bMatches[k]) k++
                if (a[i] != b[k]) transpositions++
                k++
            }
            val m = matches.toDouble()
            val j = (m / a.length + m / b.length + (m - transpositions / 2.0) / m) / 3.0
            return j
        }
        val j = jaro(s1, s2)
        val prefix = s1.zip(s2).takeWhile { it.first == it.second }.take(4).count()
        return j + prefix * 0.1 * (1 - j)
    }

    // Favorites
    suspend fun toggleFavorite(natinf: Int) = withContext(Dispatchers.IO) {
        val dao = db.dao()
        val isFav = dao.isFav(natinf) != null
        if (isFav) dao.removeFav(Favorite(natinf)) else dao.addFav(Favorite(natinf))
    }
    suspend fun isFavorite(natinf: Int): Boolean = withContext(Dispatchers.IO) { db.dao().isFav(natinf) != null }
}
