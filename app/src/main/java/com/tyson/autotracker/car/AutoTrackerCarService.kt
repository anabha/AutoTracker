package com.tyson.autotracker.car

import android.content.Intent
import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator

class AutoTrackerCarService : CarAppService() {

    // Validates that the host (Android Auto) is allowed to connect to your app
    override fun createHostValidator(): HostValidator {
        return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
    }

    override fun onCreateSession(): Session {
        return AutoTrackerSession()
    }
}

class AutoTrackerSession : Session() {
    override fun onCreateScreen(intent: Intent): Screen {
        // Launches the main screen on the car display
        return MainCarScreen(carContext)
    }
}
