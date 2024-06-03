package com.example.bletester.screens.dialogs

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.bletester.ui.theme.Purple40

@Preview(showBackground = true)
@Composable
fun CustomProgressBar() {
    var progress by remember { mutableFloatStateOf(0f) }
    val size by animateFloatAsState(
        targetValue = progress,
        tween(
            durationMillis = 1000,
            delayMillis = 200,
            easing = LinearOutSlowInEasing
        ), label = ""
    )

    Column(
        modifier = Modifier
            .fillMaxWidth() // Use fillMaxWidth instead of fillMaxSize
            .padding(top = 16.dp, start = 30.dp, end = 30.dp)
    ) {
        // Progress Text
        Row(
            modifier = Modifier
                .fillMaxWidth(), // Fill the width of the column
            horizontalArrangement = Arrangement.Center
        ) {
            Text(text = "${(progress * 100).toInt()}%")
        }



        // Progress Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(17.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(Color.Unspecified) // Background color for the progress bar
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(size)
                    .clip(RoundedCornerShape(9.dp))
                    .background(Purple40)
                    .animateContentSize()
            )
        }
    }

    LaunchedEffect(key1 = true) {
        progress = 0.7f
    }
}
