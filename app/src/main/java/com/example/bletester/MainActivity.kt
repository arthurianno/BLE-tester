package com.example.bletester

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.bletester.navigation.AppNavigation
import com.example.bletester.viewModels.ReportViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject


@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var bluetoothAdapter: BluetoothAdapter
    var mAuth = FirebaseAuth.getInstance()

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


    override fun onStart() {
        super.onStart()
        showBluetoothDialog()
        val user = mAuth.currentUser
        if (user != null) {
            // do your stuff
        } else {
            signInAnonymously()
        }

    }

    private fun signInAnonymously() {
        mAuth.signInAnonymously()
            .addOnSuccessListener { authResult ->
                Log.i("Main Activity", "signInAnonymously:Success")
            }
            .addOnFailureListener { exception ->
                Log.e("Main Activity", "signInAnonymously:FAILURE", exception)
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
