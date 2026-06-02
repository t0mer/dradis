package dev.tomerklein.dradis

import android.app.Application

/** Application entry point. Holds process-wide singletons via [dev.tomerklein.dradis.ServiceLocator]. */
class DradisApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
    }
}
