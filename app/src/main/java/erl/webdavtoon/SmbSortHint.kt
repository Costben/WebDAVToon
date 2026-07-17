package erl.webdavtoon

import android.content.Context
import android.widget.Toast

/**
 * SMB folder previews are lazy (first 4 media files found) and do not follow the
 * sort order, unlike WebDAV/FTP. When the user switches the folder sort order on
 * an SMB slot, show a one-line hint so the unchanged previews are not mistaken
 * for a bug.
 */
object SmbSortHint {
    fun maybeShowPreviewHint(
        context: Context,
        settingsManager: SettingsManager,
        previousOrder: Int,
        newOrder: Int
    ) {
        if (previousOrder == newOrder) return
        if (!settingsManager.isWebDavEnabled()) return
        if (settingsManager.getWebDavProtocol() != "smb") return
        Toast.makeText(context, R.string.smb_preview_sort_hint, Toast.LENGTH_SHORT).show()
    }
}
