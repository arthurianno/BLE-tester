package com.example.bletester.ui.theme.report

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.bletester.items.ReportItem
import com.example.bletester.receivers.SystemBroadcastReceiver

@SuppressLint("StateFlowValueCalledInComposition")
@Composable
fun ReportScreen(onBluetoothStateChanged: () -> Unit) {
    val reportViewModel: ReportViewModel = hiltViewModel()
    val reportItems = reportViewModel.reportItems
    val globalContext = LocalContext.current
    val toastMessage by reportViewModel.toastMessage.collectAsState()

    SystemBroadcastReceiver(systemAction = BluetoothAdapter.ACTION_STATE_CHANGED) { bluetoothState ->
        bluetoothState?.action?.takeIf { it == BluetoothAdapter.ACTION_STATE_CHANGED }?.let {
            onBluetoothStateChanged()
        }
    }

    LaunchedEffect(toastMessage) {
        toastMessage?.let {
            Toast.makeText(globalContext, it, Toast.LENGTH_SHORT).show()
            reportViewModel.toastMessage.value = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        if (reportItems.value.isEmpty()) {
            EmptyReportScreen()
        } else {
            ReportList(reportItems = reportItems.value)
        }
    }
}

@Composable
fun EmptyReportScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Экран отчета",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
fun ReportList(reportItems: List<ReportItem>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(reportItems) { item ->
            ReportItemCard(item)
        }
    }
}

@Composable
fun ReportItemCard(item: ReportItem) {
    Card(
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 100.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = item.device,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Address: ${item.deviceAddress}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Status: ${item.status}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = item.interpretation,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}



@Preview(showBackground = true)
@Composable
fun PreviewReportItemCard() {
    MaterialTheme {
        ReportItemCard(
            ReportItem(
                device = "Device 1",
                deviceAddress = "00:11:22:33:44:55",
                status = "Проверено",
                interpretation = "Устройство прошло проверку"
            )
        )
    }
}