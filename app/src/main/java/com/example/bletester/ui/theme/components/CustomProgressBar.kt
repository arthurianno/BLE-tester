package com.example.bletester.ui.theme.components

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.bletester.ui.theme.Purple40

@Preview(showBackground = true)
@Composable
fun CustomProgressBar(progress: Float,currentCount: Int,totalCount:Int) {
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
            .fillMaxWidth()
            .padding(top = 16.dp, start = 30.dp, end = 30.dp)
    ) {
        // Progress Text
        Row(
            modifier = Modifier
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            AnimatedCounter(
                text = "Количество устройств ",
                count = currentCount,
                modifier = Modifier.padding(end = 4.dp),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(text = "/$totalCount")
        }

        // Progress Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(17.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(Color.Unspecified)
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
}
