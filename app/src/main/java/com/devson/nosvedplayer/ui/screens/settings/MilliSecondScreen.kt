package com.devson.nosvedplayer.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MilliSecondScreen(onBack: () -> Unit) {
    val clipboardManager = LocalClipboardManager.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var liveMillis by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                liveMillis = System.currentTimeMillis()
                delay(40)
            }
        }
    }

    var millisInput by remember { mutableStateOf("") }
    val sdfA = remember { SimpleDateFormat("dd MMMM yyyy, HH:mm:ss", Locale.getDefault()) }
    val formattedDate = remember(millisInput) {
        millisInput.toLongOrNull()?.let {
            try { sdfA.format(Date(it)) } catch (e: Exception) { "Invalid value" }
        } ?: ""
    }

    val calendar = remember { Calendar.getInstance() }
    var selectedDate by remember { mutableStateOf(calendar.time) }
    val sdfB = remember { SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()) }

    val showDatePicker = remember { mutableStateOf(false) }
    val showTimePicker = remember { mutableStateOf(false) }

    if (showDatePicker.value) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDate.time)
        DatePickerDialog(
            onDismissRequest = { showDatePicker.value = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        val newCal = Calendar.getInstance().apply { timeInMillis = it }
                        val currentCal = Calendar.getInstance().apply { time = selectedDate }
                        currentCal.set(newCal.get(Calendar.YEAR), newCal.get(Calendar.MONTH), newCal.get(Calendar.DAY_OF_MONTH))
                        selectedDate = currentCal.time
                    }
                    showDatePicker.value = false
                    showTimePicker.value = true
                }) { Text("Next") }
            }
        ) { DatePicker(state = datePickerState) }
    }

    if (showTimePicker.value) {
        val tempCal = Calendar.getInstance().apply { time = selectedDate }
        val timePickerState = rememberTimePickerState(
            initialHour = tempCal.get(Calendar.HOUR_OF_DAY),
            initialMinute = tempCal.get(Calendar.MINUTE)
        )
        AlertDialog(
            onDismissRequest = { showTimePicker.value = false },
            confirmButton = {
                TextButton(onClick = {
                    val currentCal = Calendar.getInstance().apply { time = selectedDate }
                    currentCal.set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                    currentCal.set(Calendar.MINUTE, timePickerState.minute)
                    currentCal.set(Calendar.SECOND, 0)
                    selectedDate = currentCal.time
                    showTimePicker.value = false
                }) { Text("Confirm") }
            },
            text = { TimePicker(state = timePickerState) }
        )
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
                .verticalScroll(rememberScrollState())
                .padding(
                    top = padding.calculateTopPadding() + 16.dp,
                    bottom = padding.calculateBottomPadding() + 16.dp,
                    start = 16.dp,
                    end = 16.dp
                ),
            verticalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Live Current Time", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = sdfB.format(Date(liveMillis)),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(Modifier.height(4.dp))
                            Text("Milliseconds", style = MaterialTheme.typography.labelSmall)
                            Text(liveMillis.toString(), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.secondary)
                        }
                        Button(onClick = {
                            clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(liveMillis.toString()))
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Copy")
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Millis to Date", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                OutlinedTextField(
                    value = millisInput,
                    onValueChange = { if (it.all { c -> c.isDigit() }) millisInput = it },
                    label = { Text("Epoch Milliseconds") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    trailingIcon = {
                        Row {
                            if (millisInput.isNotEmpty()) {
                                IconButton(onClick = { millisInput = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                            IconButton(onClick = {
                                clipboardManager.getText()?.text?.let { text ->
                                    if (text.all { it.isDigit() }) millisInput = text
                                }
                            }) {
                                Icon(Icons.Default.ContentPaste, contentDescription = "Paste")
                            }
                        }
                    }
                )
                Text(
                    text = formattedDate.ifEmpty { "Result will appear here" },
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Date to Millis", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Surface(
                    onClick = { showDatePicker.value = true },
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Selected Date & Time (Tap to change)", style = MaterialTheme.typography.labelSmall)
                        Text(sdfB.format(selectedDate), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Milliseconds", style = MaterialTheme.typography.labelSmall)
                        Text(selectedDate.time.toString(), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.secondary)
                    }
                    Button(onClick = {
                        clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(selectedDate.time.toString()))
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Copy")
                    }
                }
            }
        }
    }
}
