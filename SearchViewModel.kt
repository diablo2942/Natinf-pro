package com.natinf.searchpro.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.natinf.searchpro.data.Infraction
import com.natinf.searchpro.data.NATRepository
import com.natinf.searchpro.search.LiteSemanticEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class UIInfraction(
    val natinf: Int,
    val qualification: String,
    val nature: String,
    val articlesDef: String,
    val articlesPeine: String,
    val isFavorite: Boolean = false
)

data class SearchState(
    val query: String = "",
    val filterNature: String? = null,
    val onlyFavorites: Boolean = false,
    val results: List<UIInfraction> = emptyList(),
    val status: String = ""
)

class SearchViewModel: ViewModel() {
    private val repo = NATRepository()
    private val ranker = LiteSemanticEngine()

    private val _state = MutableStateFlow(SearchState(status = "Initialisation..."))
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repo.ensureSeed()
            performSearch("")
            _state.value = _state.value.copy(status = "")
        }
    }

    fun onQueryChange(q: String) {
        _state.value = _state.value.copy(query = q)
        viewModelScope.launch { performSearch(q) }
    }

    fun onFilterNature(nature: String?) {
        _state.value = _state.value.copy(filterNature = nature)
        viewModelScope.launch { performSearch(_state.value.query) }
    }

    fun onToggleOnlyFavorites() {
        _state.value = _state.value.copy(onlyFavorites = !_state.value.onlyFavorites)
        viewModelScope.launch { performSearch(_state.value.query) }
    }

    fun toggleFavorite(n: Int) {
        viewModelScope.launch {
            repo.toggleFavorite(n)
            performSearch(_state.value.query)
        }
    }

    fun refreshFromInternet() {
        viewModelScope.launch {
            _state.value = _state.value.copy(status = "Téléchargement en cours…")
            try {
                val res = repo.updateFromDataGouv()
                _state.value = _state.value.copy(status = "Mise à jour OK (${res.count} entrées)")
                performSearch(_state.value.query)
            } catch (e: Exception) {
                _state.value = _state.value.copy(status = "Erreur: ${e.message}")
            }
        }
    }

    private suspend fun performSearch(q: String) {
        val nature = _state.value.filterNature
        val onlyFav = _state.value.onlyFavorites
        val base = repo.search(q, nature)
        val ranked = ranker.rank(q, base).map { it.first }
        val withFav = ranked.map { it.toUI(isFav = false) } // real fav state fetched per item below
        val results = withFav.map {
            val isFav = repo.isFavorite(it.natinf)
            it.copy(isFavorite = isFav)
        }
        _state.value = _state.value.copy(results = if (onlyFav) results.filter { it.isFavorite } else results)
    }

    private fun Infraction.toUI(isFav: Boolean): UIInfraction = UIInfraction(
        natinf = this.natinf,
        qualification = this.qualification,
        nature = this.nature,
        articlesDef = this.articlesDef,
        articlesPeine = this.articlesPeine,
        isFavorite = isFav
    )
}
