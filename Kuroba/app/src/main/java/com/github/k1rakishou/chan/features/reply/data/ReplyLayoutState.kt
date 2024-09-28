package com.github.k1rakishou.chan.features.reply.data

import android.Manifest
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.placeCursorAtEnd
import androidx.compose.runtime.IntState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.base.DebouncingCoroutineExecutor
import com.github.k1rakishou.chan.core.base.RendezvousCoroutineExecutor
import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.ReplyManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.repository.BoardFlagInfoRepository
import com.github.k1rakishou.chan.core.site.PostFormatterButton
import com.github.k1rakishou.chan.core.site.SiteAuthentication
import com.github.k1rakishou.chan.core.site.SiteSetting
import com.github.k1rakishou.chan.core.site.http.ReplyResponse
import com.github.k1rakishou.chan.core.usecase.ClearPostingCookies
import com.github.k1rakishou.chan.core.usecase.LoadBoardFlagsUseCase
import com.github.k1rakishou.chan.features.posting.PostResult
import com.github.k1rakishou.chan.features.posting.PostingServiceDelegate
import com.github.k1rakishou.chan.features.posting.PostingStatus
import com.github.k1rakishou.chan.features.reply.left.ReplyTextFieldHelpers
import com.github.k1rakishou.chan.ui.compose.clearText
import com.github.k1rakishou.chan.ui.compose.forEachTextValue
import com.github.k1rakishou.chan.ui.controller.ThreadControllerType
import com.github.k1rakishou.chan.ui.globalstate.GlobalUiStateHolder
import com.github.k1rakishou.chan.ui.helper.AppResources
import com.github.k1rakishou.chan.ui.helper.RuntimePermissionsHelper
import com.github.k1rakishou.chan.ui.helper.picker.ImagePickHelper
import com.github.k1rakishou.chan.ui.helper.picker.LocalFilePicker
import com.github.k1rakishou.chan.ui.helper.picker.PickedFile
import com.github.k1rakishou.chan.ui.helper.picker.RemoteFilePicker
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.common.AndroidUtils
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.ModularResult.Companion.Try
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.common.isNotNullNorBlank
import com.github.k1rakishou.common.isNotNullNorEmpty
import com.github.k1rakishou.common.removeIfKt
import com.github.k1rakishou.common.resumeValueSafe
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.persist_state.PersistableChanState
import com.github.k1rakishou.persist_state.ReplyMode
import com.github.k1rakishou.prefs.OptionsSetting
import dagger.Lazy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import java.util.*

