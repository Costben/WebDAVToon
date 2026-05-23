package erl.webdavtoon

import android.app.KeyguardManager
import android.content.Context

object PrivacyModeState {

    enum class ExitPolicy(val code: String) {
        ON_BACKGROUND("on_background"),
        ON_PROCESS_DEATH("on_process_death"),
        MANUAL_ONLY("manual_only");

        companion object {
            fun fromCode(code: String?): ExitPolicy = entries.firstOrNull { it.code == code } ?: ON_BACKGROUND
        }
    }

    @Volatile
    var isPrivacyMode: Boolean = false
        private set

    @Volatile
    var exitPolicy: ExitPolicy = ExitPolicy.ON_BACKGROUND
        private set

    fun initFromPrefs(context: Context) {
        val store = AppSettingsStore(context)
        val policyCode = store.getOrDefaultString(AppSettingsStore.PRIVACY_MODE_EXIT_POLICY, ExitPolicy.ON_BACKGROUND.code)
        exitPolicy = ExitPolicy.fromCode(policyCode)

        if (exitPolicy == ExitPolicy.MANUAL_ONLY) {
            val persisted = store.getOrDefaultBoolean(AppSettingsStore.PRIVACY_MODE_ACTIVE, false)
            if (persisted) {
                val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                if (km?.isDeviceSecure == true) {
                    isPrivacyMode = true
                } else {
                    isPrivacyMode = false
                    store.putBoolean(AppSettingsStore.PRIVACY_MODE_ACTIVE, false)
                }
            }
        } else {
            isPrivacyMode = false
        }
    }

    fun enter(context: Context) {
        isPrivacyMode = true
        if (exitPolicy == ExitPolicy.MANUAL_ONLY) {
            AppSettingsStore(context).putBoolean(AppSettingsStore.PRIVACY_MODE_ACTIVE, true)
        }
    }

    fun exit(context: Context) {
        isPrivacyMode = false
        AppSettingsStore(context).putBoolean(AppSettingsStore.PRIVACY_MODE_ACTIVE, false)
    }

    fun setExitPolicy(context: Context, policy: ExitPolicy) {
        exitPolicy = policy
        AppSettingsStore(context).putString(AppSettingsStore.PRIVACY_MODE_EXIT_POLICY, policy.code)
        if (policy != ExitPolicy.MANUAL_ONLY) {
            AppSettingsStore(context).putBoolean(AppSettingsStore.PRIVACY_MODE_ACTIVE, false)
        }
    }
}
