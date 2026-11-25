package com.din.htmltomp4.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.* // এটি Material 3 এর ইমপোর্ট
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.din.htmltomp4.ui.theme.BackgroundDark
import com.din.htmltomp4.viewmodel.HistoryViewModel

/** Full list of every past conversion, reached from the Dashboard's "See all" button. */
@OptIn(ExperimentalMaterial3Api::class) // <--- এই লাইনটি যোগ করা হয়েছে
@Composable
fun HistoryScreen(
    historyViewModel: HistoryViewModel,
    onBack: () -> Unit
) {
    val history by historyViewModel.history.collectAsState()
    val context = LocalContext.current

    Scaffold(
        containerColor = BackgroundDark,
        topBar = {
            TopAppBar(
                title = { Text("All conversions") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BackgroundDark,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundDark)
                .padding(padding)
        ) {
            if (history.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp)
                ) {
                    EmptyHistoryHint()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(history, key = { it.id }) { item ->
                        HistoryRow(
                            item = item,
                            onOpen = { openHistoryItem(context, item) },
                            onShare = { shareHistoryItem(context, item) },
                            onDelete = { historyViewModel.deleteItem(item) }
                        )
                    }
                }
            }
        }
    }
}