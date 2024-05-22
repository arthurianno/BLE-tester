import android.bluetooth.BluetoothAdapter
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.window.Dialog
import com.example.bletester.ReportItem
import com.example.bletester.permissions.SystemBroadcastReceiver
import com.example.bletester.viewModels.ReportViewModel

@Composable
fun ReportScreen(
    onBluetoothStateChanged: () -> Unit,
) {
    val sampleData = listOf(
        ReportItem(
            device = "Device 1",
            deviceAddress = "00:11:22:33:44:55",
            status = "Прошло проверку",
            interpretation = "Устройство прошло проверку"
        ),
        ReportItem(
            device = "Device 2",
            deviceAddress = "66:77:88:99:AA:BB",
            status = "Не прошло проверку",
            interpretation = "Устройство не прошло проверку: Ошибка подключения."
        )
    )
    val reportViewModel: ReportViewModel = hiltViewModel()
    val reportItems = remember { sampleData }

    var showDialog by remember { mutableStateOf(false) }

    SystemBroadcastReceiver(systemAction = BluetoothAdapter.ACTION_STATE_CHANGED) { bluetoothState ->
        val action = bluetoothState?.action ?: return@SystemBroadcastReceiver
        if (action == BluetoothAdapter.ACTION_STATE_CHANGED) {
            onBluetoothStateChanged()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Column {
            Button(
                onClick = { showDialog = true },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Сохранить")
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (reportItems.isEmpty()) {
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
                    items(reportItems) { item ->
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
            onSave = { fileName, filePath ->
                reportViewModel.saveReport(fileName, filePath, reportItems)
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
            modifier = Modifier.padding(3.dp) // Increased padding
        ) {
            Text(
                text = device,
                fontFamily = FontFamily.Serif,
                fontSize = 15.sp, // Adjusted text size for better readability
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp)) // Added spacing between text elements
            Text(
                text = "Address: $deviceAddress",
                fontFamily = FontFamily.Serif,
                fontSize = 10.sp, // Adjusted text size for better readability
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp)) // Added spacing between text elements
            Text(
                text = "Status: $status",
                fontFamily = FontFamily.Serif,
                fontSize = 10.sp, // Adjusted text size for better readability
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp)) // Added spacing between text elements
            Text(
                text = interpretation,
                fontFamily = FontFamily.Serif,
                fontSize = 10.sp, // Adjusted text size for better readability
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun SaveFileDialog(onSave: (String, Uri) -> Unit, onCancel: () -> Unit) {
    var fileName by remember { mutableStateOf("") }
    var filePathUri by remember { mutableStateOf<Uri?>(null) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        filePathUri = uri?.let { treeUri ->
            DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
        }
    }

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
                Text("Введите имя файла и путь сохранения", fontFamily = FontFamily.Serif, fontSize = 16.sp)
                TextField(value = fileName, onValueChange = { fileName = it }, label = { Text("Имя файла") })
                Button(
                    onClick = { launcher.launch(null) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Выбрать путь сохранения")
                }
                filePathUri?.let {
                    Text("Выбранный путь: $it")
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Button(onClick = onCancel) {
                        Text("Отмена")
                    }
                    Button(
                        onClick = {
                            if (fileName.isNotBlank() && filePathUri != null) {
                                onSave(fileName, filePathUri!!)
                            }
                        },
                        enabled = fileName.isNotBlank() && filePathUri != null
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