@Stable
class ReplyLayoutState(
  val chanDescriptor: ChanDescriptor,
  val threadControllerType: ThreadControllerType,
  private val callbacks: Callbacks,
  private val coroutineScope: CoroutineScope,
  private val appResourcesLazy: Lazy<AppResources>,
  private val replyLayoutHelperLazy: Lazy<ReplyLayoutHelper>,
  private val siteManagerLazy: Lazy<SiteManager>,
  private val boardManagerLazy: Lazy<BoardManager>,
  private val replyManagerLazy: Lazy<ReplyManager>,
  private val postFormattingButtonsFactoryLazy: Lazy<PostFormattingButtonsFactory>,
  private val themeEngineLazy: Lazy<ThemeEngine>,
  private val globalUiStateHolderLazy: Lazy<GlobalUiStateHolder>,
  private val postingServiceDelegateLazy: Lazy<PostingServiceDelegate>,
  private val boardFlagInfoRepositoryLazy: Lazy<BoardFlagInfoRepository>,
  private val runtimePermissionsHelperLazy: Lazy<RuntimePermissionsHelper>,
  private val imagePickHelperLazy: Lazy<ImagePickHelper>,
  private val clearPostingCookiesLazy: Lazy<ClearPostingCookies>
) {
  private val appResources: AppResources
    get() = appResourcesLazy.get()
  private val replyLayoutHelper: ReplyLayoutHelper
    get() = replyLayoutHelperLazy.get()
  private val siteManager: SiteManager
    get() = siteManagerLazy.get()
  private val boardManager: BoardManager
    get() = boardManagerLazy.get()
  private val replyManager: ReplyManager
    get() = replyManagerLazy.get()
  private val postFormattingButtonsFactory: PostFormattingButtonsFactory
    get() = postFormattingButtonsFactoryLazy.get()
  private val themeEngine: ThemeEngine
    get() = themeEngineLazy.get()
  private val globalUiStateHolder: GlobalUiStateHolder
    get() = globalUiStateHolderLazy.get()
  private val postingServiceDelegate: PostingServiceDelegate
    get() = postingServiceDelegateLazy.get()
  private val boardFlagInfoRepository: BoardFlagInfoRepository
    get() = boardFlagInfoRepositoryLazy.get()
  private val runtimePermissionsHelper: RuntimePermissionsHelper
    get() = runtimePermissionsHelperLazy.get()
  private val imagePickHelper: ImagePickHelper
    get() = imagePickHelperLazy.get()
  private val clearPostingCookies: ClearPostingCookies
    get() = clearPostingCookiesLazy.get()

  val replyTextState = TextFieldState()
  val subjectTextState = TextFieldState()
  val nameTextState = TextFieldState()
  val optionsTextState = TextFieldState()

  private val _replyFieldHintText = mutableStateOf<AnnotatedString>(AnnotatedString(""))
  val replyFieldHintText: State<AnnotatedString>
    get() = _replyFieldHintText

  private val _syntheticAttachables = mutableStateListOf<SyntheticReplyAttachable>()
  val syntheticAttachables: List<SyntheticReplyAttachable>
    get() = _syntheticAttachables

  private val _attachables = mutableStateOf<ReplyAttachables>(ReplyAttachables())
  val attachables: State<ReplyAttachables>
    get() = _attachables

  private val _postFormatterButtons = mutableStateOf<List<PostFormatterButton>>(emptyList())
  val postFormatterButtons: State<List<PostFormatterButton>>
    get() = _postFormatterButtons

  private val _maxCommentLength = mutableIntStateOf(0)

  private val _hasFlagsToShow = mutableStateOf<Boolean>(false)
  val hasFlagsToShow: State<Boolean>
    get() = _hasFlagsToShow

  private val _flag = mutableStateOf<LoadBoardFlagsUseCase.FlagInfo?>(null)
  val flag: State<LoadBoardFlagsUseCase.FlagInfo?>
    get() = _flag

  private val _replyLayoutAnimationState = mutableStateOf<ReplyLayoutAnimationState>(ReplyLayoutAnimationState.Collapsed)
  val replyLayoutAnimationState: State<ReplyLayoutAnimationState>
    get() = _replyLayoutAnimationState

  private val _replyLayoutVisibility = mutableStateOf<ReplyLayoutVisibility>(ReplyLayoutVisibility.Collapsed)
  val replyLayoutVisibility: State<ReplyLayoutVisibility>
    get() = _replyLayoutVisibility

  private val _sendReplyState = mutableStateOf<SendReplyState>(SendReplyState.Finished)
  val sendReplyState: State<SendReplyState>
    get() = _sendReplyState

  private val _replySendProgressInPercentsState = mutableIntStateOf(-1)
  val replySendProgressInPercentsState: IntState
    get() = _replySendProgressInPercentsState

  private val _displayCaptchaPresolveButton = mutableStateOf(true)
  val displayCaptchaPresolveButton: State<Boolean>
    get() = _displayCaptchaPresolveButton

  val isCatalogMode: Boolean
    get() = threadControllerType == ThreadControllerType.Catalog

  private val filePickerExecutor = RendezvousCoroutineExecutor(coroutineScope)
  private val highlightQuotesExecutor = DebouncingCoroutineExecutor(coroutineScope)
  private val flagLoaderExecutor = RendezvousCoroutineExecutor(coroutineScope)

  private var persistInReplyManagerJob: Job? = null
  private var colorizeReplyTextJob: Job? = null
  private val compositeJob = mutableListOf<Job>()

  suspend fun bindChanDescriptor(chanDescriptor: ChanDescriptor) {
    replyManager.awaitUntilFilesAreLoaded()
    Logger.debug(TAG) { "bindChanDescriptor(${chanDescriptor})" }

    _replyLayoutAnimationState.value = ReplyLayoutAnimationState.Collapsed
    _replyLayoutVisibility.value = ReplyLayoutVisibility.Collapsed
    _sendReplyState.value = SendReplyState.Finished
    _replySendProgressInPercentsState.intValue = -1

    loadDraftIntoViews(chanDescriptor)
    updateCaptchaButtonVisibility()

    compositeJob += coroutineScope.launch {
      replyManager.listenForReplyFilesUpdates()
        .filter { fileUuids -> fileUuids.isNotEmpty() }
        .onEach { forceUpdateFiles -> updateAttachables(forceUpdateFiles) }
        .collect()
    }

    compositeJob += coroutineScope.launch {
      imagePickHelper.pickedFilesUpdateFlow
        .filter { fileUuids -> fileUuids.isNotEmpty() }
        .onEach { fileUuids -> updateAttachables(fileUuids) }
        .collect()
    }

    compositeJob += coroutineScope.launch {
      imagePickHelper.syntheticFilesUpdatesFlow
        .onEach { syntheticReplyAttachable ->
          when (syntheticReplyAttachable.state) {
            SyntheticReplyAttachableState.Initializing,
            SyntheticReplyAttachableState.Downloading,
            SyntheticReplyAttachableState.Decoding -> {
              val index = _syntheticAttachables
                .indexOfFirst { attachable -> attachable.id == syntheticReplyAttachable.id }

              if (index >= 0) {
                _syntheticAttachables[index] = syntheticReplyAttachable
              } else {
                _syntheticAttachables.add(0, syntheticReplyAttachable)
              }
            }
            SyntheticReplyAttachableState.Done -> {
              _syntheticAttachables.removeIfKt { attachable -> attachable.id == syntheticReplyAttachable.id }
            }
          }
        }
        .collect()
    }

    compositeJob += coroutineScope.launch {
      replyTextState.forEachTextValue {
        afterReplyTextChanged()
      }
    }

    compositeJob += coroutineScope.launch {
      subjectTextState.forEachTextValue {
        persistInReplyManager()
      }
    }

    compositeJob += coroutineScope.launch {
      nameTextState.forEachTextValue {
        persistInReplyManager()
      }
    }

    compositeJob += coroutineScope.launch {
      optionsTextState.forEachTextValue {
        persistInReplyManager()
      }
    }

    compositeJob += coroutineScope.launch {
      val replyModeSetting = siteManager.bySiteDescriptor(chanDescriptor.siteDescriptor())
        ?.getSettingBySettingId<OptionsSetting<ReplyMode>>(SiteSetting.SiteSettingId.LastUsedReplyMode)

      if (replyModeSetting == null) {
        return@launch
      }

      replyModeSetting.listenForChanges()
        .asFlow()
        .onEach { updateCaptchaButtonVisibility() }
        .collect()
    }
  }

  fun unbindChanDescriptor() {
    persistInReplyManagerJob?.cancel()
    persistInReplyManagerJob = null
    colorizeReplyTextJob?.cancel()
    colorizeReplyTextJob = null

    compositeJob.forEach { job -> job.cancel() }
    compositeJob.clear()
  }

  suspend fun onPostingStatusEvent(status: PostingStatus) {
    withContext(Dispatchers.Main) {
      if (status.chanDescriptor != chanDescriptor) {
        // The user may open another thread while the reply is being uploaded so we need to check
        // whether this even actually belongs to this catalog/thread.
        Logger.verbose(TAG) { "onPostingStatusEvent(${status.chanDescriptor}) wrong chanDescriptor: ${chanDescriptor}" }
        return@withContext
      }

      when (status) {
        is PostingStatus.Attached -> {
          Logger.d(TAG, "onPostingStatusEvent(${status.chanDescriptor}) -> ${status.javaClass.simpleName}")
        }
        is PostingStatus.Enqueued,
        is PostingStatus.WaitingForSiteRateLimitToPass,
        is PostingStatus.WaitingForAdditionalService,
        is PostingStatus.BeforePosting -> {
          Logger.d(TAG, "onPostingStatusEvent(${status.chanDescriptor}) -> ${status.javaClass.simpleName}")
        }
        is PostingStatus.UploadingProgress -> {
          _replySendProgressInPercentsState.intValue = status.progressInPercents()
          Logger.verbose(TAG) { "onPostingStatusEvent(${status.chanDescriptor}) -> ${status}" }
        }
        is PostingStatus.Uploaded -> {
          _replySendProgressInPercentsState.intValue = -1
          Logger.d(TAG, "onPostingStatusEvent(${status.chanDescriptor}) -> ${status.javaClass.simpleName}")
        }
        is PostingStatus.AfterPosting -> {
          Logger.d(TAG, "onPostingStatusEvent(${status.chanDescriptor}) -> " +
            "${status.javaClass.simpleName}, status.postResult: ${status.postResult}")

          _replySendProgressInPercentsState.intValue = -1
          onSendReplyEnd()

          when (val postResult = status.postResult) {
            PostResult.Canceled -> {
              onPostSendCanceled(chanDescriptor = status.chanDescriptor)
            }
            is PostResult.Error -> {
              onPostSendError(
                chanDescriptor = status.chanDescriptor,
                exception = postResult.throwable
              )
            }
            is PostResult.Banned -> {
              onPostSendErrorBanned(postResult.banMessage, postResult.banInfo)
            }
            is PostResult.Success -> {
              onPostSendComplete(
                chanDescriptor = status.chanDescriptor,
                replyResponse = postResult.replyResponse,
                replyMode = postResult.replyMode,
                retrying = postResult.retrying
              )
            }
          }

          Logger.d(TAG, "onPostingStatusEvent($chanDescriptor) consumeTerminalEvent(${status.chanDescriptor})")
          postingServiceDelegate.consumeTerminalEvent(status.chanDescriptor)

          loadDraftIntoViews(status.chanDescriptor)
        }
      }
    }
  }

  fun onHeightChanged(newHeight: Int) {
    onHeightChangedInternal(newHeight)
  }

  fun isReplyLayoutCollapsed(): Boolean {
    return _replyLayoutVisibility.value.isCollapsed()
  }

  fun isReplyLayoutOpenedOrExpanded(): Boolean {
    return _replyLayoutVisibility.value.isOpened() || _replyLayoutVisibility.value.isExpanded()
  }

  fun isReplyLayoutExpanded(): Boolean {
    return _replyLayoutVisibility.value.isExpanded()
  }

  fun onAnimationStarted(animationState: ReplyLayoutAnimationState) {
    when (animationState) {
      ReplyLayoutAnimationState.Collapsing -> {
        // no-op
      }
      ReplyLayoutAnimationState.Collapsed -> {
        _replyLayoutAnimationState.value = ReplyLayoutAnimationState.Collapsing
      }
      ReplyLayoutAnimationState.Opening -> {
        // no-op
      }
      ReplyLayoutAnimationState.Opened -> {
        _replyLayoutAnimationState.value = ReplyLayoutAnimationState.Opening
      }
      ReplyLayoutAnimationState.Expanding -> {
        // no-op
      }
      ReplyLayoutAnimationState.Expanded -> {
        _replyLayoutAnimationState.value = ReplyLayoutAnimationState.Expanding
      }
    }
  }

  fun onAnimationFinished(animationState: ReplyLayoutAnimationState) {
    when (animationState) {
      ReplyLayoutAnimationState.Collapsing -> {
        // no-op
      }
      ReplyLayoutAnimationState.Collapsed -> {
        _replyLayoutAnimationState.value = ReplyLayoutAnimationState.Collapsed
        _replyLayoutVisibility.value = ReplyLayoutVisibility.Collapsed
        onReplyLayoutVisibilityChangedInternal(ReplyLayoutVisibility.Collapsed)
      }
      ReplyLayoutAnimationState.Opening -> {
        // no-op
      }
      ReplyLayoutAnimationState.Opened -> {
        _replyLayoutAnimationState.value = ReplyLayoutAnimationState.Opened
        _replyLayoutVisibility.value = ReplyLayoutVisibility.Opened
        onReplyLayoutVisibilityChangedInternal(ReplyLayoutVisibility.Opened)
      }
      ReplyLayoutAnimationState.Expanding -> {
        // no-op
      }
      ReplyLayoutAnimationState.Expanded -> {
        _replyLayoutAnimationState.value = ReplyLayoutAnimationState.Expanded
        _replyLayoutVisibility.value = ReplyLayoutVisibility.Expanded
        onReplyLayoutVisibilityChangedInternal(ReplyLayoutVisibility.Expanded)
      }
    }
  }

  fun collapseReplyLayout() {
    if (_replyLayoutAnimationState.value != ReplyLayoutAnimationState.Collapsing) {
      _replyLayoutAnimationState.value = ReplyLayoutAnimationState.Collapsing
    }
  }

  fun openReplyLayout() {
    if (_replyLayoutAnimationState.value != ReplyLayoutAnimationState.Opening) {
      _replyLayoutAnimationState.value = ReplyLayoutAnimationState.Opening
    }
  }

  fun expandReplyLayout() {
    if (_replyLayoutAnimationState.value != ReplyLayoutAnimationState.Expanding) {
      _replyLayoutAnimationState.value = ReplyLayoutAnimationState.Expanded
    }
  }

  fun insertTags(postFormatterButton: PostFormatterButton) {
    try {
      val replyText = replyTextState.text.toString()

      val capacity = replyText.length + postFormatterButton.openTag.length + postFormatterButton.closeTag.length
      var selectionIndex = -1

      val newText = buildString(capacity = capacity) {
        val selectionStart = replyTextState.selection.start
        val selectionEnd = replyTextState.selection.end

        append(replyText.subSequence(0, selectionStart))
        append(postFormatterButton.openTag)

        if (selectionEnd > selectionStart) {
          append(replyText.subSequence(selectionStart, selectionEnd))
        }

        selectionIndex = this.length

        append(postFormatterButton.closeTag)
        append(replyText.subSequence(selectionEnd, replyText.length))
      }

      replyTextState.edit {
        clearText()
        append(newText)
        selection = TextRange(selectionIndex)
      }

      afterReplyTextChanged()
    } catch (error: Throwable) {
      Logger.error(TAG, error) { "insertTags() error postFormatterButton: ${postFormatterButton}" }

      showErrorToast(error)
    }
  }

  fun quote(post: ChanPost, withText: Boolean) {
    val comment = if (withText) {
      post.postComment.comment().toString()
    } else {
      null
    }

    handleQuote(post.postDescriptor.postNo, comment)
  }

  fun quote(postDescriptor: PostDescriptor, text: CharSequence) {
    handleQuote(postDescriptor.postNo, text.toString())
  }

  private fun handleQuote(postNo: Long, textQuote: String?) {
    try {
      replyLayoutHelper.handleQuote(
        replyTextState = replyTextState,
        postNo = postNo,
        textQuote = textQuote
      )

      afterReplyTextChanged()
    } catch (error: Throwable) {
      Logger.error(TAG, error) {
        "replyLayoutHelper.handleQuote() error. " +
          "replyTextState: '${replyTextState}', " +
          "selection: ${replyTextState.selection}, " +
          "postNo: ${postNo}, " +
          "textQuote: '$textQuote'"
      }

      showErrorToast(error)
    }
  }

  fun removeAttachedMedia(attachedMedia: ReplyFileAttachable) {
    removeAttachedMedia(attachedMedia.fileUuid)
  }

  fun removeAttachedMedia(fileUuid: UUID) {
    replyManager.deleteFile(
      fileUuid = fileUuid,
      notifyListeners = true
    )
      .onError { error ->
        Logger.error(TAG) { "removeAttachedMedia(${fileUuid}) error: ${error.errorMessageOrClassName()}" }
        showErrorToast(error)
      }
      .onSuccess {
        showToast(appResources.string(R.string.reply_layout_attached_media_deleted))
      }
      .ignore()
  }

  fun removeThisFileName(fileUuid: UUID) {
    coroutineScope.launch(Dispatchers.IO) {
      val replyFileMeta = replyManager.getReplyFileByFileUuid(fileUuid)
        .mapValue { replyFile -> replyFile?.getReplyFileMeta()?.unwrap() }
        .onError { throwable -> showErrorToast(throwable) }
        .valueOrNull()
        ?: return@launch

      val newFileName = replyManager.getNewImageName(replyFileMeta.fileName)

      replyManager.updateFileName(
        fileUuid = replyFileMeta.fileUuid,
        newFileName = newFileName,
        notifyListeners = true
      )
        .onError { error ->
          Logger.e(TAG, "removeSelectedFilesName(${replyFileMeta.fileUuid}) Failed to update file name", error)
          showErrorToast(error)
        }
        .onSuccess {
          showToast(appResources.string(R.string.reply_layout_filename_removed))
        }
        .ignore()
    }
  }

  fun removeThisFileMetadata(fileUuid: UUID) {
    coroutineScope.launch(Dispatchers.IO) {
      val replyFile = replyManager.getReplyFileByFileUuid(fileUuid)
        .onError { throwable -> showErrorToast(throwable) }
        .valueOrNull()
        ?: return@launch

      doWithProgressDialog {
        val (allSuccess, updatedFileUuids) = replyLayoutHelper.removeFilesMetadata(listOf(replyFile))
        updateAttachables(updatedFileUuids)

        withContext(Dispatchers.Main) {
          if (allSuccess) {
            showToast(appResources.string(R.string.reply_layout_metadata_remove_success))
          } else {
            showErrorToast(appResources.string(R.string.reply_layout_metadata_remove_success_partial))
          }
        }
      }
    }
  }

  fun changeThisFileChecksum(fileUuid: UUID) {
    coroutineScope.launch(Dispatchers.IO) {
      val replyFile = replyManager.getReplyFileByFileUuid(fileUuid)
        .onError { throwable -> showErrorToast(throwable) }
        .valueOrNull()
        ?: return@launch

      doWithProgressDialog {
        val (allSuccess, updatedFileUuids) = replyLayoutHelper.changeFilesChecksum(listOf(replyFile))
        updateAttachables(updatedFileUuids)

        withContext(Dispatchers.Main) {
          if (allSuccess) {
            showToast(appResources.string(R.string.reply_layout_checksum_change_success))
          } else {
            showErrorToast(appResources.string(R.string.reply_layout_checksum_change_success_partial))
          }
        }
      }
    }
  }

  fun deleteSelectedFiles() {
    replyManager.deleteSelectedFiles(
      notifyListeners = true
    )
      .onError { error ->
        Logger.error(TAG) { "deleteSelectedFiles() error: ${error.errorMessageOrClassName()}" }
        showErrorToast(error)
      }
      .onSuccess {
        showToast(appResources.string(R.string.reply_layout_selected_files_deleted))
      }
      .ignore()
  }

  fun removeSelectedFilesName() {
    coroutineScope.launch(Dispatchers.IO) {
      replyManager.iterateSelectedFilesOrdered { _, _, replyFileMeta ->
        val newFileName = replyManager.getNewImageName(replyFileMeta.fileName)

        replyManager.updateFileName(
          fileUuid = replyFileMeta.fileUuid,
          newFileName = newFileName,
          notifyListeners = true
        )
          .onError { error ->
            Logger.e(TAG, "removeSelectedFilesName(${replyFileMeta.fileUuid}) Failed to update file name", error)
            showErrorToast(error)
          }
          .onSuccess {
            showToast(appResources.string(R.string.reply_layout_filename_removed))
          }
          .ignore()
      }
    }
  }

  fun removeSelectedFilesMetadata() {
    coroutineScope.launch(Dispatchers.IO) {
      val selectedFiles = replyManager.getSelectedFilesOrdered()
      if (selectedFiles.isEmpty()) {
        return@launch
      }

      doWithProgressDialog {
        val (allSuccess, updatedFileUuids) = replyLayoutHelper.removeFilesMetadata(selectedFiles)
        updateAttachables(updatedFileUuids)

        withContext(Dispatchers.Main) {
          if (allSuccess) {
            showToast(appResources.string(R.string.reply_layout_metadata_remove_success))
          } else {
            showErrorToast(appResources.string(R.string.reply_layout_metadata_remove_success_partial))
          }
        }
      }
    }
  }

  fun changeSelectedFilesChecksum() {
    coroutineScope.launch(Dispatchers.IO) {
      val selectedFiles = replyManager.getSelectedFilesOrdered()
      if (selectedFiles.isEmpty()) {
        return@launch
      }

      doWithProgressDialog {
        val (allSuccess, updatedFileUuids) = replyLayoutHelper.changeFilesChecksum(selectedFiles)
        updateAttachables(updatedFileUuids)

        withContext(Dispatchers.Main) {
          if (allSuccess) {
            showToast(appResources.string(R.string.reply_layout_checksum_change_success))
          } else {
            showErrorToast(appResources.string(R.string.reply_layout_checksum_change_success_partial))
          }
        }
      }
    }
  }

  fun selectUnselectAll(selectAll: Boolean) {
    coroutineScope.launch(Dispatchers.IO) {
      val toUpdate = mutableListOf<UUID>()

      replyManager.iterateFilesOrdered { _, _, replyFileMeta ->
        if (replyFileMeta.selected != selectAll) {
          toUpdate += replyFileMeta.fileUuid
        }
      }

      if (toUpdate.isEmpty()) {
        return@launch
      }

      toUpdate.forEach { fileUuid ->
        replyManager.updateFileSelection(
          fileUuid = fileUuid,
          selected = selectAll,
          notifyListeners = false
        )
      }

      updateAttachables(toUpdate)
    }
  }

  fun markUnmarkAsSpoiler(fileUuid: UUID, spoiler: Boolean) {
    coroutineScope.launch(Dispatchers.IO) {
      replyManager.updateFileSpoilerFlag(
        fileUuid = fileUuid,
        spoiler = spoiler,
        notifyListeners = true
      )
        .onError { error ->
          Logger.e(TAG, "markUnmarkAsSpoiler($fileUuid, $spoiler) error", error)
          showErrorToast(error)
        }
        .onSuccess {
          showToast(appResources.string(R.string.reply_layout_spoiler_flag_updated))
        }
        .ignore()
    }
  }

  fun isReplyFileMarkedAsSpoiler(fileUuid: UUID): Boolean {
    return replyManager.isMarkedAsSpoiler(fileUuid)
      .onError { error -> Logger.e(TAG, "isReplyFileMarkedAsSpoiler($fileUuid) error", error) }
      .valueOrNull() ?: false
  }

  fun onAttachableSelectionChanged(attachedMedia: ReplyFileAttachable, selected: Boolean) {
    replyManager.updateFileSelection(
      fileUuid = attachedMedia.fileUuid,
      selected = selected,
      notifyListeners = true
    )
      .onError { error ->
        Logger.error(TAG) {
          "onAttachableSelectionChanged(${attachedMedia.fileUuid}, ${selected}) error: ${error.errorMessageOrClassName()}"
        }

        showErrorToast(error)
      }
      .ignore()
  }

  fun pickLocalMedia() {
    filePickerExecutor.post {
      if (!requestPermissionIfNeededSuspend()) {
        showToast(appResources.string(R.string.reply_layout_pick_file_permission_required))
        return@post
      }

      try {
        val input = LocalFilePicker.LocalFilePickerInput(
          notifyListeners = true,
          replyChanDescriptor = chanDescriptor
        )

        val pickedFileResult = withContext(Dispatchers.IO) { imagePickHelper.pickLocalFile(input) }
          .unwrap()

        val replyFiles = when (pickedFileResult) {
          is PickedFile.Failure -> throw pickedFileResult.reason
          is PickedFile.Result -> pickedFileResult.replyFiles
        }

        replyFiles.forEach { replyFile ->
          val replyFileMeta = replyFile.getReplyFileMeta().safeUnwrap { error ->
            Logger.e(TAG, "pickLocalMedia() imagePickHelper.pickLocalFile($chanDescriptor) getReplyFileMeta() error", error)
            showErrorToast(error)
            return@forEach
          }

          val maxAllowedFilesPerPost = replyLayoutHelper.getMaxAllowedFilesPerPost(chanDescriptor)
          if (maxAllowedFilesPerPost != null && canAutoSelectFile(maxAllowedFilesPerPost).unwrap()) {
            replyManager.updateFileSelection(
              fileUuid = replyFileMeta.fileUuid,
              selected = true,
              notifyListeners = true
            )
          }
        }

        Logger.d(TAG, "pickLocalMedia() success")
        showToast(appResources.string(R.string.reply_layout_local_file_pick_success))
      } catch (error: Throwable) {
        Logger.error(TAG) { "pickLocalMedia() error: ${error.errorMessageOrClassName()}" }

        showToast(
          appResources.string(R.string.reply_layout_local_file_pick_error, error.errorMessageOrClassName())
        )
      }
    }
  }

  fun pickRemoteMedia(selectedImageUrl: HttpUrl) {
    filePickerExecutor.post {
      try {
        val selectedImageUrlString = selectedImageUrl.toString()

        val input = RemoteFilePicker.RemoteFilePickerInput(
          notifyListeners = true,
          replyChanDescriptor = chanDescriptor,
          imageUrls = listOf(selectedImageUrlString)
        )

        val pickedFileResult = withContext(Dispatchers.IO) { imagePickHelper.pickRemoteFile(input) }
          .unwrap()

        val replyFiles = when (pickedFileResult) {
          is PickedFile.Failure -> throw pickedFileResult.reason
          is PickedFile.Result -> pickedFileResult.replyFiles
        }

        replyFiles.forEach { replyFile ->
          val replyFileMeta = replyFile.getReplyFileMeta().safeUnwrap { error ->
            Logger.e(TAG, "pickLocalMedia() imagePickHelper.pickRemoteMedia($chanDescriptor) getReplyFileMeta() error", error)
            showErrorToast(error)
            return@forEach
          }

          val maxAllowedFilesPerPost = replyLayoutHelper.getMaxAllowedFilesPerPost(chanDescriptor)
          if (maxAllowedFilesPerPost != null && canAutoSelectFile(maxAllowedFilesPerPost).unwrap()) {
            replyManager.updateFileSelection(
              fileUuid = replyFileMeta.fileUuid,
              selected = true,
              notifyListeners = true
            )
          }
        }

        Logger.d(TAG, "pickRemoteMedia() success")
        showToast(appResources.string(R.string.reply_layout_remote_file_pick_success))
      } catch (error: Throwable) {
        Logger.error(TAG) { "pickRemoteMedia() error: ${error.errorMessageOrClassName()}" }
        showToast(
          appResources.string(R.string.reply_layout_remote_file_pick_error, error.errorMessageOrClassName())
        )
      }
    }
  }

  fun onSendReplyStart() {
    Logger.debug(TAG) { "onSendReplyStart(${chanDescriptor})" }
    _sendReplyState.value = SendReplyState.Started
  }

  fun onReplyEnqueued() {
    Logger.debug(TAG) { "onReplyEnqueued(${chanDescriptor})" }

    if (isReplyLayoutExpanded()) {
      openReplyLayout()
    }

    hideDialog()
  }

  fun onSendReplyEnd() {
    Logger.debug(TAG) { "onSendReplyEnd(${chanDescriptor})" }
    _sendReplyState.value = SendReplyState.Finished
  }

  fun onReplyLayoutPositionChanged(boundsInRoot: Rect) {
    globalUiStateHolder.updateReplyLayoutState {
      updateReplyLayoutForController(threadControllerType) { individualReplyLayoutWritable ->
        individualReplyLayoutWritable.onReplyLayoutPositionChanged(boundsInRoot)
      }
    }
  }

  private suspend fun doWithProgressDialog(
    title: String? = null,
    block: suspend () -> Unit
  ) {
    try {
      withContext(Dispatchers.Main) {
        val actualTitle = title ?: appResources.string(R.string.doing_heavy_lifting_please_wait)
        callbacks.showProgressDialog(actualTitle)
      }
      block()
    } finally {
      withContext(Dispatchers.Main) { callbacks.hideProgressDialog() }
    }
  }

  private suspend fun loadDraftIntoViews(chanDescriptor: ChanDescriptor) {
    if (chanDescriptor is ChanDescriptor.CompositeCatalogDescriptor) {
      _replyLayoutVisibility.value = ReplyLayoutVisibility.Collapsed
      return
    }

    replyManager.readReply(chanDescriptor) { reply ->
      replyTextState.edit {
        clearText()
        append(reply.comment)
        placeCursorAtEnd()
      }

      subjectTextState.edit {
        clearText()
        append(reply.subject)
        placeCursorAtEnd()
      }

      nameTextState.edit {
        clearText()
        append(reply.postName)
        placeCursorAtEnd()
      }

      optionsTextState.edit {
        clearText()
        append(reply.options)
        placeCursorAtEnd()
      }

      boardManager.byBoardDescriptor(chanDescriptor.boardDescriptor())
        ?.let { chanBoard -> _maxCommentLength.intValue = chanBoard.maxCommentChars }
    }

    val postFormattingButtons = postFormattingButtonsFactory.createPostFormattingButtons(chanDescriptor.boardDescriptor())
    _postFormatterButtons.value = postFormattingButtons

    flagLoaderExecutor.post {
      _hasFlagsToShow.value = boardFlagInfoRepository.getFlagInfoList(chanDescriptor.boardDescriptor()).isNotEmpty()
      _flag.value = boardFlagInfoRepository.getLastUsedFlagInfo(chanDescriptor.boardDescriptor())
    }

    updateAttachables(emptyList())
  }

  suspend fun loadViewsIntoDraft(): Boolean {
    withContext(Dispatchers.IO) {
      val lastUsedFlagKey = boardFlagInfoRepository.getLastUsedFlagKey(chanDescriptor.boardDescriptor())

      replyManager.readReply(chanDescriptor) { reply ->
        reply.comment = replyTextState.text.toString()
        reply.postName = nameTextState.text.toString()
        reply.subject = subjectTextState.text.toString()
        reply.options = optionsTextState.text.toString()

        if (lastUsedFlagKey.isNotNullNorEmpty()) {
          reply.flag = lastUsedFlagKey
        }

        replyManager.persistDraft(chanDescriptor, reply)
      }
    }

    return true
  }

  suspend fun attachableFileStatus(replyFileAttachable: ReplyFileAttachable): AnnotatedString {
    return replyLayoutHelper.attachableFileStatus(
      chanDescriptor = chanDescriptor,
      chanTheme = themeEngine.chanTheme,
      clickedFile = replyFileAttachable
    )
  }

  fun onImageOptionsApplied(fileUuid: UUID) {
    replyManager.notifyReplyFilesChanged(fileUuid)
  }

  suspend fun onFlagSelected(selectedFlag: LoadBoardFlagsUseCase.FlagInfo) {
    _flag.value = selectedFlag
    persistInReplyManager()
  }

  fun hasSelectedFiles(): Boolean {
    return attachables.value.attachables
      .any { replyFileAttachable -> replyFileAttachable.selected }
  }

  fun selectedFilesCount(): Int {
    return attachables.value.attachables.size
  }

  fun allFilesSelected(): Boolean {
    return attachables.value.attachables
      .all { replyFileAttachable -> replyFileAttachable.selected }
  }

  fun updateCaptchaButtonVisibility() {
    val descriptor = chanDescriptor

    val site = siteManager.bySiteDescriptor(descriptor.siteDescriptor())
      ?: return

    val replyMode = site
      .getSettingBySettingId<OptionsSetting<ReplyMode>>(SiteSetting.SiteSettingId.LastUsedReplyMode)?.get()
      ?: return

    val siteDoesNotRequireAuthentication = site.actions().postAuthenticate().type == SiteAuthentication.Type.NONE
    if (siteDoesNotRequireAuthentication) {
      _displayCaptchaPresolveButton.value = false
      return
    }

    _displayCaptchaPresolveButton.value = when (replyMode) {
      ReplyMode.Unknown,
      ReplyMode.ReplyModeSolveCaptchaManually,
      ReplyMode.ReplyModeSendWithoutCaptcha,
      ReplyMode.ReplyModeSolveCaptchaAuto -> true
      ReplyMode.ReplyModeUsePasscode -> false
    }
  }

  private fun afterReplyTextChanged() {
    if (isReplyLayoutCollapsed()) {
      return
    }

    colorizeReplyTextJob?.cancel()
    colorizeReplyTextJob = coroutineScope.launch {
      updateReplyFieldHintText()
      updateHighlightedPosts()
      persistInReplyManager()
    }
  }

  private fun canAutoSelectFile(maxAllowedFilesPerPost: Int): ModularResult<Boolean> {
    return Try { replyManager.selectedFilesCount().unwrap() < maxAllowedFilesPerPost }
  }

  private suspend fun requestPermissionIfNeededSuspend(): Boolean {
    if (AndroidUtils.isAndroid13()) {
      // Can't request READ_EXTERNAL_STORAGE on API 33+
      return true
    }

    val permission = Manifest.permission.READ_EXTERNAL_STORAGE

    if (runtimePermissionsHelper.hasPermission(permission)) {
      return true
    }

    return suspendCancellableCoroutine<Boolean> { cancellableContinuation ->
      runtimePermissionsHelper.requestPermission(permission) { granted ->
        cancellableContinuation.resumeValueSafe(granted)
      }
    }
  }

  private suspend fun updateAttachables(forceUpdateFiles: Collection<UUID>) {
    val replyAttachables = replyLayoutHelper.enumerateReplyFiles(chanDescriptor, forceUpdateFiles)
      .onError { error ->
        Logger.error(TAG) {
          "updateAttachables() Failed to enumerate reply files for ${chanDescriptor}, error: ${error.errorMessageOrClassName()}"
        }

        val message = appResources.string(
          R.string.reply_layout_enumerate_attachables_error,
          error.errorMessageOrClassName()
        )

        showToast(message)
      }
      .valueOrNull()

    if (replyAttachables != null) {
      _attachables.value = replyAttachables
    }

    updateReplyFieldHintText()
  }

  private fun persistInReplyManager() {
    persistInReplyManagerJob?.cancel()
    persistInReplyManagerJob = coroutineScope.launch {
      delay(500)
      loadViewsIntoDraft()
    }
  }

  private fun onHeightChangedInternal(
    newHeight: Int
  ) {
    globalUiStateHolder.updateReplyLayoutState {
      updateReplyLayoutForController(threadControllerType) { individualReplyLayoutGlobalState ->
        individualReplyLayoutGlobalState.updateCurrentReplyLayoutHeight(newHeight)
      }
    }
  }

  private fun onReplyLayoutVisibilityChangedInternal(replyLayoutVisibility: ReplyLayoutVisibility) {
    globalUiStateHolder.updateReplyLayoutState {
      updateReplyLayoutForController(threadControllerType) { individualReplyLayoutGlobalState ->
        individualReplyLayoutGlobalState.updateReplyLayoutVisibility(replyLayoutVisibility)
      }
    }

    if (replyLayoutVisibility.isExpanded()) {
      if (!PersistableChanState.newReplyLayoutTutorialFinished.get()) {
        PersistableChanState.newReplyLayoutTutorialFinished.set(true)
      }
    }

    if (replyLayoutVisibility.isOpened() || replyLayoutVisibility.isExpanded()) {
      updateHighlightedPosts()
    } else if (replyLayoutVisibility.isCollapsed()) {
      callbacks.highlightQuotes(emptySet())
    }
  }

  private fun updateReplyFieldHintText() {
    _replyFieldHintText.value = formatLabelText(
      replyAttachables = _attachables.value,
      replyText = replyTextState.text.toString(),
      maxCommentLength = _maxCommentLength.intValue
    )
  }

  private fun updateHighlightedPosts() {
    highlightQuotesExecutor.post(300) {
      val replyTextCopy = replyTextState.text.toString()

      val foundQuotes = withContext(Dispatchers.Default) {
        ReplyTextFieldHelpers.findAllQuotesInText(chanDescriptor, replyTextCopy)
      }

      callbacks.highlightQuotes(foundQuotes)
    }
  }

  @Suppress("ConvertTwoComparisonsToRangeCheck")
  private fun formatLabelText(
    replyAttachables: ReplyAttachables,
    replyText: CharSequence,
    maxCommentLength: Int
  ): AnnotatedString {
    return buildAnnotatedString {
      val commentLength = replyText.length

      if (maxCommentLength > 0 && commentLength > maxCommentLength) {
        withStyle(SpanStyle(color = themeEngine.chanTheme.errorColorCompose)) {
          append(commentLength.toString())
        }
      } else {
        append(commentLength.toString())
      }

      if (maxCommentLength > 0) {
        append("/")
        append(maxCommentLength.toString())
      }

      if (replyAttachables.attachables.isNotEmpty()) {
        append("  ")

        val selectedAttachablesCount = replyAttachables.attachables.count { replyFileAttachable -> replyFileAttachable.selected }
        val maxAllowedAttachablesPerPost = replyAttachables.maxAllowedAttachablesPerPost
        val totalAttachablesCount = replyAttachables.attachables.size

        if (maxAllowedAttachablesPerPost > 0 && selectedAttachablesCount > maxAllowedAttachablesPerPost) {
          withStyle(SpanStyle(color = themeEngine.chanTheme.errorColorCompose)) {
            append(selectedAttachablesCount.toString())
          }
        } else {
          append(selectedAttachablesCount.toString())
        }

        if (maxAllowedAttachablesPerPost > 0) {
          append("/")
          append(maxAllowedAttachablesPerPost.toString())
        }

        append(" ")
        append("(")
        append(totalAttachablesCount.toString())
        append(")")
      }
    }
  }

  private fun onPostSendCanceled(chanDescriptor: ChanDescriptor) {
    Logger.debug(TAG) { "onPostSendCanceled(${chanDescriptor})" }
    showToast(appResources.string(R.string.reply_send_canceled_by_user))
  }

  private fun onPostSendError(chanDescriptor: ChanDescriptor, exception: Throwable) {
    BackgroundUtils.ensureMainThread()
    Logger.e(TAG, "onPostSendError(${chanDescriptor})", exception)

    showDialog(
      message = appResources.string(R.string.reply_error_message, exception.errorMessageOrClassName())
    )
  }

  private fun onPostSendErrorBanned(banMessage: CharSequence?, banInfo: ReplyResponse.BanInfo) {
    val title = when (banInfo) {
      ReplyResponse.BanInfo.Banned -> appResources.string(R.string.reply_layout_info_title_ban_info)
      ReplyResponse.BanInfo.Warned -> appResources.string(R.string.reply_layout_info_title_warning_info)
    }

    val message = if (banMessage.isNotNullNorBlank()) {
      banMessage
    } else {
      when (banInfo) {
        ReplyResponse.BanInfo.Banned -> appResources.string(R.string.post_service_response_probably_banned)
        ReplyResponse.BanInfo.Warned -> appResources.string(R.string.post_service_response_probably_warned)
      }
    }

    showBannedDialog(
      title = title,
      message = message,
      neutralButton = { clearPostingCookies.perform(chanDescriptor.siteDescriptor()) },
      positiveButton = {},
      onDismissListener = null
    )
  }

  private suspend fun onPostSendComplete(
    chanDescriptor: ChanDescriptor,
    replyResponse: ReplyResponse,
    replyMode: ReplyMode,
    retrying: Boolean
  ) {
    BackgroundUtils.ensureMainThread()

    when {
      replyResponse.posted -> {
        Logger.d(TAG, "onPostSendComplete(${chanDescriptor}) posted is true replyResponse: $replyResponse")
        onPostedSuccessfully(
          prevChanDescriptor = chanDescriptor,
          replyResponse = replyResponse
        )
      }
      replyResponse.requireAuthentication -> {
        Logger.d(TAG, "onPostSendComplete(${chanDescriptor}) requireAuthentication os true replyResponse: $replyResponse")
        onPostCompleteUnsuccessful(
          chanDescriptor = chanDescriptor,
          replyResponse = replyResponse,
          additionalErrorMessage = null,
          onDismissListener = {
            callbacks.showCaptcha(
              chanDescriptor = chanDescriptor,
              replyMode = replyMode,
              autoReply = true,
              afterPostingAttempt = true
            )
          }
        )
      }
      else -> {
        Logger.d(TAG, "onPostSendComplete(${chanDescriptor}) else branch replyResponse: $replyResponse, retrying: $retrying")

        if (retrying) {
          // To avoid infinite cycles
          onPostCompleteUnsuccessful(
            chanDescriptor = chanDescriptor,
            replyResponse = replyResponse,
            additionalErrorMessage = null
          )

          return
        }

        when (replyResponse.additionalResponseData) {
          ReplyResponse.AdditionalResponseData.NoOp -> {
            onPostCompleteUnsuccessful(
              chanDescriptor = chanDescriptor,
              replyResponse = replyResponse,
              additionalErrorMessage = null
            )
          }
        }
      }
    }
  }

  private fun onPostCompleteUnsuccessful(
    chanDescriptor: ChanDescriptor,
    replyResponse: ReplyResponse,
    additionalErrorMessage: String? = null,
    onDismissListener: (() -> Unit)? = null
  ) {
    val errorMessage = when {
      additionalErrorMessage != null -> {
        appResources.string(R.string.reply_error_message, additionalErrorMessage)
      }
      replyResponse.errorMessageShort != null -> {
        appResources.string(R.string.reply_error_message, replyResponse.errorMessageShort!!)
      }
      replyResponse.requireAuthentication -> {
        val errorMessage = if (replyResponse.errorMessageShort.isNotNullNorBlank()) {
          replyResponse.errorMessageShort!!
        } else {
          appResources.string(R.string.reply_error_authentication_required)
        }

        appResources.string(R.string.reply_error_message, errorMessage)
      }
      else -> appResources.string(R.string.reply_error_unknown, replyResponse.asFormattedText())
    }

    Logger.e(TAG, "onPostCompleteUnsuccessful(${chanDescriptor}) error: $errorMessage")

    showDialog(
      message = errorMessage,
      onDismissListener = onDismissListener
    )
  }

  private suspend fun onPostedSuccessfully(
    prevChanDescriptor: ChanDescriptor,
    replyResponse: ReplyResponse
  ) {
    val siteDescriptor = replyResponse.siteDescriptor

    Logger.debug(TAG) {
      "onPostedSuccessfully(${prevChanDescriptor}) siteDescriptor: $siteDescriptor, replyResponse: $replyResponse"
    }

    if (siteDescriptor == null) {
      Logger.error(TAG) {
        "onPostedSuccessfully(${prevChanDescriptor}) siteDescriptor is null"
      }

      return
    }

    // if the thread being presented has changed in the time waiting for this call to
    // complete, the loadable field in ReplyPresenter will be incorrect; reconstruct
    // the loadable (local to this method) from the reply response
    val localSite = siteManager.bySiteDescriptor(siteDescriptor)
    if (localSite == null) {
      Logger.error(TAG) {
        "onPostedSuccessfully(${prevChanDescriptor}) localSite is null"
      }

      return
    }

    val boardDescriptor = BoardDescriptor.create(
      siteDescriptor = siteDescriptor,
      boardCode = replyResponse.boardCode
    )

    val threadNo = if (replyResponse.threadNo <= 0L) {
      replyResponse.postNo
    } else {
      replyResponse.threadNo
    }

    val newThreadDescriptor = ChanDescriptor.ThreadDescriptor.create(
      siteName = localSite.name(),
      boardCode = boardDescriptor.boardCode,
      threadNo = threadNo
    )

    hideDialog()
    collapseReplyLayout()
    callbacks.onPostedSuccessfully(prevChanDescriptor, newThreadDescriptor)

    Logger.debug(TAG) {
      "onPostedSuccessfully(${prevChanDescriptor}) success, newThreadDescriptor: ${newThreadDescriptor}"
    }
  }

  private fun showToast(message: String) {
    coroutineScope.launch(Dispatchers.Main) {
      callbacks.showToast(message)
    }
  }

  private fun showErrorToast(message: String) {
    callbacks.showErrorToast(message)
  }

  private fun showErrorToast(throwable: Throwable) {
    coroutineScope.launch(Dispatchers.Main) {
      callbacks.showErrorToast(throwable)
    }
  }

  private fun showDialog(message: CharSequence, onDismissListener: (() -> Unit)? = null) {
    val title = appResources.string(R.string.reply_layout_dialog_title)
    showDialog(title, message, onDismissListener)
  }

  private fun showBannedDialog(
    title: String,
    message: CharSequence,
    neutralButton: () -> Unit,
    positiveButton: () -> Unit,
    onDismissListener: (() -> Unit)? = null
  ) {
    coroutineScope.launch(Dispatchers.Main) {
      callbacks.showBanDialog(
        title = title,
        message = message,
        neutralButton = neutralButton,
        positiveButton = positiveButton,
        onDismissListener = onDismissListener
      )
    }
  }

  private fun showDialog(title: String, message: CharSequence, onDismissListener: (() -> Unit)? = null) {
    coroutineScope.launch(Dispatchers.Main) {
      callbacks.showDialog(title, message, onDismissListener)
    }
  }

  private fun hideDialog() {
    coroutineScope.launch(Dispatchers.Main) {
      callbacks.hideDialog()
    }
  }

  interface Callbacks {
    fun showCaptcha(
      chanDescriptor: ChanDescriptor,
      replyMode: ReplyMode,
      autoReply: Boolean,
      afterPostingAttempt: Boolean
    )

    fun showDialog(
      title: String,
      message: CharSequence,
      onDismissListener: (() -> Unit)? = null
    )

    fun hideDialog()

    fun showBanDialog(
      title: String,
      message: CharSequence,
      neutralButton: () -> Unit,
      positiveButton: () -> Unit,
      onDismissListener: (() -> Unit)? = null
    )

    fun hideBanDialog()

    fun showProgressDialog(title: String)
    fun hideProgressDialog()

    fun showToast(message: String)
    fun showErrorToast(throwable: Throwable)
    fun showErrorToast(message: String)

    suspend fun onPostedSuccessfully(
      prevChanDescriptor: ChanDescriptor,
      newThreadDescriptor: ChanDescriptor.ThreadDescriptor
    )

    fun highlightQuotes(quotes: Set<PostDescriptor>)
  }

  companion object {
    private const val TAG = "ReplyLayoutState"
  }

}