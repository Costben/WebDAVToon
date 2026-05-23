package erl.webdavtoon

import android.app.Activity
import android.app.Application
import android.os.Bundle

class PrivacyLifecycleObserver(private val app: Application) : Application.ActivityLifecycleCallbacks {

    private var startedCount = 0

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

    override fun onActivityStarted(activity: Activity) {
        startedCount++
    }

    override fun onActivityResumed(activity: Activity) = Unit

    override fun onActivityPaused(activity: Activity) = Unit

    override fun onActivityStopped(activity: Activity) {
        startedCount = (startedCount - 1).coerceAtLeast(0)
        if (startedCount == 0 &&
            PrivacyModeState.isPrivacyMode &&
            PrivacyModeState.exitPolicy == PrivacyModeState.ExitPolicy.ON_BACKGROUND
        ) {
            PrivacyModeState.exit(app)
        }
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    override fun onActivityDestroyed(activity: Activity) = Unit
}
