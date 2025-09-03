package com.natinf.searchpro.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.natinf.searchpro.vm.SearchState
import com.natinf.searchpro.vm.SearchViewModel
import com.natinf.searchpro.vm.UIInfraction
import com.natinf.searchpro.util.PdfExporter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(vm: SearchViewModel = viewModel()) {
    val nav = rememberNavController()
    val state by vm.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("NATINF Search Pro") },
                actions = {
                    val ctx = LocalContext.current
                    IconButton(onClick = { vm.refreshFromInternet() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Mettre à jour")
                    }
                }
            )
        }
    ) { padding ->
        NavHost(navController = nav, startDestination = "list", modifier = Modifier.padding(padding)) {
            composable("list") { ListScreen(state, vm, nav) }
            composable("detail/{id}") { backStackEntry ->
                val id = backStackEntry.arguments?.getString("id")?.toIntOrNull() ?: return@composable
                DetailScreen(id, vm)
            }
        }
    }
}

@Composable
private fun ListScreen(state: SearchState, vm: SearchViewModel, nav: NavHostController) {
    Column(Modifier.padding(12.dp)) {
        OutlinedTextField(
            value = state.query,
            onValueChange = vm::onQueryChange,
            label = { Text("Rechercher (mot-clé, article, numéro NATINF)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChips(state.filterNature, vm::onFilterNature)
            FilterFavorites(state.onlyFavorites, vm::onToggleOnlyFavorites)
        }
        if (state.status.isNotBlank()) {
            Spacer(Modifier.height(8.dp)); AssistChip(onClick = {}, label = { Text(state.status) })
        }
        Spacer(Modifier.height(8.dp))
        if (state.results.isEmpty()) {
            Text("Aucun résultat.")
        } else {
            Text("${state.results.size} résultat(s)", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            LazyColumn {
                items(state.results) { it ->
                    InfractionRow(it, onClick = { nav.navigate("detail/${it.natinf}") }, onFav = { vm.toggleFavorite(it.natinf) })
                }
            }
        }
    }
}

@Composable
private fun FilterChips(selectedNature: String?, onSelect: (String?) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AssistChip(onClick = { onSelect(null) }, label = { Text("Tous") }, enabled = selectedNature != null)
        AssistChip(onClick = { onSelect("Crime") }, label = { Text("Crimes") }, enabled = selectedNature != "Crime")
        AssistChip(onClick = { onSelect("Délit") }, label = { Text("Délits") }, enabled = selectedNature != "Délit")
        AssistChip(onClick = { onSelect("Contravention") }, label = { Text("Contraventions") }, enabled = selectedNature != "Contravention")
    }
}

@Composable
private fun FilterFavorites(onlyFav: Boolean, toggle: () -> Unit) {
    FilterChip(selected = onlyFav, onClick = toggle, label = { Text(if (onlyFav) "Favoris" else "Tous") })
}

@Composable
private fun InfractionRow(item: UIInfraction, onClick: () -> Unit, onFav: () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth().padding(bottom = 10.dp).clickable(onClick = onClick)) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text("NATINF ${item.natinf}", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(2.dp))
                Text(item.qualification, maxLines = 2)
                Spacer(Modifier.height(4.dp))
                Text("Nature : ${item.nature}", fontWeight = FontWeight.SemiBold)
            }
            IconButton(onClick = onFav) {
                Icon(if (item.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, contentDescription = "Favori")
            }
        }
    }
}

@Composable
private fun DetailScreen(id: Int, vm: SearchViewModel) {
    val ctx = LocalContext.current
    val state by vm.state.collectAsState()
    val item = state.results.find { it.natinf == id } ?: return
    Column(Modifier.padding(16.dp)) {
        Text("NATINF ${item.natinf}", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(6.dp))
        Text(item.qualification)
        Spacer(Modifier.height(8.dp))
        Text("Nature : ${item.nature}", fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        if (item.articlesDef.isNotBlank()) LinkToLegifrance("Articles de définition", item.articlesDef)
        if (item.articlesPeine.isNotBlank()) LinkToLegifrance("Articles de peines", item.articlesPeine)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.toggleFavorite(item.natinf) }) { Text(if (item.isFavorite) "Retirer des favoris" else "Ajouter aux favoris") }
            Button(onClick = {
                val file = PdfExporter.exportInfraction(ctx, item)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, file)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                ctx.startActivity(Intent.createChooser(intent, "Partager l'infraction"))
            }) { Text("Partager PDF") }
        }
    }
}

@Composable
private fun LinkToLegifrance(label: String, query: String) {
    val ctx = LocalContext.current
    val url = "https://www.legifrance.gouv.fr/recherche?searchField=ALL&query=" + Uri.encode(query)
    TextButton(onClick = {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }) {
        Text(label)
    }
}
