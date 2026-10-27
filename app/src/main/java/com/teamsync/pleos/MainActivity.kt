package com.teamsync.pleos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.teamsync.pleos.log.AppLog
import com.teamsync.pleos.ui.Bg
import com.teamsync.pleos.ui.DevPanel
import com.teamsync.pleos.ui.HomeScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLog.i("UI", "MainActivity onCreate restored=${savedInstanceState != null}")
        setContent {
            val gate by AppGraph.gate.collectAsStateWithLifecycle()
            val car by AppGraph.car.state.collectAsStateWithLifecycle()
            val vehicle by AppGraph.vehicle.state.collectAsStateWithLifecycle()
            val errors by AppLog.errors.collectAsStateWithLifecycle()
            var devPanel by rememberSaveable { mutableStateOf(false) }

            MaterialTheme(colorScheme = darkColorScheme()) {
                Box(Modifier.fillMaxSize().background(Bg)) {
                    if (devPanel) {
                        DevPanel(gate, car, vehicle, errors, onClose = { devPanel = false })
                    } else {
                        HomeScreen(gate, car, vehicle, onOpenDevPanel = { devPanel = true })
                    }
                }
            }
        }
    }
}
