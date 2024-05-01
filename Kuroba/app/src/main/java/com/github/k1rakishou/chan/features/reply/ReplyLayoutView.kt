package com.github.k1rakishou.chan.features.reply

import android.content.Context
import android.text.Spannable
import android.util.AttributeSet
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.AnnotatedString
import androidx.core.text.getSpans
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.base.KurobaCoroutineScope
import com.github.k1rakishou.chan.core.base.SerializedCoroutineExecutor
import com.github.k1rakishou.chan.core.helper.DialogFactory
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.site.SiteAuthentication
import com.github.k1rakishou.chan.core.site.SiteSetting
import com.github.k1rakishou.chan.core.usecase.LoadBoardFlagsUseCase
import com.github.k1rakishou.chan.features.remote_image_search.ImageSearchController
import com.github.k1rakishou.chan.features.reply.data.ReplyFileAttachable
import com.github.k1rakishou.chan.features.reply.data.ReplyLayoutVisibility
import com.github.k1rakishou.chan.ui.compose.providers.ComposeEntrypoint
import com.github.k1rakishou.chan.ui.controller.FloatingListMenuController
import com.github.k1rakishou.chan.ui.controller.OpenUrlInWebViewController
import com.github.k1rakishou.chan.ui.controller.ThreadControllerType
import com.github.k1rakishou.chan.ui.controller.dialog.KurobaComposeDialogController
import com.github.k1rakishou.chan.ui.helper.AppResources
import com.github.k1rakishou.chan.ui.layout.ThreadListLayout
import com.github.k1rakishou.chan.ui.view.floating_menu.CheckableFloatingListMenuItem
import com.github.k1rakishou.chan.ui.view.floating_menu.FloatingListMenuItem
import com.github.k1rakishou.chan.ui.view.widget.dialog.KurobaAlertDialog
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.IHasViewModelScope
import com.github.k1rakishou.chan.utils.ViewModelScope
import com.github.k1rakishou.chan.utils.WebViewLink
import com.github.k1rakishou.chan.utils.WebViewLinkMovementMethod
import com.github.k1rakishou.chan.utils.viewModelByKeyEager
import com.github.k1rakishou.common.AndroidUtils
import com.github.k1rakishou.common.requireComponentActivity
import com.github.k1rakishou.common.resumeValueSafe
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.persist_state.PersistableChanState
import com.github.k1rakishou.persist_state.ReplyMode
import com.github.k1rakishou.prefs.BooleanSetting
import com.github.k1rakishou.prefs.OptionsSetting
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.*
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject

