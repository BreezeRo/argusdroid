package dev.argus.tracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.argus.tracker.ArgusApplication
import dev.argus.tracker.worker.WorkScheduler

@Composable
fun ArgusApp() {
    val context = LocalContext.current
    val app = context.applicationContext as ArgusApplication
    val viewModel = viewModel<ArgusViewModel>(
        factory = ArgusViewModel.Factory(app.container.repository)
    )

    val recent by viewModel.recentEncounters.collectAsState()
    val summary by viewModel.summary.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.refreshSummary()
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Argus Tracker", style = MaterialTheme.typography.headlineMedium)
            Text("Long-term encounter intelligence across mobile sensors.")

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { WorkScheduler.start(context) }) {
                    Text("Start Tracking")
                }
                Button(onClick = {
                    WorkScheduler.stop(context)
                    viewModel.refreshSummary()
                }) {
                    Text("Stop")
                }
            }

            Text("Last 24h Summary", fontWeight = FontWeight.Bold)
            LazyColumn(contentPadding = PaddingValues(bottom = 8.dp)) {
                items(summary) { item ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(item.source)
                            Text(item.count.toString())
                        }
                    }
                }
            }

            Text("Recent Encounters", fontWeight = FontWeight.Bold)
            LazyColumn {
                items(recent.take(25)) { encounter ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("${encounter.source} • ${encounter.primaryId}")
                            Text("RSSI=${encounter.rssiDbm ?: "n/a"} dBm, Freq=${encounter.frequencyMhz ?: "n/a"} MHz")
                        }
                    }
                }
            }
        }
    }
}
