package com.natinf.searchpro.search

import android.content.Context
import android.util.Log
import com.natinf.searchpro.data.Normalizer
import com.natinf.searchpro.data.Infraction

interface SemanticEngine {
    fun rank(query: String, candidates: List<Infraction>): List<Pair<Infraction, Double>>
}

class LiteSemanticEngine: SemanticEngine {
    // Bag-of-words scoring with char n-grams + Jaro-Winkler already applied in repo.score()
    override fun rank(query: String, candidates: List<Infraction>): List<Pair<Infraction, Double>> {
        val q = Normalizer.normalizeForSearch(query)
        val qTerms = q.split(Regex("\\s+")).filter { it.isNotBlank() }.toSet()
        fun scoreText(text: String): Double {
            val t = Normalizer.normalizeForSearch(text)
            val hits = qTerms.count { t.contains(it) }
            val len = t.length.coerceAtLeast(1)
            return hits * 1.0 + (if (t.contains(q)) 2.0 else 0.0) - (len / 10000.0)
        }
        return candidates.map { it to (scoreText(it.qualification) + scoreText(it.articlesDef) * 0.5 + scoreText(it.articlesPeine) * 0.5) }
            .sortedByDescending { it.second }
    }
}

class OnnxSemanticEngine(private val context: Context): SemanticEngine {
    // Stub: plug your ONNX model here (e.g., MiniLM) and compute embeddings on-device.
    // For maintenant, we fallback to Lite.
    private val fallback = LiteSemanticEngine()
    override fun rank(query: String, candidates: List<Infraction>): List<Pair<Infraction, Double>> {
        Log.w("OnnxSemanticEngine", "ONNX model not bundled; using Lite fallback")
        return fallback.rank(query, candidates)
    }
}
