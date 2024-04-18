package com.github.k1rakishou.chan.features.toolbar

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.textAsFlow
import androidx.compose.runtime.IntState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import com.github.k1rakishou.chan.features.toolbar.state.KurobaToolbarSubState
import com.github.k1rakishou.chan.ui.compose.clearText
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

abstract class KurobaBaseSearchToolbarSubState(
  initialSearchQuery: String?
) : KurobaToolbarSubState() {

  protected val _searchBarCreatedState = mutableStateOf(false)
  val searchBarCreatedState: State<Boolean>
    get() = _searchBarCreatedState

  protected val _searchVisibleState = mutableStateOf(false)
  val searchVisibleState: State<Boolean>
    get() = _searchVisibleState

  protected val _searchQueryState = TextFieldState(initialText = initialSearchQuery ?: "")
  val searchQueryState: TextFieldState
    get() = _searchQueryState

  protected val _currentSearchItemIndex = mutableIntStateOf(-1)
  val currentSearchItemIndex: IntState
    get() = _currentSearchItemIndex

  protected val _totalFoundItems = mutableIntStateOf(-1)
  val totalFoundItems: IntState
    get() = _totalFoundItems

  private val _showFoundItemsAsPopupClicked = MutableSharedFlow<Unit>(
    extraBufferCapacity = 1,
    onBufferOverflow = BufferOverflow.DROP_OLDEST
  )
  val showFoundItemsAsPopupClicked: SharedFlow<Unit>
    get() = _showFoundItemsAsPopupClicked.asSharedFlow()

  override fun onCreated() {
    super.onCreated()

    _searchBarCreatedState.value = true
  }

  override fun onShown() {
    super.onShown()
    _searchVisibleState.value = true
  }

  override fun onHidden() {
    super.onHidden()
    _searchVisibleState.value = false
  }

  override fun onDestroyed() {
    super.onDestroyed()

    _searchQueryState.edit { clearText() }
    _currentSearchItemIndex.value = -1
    _totalFoundItems.value = -1
    _searchBarCreatedState.value = false
  }

  fun updateActiveSearchInfo(currentIndex: Int, totalFound: Int) {
    _currentSearchItemIndex.value = currentIndex
    _totalFoundItems.value = totalFound
  }

  fun listenForSearchQueryUpdates(): Flow<String> {
    return _searchQueryState.textAsFlow()
      .map { textFieldCharSequence -> textFieldCharSequence.toString() }
      .filter { isInSearchMode() }
  }

  fun listenForSearchCreationUpdates(): Flow<Boolean> {
    return snapshotFlow { _searchBarCreatedState.value }
  }

  fun listenForSearchVisibilityUpdates(): Flow<Boolean> {
    return snapshotFlow { _searchVisibleState.value }
  }

  fun isInSearchMode(): Boolean {
    return _searchVisibleState.value
  }

  fun onShowFoundItemsAsPopupClicked() {
    _showFoundItemsAsPopupClicked.tryEmit(Unit)
  }

}