package com.example.bletester

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.bletester.navigation.AppNavigation
import com.example.bletester.viewModels.ReportViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject


@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var bluetoothAdapter: BluetoothAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val reportViewModel: ReportViewModel = hiltViewModel()
            AppNavigation(
                onBluetoothStateChanged = {
                    showBluetoothDialog()
                }
            )
        }
    }








    private var isBtDialogShowed = false
    private fun showBluetoothDialog(){
        if(!bluetoothAdapter.isEnabled){
            if(!isBtDialogShowed){
            val enableBluetoothIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startBluetoothIntentForResult.launch(enableBluetoothIntent)
            isBtDialogShowed = true
        }
        }
    }

    private val startBluetoothIntentForResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()){result ->
            isBtDialogShowed = false
            if(result.resultCode != RESULT_OK){
                showBluetoothDialog()
            }
        }


}