class ReplyLayoutView @JvmOverloads constructor(
  context: Context,
  attributeSet: AttributeSet? = null,
  defAttrStyle: Int = 0
) : FrameLayout(context, attributeSet, defAttrStyle),
  ReplyLayoutViewModel.ReplyLayoutViewCallbacks,
  ThreadListLayout.ReplyLayoutViewCallbacks,
  WebViewLinkMovementMethod.ClickListener,
  IHasViewModelScope {

  @Inject
  lateinit var dialogFactory: DialogFactory
  @Inject
  lateinit var appResources: AppResources
  @Inject
  lateinit var globalWindowInsetsManager: GlobalWindowInsetsManager
  @Inject
  lateinit var siteManager: SiteManager

  private lateinit var replyLayoutViewModel: ReplyLayoutViewModel
  private lateinit var replyLayoutCallbacks: ReplyLayoutViewModel.ThreadListLayoutCallbacks

  private val composeView: ComposeView
  private val coroutineScope = KurobaCoroutineScope()
  private val readyState = mutableStateOf(false)
  private val dialogHandle = AtomicReference<KurobaAlertDialog.AlertDialogHandle?>(null)
  private val banDialogHandle = AtomicReference<KurobaAlertDialog.AlertDialogHandle?>(null)
  private val showPostingDialogExecutor = SerializedCoroutineExecutor(coroutineScope)

  override val viewModelScope: ViewModelScope
    get() {
      val componentActivity = context.requireComponentActivity()
      return ViewModelScope.ActivityScope(componentActivity, componentActivity.viewModelStore)
    }

  init {
    AppModuleAndroidUtils.extractActivityComponent(context)
      .inject(this)

    removeAllViews()

    composeView = ComposeView(context)
    composeView.layoutParams = ViewGroup.LayoutParams(
      ViewGroup.LayoutParams.MATCH_PARENT,
      ViewGroup.LayoutParams.MATCH_PARENT
    )

    addView(composeView)

    composeView.setContent {
      ComposeEntrypoint {
        val ready by readyState
        if (!ready) {
          return@ComposeEntrypoint
        }

        ReplyLayout(
          replyLayoutViewModel = replyLayoutViewModel,
          onPresolveCaptchaButtonClicked = replyLayoutCallbacks::onPresolveCaptchaButtonClicked,
        )
      }
    }
  }

  override fun onCreate(threadControllerType: ThreadControllerType, callbacks: ReplyLayoutViewModel.ThreadListLayoutCallbacks) {
    replyLayoutCallbacks = callbacks

    replyLayoutViewModel = viewModelByKeyEager<ReplyLayoutViewModel>(
      key = threadControllerType.name,
      params = { threadControllerType }
    )
    replyLayoutViewModel.bindThreadListLayoutCallbacks(callbacks)
    replyLayoutViewModel.bindReplyLayoutViewCallbacks(this)

    readyState.value = true
  }

  override fun onDestroy() {
    replyLayoutViewModel.unbindCallbacks()
    coroutineScope.cancelChildren()
  }

  override suspend fun bindChanDescriptor(descriptor: ChanDescriptor, threadControllerType: ThreadControllerType) {
    replyLayoutViewModel.bindChanDescriptor(descriptor, threadControllerType)
  }

  override fun quote(post: ChanPost, withText: Boolean) {
    replyLayoutViewModel.quote(post, withText)
  }

  override fun quote(postDescriptor: PostDescriptor, text: CharSequence) {
    replyLayoutViewModel.quote(postDescriptor, text)
  }

  override fun replyLayoutVisibility(): ReplyLayoutVisibility {
    return replyLayoutViewModel.replyLayoutVisibility()
  }

  override fun isExpanded(): Boolean {
    return replyLayoutViewModel.replyLayoutVisibility() == ReplyLayoutVisibility.Expanded
  }

  override fun isOpened(): Boolean {
    return replyLayoutViewModel.replyLayoutVisibility() == ReplyLayoutVisibility.Opened
  }

  override fun isCollapsed(): Boolean {
    return replyLayoutViewModel.replyLayoutVisibility() == ReplyLayoutVisibility.Collapsed
  }

  override fun isOpenedOrExpanded(): Boolean {
    return isOpened() || isExpanded()
  }

  override fun updateReplyLayoutVisibility(newReplyLayoutVisibility: ReplyLayoutVisibility) {
    replyLayoutViewModel.updateReplyLayoutVisibility(newReplyLayoutVisibility)
  }

  override fun enqueueReply(chanDescriptor: ChanDescriptor, replyMode: ReplyMode, retrying: Boolean) {
    replyLayoutViewModel.enqueueReply(chanDescriptor, replyMode, retrying)
  }

  override fun onImageOptionsApplied(fileUuid: UUID) {
    replyLayoutViewModel.onImageOptionsApplied(fileUuid)
  }

  override fun hideKeyboard() {
    AndroidUtils.hideKeyboard(this)
  }

  override fun onBack(): Boolean {
    return replyLayoutViewModel.onBack()
  }

  override fun onWebViewLinkClick(type: WebViewLink.Type, link: String) {
    Logger.d(TAG, "onWebViewLinkClick type: ${type}, link: ${link}")

    when (type) {
      WebViewLink.Type.BanMessage -> {
        replyLayoutCallbacks.presentController(OpenUrlInWebViewController(context, link))
      }
    }
  }

  override suspend fun showDialogSuspend(title: String, message: CharSequence?) {
    suspendCancellableCoroutine<Unit> { cancellableContinuation ->
      showDialog(
        title = title,
        message = message,
        onDismissListener = { cancellableContinuation.resumeValueSafe(Unit) }
      )
    }
  }

  override suspend fun promptUserForMediaUrl(): String? {
    val params = KurobaComposeDialogController.dialogWithInput(
      title = KurobaComposeDialogController.Text.Id(R.string.reply_layout_remote_file_pick_enter_url_dialog_title),
      input = KurobaComposeDialogController.Input.String(
        hint = KurobaComposeDialogController.Text.Id(R.string.reply_layout_remote_file_pick_enter_url_dialog_hint)
      )
    )

    dialogFactory.showDialog(
      context = context,
      params = params
    )

    return params.awaitInputResult()
  }

  override fun showDialog(
    title: String,
    message: CharSequence?,
    onDismissListener: (() -> Unit)?
  ) {
    showDialogInternal(
      banDialog = false,
      title = title,
      message = message,
      neutralButton = null,
      positiveButton = null,
      onDismissListener = onDismissListener
    )
  }

  override fun showBanDialog(
    title: String,
    message: CharSequence?,
    neutralButton: () -> Unit,
    positiveButton: () -> Unit,
    onDismissListener: (() -> Unit)?
  ) {
    showDialogInternal(
      banDialog = true,
      title = title,
      message = message,
      neutralButton = neutralButton,
      positiveButton = positiveButton,
      onDismissListener = onDismissListener
    )
  }

  override fun hideDialog() {
    dialogHandle.getAndSet(null)?.dismiss()
  }

  override fun hideBanDialog() {
    banDialogHandle.getAndSet(null)?.dismiss()
  }

  override fun onAttachedMediaClicked(attachedMedia: ReplyFileAttachable, isFileSupportedForReencoding: Boolean) {
    replyLayoutCallbacks.showMediaReencodingController(attachedMedia, isFileSupportedForReencoding)
  }

  override suspend fun onAttachedMediaLongClicked(attachedMedia: ReplyFileAttachable) {
    showAttachFileOptions(attachedMedia.fileUuid)
  }

  override fun onDontKeepActivitiesSettingDetected() {
    dialogFactory.createSimpleInformationDialog(
      context = context,
      titleText = appResources.string(R.string.dont_keep_activities_setting_enabled),
      descriptionText = appResources.string(R.string.dont_keep_activities_setting_enabled_description)
    )
  }

  override fun showFileStatusDialog(attachableFileStatus: AnnotatedString) {
    dialogFactory.showDialog(
      context = context,
      params = KurobaComposeDialogController.informationDialog(
        title = KurobaComposeDialogController.Text.String(
          value = appResources.string(R.string.reply_file_status_dialog_title)
        ),
        description = KurobaComposeDialogController.Text.AnnotatedString(attachableFileStatus)
      )
    )
  }

  override suspend fun promptUserToSelectFlag(chanDescriptor: ChanDescriptor): LoadBoardFlagsUseCase.FlagInfo? {
    val flagInfoList = replyLayoutViewModel.getFlagInfoList(chanDescriptor.boardDescriptor())
    if (flagInfoList.isEmpty()) {
      return null
    }

    val floatingMenuItems = mutableListOf<FloatingListMenuItem>()

    flagInfoList.forEach { boardFlag ->
      floatingMenuItems += FloatingListMenuItem(
        key = boardFlag.flagKey,
        name = boardFlag.asUserReadableString(),
        value = boardFlag
      )
    }

    if (floatingMenuItems.isEmpty()) {
      return null
    }

    val selectedBoardFlag = suspendCancellableCoroutine<LoadBoardFlagsUseCase.FlagInfo?> { continuation ->
      val floatingMenuScreen = FloatingListMenuController(
        context = context,
        constraintLayoutBias = globalWindowInsetsManager.lastTouchCoordinatesAsConstraintLayoutBias(),
        items = floatingMenuItems,
        itemClickListener = { clickedItem ->
          val selectedFlagInfo = clickedItem.value as? LoadBoardFlagsUseCase.FlagInfo
          continuation.resumeValueSafe(selectedFlagInfo)
        },
        menuDismissListener = {
          continuation.resumeValueSafe(null)
        }
      )

      replyLayoutCallbacks.presentController(floatingMenuScreen)
    }

    return selectedBoardFlag
  }

  override suspend fun promptUserToConfirmMediaDeletion(): Boolean {
    return suspendCancellableCoroutine<Boolean> { cancellableContinuation ->
      dialogFactory.showDialog(
        context = context,
        params = KurobaComposeDialogController.confirmationDialog(
          title = KurobaComposeDialogController.Text.Id(R.string.reply_layout_attached_deletion_dialog_title),
          description = null,
          negativeButton = KurobaComposeDialogController.DialogButton(
            buttonText = R.string.no,
            onClick = { cancellableContinuation.resumeValueSafe(false) }
          ),
          positionButton = KurobaComposeDialogController.PositiveDialogButton(
            buttonText = R.string.yes,
            isActionDangerous = true,
            onClick = { cancellableContinuation.resumeValueSafe(true) }
          )
        ),
        onDismissListener = { cancellableContinuation.resumeValueSafe(false) }
      )
    }
  }

  fun onPickLocalMediaButtonClicked() {
    replyLayoutViewModel.onPickLocalMediaButtonClicked()
  }

  fun onPickRemoteMediaButtonClicked() {
    replyLayoutViewModel.onPickRemoteMediaButtonClicked()
  }

  fun onSearchRemoteMediaButtonClicked(chanDescriptor: ChanDescriptor) {
    val imageSearchController = ImageSearchController(
      context = context,
      onImageSelected = { selectedImageUrl -> replyLayoutViewModel.onRemoteImageSelected(selectedImageUrl) }
    )

    replyLayoutCallbacks.pushController(imageSearchController)
  }

  override fun onReplyLayoutOptionsButtonClicked() {
    val chanDescriptor = replyLayoutViewModel.boundChanDescriptor.value
      ?: return

    val prevReplyMode = siteManager.bySiteDescriptor(chanDescriptor.siteDescriptor())
      ?.getSettingBySettingId<OptionsSetting<ReplyMode>>(SiteSetting.SiteSettingId.LastUsedReplyMode)
      ?.get()
      ?: ReplyMode.Unknown

    showReplyOptions(chanDescriptor, prevReplyMode)
  }

  private fun showDialogInternal(
    banDialog: Boolean,
    title: String,
    message: CharSequence?,
    neutralButton: (() -> Unit)?,
    positiveButton: (() -> Unit)?,
    onDismissListener: (() -> Unit)?
  ) {
    if (title.isBlank() && message.isNullOrBlank()) {
      if (banDialog) {
        hideBanDialog()
      } else {
        hideDialog()
      }

      onDismissListener?.invoke()
      return
    }

    showPostingDialogExecutor.post {
      try {
        val linkMovementMethod = if (hasWebViewLinks(message)) {
          WebViewLinkMovementMethod(webViewLinkClickListener = this)
        } else {
          null
        }

        suspendCancellableCoroutine<Unit> { continuation ->
          if (banDialog) {
            continuation.invokeOnCancellation { banDialogHandle.getAndSet(null)?.dismiss() }

            val dialogId = "ReplyPresenterPostingBanDialog"
            dialogFactory.dismissDialogById(dialogId)

            val handle = dialogFactory.createSimpleConfirmationDialog(
              context = context,
              dialogId = dialogId,
              titleText = title,
              descriptionText = message,
              negativeButtonText = appResources.string(R.string.reply_layout_ban_dialog_clear_cookies),
              onNegativeButtonClickListener = { neutralButton?.invoke() },
              positiveButtonText = appResources.string(R.string.ok),
              onPositiveButtonClickListener = { positiveButton?.invoke() },
              customLinkMovementMethod = linkMovementMethod,
              onDismissListener = { continuation.resumeValueSafe(Unit) }
            )

            banDialogHandle.getAndSet(handle)
              ?.dismiss()
          } else {
            continuation.invokeOnCancellation { dialogHandle.getAndSet(null)?.dismiss() }

            val dialogId = "ReplyPresenterPostingErrorDialog"
            dialogFactory.dismissDialogById(dialogId)

            val handle = dialogFactory.createSimpleInformationDialog(
              context = context,
              dialogId = dialogId,
              titleText = title,
              descriptionText = message,
              customLinkMovementMethod = linkMovementMethod,
              onDismissListener = { continuation.resumeValueSafe(Unit) }
            )

            dialogHandle.getAndSet(handle)
              ?.dismiss()
          }
        }
      } finally {
        onDismissListener?.invoke()
      }
    }
  }

  private fun hasWebViewLinks(message: CharSequence?): Boolean {
    var hasWebViewLinks = false

    if (message is Spannable) {
      hasWebViewLinks = message.getSpans<WebViewLink>().isNotEmpty()
    }

    return hasWebViewLinks
  }

  private suspend fun showAttachFileOptions(selectedFileUuid: UUID) {
    val floatingListMenuItems = mutableListOf<FloatingListMenuItem>()

    floatingListMenuItems += FloatingListMenuItem(
      key = ACTION_DELETE_THIS_FILE,
      name = context.getString(R.string.layout_reply_files_area_delete_file_action),
      value = selectedFileUuid
    )

    floatingListMenuItems += FloatingListMenuItem(
      key = ACTION_REMOVE_THIS_FILE_NAME,
      name = context.getString(R.string.layout_reply_files_area_this_file_remove_file_name),
      value = selectedFileUuid
    )

    floatingListMenuItems += FloatingListMenuItem(
      key = ACTION_REMOVE_THIS_FILE_METADATA,
      name = context.getString(R.string.layout_reply_files_area_this_file_remove_metadata),
      value = selectedFileUuid
    )

    floatingListMenuItems += FloatingListMenuItem(
      key = ACTION_CHANGE_THIS_FILE_CHECKSUM,
      name = context.getString(R.string.layout_reply_files_area_this_file_change_checksum),
      value = selectedFileUuid
    )

    if (replyLayoutViewModel.boardsSupportsSpoilers()) {
      if (replyLayoutViewModel.isReplyFileMarkedAsSpoiler(selectedFileUuid)) {
        floatingListMenuItems += FloatingListMenuItem(
          key = ACTION_UNMARK_THIS_FILE_AS_SPOILER,
          name = context.getString(R.string.layout_reply_files_area_unmark_as_spoiler),
          value = selectedFileUuid
        )
      } else {
        floatingListMenuItems += FloatingListMenuItem(
          key = ACTION_MARK_THIS_FILE_AS_SPOILER,
          name = context.getString(R.string.layout_reply_files_area_mark_as_spoiler),
          value = selectedFileUuid
        )
      }
    }

    if (replyLayoutViewModel.hasSelectedFiles()) {
      floatingListMenuItems += FloatingListMenuItem(
        key = ACTION_DELETE_SELECTED_FILES,
        name = context.getString(R.string.layout_reply_files_area_selected_files_delete),
        value = selectedFileUuid
      )

      floatingListMenuItems += FloatingListMenuItem(
        key = ACTION_REMOVE_SELECTED_FILES_FILE_NAME,
        name = context.getString(R.string.layout_reply_files_area_selected_files_remove_file_name),
        value = selectedFileUuid
      )

      floatingListMenuItems += FloatingListMenuItem(
        key = ACTION_REMOVE_SELECTED_FILES_METADATA,
        name = context.getString(R.string.layout_reply_files_area_selected_files_remove_metadata),
        value = selectedFileUuid
      )

      floatingListMenuItems += FloatingListMenuItem(
        key = ACTION_CHANGE_SELECTED_FILES_CHECKSUM,
        name = context.getString(R.string.layout_reply_files_area_selected_files_change_checksum),
        value = selectedFileUuid
      )
    }

    if (replyLayoutViewModel.allFilesSelected()) {
      floatingListMenuItems += FloatingListMenuItem(
        key = ACTION_UNSELECT_ALL,
        name = context.getString(R.string.layout_reply_files_area_unselect_all),
        value = selectedFileUuid
      )
    } else {
      floatingListMenuItems += FloatingListMenuItem(
        key = ACTION_SELECT_ALL,
        name = context.getString(R.string.layout_reply_files_area_select_all),
        value = selectedFileUuid
      )
    }

    val clickedItem = suspendCancellableCoroutine<FloatingListMenuItem?> { cancellableContinuation ->
      val floatingListMenuController = FloatingListMenuController(
        context = context,
        constraintLayoutBias = globalWindowInsetsManager.lastTouchCoordinatesAsConstraintLayoutBias(),
        items = floatingListMenuItems,
        itemClickListener = { item -> cancellableContinuation.resumeValueSafe(item) },
        menuDismissListener = { cancellableContinuation.resumeValueSafe(null) }
      )

      replyLayoutCallbacks.presentController(floatingListMenuController)
    }

    if (clickedItem == null) {
      return
    }

    onAttachFileItemClicked(clickedItem)
  }

  private fun onAttachFileItemClicked(item: FloatingListMenuItem) {
    val id = item.key as Int
    val clickedFileUuid = item.value as UUID

    when (id) {
      ACTION_DELETE_THIS_FILE -> replyLayoutViewModel.removeAttachedMedia(clickedFileUuid)
      ACTION_REMOVE_THIS_FILE_NAME -> replyLayoutViewModel.removeThisFileName(clickedFileUuid)
      ACTION_REMOVE_THIS_FILE_METADATA -> replyLayoutViewModel.removeThisFileMetadata(clickedFileUuid)
      ACTION_CHANGE_THIS_FILE_CHECKSUM -> replyLayoutViewModel.changeThisFileChecksum(clickedFileUuid)
      ACTION_DELETE_SELECTED_FILES -> replyLayoutViewModel.deleteSelectedFiles()
      ACTION_REMOVE_SELECTED_FILES_FILE_NAME -> replyLayoutViewModel.removeSelectedFilesName()
      ACTION_REMOVE_SELECTED_FILES_METADATA -> replyLayoutViewModel.removeSelectedFilesMetadata()
      ACTION_CHANGE_SELECTED_FILES_CHECKSUM -> replyLayoutViewModel.changeSelectedFilesChecksum()
      ACTION_SELECT_ALL -> replyLayoutViewModel.selectUnselectAll(selectAll = true)
      ACTION_UNSELECT_ALL -> replyLayoutViewModel.selectUnselectAll(selectAll = false)
      ACTION_MARK_THIS_FILE_AS_SPOILER -> replyLayoutViewModel.markUnmarkAsSpoiler(clickedFileUuid, spoiler = true)
      ACTION_UNMARK_THIS_FILE_AS_SPOILER -> replyLayoutViewModel.markUnmarkAsSpoiler(clickedFileUuid, spoiler = false)
    }
  }

  private fun showReplyOptions(chanDescriptor: ChanDescriptor, prevReplyMode: ReplyMode) {
    val menuItems = mutableListOf<FloatingListMenuItem>()
    val availableReplyModes = buildReplyModeOptions(chanDescriptor, prevReplyMode)

    val ignoreReplyCooldowns = siteManager.bySiteDescriptor(chanDescriptor.siteDescriptor())
      ?.getSettingBySettingId<BooleanSetting>(SiteSetting.SiteSettingId.IgnoreReplyCooldowns)
    val lastUsedReplyMode = siteManager.bySiteDescriptor(chanDescriptor.siteDescriptor())
      ?.getSettingBySettingId<OptionsSetting<ReplyMode>>(SiteSetting.SiteSettingId.LastUsedReplyMode)
    val check4chanPostAcknowledgedSetting = siteManager.bySiteDescriptor(chanDescriptor.siteDescriptor())
      ?.getSettingBySettingId<BooleanSetting>(SiteSetting.SiteSettingId.Check4chanPostAcknowledged)

    menuItems += FloatingListMenuItem(
      key = ACTION_REPLY_MODES,
      name = appResources.string(R.string.reply_layout_reply_modes),
      more = availableReplyModes
    )
    menuItems += CheckableFloatingListMenuItem(
      key = ACTION_IGNORE_REPLY_COOLDOWNS,
      name = appResources.string(R.string.reply_layout_ignore_reply_cooldowns),
      checked = ignoreReplyCooldowns?.get() == true
    )

    if (chanDescriptor.siteDescriptor().is4chan()) {
      check4chanPostAcknowledgedSetting?.get()?.let { check4chanPostAcknowledged ->
        menuItems += CheckableFloatingListMenuItem(
          key = ACTION_CHECK_4CHAN_POST_ACKNOWLEDGED,
          name = appResources.string(R.string.reply_layout_check_if_post_was_actually_acknowledged_by_4chan),
          checked = check4chanPostAcknowledged
        )
      }
    }

    menuItems += FloatingListMenuItem(
      key = ACTION_RESET_REMEMBERED_FILE_PICKER,
      name = appResources.string(R.string.reply_layout_reset_remembered_file_picker)
    )

    val floatingListMenuController = FloatingListMenuController(
      context = context,
      constraintLayoutBias = globalWindowInsetsManager.lastTouchCoordinatesAsConstraintLayoutBias(),
      items = menuItems,
      itemClickListener = { clickedItem ->
        if (clickedItem.key is Int) {
          when (clickedItem.key) {
            ACTION_IGNORE_REPLY_COOLDOWNS -> {
              ignoreReplyCooldowns?.toggle()
            }
            ACTION_CHECK_4CHAN_POST_ACKNOWLEDGED -> {
              check4chanPostAcknowledgedSetting?.toggle()
            }
            ACTION_RESET_REMEMBERED_FILE_PICKER -> {
              PersistableChanState.lastRememberedFilePicker.remove()
            }
          }
        } else if (clickedItem.key is ReplyMode) {
          val replyMode = clickedItem.key as? ReplyMode
            ?: return@FloatingListMenuController

          lastUsedReplyMode?.set(replyMode)

          replyLayoutViewModel.updateCaptchaButtonVisibility()
        }
      }
    )

    replyLayoutCallbacks.presentController(floatingListMenuController)
  }

  private fun buildReplyModeOptions(
    chanDescriptor: ChanDescriptor,
    prevReplyMode: ReplyMode
  ): MutableList<FloatingListMenuItem> {
    val availableReplyModes = mutableListOf<FloatingListMenuItem>()
    val site = siteManager.bySiteDescriptor(chanDescriptor.siteDescriptor())
    val groupId = "reply_mode"

    if (site?.actions()?.postAuthenticate()?.type != SiteAuthentication.Type.NONE) {
      availableReplyModes += CheckableFloatingListMenuItem(
        key = ReplyMode.ReplyModeSolveCaptchaManually,
        name = appResources.string(R.string.reply_mode_solve_captcha_and_post),
        groupId = groupId,
        checked = prevReplyMode == ReplyMode.ReplyModeSolveCaptchaManually
      )
    }

    availableReplyModes += CheckableFloatingListMenuItem(
      key = ReplyMode.ReplyModeSendWithoutCaptcha,
      name = appResources.string(R.string.reply_mode_attempt_post_without_captcha),
      groupId = groupId,
      checked = prevReplyMode == ReplyMode.ReplyModeSendWithoutCaptcha
    )

    if (replyLayoutViewModel.paidCaptchaSolversSupportedAndEnabled(chanDescriptor)) {
      availableReplyModes += CheckableFloatingListMenuItem(
        key = ReplyMode.ReplyModeSolveCaptchaAuto,
        name = appResources.string(R.string.reply_mode_post_with_captcha_solver),
        groupId = groupId,
        checked = prevReplyMode == ReplyMode.ReplyModeSolveCaptchaAuto
      )
    }

    if (site?.actions()?.isLoggedIn() == true) {
      availableReplyModes += CheckableFloatingListMenuItem(
        key = ReplyMode.ReplyModeUsePasscode,
        name = appResources.string(R.string.reply_mode_post_with_passcode),
        groupId = groupId,
        checked = prevReplyMode == ReplyMode.ReplyModeUsePasscode
      )
    }

    return availableReplyModes
  }

  companion object {
    private const val TAG = "ReplyLayoutView"

    private const val ACTION_DELETE_THIS_FILE = 1
    private const val ACTION_DELETE_SELECTED_FILES = 2
    private const val ACTION_REMOVE_SELECTED_FILES_FILE_NAME = 3
    private const val ACTION_REMOVE_SELECTED_FILES_METADATA = 4
    private const val ACTION_CHANGE_SELECTED_FILES_CHECKSUM = 5
    private const val ACTION_SELECT_ALL = 6
    private const val ACTION_UNSELECT_ALL = 7
    private const val ACTION_MARK_THIS_FILE_AS_SPOILER = 8
    private const val ACTION_UNMARK_THIS_FILE_AS_SPOILER = 9
    private const val ACTION_REMOVE_THIS_FILE_NAME = 10
    private const val ACTION_REMOVE_THIS_FILE_METADATA = 11
    private const val ACTION_CHANGE_THIS_FILE_CHECKSUM = 12

    private const val ACTION_REPLY_MODES = 100
    private const val ACTION_IGNORE_REPLY_COOLDOWNS = 101
    private const val ACTION_CHECK_4CHAN_POST_ACKNOWLEDGED = 102
    private const val ACTION_RESET_REMEMBERED_FILE_PICKER = 103
  }

}