package com.devson.nosvedplayer.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolScreen(onBack: () -> Unit) {
    var millisInput by remember { mutableStateOf("") }
    var dateInput by remember { mutableStateOf("") }
    val sdf = remember { SimpleDateFormat("dd MMMM yyyy, HH:mm:ss", Locale.getDefault()) }

    val formattedDate = remember(millisInput) {
        millisInput.toLongOrNull()?.let {
            try { sdf.format(Date(it)) } catch (e: Exception) { "Invalid value" }
        } ?: ""
    }

    val parsedMillis = remember(dateInput) {
        if (dateInput.isEmpty()) ""
        else try {
            sdf.parse(dateInput)?.time?.toString() ?: "Invalid format"
        } catch (e: Exception) {
            "Invalid format"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Timestamp Tool", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Millis to Date", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                OutlinedTextField(
                    value = millisInput,
                    onValueChange = { if (it.all { char -> char.isDigit() }) millisInput = it },
                    label = { Text("Epoch Milliseconds") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
                Text(
                    text = formattedDate.ifEmpty { "Enter milliseconds to convert" },
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (formattedDate.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Date to Millis", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                OutlinedTextField(
                    value = dateInput,
                    onValueChange = { dateInput = it },
                    label = { Text("Date String") },
                    placeholder = { Text("dd MMMM yyyy, HH:mm:ss") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Text(
                    text = parsedMillis.ifEmpty { "Enter date string to convert" },
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (parsedMillis == "Invalid format") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
