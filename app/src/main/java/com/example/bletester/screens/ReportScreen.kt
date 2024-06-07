
import android.bluetooth.BluetoothAdapter
import android.util.Log
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.bletester.ReportItem
import com.example.bletester.permissions.SystemBroadcastReceiver
import com.example.bletester.viewModels.ReportViewModel
import kotlinx.coroutines.launch

@Composable
fun ReportScreen(
    onBluetoothStateChanged: () -> Unit,
) {
    val sampleDataUncheck = listOf(
        ReportItem(
            device = "Device 1",
            deviceAddress = "00:11:22:33:44:55",
            status = "Не прошло проверку",
            interpretation = "Устройство не прошло проверку"
        ),
        ReportItem(
            device = "Device 2",
            deviceAddress = "66:77:88:99:AA:BB",
            status = "Не прошло проверку",
            interpretation = "Устройство не прошло проверку: Ошибка подключения."
        ),
        ReportItem(
            device = "Device 3",
            deviceAddress = "66:77:88:99:AA:BB",
            status = "Не прошло проверку",
            interpretation = "Устройство не прошло проверку: Ошибка подключения."
        ),
        ReportItem(
            device = "Device 4",
            deviceAddress = "66:77:88:99:AA:BB",
            status = "Не прошло проверку",
            interpretation = "Устройство не прошло проверку: Ошибка подключения."
        )
    )
    val sampleDataCheck = listOf(
        ReportItem(
            device = "Device 1",
            deviceAddress = "00:11:22:33:44:55",
            status = "прошло проверку",
            interpretation = "Устройство прошло проверку"
        ),
        ReportItem(
            device = "Device 2",
            deviceAddress = "66:77:88:99:AA:BB",
            status = "прошло проверку",
            interpretation = "Устройство прошло проверку"
        ),
        ReportItem(
            device = "Device 3",
            deviceAddress = "66:77:88:99:AA:BB",
            status = "прошло проверку",
            interpretation = "Устройство прошло проверку"
        ),
        ReportItem(
            device = "Device 4",
            deviceAddress = "66:77:88:99:AA:BB",
            status = "прошло проверку",
            interpretation = "Устройство прошло проверку"
        )
    )
    val reportViewModel: ReportViewModel = hiltViewModel()
    val reportItems = reportViewModel.reportItems
    val context = LocalContext.current
    val globalContext = LocalContext.current
    val toastMessage by reportViewModel.toastMessage.collectAsState()
    var fileNameInput by remember { mutableStateOf("") }
    var showDialog by remember { mutableStateOf(false) }
    var alertShowDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()


    SystemBroadcastReceiver(systemAction = BluetoothAdapter.ACTION_STATE_CHANGED) { bluetoothState ->
        val action = bluetoothState?.action ?: return@SystemBroadcastReceiver
        if (action == BluetoothAdapter.ACTION_STATE_CHANGED) {
            onBluetoothStateChanged()
        }
    }

    LaunchedEffect(toastMessage) {
        toastMessage?.let {
            Toast.makeText(globalContext, it, Toast.LENGTH_SHORT).show()
            reportViewModel.toastMessage.value = null // Сбросить сообщение после показа
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Button(
                    onClick = { showDialog = true }
                ) {
                    Text("Сохранить")
                }

                IconButton(onClick = {
                    alertShowDialog = true
                }) {
                    Icon(imageVector = Icons.Default.Email, contentDescription = "Загрузить отчет")
                }

                if (alertShowDialog) {
                    AlertDialog(
                        onDismissRequest = { alertShowDialog = false },
                        title = {
                            Text("Введите имя файла")
                        },
                        text = {
                            TextField(
                                value = fileNameInput,
                                onValueChange = { fileNameInput = it },
                                label = { Text("Имя файла") }
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    alertShowDialog = false
                                    coroutineScope.launch {
                                        val exists = reportViewModel.isReportFileExists(fileNameInput)
                                        if (exists) {
                                            val content = reportViewModel.loadTaskFromIni(fileNameInput)
                                            content.let {
                                                Toast.makeText(context, "Файл найден и загружен!", Toast.LENGTH_LONG).show()
                                            }
                                        } else {
                                            Toast.makeText(context, "Файл не найден", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            ) {
                                Text("OK")
                            }
                        },
                        dismissButton = {
                            Button(
                                onClick = { alertShowDialog = false }
                            ) {
                                Text("Отмена")
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (reportItems.value.isEmpty()) {
                Text(
                    text = "Экран отчета",
                    fontFamily = FontFamily.Serif,
                    fontSize = 22.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(reportItems.value) { item ->
                        ReportItemCard(
                            device = item.device,
                            deviceAddress = item.deviceAddress,
                            status = item.status,
                            interpretation = item.interpretation
                        )
                    }
                }
            }
        }
    }

    if (showDialog) {
        SaveFileDialog(
            onSave = { fileName ->
                reportViewModel.saveReport(reportItems.value)
                Log.e("ReportScreen","checking filename : $fileName and value: ${reportItems.value}")
                showDialog = false
            },
            onCancel = { showDialog = false }
        )
    }
}

@Composable
fun ReportItemCard(device: String, deviceAddress: String, status: String, interpretation: String) {
    Card(
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp), // Decreased the height of the card
        elevation = CardDefaults.cardElevation(
            defaultElevation = 8.dp // Reduced the elevation
        )
    ) {
        Column(
            modifier = Modifier.padding(3.dp)
        ) {
            Text(
                text = device,
                fontFamily = FontFamily.Serif,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Address: $deviceAddress",
                fontFamily = FontFamily.Serif,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Status: $status",
                fontFamily = FontFamily.Serif,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = interpretation,
                fontFamily = FontFamily.Serif,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun SaveFileDialog(onSave: (String) -> Unit, onCancel: () -> Unit) {
    var fileName by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onCancel) {
        Card(
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Введите имя файла для сохранения", fontFamily = FontFamily.Serif, fontSize = 16.sp)
                TextField(value = fileName, onValueChange = { fileName = it }, label = { Text("Имя файла") })

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Button(onClick = onCancel) {
                        Text("Отмена")
                    }
                    Button(
                        onClick = {
                            if (fileName.isNotBlank()) {
                                onSave(fileName)
                            }
                        },
                        enabled = fileName.isNotBlank()
                    ) {
                        Text("Сохранить")
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewReportItemCard() {
    ReportItemCard(
        device = "Device 1",
        deviceAddress = "00:11:22:33:44:55",
        status = "Проверено",
        interpretation = "Устройство прошло проверку"
    )
}
