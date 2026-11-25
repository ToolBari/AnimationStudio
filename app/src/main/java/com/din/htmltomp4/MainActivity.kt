package com.din.htmltomp4

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.ui.Modifier
import com.din.htmltomp4.navigation.AppNavGraph
import com.din.htmltomp4.ui.theme.HtmlToMp4Theme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HtmlToMp4Theme {
                // enableEdgeToEdge() draws content behind the system bars by
                // design (for the immersive dark look), but that means every
                // screen needs its own inset padding or titles/buttons get
                // clipped under the status/nav bars. Applying it once here,
                // around the whole nav graph, covers every screen instead of
                // needing it repeated in each one.
                Box(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
                    AppNavGraph()
                }
            }
        }
    }
}
