package com.example.bletester.items

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@SuppressLint("MissingPermission")
@Composable
fun DeviceListItem(device: String) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(5.dp)
            .clickable { expanded = !expanded }
            .clip(RoundedCornerShape(8.dp)),
        elevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(10.dp)
        ) {
            Text(
                text = "Device Name: $device",
                fontFamily = FontFamily.Serif,
                fontSize = 14.sp,
                color = if (expanded) Color.Blue else Color.Black,
            )
            if (expanded) {
                Text(
                    text = "MAC Address: None",
                    fontFamily = FontFamily.Serif,
                    fontSize = 14.sp,
                    color = Color.Gray,
                )
            }
        }
    }
}