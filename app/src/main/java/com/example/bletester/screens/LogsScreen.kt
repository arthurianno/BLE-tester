package com.example.bletester.screens

import android.bluetooth.BluetoothAdapter
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bletester.LogItem
import com.example.bletester.LogLevel
import com.example.bletester.Logger
import com.example.bletester.permissions.SystemBroadcastReceiver
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LogsScreen(onBluetoothStateChanged: () -> Unit) {

    SystemBroadcastReceiver(systemAction = BluetoothAdapter.ACTION_STATE_CHANGED) { bluetoothState ->
        val action = bluetoothState?.action ?: return@SystemBroadcastReceiver
        if (action == BluetoothAdapter.ACTION_STATE_CHANGED) {
            onBluetoothStateChanged()
        }
    }

    // Состояние для хранения логов
    var logs by remember { mutableStateOf(Logger.getLogs()) }




    // Подписываемся на обновления логов
    DisposableEffect(Unit) {
        val listener: (List<LogItem>) -> Unit = { updatedLogs ->
            logs = updatedLogs
        }
        Logger.addLogListener(listener)
        onDispose {
            // Здесь можно отписаться от обновлений, если это необходимо
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopStart) {
        LazyColumn {
            itemsIndexed(items = logs, key = { _, log -> log.message }) { _, log ->
                LogItemView(log)
            }
        }
    }
}

@Composable
fun LogItemView(log: LogItem) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(8.dp)
    ) {
        Icon(
            imageVector = getIconForLogLevel(log.level),
            contentDescription = log.level.name,
            tint = log.level.color
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = "${log.tag}: ${log.message}",
                color = log.level.color
            )
            Text(
                text = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date()),
                fontSize = 10.sp,
                color = Color.Gray
            )
        }
    }
    Divider(color = Color.LightGray, thickness = 1.dp)
}

fun getIconForLogLevel(level: LogLevel): ImageVector {
    return when (level) {
        LogLevel.Error -> Icons.Filled.Warning
        LogLevel.Info -> Icons.Filled.Info
        // Добавьте иконки для других уровней логирования
        else -> Icons.Filled.Info
    }
}