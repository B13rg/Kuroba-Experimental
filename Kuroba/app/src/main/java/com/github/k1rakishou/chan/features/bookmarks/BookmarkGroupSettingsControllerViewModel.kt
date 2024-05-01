package com.github.k1rakishou.chan.features.bookmarks

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.github.k1rakishou.chan.core.base.BaseViewModel
import com.github.k1rakishou.chan.core.di.component.viewmodel.ViewModelComponent
import com.github.k1rakishou.chan.core.di.module.shared.ViewModelAssistedFactory
import com.github.k1rakishou.chan.core.manager.ThreadBookmarkGroupManager
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.move
import com.github.k1rakishou.common.removeIfKt
import com.github.k1rakishou.model.data.bookmark.ThreadBookmarkGroup
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

class BookmarkGroupSettingsControllerViewModel(
  private val savedStateHandle: SavedStateHandle,
  private val threadBookmarkGroupManager: ThreadBookmarkGroupManager
) : BaseViewModel() {

  private var _loading = mutableStateOf(true)
  val loading: State<Boolean>
    get() = _loading

  val threadBookmarkGroupItems = mutableStateListOf<ThreadBookmarkGroupItem>()

  override fun injectDependencies(component: ViewModelComponent) {
    component.inject(this)
  }

  override suspend fun onViewModelReady() {
  }

  fun reload() {
    viewModelScope.launch {
      val newThreadBookmarkGroupItems = mutableListOf<ThreadBookmarkGroupItem>()

      threadBookmarkGroupManager.viewBookmarkGroupsOrdered { threadBookmarkGroup ->
        newThreadBookmarkGroupItems += ThreadBookmarkGroupItem(
          groupId = threadBookmarkGroup.groupId,
          groupName = threadBookmarkGroup.groupName,
          groupOrder = threadBookmarkGroup.groupOrder,
          groupEntriesCount = threadBookmarkGroup.getEntriesCount(),
          hasNoMatcher = threadBookmarkGroup.matchingPattern == null
        )
      }

      threadBookmarkGroupItems.clear()

      if (newThreadBookmarkGroupItems.isNotEmpty()) {
        threadBookmarkGroupItems.addAll(newThreadBookmarkGroupItems)
      }

      _loading.value = false
    }
  }

  suspend fun moveBookmarkGroup(fromIndex: Int, toIndex: Int, fromGroupId: String, toGroupId: String) {
    if (threadBookmarkGroupManager.onBookmarkGroupMoving(fromGroupId, toGroupId)) {
      threadBookmarkGroupItems.move(fromIdx = fromIndex, toIdx = toIndex)
    }
  }

  suspend fun onMoveBookmarkGroupComplete() {
    threadBookmarkGroupManager.updateGroupOrders()
  }

  suspend fun removeBookmarkGroup(groupId: String): ModularResult<Unit> {
    return withContext(NonCancellable) {
      val prevBookmarkDescriptorsInGroup = threadBookmarkGroupManager.getBookmarkDescriptorsInGroup(groupId)

      val removeResult = threadBookmarkGroupManager.removeBookmarkGroup(groupId)
      if (removeResult.valueOrNull() == true) {
        if (prevBookmarkDescriptorsInGroup.isNotEmpty()) {
          threadBookmarkGroupManager.createGroupEntries(
            bookmarkThreadDescriptors = prevBookmarkDescriptorsInGroup,
            forceDefaultGroup = true
          )
        }

        threadBookmarkGroupItems
          .removeIfKt { threadBookmarkGroupItem -> threadBookmarkGroupItem.groupId == groupId }
      }

      return@withContext removeResult.mapValue { Unit }
    }
  }

  suspend fun moveBookmarksIntoGroup(
    groupId: String,
    bookmarksToMove: List<ChanDescriptor.ThreadDescriptor>
  ): ModularResult<Boolean> {
    return threadBookmarkGroupManager.moveBookmarksFromGroupToGroup(bookmarksToMove, groupId)
  }

  suspend fun createBookmarkGroup(groupName: String): ModularResult<Unit> {
    return threadBookmarkGroupManager.createBookmarkGroup(groupName)
  }

  suspend fun existingGroupIdAndName(groupName: String): ModularResult<ThreadBookmarkGroupManager.GroupIdWithName?> {
    return threadBookmarkGroupManager.existingGroupIdAndName(groupName)
  }

  data class ThreadBookmarkGroupItem(
    val groupId: String,
    val groupName: String,
    val groupOrder: Int,
    val groupEntriesCount: Int,
    val hasNoMatcher: Boolean
  ) {
    fun isDefaultGroup(): Boolean = ThreadBookmarkGroup.isDefaultGroup(groupId)
  }

  class ViewModelFactory @Inject constructor(
    private val threadBookmarkGroupManager: ThreadBookmarkGroupManager
  ) : ViewModelAssistedFactory<BookmarkGroupSettingsControllerViewModel> {
    override fun create(handle: SavedStateHandle): BookmarkGroupSettingsControllerViewModel {
      return BookmarkGroupSettingsControllerViewModel(
        savedStateHandle = handle,
        threadBookmarkGroupManager = threadBookmarkGroupManager
      )
    }
  }

}