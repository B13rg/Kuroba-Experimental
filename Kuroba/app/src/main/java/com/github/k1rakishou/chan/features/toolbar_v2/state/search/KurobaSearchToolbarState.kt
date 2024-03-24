package com.github.k1rakishou.chan.features.toolbar_v2.state.search

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.textAsFlow
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import com.github.k1rakishou.chan.features.toolbar_v2.ToolbarMenu
import com.github.k1rakishou.chan.features.toolbar_v2.ToolbarMenuItem
import com.github.k1rakishou.chan.features.toolbar_v2.state.IKurobaToolbarParams
import com.github.k1rakishou.chan.features.toolbar_v2.state.IKurobaToolbarState
import com.github.k1rakishou.chan.features.toolbar_v2.state.ToolbarStateKind
import com.github.k1rakishou.chan.ui.compose.clearText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class KurobaSearchToolbarParams(
  val toolbarMenu: ToolbarMenu? = null
) : IKurobaToolbarParams {
  override val kind: ToolbarStateKind = ToolbarStateKind.Search
}

@Stable
class KurobaSearchToolbarState(
  params: KurobaSearchToolbarParams = KurobaSearchToolbarParams()
) : IKurobaToolbarState {
  private val _toolbarMenu = mutableStateOf<ToolbarMenu?>(params.toolbarMenu)
  val toolbarMenu: State<ToolbarMenu?>
    get() = _toolbarMenu

  private val _searchVisible = mutableStateOf(false)
  val searchVisible: State<Boolean>
    get() = _searchVisible

  val searchQueryState = TextFieldState()

  override val kind: ToolbarStateKind = params.kind

  override val leftMenuItem: ToolbarMenuItem? = null

  override val rightToolbarMenu: ToolbarMenu?
    get() = _toolbarMenu.value

  override fun update(params: IKurobaToolbarParams) {
    params as KurobaSearchToolbarParams

    _toolbarMenu.value = params.toolbarMenu
  }

  override fun updateFromState(toolbarState: IKurobaToolbarState) {
    toolbarState as KurobaSearchToolbarState

    _toolbarMenu.value = toolbarState._toolbarMenu.value
    // Do not update current state's `_searchVisible` from `toolbarState`
  }

  override fun onPushed() {
    _searchVisible.value = true
  }

  override fun onPopped() {
    _searchVisible.value = false
    searchQueryState.edit { clearText() }
  }

  fun listenForSearchQueryUpdates(): Flow<String> {
    return searchQueryState.textAsFlow()
      .map { textFieldCharSequence -> textFieldCharSequence.toString() }
  }

  fun listenForSearchVisibilityUpdates(): Flow<Boolean> {
    return snapshotFlow { _searchVisible.value }
  }

  fun isInSearchMode(): Boolean {
    return _searchVisible.value
  }

  fun clearSearchQuery() {
    searchQueryState.edit { clearText() }
  }

  override fun toString(): String {
    return "KurobaSearchToolbarState(searchVisible: ${_searchVisible.value}, searchQuery: ${searchQueryState.text})"
  }

}