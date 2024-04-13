package com.github.k1rakishou.chan.features.my_posts

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.base.BaseViewModel
import com.github.k1rakishou.chan.core.base.DebouncingCoroutineExecutor
import com.github.k1rakishou.chan.core.base.ViewModelSelectionHelper
import com.github.k1rakishou.chan.core.compose.AsyncData
import com.github.k1rakishou.chan.core.di.component.viewmodel.ViewModelComponent
import com.github.k1rakishou.chan.core.di.module.viewmodel.ViewModelAssistedFactory
import com.github.k1rakishou.chan.core.manager.SavedReplyManager
import com.github.k1rakishou.chan.ui.view.bottom_menu_panel.BottomMenuPanelItem
import com.github.k1rakishou.chan.ui.view.bottom_menu_panel.BottomMenuPanelItemId
import com.github.k1rakishou.common.isNotNullNorEmpty
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.post.ChanSavedReply
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.joda.time.DateTimeZone
import org.joda.time.format.DateTimeFormatterBuilder
import org.joda.time.format.ISODateTimeFormat
import java.util.*
import javax.inject.Inject

class SavedPostsViewModel(
  private val savedStateHandle: SavedStateHandle,
  private val savedReplyManager: SavedReplyManager
) : BaseViewModel() {
  private val _myPostsViewModelState = MutableStateFlow(MyPostsViewModelState())
  val myPostsViewModelState: StateFlow<MyPostsViewModelState>
    get() = _myPostsViewModelState.asStateFlow()

  private val searchQueryDebouncer = DebouncingCoroutineExecutor(viewModelScope)
  val viewModelSelectionHelper = ViewModelSelectionHelper<PostDescriptor, MenuItemClickEvent>()

  private var _rememberedFirstVisibleItemIndex: Int = 0
  val rememberedFirstVisibleItemIndex: Int
    get() = _rememberedFirstVisibleItemIndex

  private var _rememberedFirstVisibleItemScrollOffset: Int = 0
  val rememberedFirstVisibleItemScrollOffset: Int
    get() = _rememberedFirstVisibleItemScrollOffset

  private val _searchQuery = mutableStateOf<String?>(null)

  override fun injectDependencies(component: ViewModelComponent) {
    component.inject(this)
  }

  fun updatePrevLazyListState(firstVisibleItemIndex: Int, firstVisibleItemScrollOffset: Int) {
    _rememberedFirstVisibleItemIndex = firstVisibleItemIndex
    _rememberedFirstVisibleItemScrollOffset = firstVisibleItemScrollOffset
  }

  fun updateSearchQuery(newSearchQuery: String?) {
    _searchQuery.value = newSearchQuery
  }

  override suspend fun onViewModelReady() {
    viewModelScope.launch {
      savedReplyManager.savedRepliesUpdateFlow
        .debounce(1_000L)
        .collect { reloadSavedReplies() }
    }

    _myPostsViewModelState.updateState { copy(savedRepliesGroupedAsync = AsyncData.Loading) }

    val result = savedReplyManager.loadAll()
    if (result.isError()) {
      _myPostsViewModelState.updateState { copy(savedRepliesGroupedAsync = AsyncData.Error(result.unwrapError())) }
      return
    }

    reloadSavedReplies()
  }

  fun deleteSavedPosts(postDescriptors: List<PostDescriptor>) {
    viewModelScope.launch {
      savedReplyManager.unsavePosts(postDescriptors)
    }
  }

  fun toggleGroupSelection(threadDescriptor: ChanDescriptor.ThreadDescriptor) {
    val savedRepliesGrouped = (myPostsViewModelState.value.savedRepliesGroupedAsync as? AsyncData.Data)?.data
    if (savedRepliesGrouped == null) {
      return
    }

    val toggledSavedRepliesGroup = savedRepliesGrouped
      .firstOrNull { savedRepliesGroup -> savedRepliesGroup.threadDescriptor == threadDescriptor }

    if (toggledSavedRepliesGroup == null) {
      return
    }

    val groupPostDescriptors = toggledSavedRepliesGroup.savedReplyDataList.map { it.postDescriptor }
    val allSelected = groupPostDescriptors
      .all { postDescriptor -> viewModelSelectionHelper.isSelected(postDescriptor) }

    groupPostDescriptors.forEach { postDescriptor ->
      if (allSelected) {
        viewModelSelectionHelper.unselect(postDescriptor)
      } else {
        viewModelSelectionHelper.select(postDescriptor)
      }
    }
  }

  fun toggleSelection(postDescriptor: PostDescriptor) {
    viewModelSelectionHelper.toggleSelection(postDescriptor)
  }

  fun updateQueryAndReload() {
    searchQueryDebouncer.post(125L, { reloadSavedReplies() })
  }

  private suspend fun reloadSavedReplies() {
    withContext(Dispatchers.Default) {
      _myPostsViewModelState.updateState { copy(savedRepliesGroupedAsync = AsyncData.Loading) }

      val allSavedReplies = savedReplyManager.getAll()
      if (allSavedReplies.isEmpty()) {
        _myPostsViewModelState.updateState { copy(savedRepliesGroupedAsync = AsyncData.Data(emptyList())) }
        return@withContext
      }

      val groupedSavedReplies = allSavedReplies.entries
        .filter { mapEntry -> mapEntry.value.isNotEmpty() }
        .sortedByDescending { (_, savedReplies) ->
          return@sortedByDescending savedReplies.maxByOrNull { it.createdOn.millis }
            ?.createdOn?.millis ?: 0L
        }
        .mapNotNull { (threadDescriptor, savedReplies) ->
          val firstSavedReply = savedReplies.firstOrNull()
            ?: return@mapNotNull null

          val headerThreadInfo = buildString {
            append(threadDescriptor.siteName())
            append("/")
            append(threadDescriptor.boardCode())
            append("/")
            append(", Thread No. ")
            append(threadDescriptor.threadNo)
          }

          val headerThreadSubject = firstSavedReply.subject

          val savedReplyDataList = processSavedReplies(savedReplies)
          if (savedReplyDataList.isEmpty()) {
            return@mapNotNull null
          }

          return@mapNotNull GroupedSavedReplies(
            threadDescriptor = threadDescriptor,
            headerThreadInfo = headerThreadInfo,
            headerThreadSubject = headerThreadSubject,
            savedReplyDataList = savedReplyDataList
          )
        }

      _myPostsViewModelState.updateState {
        copy(savedRepliesGroupedAsync = AsyncData.Data(groupedSavedReplies))
      }
    }
  }

  private fun processSavedReplies(savedReplies: List<ChanSavedReply>): List<SavedReplyData> {
    return savedReplies
      .sortedBy { chanSavedReply -> chanSavedReply.postDescriptor.postNo }
      .mapNotNull { savedReply ->
        val dateTime = if (savedReply.createdOn.millis <= 0) {
          null
        } else {
          DATE_TIME_PRINTER.print(savedReply.createdOn)
        }

        val postHeader = buildString {
          append("Post No. ")
          append(savedReply.postDescriptor.postNo)

          if (dateTime != null) {
            append(" ")
            append(dateTime)
          }
        }

        val comment = savedReply.comment ?: "<Empty comment>"

        val searchQuery = _searchQuery.value
        if (searchQuery.isNotNullNorEmpty()) {
          var matches = false

          if (!matches && postHeader.contains(searchQuery, ignoreCase = true)) {
            matches = true
          }

          if (!matches && comment.contains(searchQuery, ignoreCase = true)) {
            matches = true
          }

          if (!matches) {
            return@mapNotNull null
          }
        }

        return@mapNotNull SavedReplyData(
          postDescriptor = savedReply.postDescriptor,
          postHeader = postHeader,
          comment = comment,
          dateTime = dateTime
        )
      }
  }

  fun getBottomPanelMenus(): List<BottomMenuPanelItem> {
    val currentlySelectedItems = viewModelSelectionHelper.getCurrentlySelectedItems()
    if (currentlySelectedItems.isEmpty()) {
      return emptyList()
    }

    val itemsList = mutableListOf<BottomMenuPanelItem>()

    itemsList += BottomMenuPanelItem(
      menuItemId = PostMenuItemId(MenuItemType.Delete),
      iconResId = R.drawable.ic_baseline_delete_outline_24,
      textResId = R.string.bottom_menu_item_delete,
      onClickListener = {
        val clickEvent = MenuItemClickEvent(
          menuItemType = MenuItemType.Delete,
          items = viewModelSelectionHelper.getCurrentlySelectedItems()
        )

        viewModelSelectionHelper.emitBottomPanelMenuItemClickEvent(clickEvent)
        viewModelSelectionHelper.unselectAll()
      }
    )

    return itemsList
  }

  fun deleteAllSavedPosts() {
    viewModelScope.launch { savedReplyManager.deleteAll() }
  }

  class PostMenuItemId(val menuItemType: MenuItemType) :
    BottomMenuPanelItemId {
    override fun id(): Int {
      return menuItemType.id
    }
  }

  data class MenuItemClickEvent(
    val menuItemType: MenuItemType,
    val items: List<PostDescriptor>
  )

  enum class MenuItemType(val id: Int) {
    Delete(0)
  }

  data class MyPostsViewModelState(
    val savedRepliesGroupedAsync: AsyncData<List<GroupedSavedReplies>> = AsyncData.NotInitialized
  )

  data class GroupedSavedReplies(
    val threadDescriptor: ChanDescriptor.ThreadDescriptor,
    val headerThreadInfo: String,
    val headerThreadSubject: String?,
    val savedReplyDataList: List<SavedReplyData>
  )

  data class SavedReplyData(
    val postDescriptor: PostDescriptor,
    val postHeader: String,
    val comment: String,
    val dateTime: String?
  )

  class ViewModelFactory @Inject constructor(
    private val savedReplyManager: SavedReplyManager
  ) : ViewModelAssistedFactory<SavedPostsViewModel> {
    override fun create(handle: SavedStateHandle): SavedPostsViewModel {
      return SavedPostsViewModel(
        savedStateHandle = handle,
        savedReplyManager = savedReplyManager
      )
    }
  }

  companion object {
    private const val TAG = "SavedPostsViewModel"

    private val DATE_TIME_PRINTER = DateTimeFormatterBuilder()
      .append(ISODateTimeFormat.date())
      .appendLiteral(' ')
      .append(ISODateTimeFormat.hourMinuteSecond())
      .toFormatter()
      .withZone(DateTimeZone.forTimeZone(TimeZone.getDefault()))
  }

}