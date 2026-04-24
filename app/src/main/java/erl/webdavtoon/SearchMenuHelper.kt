package erl.webdavtoon

import android.content.Context
import android.view.MenuItem
import androidx.appcompat.widget.SearchView

object SearchMenuHelper {
    fun configureLiveSearch(
        context: Context,
        searchItem: MenuItem?,
        hint: String,
        currentKeyword: () -> String,
        onKeywordChanged: (String) -> Unit
    ) {
        if (searchItem == null) return
        val searchView = (searchItem.actionView as? SearchView) ?: SearchView(context).also { view ->
            searchItem.actionView = view
        }
        searchItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM or MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW)
        searchView.queryHint = hint
        val keyword = currentKeyword()
        if (keyword.isNotEmpty()) {
            searchItem.expandActionView()
        }
        searchView.setQuery(keyword, false)
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                val submittedKeyword = query.orEmpty().trim()
                if (submittedKeyword != currentKeyword()) {
                    onKeywordChanged(submittedKeyword)
                }
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                val changedKeyword = newText.orEmpty().trim()
                if (changedKeyword != currentKeyword()) {
                    onKeywordChanged(changedKeyword)
                }
                return true
            }
        })
    }
}
