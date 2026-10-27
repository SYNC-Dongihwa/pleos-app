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
import com.teamsync.pleos.car.GateState
import com.teamsync.pleos.log.AppLog
import com.teamsync.pleos.tutorial.Mode
import com.teamsync.pleos.ui.Bg
import com.teamsync.pleos.ui.DevPanel
import com.teamsync.pleos.ui.FinishScreen
import com.teamsync.pleos.ui.HomeScreen
import com.teamsync.pleos.ui.TutorialScreen
import com.teamsync.pleos.vehicle.VehicleConnection

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLog.i("UI", "MainActivity onCreate restored=${savedInstanceState != null}")
        val controller = AppGraph.tutorial
        setContent {
            val gate by AppGraph.gate.collectAsStateWithLifecycle()
            val car by AppGraph.car.state.collectAsStateWithLifecycle()
            val vehicle by AppGraph.vehicle.state.collectAsStateWithLifecycle()
            val errors by AppLog.errors.collectAsStateWithLifecycle()
            val tutorial by controller.state.collectAsStateWithLifecycle()
            val speech by AppGraph.speech.status.collectAsStateWithLifecycle()
            var devPanel by rememberSaveable { mutableStateOf(false) }
            val canStart = gate is GateState.Parked && vehicle.connection is VehicleConnection.Connected

            MaterialTheme(colorScheme = darkColorScheme()) {
                Box(Modifier.fillMaxSize().background(Bg)) {
                    when {
                        devPanel -> DevPanel(gate, car, vehicle, errors, tutorial, speech, onClose = { devPanel = false })
                        tutorial.mode == Mode.Running || tutorial.mode is Mode.Paused -> TutorialScreen(
                            tutorial, controller.steps, speech,
                            onSkip = controller::skip, onRepeat = controller::repeat, onQuit = controller::quit,
                        )
                        tutorial.mode == Mode.Finished -> FinishScreen(
                            tutorial, controller.steps, controller.isMaster, canStart,
                            onRetry = controller::retry, onRestart = { controller.start(fromBeginning = true) }, onHome = controller::quit,
                        )
                        else -> HomeScreen(
                            gate, car, vehicle, tutorial, controller.steps.size, controller.allResolved,
                            onStart = controller::start, onShowResults = controller::showResults,
                            onOpenDevPanel = { devPanel = true },
                        )
                    }
                }
            }
        }
    }
}
