package com.smarttoolfactory.composeimage.demo.zoom

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smarttoolfactory.image.zoom.AnimatedZoomLayout

@Composable
fun AnimatedZoomDemo() {

    Column(modifier = Modifier.fillMaxSize()) {
        AnimatedZoomLayout(
            modifier = Modifier.fillMaxSize(),
            enabled = { zoom, pan, rotation ->
                (zoom > 1.2f)
            }
        ) {
            Text(
                modifier = Modifier
                    .size(300.dp, height = 400.dp)
                    .background(Color.Yellow),
                text = "This Composable can be zoomed, rotated, or can moved.\n\n" +
                        "Also can move back to correct bounds if size \n" +
                        " of content is passed" +
                        "as parameter to Modifier.animatedZoom\n\n" +
                        "Fling gesture when last pointer is up",
                fontSize = 20.sp
            )
        }
    }
}