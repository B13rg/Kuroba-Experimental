package com.github.k1rakishou.chan.features.my_posts

import android.content.Context
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Card
import androidx.compose.material.Divider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.controller.DeprecatedNavigationFlags
import com.github.k1rakishou.chan.core.base.BaseSelectionHelper
import com.github.k1rakishou.chan.core.compose.AsyncData
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.core.helper.DialogFactory
import com.github.k1rakishou.chan.core.helper.StartActivityStartupHandlerHelper
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.core.manager.WindowInsetsListener
import com.github.k1rakishou.chan.features.drawer.MainControllerCallbacks
import com.github.k1rakishou.chan.features.toolbar_v2.HamburgMenuItem
import com.github.k1rakishou.chan.features.toolbar_v2.KurobaToolbarState
import com.github.k1rakishou.chan.features.toolbar_v2.ToolbarMiddleContent
import com.github.k1rakishou.chan.features.toolbar_v2.ToolbarText
import com.github.k1rakishou.chan.ui.compose.SelectableItem
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeErrorMessage
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeProgressIndicator
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeText
import com.github.k1rakishou.chan.ui.compose.ktu
import com.github.k1rakishou.chan.ui.compose.providers.ComposeEntrypoint
import com.github.k1rakishou.chan.ui.compose.providers.LocalChanTheme
import com.github.k1rakishou.chan.ui.compose.simpleVerticalScrollbar
import com.github.k1rakishou.chan.ui.controller.navigation.TabPageController
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.chan.utils.viewModelByKey
import com.github.k1rakishou.common.isNotNullNorEmpty
import com.github.k1rakishou.core_themes.ChanTheme
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

