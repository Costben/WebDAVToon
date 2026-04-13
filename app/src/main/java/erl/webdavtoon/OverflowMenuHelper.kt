package erl.webdavtoon

import android.view.Menu

object OverflowMenuHelper {
    fun enableOptionalIcons(menu: Menu?) {
        if (menu == null) return

        runCatching {
            val method = menu.javaClass.getDeclaredMethod(
                "setOptionalIconsVisible",
                Boolean::class.javaPrimitiveType
            )
            method.isAccessible = true
            method.invoke(menu, true)
        }
    }
}