class SavedPostsController(
  context: Context,
  private val mainControllerCallbacks: MainControllerCallbacks,
  private val startActivityCallback: StartActivityStartupHandlerHelper.StartActivityCallbacks
) : TabPageController(context),  WindowInsetsListener {

  @Inject
  lateinit var themeEngine: ThemeEngine
  @Inject
  lateinit var globalWindowInsetsManager: GlobalWindowInsetsManager
  @Inject
  lateinit var dialogFactory: DialogFactory

  private val bottomPadding = mutableIntStateOf(0)
  private val viewModel by lazy { requireComponentActivity().viewModelByKey<SavedPostsViewModel>() }

  override fun injectDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  override fun updateToolbarState(): KurobaToolbarState {
    updateNavigationFlags(
      newNavigationFlags = DeprecatedNavigationFlags(
        hasDrawer = true,
        hasBack = false,
        swipeable = false
      )
    )

    toolbarState.enterDefaultMode(
      leftItem = HamburgMenuItem(
        onClick = { toolbarIcon ->
          // TODO: New toolbar. Open the drawer.
        }
      ),
      middleContent = ToolbarMiddleContent.Title(
        title = ToolbarText.Id(R.string.controller_saved_posts)
      ),
      iconClickInterceptor = {
        viewModel.viewModelSelectionHelper.unselectAll()
        return@enterDefaultMode false
      },
      menuBuilder = {
        withMenuItem(
          drawableId = R.drawable.ic_search_white_24dp,
          onClick = { toolbarState.enterSearchMode() }
        )

        withOverflowMenu {
          withOverflowMenuItem(
            id = ACTION_DELETE_ALL_SAVED_POSTS,
            stringId = R.string.controller_saved_posts_delete_all,
            onClick = { onDeleteAllSavedPostsClicked() }
          )
        }
      }
    )

    return toolbarState
  }

  override fun onTabFocused() {
  }

  override fun canSwitchTabs(): Boolean {
    if (viewModel.viewModelSelectionHelper.isInSelectionMode()) {
      return false
    }

    return true
  }

  override fun onBack(): Boolean {
    if (viewModel.viewModelSelectionHelper.unselectAll()) {
      return true
    }

    return super.onBack()
  }

  override fun onCreate() {
    super.onCreate()

    controllerScope.launch {
      viewModel.viewModelSelectionHelper.selectionMode.collect { selectionEvent ->
        onNewSelectionEvent(selectionEvent)
      }
    }

    controllerScope.launch {
      viewModel.viewModelSelectionHelper.bottomPanelMenuItemClickEventFlow
        .collect { menuItemClickEvent ->
          onMenuItemClicked(menuItemClickEvent.menuItemType, menuItemClickEvent.items)
        }
    }

    controllerScope.launch {
      toolbarState.search.listenForSearchVisibilityUpdates()
        .onEach { searchVisible ->
          if (!searchVisible) {
            viewModel.updateSearchQuery(null)
            viewModel.updateQueryAndReload()
          }
        }
        .collect()
    }

    controllerScope.launch {
      toolbarState.search.listenForSearchQueryUpdates()
        .onEach { entered ->
          viewModel.updateSearchQuery(entered)
          viewModel.updateQueryAndReload()
        }
        .collect()
    }

    mainControllerCallbacks.onBottomPanelStateChanged { onInsetsChanged() }

    globalWindowInsetsManager.addInsetsUpdatesListener(this)
    onInsetsChanged()

    view = ComposeView(context).apply {
      setContent {
        ComposeEntrypoint {
          Box(modifier = Modifier.fillMaxSize()) {
            BuildContent()
          }
        }
      }
    }
  }

  override fun onDestroy() {
    super.onDestroy()

    globalWindowInsetsManager.removeInsetsUpdatesListener(this)
    mainControllerCallbacks.hideBottomPanel()

    viewModel.updateQueryAndReload()
    viewModel.viewModelSelectionHelper.unselectAll()
  }

  override fun onInsetsChanged() {
    bottomPadding.intValue = calculateBottomPaddingForRecyclerInDp(
      globalWindowInsetsManager = globalWindowInsetsManager,
      mainControllerCallbacks = mainControllerCallbacks
    )
  }

  @Composable
  private fun BoxScope.BuildContent() {
    val myPostsViewModelState by viewModel.myPostsViewModelState.collectAsState()

    val savedRepliesGrouped = when (val savedRepliesAsync = myPostsViewModelState.savedRepliesGroupedAsync) {
      AsyncData.NotInitialized -> return
      AsyncData.Loading -> {
        KurobaComposeProgressIndicator()
        return
      }
      is AsyncData.Error -> {
        KurobaComposeErrorMessage(error = savedRepliesAsync.throwable)
        return
      }
      is AsyncData.Data -> savedRepliesAsync.data
    }

    BuildSavedRepliesList(
      savedRepliesGrouped = savedRepliesGrouped,
      onHeaderClicked = { threadDescriptor ->
        if (viewModel.viewModelSelectionHelper.isInSelectionMode()) {
          viewModel.toggleGroupSelection(threadDescriptor)

          return@BuildSavedRepliesList
        }

        startActivityCallback.loadThreadAndMarkPost(
          postDescriptor = threadDescriptor.toOriginalPostDescriptor(),
          animated = true
        )
      },
      onReplyClicked = { postDescriptor ->
        if (viewModel.viewModelSelectionHelper.isInSelectionMode()) {
          viewModel.toggleSelection(postDescriptor)

          return@BuildSavedRepliesList
        }

        startActivityCallback.loadThreadAndMarkPost(
          postDescriptor = postDescriptor,
          animated = true
        )
      },
      onHeaderLongClicked = { threadDescriptor ->
        if (toolbarState.isInSearchMode()) {
          return@BuildSavedRepliesList
        }

        controllerViewOrNull()?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        viewModel.toggleGroupSelection(threadDescriptor)
      },
      onReplyLongClicked = { postDescriptor ->
        if (toolbarState.isInSearchMode()) {
          return@BuildSavedRepliesList
        }

        controllerViewOrNull()?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        viewModel.toggleSelection(postDescriptor)
      }
    )
  }

  @Composable
  private fun BuildSavedRepliesList(
    savedRepliesGrouped: List<SavedPostsViewModel.GroupedSavedReplies>,
    onHeaderClicked: (ChanDescriptor.ThreadDescriptor) -> Unit,
    onHeaderLongClicked: (ChanDescriptor.ThreadDescriptor) -> Unit,
    onReplyClicked: (PostDescriptor) -> Unit,
    onReplyLongClicked: (PostDescriptor) -> Unit,
  ) {
    val chanTheme = LocalChanTheme.current
    val state = rememberLazyListState(
      initialFirstVisibleItemIndex = viewModel.rememberedFirstVisibleItemIndex,
      initialFirstVisibleItemScrollOffset = viewModel.rememberedFirstVisibleItemScrollOffset
    )

    DisposableEffect(key1 = Unit, effect = {
      onDispose {
        viewModel.updatePrevLazyListState(state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset)
      }
    })

    val padding by bottomPadding
    val bottomPadding = remember(key1 = padding) { PaddingValues(bottom = padding.dp) }

    LazyColumn(
      state = state,
      contentPadding = bottomPadding,
      modifier = Modifier
        .fillMaxSize()
        .simpleVerticalScrollbar(state, chanTheme, bottomPadding)
    ) {
      if (savedRepliesGrouped.isEmpty()) {
        val searchQuery = toolbarState.search.searchQueryState.text
        if (searchQuery.isNullOrEmpty()) {
          item(key = "nothing_found_message") {
            KurobaComposeErrorMessage(
              errorMessage = stringResource(id = R.string.search_nothing_found)
            )
          }
        } else {
          item(key = "nothing_found_by_query_message_$searchQuery") {
            KurobaComposeErrorMessage(
              errorMessage = stringResource(id = R.string.search_nothing_found_with_query, searchQuery)
            )
          }
        }

        return@LazyColumn
      }

      savedRepliesGrouped.forEachIndexed { groupIndex, groupedSavedReplies ->
        item(key = "card_${groupIndex}") {
          Card(modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(2.dp)
          ) {
            Column(modifier = Modifier
              .fillMaxSize()
              .background(chanTheme.backColorSecondaryCompose)
              .padding(2.dp)
            ) {
              GroupedSavedReplyHeader(groupedSavedReplies, chanTheme, onHeaderClicked, onHeaderLongClicked)

              groupedSavedReplies.savedReplyDataList.forEach { groupedSavedReplyData ->
                Divider(
                  modifier = Modifier.padding(horizontal = 4.dp),
                  color = chanTheme.dividerColorCompose,
                  thickness = 1.dp
                )

                GroupedSavedReply(groupedSavedReplyData, chanTheme, onReplyClicked, onReplyLongClicked)
              }
            }
          }
        }
      }
    }
  }

  @OptIn(ExperimentalFoundationApi::class)
  @Composable
  private fun GroupedSavedReplyHeader(
    groupedSavedReplies: SavedPostsViewModel.GroupedSavedReplies,
    chanTheme: ChanTheme,
    onHeaderClicked: (ChanDescriptor.ThreadDescriptor) -> Unit,
    onHeaderLongClicked: (ChanDescriptor.ThreadDescriptor) -> Unit
  ) {
    Box(modifier = Modifier
      .fillMaxWidth()
      .wrapContentHeight()
      .combinedClickable(
        onClick = { onHeaderClicked(groupedSavedReplies.threadDescriptor) },
        onLongClick = { onHeaderLongClicked(groupedSavedReplies.threadDescriptor) }
      )
      .padding(horizontal = 4.dp, vertical = 4.dp)
    ) {
      Column(modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight()
      ) {

        if (groupedSavedReplies.headerThreadSubject.isNotNullNorEmpty()) {
          KurobaComposeText(
            text = groupedSavedReplies.headerThreadSubject,
            fontSize = 14.ktu,
            color = chanTheme.postSubjectColorCompose,
            maxLines = 3,
            modifier = Modifier
              .fillMaxWidth()
              .wrapContentHeight()
          )

          Spacer(modifier = Modifier.height(2.dp))
        }

        KurobaComposeText(
          text = groupedSavedReplies.headerThreadInfo,
          fontSize = 12.ktu,
          color = chanTheme.textColorHintCompose,
          modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
        )
      }
    }
  }

  @OptIn(ExperimentalFoundationApi::class)
  @Composable
  private fun GroupedSavedReply(
    groupedSavedReplyData: SavedPostsViewModel.SavedReplyData,
    chanTheme: ChanTheme,
    onReplyClicked: (PostDescriptor) -> Unit,
    onReplyLongClicked: (PostDescriptor) -> Unit,
  ) {
    val selectionEvent by viewModel.viewModelSelectionHelper.collectSelectionModeAsState()
    val isInSelectionMode = selectionEvent?.isIsSelectionMode() ?: false
    val postDescriptor = groupedSavedReplyData.postDescriptor

    SelectableItem(
      isInSelectionMode = isInSelectionMode,
      observeSelectionStateFunc = { viewModel.viewModelSelectionHelper.observeSelectionState(postDescriptor) },
      onSelectionChanged = { viewModel.viewModelSelectionHelper.toggleSelection(postDescriptor) }
    ) {
      Box(modifier = Modifier
        .fillMaxSize()
        .defaultMinSize(minHeight = 42.dp)
        .combinedClickable(
          onClick = { onReplyClicked(postDescriptor) },
          onLongClick = { onReplyLongClicked(postDescriptor) }
        )
        .padding(horizontal = 4.dp, vertical = 2.dp)
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
        ) {
          KurobaComposeText(
            text = groupedSavedReplyData.postHeader,
            fontSize = 12.ktu,
            maxLines = 1,
            color = chanTheme.textColorHintCompose,
            modifier = Modifier
              .fillMaxWidth()
              .wrapContentHeight()
          )

          Spacer(modifier = Modifier.height(2.dp))

          KurobaComposeText(
            text = groupedSavedReplyData.comment,
            fontSize = 14.ktu,
            maxLines = 5,
            modifier = Modifier
              .fillMaxWidth()
              .wrapContentHeight()
          )
        }
      }
    }
  }

  private fun onMenuItemClicked(
    menuItemType: SavedPostsViewModel.MenuItemType,
    selectedItems: List<PostDescriptor>
  ) {
    if (selectedItems.isEmpty()) {
      return
    }

    when (menuItemType) {
      SavedPostsViewModel.MenuItemType.Delete -> {
        val title = getString(R.string.controller_saved_posts_delete_many_posts, selectedItems.size)
        val descriptionText = getString(R.string.controller_saved_posts_delete_many_posts_description)

        dialogFactory.createSimpleConfirmationDialog(
          context,
          titleText = title,
          descriptionText = descriptionText,
          negativeButtonText = getString(R.string.cancel),
          positiveButtonText = getString(R.string.delete),
          onPositiveButtonClickListener = {
            viewModel.deleteSavedPosts(selectedItems)
          }
        )
      }
    }
  }

  private fun onNewSelectionEvent(selectionEvent: BaseSelectionHelper.SelectionEvent?) {
    when (selectionEvent) {
      BaseSelectionHelper.SelectionEvent.EnteredSelectionMode,
      BaseSelectionHelper.SelectionEvent.ItemSelectionToggled -> {
        mainControllerCallbacks.showBottomPanel(viewModel.getBottomPanelMenus())
        enterSelectionModeOrUpdate()
      }
      BaseSelectionHelper.SelectionEvent.ExitedSelectionMode -> {
        mainControllerCallbacks.hideBottomPanel()

        if (toolbarState.isInSelectionMode()) {
          toolbarState.pop()
        }
      }
      null -> return
    }
  }

  private fun enterSelectionModeOrUpdate() {
    if (!toolbarState.isInSelectionMode()) {
      toolbarState.enterSelectionMode()
    }

    toolbarState.selection.updateTitle(ToolbarText.String(formatSelectionText()))
  }

  private fun formatSelectionText(): String {
    require(viewModel.viewModelSelectionHelper.isInSelectionMode()) { "Not in selection mode" }

    return AppModuleAndroidUtils.getString(
      R.string.controller_local_archive_selection_title,
      viewModel.viewModelSelectionHelper.selectedItemsCount()
    )
  }

  private fun onDeleteAllSavedPostsClicked() {
    dialogFactory.createSimpleConfirmationDialog(
      context = context,
      titleText = getString(R.string.controller_saved_posts_delete_all_dialog_title),
      descriptionText = getString(R.string.controller_saved_posts_delete_all_dialog_description),
      negativeButtonText = getString(R.string.cancel),
      positiveButtonText = getString(R.string.delete),
      onPositiveButtonClickListener = { viewModel.deleteAllSavedPosts() }
    )
  }

  companion object {
    private const val ACTION_DELETE_ALL_SAVED_POSTS = 0
  }

}