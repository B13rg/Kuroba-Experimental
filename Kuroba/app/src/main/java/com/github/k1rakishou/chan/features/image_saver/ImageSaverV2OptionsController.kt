package com.github.k1rakishou.chan.features.image_saver

import android.content.Context
import android.net.Uri
import android.text.TextWatcher
import android.view.View
import android.widget.FrameLayout
import android.widget.RadioGroup
import android.widget.Toast
import androidx.core.view.children
import androidx.core.widget.doAfterTextChanged
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.base.Debouncer
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.ui.controller.BaseFloatingController
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableButton
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableCheckBox
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableEditText
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableTextInputLayout
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableTextView
import com.github.k1rakishou.chan.ui.view.ViewContainerWithMaxSize
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.dp
import com.github.k1rakishou.chan.utils.doIgnoringTextWatcher
import com.github.k1rakishou.chan.utils.setVisibilityFast
import com.github.k1rakishou.common.isNotNullNorBlank
import com.github.k1rakishou.fsaf.FileChooser
import com.github.k1rakishou.fsaf.FileManager
import com.github.k1rakishou.fsaf.callback.directory.PermanentDirectoryChooserCallback
import com.github.k1rakishou.model.data.post.ChanPostImage
import com.github.k1rakishou.persist_state.ImageSaverV2Options
import com.github.k1rakishou.persist_state.PersistableChanState
import javax.inject.Inject

class ImageSaverV2OptionsController(
  context: Context,
  private val options: Options,
) : BaseFloatingController(context) {
  private lateinit var imageNameOptionsGroup: RadioGroup
  private lateinit var duplicatesResolutionOptionsGroup: RadioGroup
  private lateinit var appendSiteName: ColorizableCheckBox
  private lateinit var appendBoardCode: ColorizableCheckBox
  private lateinit var appendThreadId: ColorizableCheckBox
  private lateinit var rootDir: ColorizableTextView
  private lateinit var customFileName: ColorizableEditText
  private lateinit var outputFile: ColorizableEditText
  private lateinit var outputDirFile: ColorizableTextInputLayout
  private lateinit var additionalDirs: ColorizableEditText
  private lateinit var customFileNameTil: ColorizableTextInputLayout
  private lateinit var additionalDirectoriesTil: ColorizableTextInputLayout
  private lateinit var cancelButton: ColorizableButton
  private lateinit var saveButton: ColorizableButton

  private lateinit var fileNameTextWatcher: TextWatcher
  private lateinit var additionalDirsTextWatcher: TextWatcher

  private var needCallCancelFunc = true

  private val currentSetting = PersistableChanState.imageSaverV2PersistedOptions.get().copy()

  private val fileNameDebouncer = Debouncer(false)
  private val additionalDirsDebouncer = Debouncer(false)

  private var overriddenFileName: String? = null

  @Inject
  lateinit var fileChooser: FileChooser
  @Inject
  lateinit var fileManager: FileManager

  override fun injectDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  override fun getLayoutId(): Int = R.layout.controller_image_saver_options

  override fun onCreate() {
    super.onCreate()

    imageNameOptionsGroup = view.findViewById(R.id.image_name_options_group)
    duplicatesResolutionOptionsGroup = view.findViewById(R.id.duplicate_resolution_options_group)
    rootDir = view.findViewById(R.id.root_dir)
    appendSiteName = view.findViewById(R.id.append_site_name)
    appendBoardCode = view.findViewById(R.id.append_board_code)
    appendThreadId = view.findViewById(R.id.append_thread_id)
    additionalDirs = view.findViewById(R.id.additional_directories)
    outputFile = view.findViewById(R.id.output_file)
    outputDirFile = view.findViewById(R.id.output_file_til)
    customFileName = view.findViewById(R.id.custom_file_name)
    customFileNameTil = view.findViewById(R.id.custom_file_name_til)
    additionalDirectoriesTil = view.findViewById(R.id.additional_directories_til)
    cancelButton = view.findViewById(R.id.cancel_button)
    saveButton = view.findViewById(R.id.save_button)

    val widthConstraintContainer = view.findViewById<ViewContainerWithMaxSize>(R.id.width_constraint_container)
    widthConstraintContainer.desiredWidth = DESIRED_WIDTH

    when (options) {
      is Options.MultipleImages -> {
        // no customFileName because we can't rename multiple images at once
        customFileNameTil.setVisibilityFast(View.GONE)
        customFileName.setVisibilityFast(View.GONE)
      }
      is Options.ResultDirAccessProblems -> {
        // no customFileName here because it's either already set or the initial request was to
        // download multiple images.
        customFileNameTil.setVisibilityFast(View.GONE)
        customFileName.setVisibilityFast(View.GONE)

        imageNameOptionsGroup.enableOrDisable(enable = false)
        duplicatesResolutionOptionsGroup.enableOrDisable(enable = false)

        appendSiteName.isEnabled = false
        appendBoardCode.isEnabled = false
        appendThreadId.isEnabled = false

        additionalDirectoriesTil.isEnabled = false
        additionalDirs.isEnabled = false

        outputDirFile.isEnabled = false
        outputFile.isEnabled = false

        saveButton.setText(R.string.retry)
      }
      is Options.SingleImage -> {
        // no-op, fileName is visible by default
      }
    }

    imageNameOptionsGroup.setOnCheckedChangeListener { _, itemId ->
      this.overriddenFileName = null

      when (itemId) {
        R.id.image_name_options_use_server_name -> {
          currentSetting.imageNameOptions = ImageSaverV2Options.ImageNameOptions.UseServerFileName.rawValue
        }
        R.id.image_name_options_use_original_name -> {
          currentSetting.imageNameOptions = ImageSaverV2Options.ImageNameOptions.UseOriginalFileName.rawValue
        }
      }

      applyOptionsToView()
    }
    duplicatesResolutionOptionsGroup.setOnCheckedChangeListener { _, itemId ->
      when (itemId) {
        R.id.duplicate_resolution_options_ask -> {
          currentSetting.duplicatesResolution = ImageSaverV2Options.DuplicatesResolution.AskWhatToDo.rawValue
        }
        R.id.duplicate_resolution_options_overwrite -> {
          currentSetting.duplicatesResolution = ImageSaverV2Options.DuplicatesResolution.Overwrite.rawValue
        }
        R.id.duplicate_resolution_options_skip -> {
          currentSetting.duplicatesResolution = ImageSaverV2Options.DuplicatesResolution.Skip.rawValue
        }
      }

      applyOptionsToView()
    }

    rootDir.setOnClickListener {
      fileChooser.openChooseDirectoryDialog(object : PermanentDirectoryChooserCallback() {
        override fun onCancel(reason: String) {
          showToast(context.getString(R.string.controller_image_save_options_canceled_with_reason, reason))
        }

        override fun onResult(uri: Uri) {
          if (!checkRootDirAccessible(uri)) {
            return
          }

          currentSetting.rootDirectoryUri?.let { dirUriString ->
            if (uri.toString() == dirUriString) {
              return@let
            }

            val dirUri = Uri.parse(dirUriString)
            fileChooser.forgetSAFTree(dirUri)
          }

          currentSetting.rootDirectoryUri = uri.toString()
          applyOptionsToView()
        }
      })
    }

    fileNameTextWatcher = customFileName.doAfterTextChanged { editable ->
      fileNameDebouncer.post({
        overriddenFileName = editable?.toString()
        applyOptionsToView()
      }, 100)
    }
    additionalDirsTextWatcher = additionalDirs.doAfterTextChanged { editable ->
      additionalDirsDebouncer.post({
        val input = editable?.toString()
        if (input.isNullOrEmpty()) {
          currentSetting.subDirs = null
          applyOptionsToView()
          return@post
        }

        currentSetting.subDirs = editable.toString()
        applyOptionsToView()
      }, 100)
    }

    appendSiteName.setOnCheckedChangeListener { _, isChecked ->
      currentSetting.appendSiteName = isChecked
      applyOptionsToView()
    }
    appendBoardCode.setOnCheckedChangeListener { _, isChecked ->
      currentSetting.appendBoardCode = isChecked
      applyOptionsToView()
    }
    appendThreadId.setOnCheckedChangeListener { _, isChecked ->
      currentSetting.appendThreadId = isChecked
      applyOptionsToView()
    }

    val outsideArea = view.findViewById<FrameLayout>(R.id.outside_area)
    outsideArea.setOnClickListener { pop() }

    cancelButton.setOnClickListener {
      pop()
    }
    saveButton.setOnClickListener {
      val newFileName = if (options is Options.SingleImage) {
        customFileName.text?.toString()
      } else {
        null
      }

      PersistableChanState.imageSaverV2PersistedOptions.set(currentSetting)
      needCallCancelFunc = false

      when (options) {
        is Options.MultipleImages -> options.onSaveClicked(currentSetting)
        is Options.ResultDirAccessProblems -> options.onRetryClicked(currentSetting)
        is Options.SingleImage -> options.onSaveClicked(currentSetting, newFileName)
      }

      pop()
    }

    currentSetting.rootDirectoryUri?.let { rootDirUri ->
      checkRootDirAccessible(Uri.parse(rootDirUri))
    }

    applyOptionsToView()
  }

  private fun RadioGroup.enableOrDisable(enable: Boolean) {
    isEnabled = enable

    children.forEach { child ->
      child.isEnabled = enable
    }
  }

  private fun checkRootDirAccessible(uri: Uri): Boolean {
    val externalFile = fileManager.fromUri(uri)
    if (externalFile == null) {
      showToast(context.getString(R.string.image_saver_root_dir_is_inaccessible), Toast.LENGTH_LONG)
      return false
    }

    if (!fileManager.isDirectory(externalFile)) {
      showToast(context.getString(R.string.image_saver_root_uri_is_not_a_dir, uri), Toast.LENGTH_LONG)
      return false
    }

    if (!fileManager.exists(externalFile)) {
      showToast(context.getString(R.string.image_saver_root_dir_uri_does_not_exist, uri), Toast.LENGTH_LONG)
      return false
    }

    return true
  }

  override fun onDestroy() {
    super.onDestroy()

    if (needCallCancelFunc && options is Options.ResultDirAccessProblems) {
      options.onCancelClicked()
    }
  }

  private fun checkSubDirsValid(input: String): Boolean {
    if (input.isBlank()) {
      return false
    }

    val subDirs = input
      .split("\\")

    for ((index, subDir) in subDirs.withIndex()) {
      if (pathContainsBadSymbols(subDir)) {
        return false
      }

      if (subDir.isBlank()) {
        return false
      }
    }

    return true
  }

  private fun pathContainsBadSymbols(subDir: String): Boolean {
    for (char in subDir) {
      if (char.isLetterOrDigit() || char == '\\' || char == '_' || char == '-') {
        continue
      }

      return true
    }

    return false
  }

  private fun fileNameContainsBadSymbols(fileName: String): Boolean {
    for (char in fileName) {
      if (char.isLetterOrDigit() || char == '_' || char == '-') {
        continue
      }

      return true
    }

    return false
  }

  private fun applyOptionsToView() {
    val currentImageSaverSetting = currentSetting
    
    outputFile.error = null
    customFileName.error = null
    additionalDirs.error = null

    when (currentImageSaverSetting.imageNameOptions) {
      ImageSaverV2Options.ImageNameOptions.UseServerFileName.rawValue -> {
        imageNameOptionsGroup.check(R.id.image_name_options_use_server_name)
      }
      ImageSaverV2Options.ImageNameOptions.UseOriginalFileName.rawValue -> {
        imageNameOptionsGroup.check(R.id.image_name_options_use_original_name)
      }
    }

    when (currentImageSaverSetting.duplicatesResolution) {
      ImageSaverV2Options.DuplicatesResolution.AskWhatToDo.rawValue -> {
        duplicatesResolutionOptionsGroup.check(R.id.duplicate_resolution_options_ask)
      }
      ImageSaverV2Options.DuplicatesResolution.Overwrite.rawValue -> {
        duplicatesResolutionOptionsGroup.check(R.id.duplicate_resolution_options_overwrite)
      }
      ImageSaverV2Options.DuplicatesResolution.Skip.rawValue -> {
        duplicatesResolutionOptionsGroup.check(R.id.duplicate_resolution_options_skip)
      }
    }

    val chanPostImage = options.chanPostImageOrNull()

    val currentFileNameString = if (chanPostImage != null) {
      val currentFileNameString = if (overriddenFileName != null) {
        overriddenFileName
      } else {
        when (currentImageSaverSetting.imageNameOptions) {
          ImageSaverV2Options.ImageNameOptions.UseServerFileName.rawValue -> {
            chanPostImage.serverFilename
          }
          ImageSaverV2Options.ImageNameOptions.UseOriginalFileName.rawValue -> {
            chanPostImage.filename
          }
          else -> throw IllegalArgumentException("Unknown imageNameOptions: ${currentImageSaverSetting.imageNameOptions}")
        }
      }

      customFileName.doIgnoringTextWatcher(fileNameTextWatcher) {
        text?.replace(0, text!!.length, currentFileNameString)
      }

      currentFileNameString
    } else {
      null
    }

    appendSiteName.isChecked = currentImageSaverSetting.appendSiteName
    appendBoardCode.isChecked = currentImageSaverSetting.appendBoardCode
    appendThreadId.isChecked = currentImageSaverSetting.appendThreadId

    val rootDirectoryUriString = currentImageSaverSetting.rootDirectoryUri
    if (rootDirectoryUriString.isNullOrBlank()) {
      rootDir.textSize = 20f
      rootDir.text = context.getString(R.string.controller_image_save_options_click_to_set_root_dir)
    } else {
      rootDir.text = rootDirectoryUriString
      rootDir.textSize = 14f
    }

    val subDirsString = currentImageSaverSetting.subDirs

    if (subDirsString != null && !checkSubDirsValid(subDirsString)) {
      additionalDirs.error = context.getString(R.string.controller_image_save_options_sub_dirs_are_not_valid)
      enableDisableSaveButton(enable = false)
      return
    }

    additionalDirs.doIgnoringTextWatcher(additionalDirsTextWatcher) {
      text?.replace(0, text!!.length, subDirsString ?: "")
    }

    if (rootDirectoryUriString.isNullOrBlank()) {
      outputFile.error = context.getString(R.string.controller_image_save_options_root_dir_not_set)
      enableDisableSaveButton(enable = false)
      return
    }

    val outputDirText = buildString {
      append("<Root dir>")

      if (currentImageSaverSetting.appendSiteName) {
        append("\\<Site name>")
      }
      if (currentImageSaverSetting.appendBoardCode) {
        append("\\<Board code>")
      }
      if (currentImageSaverSetting.appendThreadId) {
        append("\\<Thread id>")
      }
      if (subDirsString.isNotNullNorBlank()) {
        append("\\${subDirsString}")
      }

      if (currentFileNameString != null && chanPostImage != null) {
        append("\\${currentFileNameString}")
        append(".")
        append(chanPostImage.extension)
      }
    }

    outputFile.setText(outputDirText)

    if (chanPostImage != null) {
      if (currentFileNameString.isNullOrBlank()) {
        customFileName.error = context.getString(R.string.controller_image_save_options_file_name_is_blank)
        enableDisableSaveButton(enable = false)
        return
      }

      if (fileNameContainsBadSymbols(currentFileNameString)) {
        customFileName.error = context.getString(R.string.controller_image_save_options_file_name_bad_symbols)
        enableDisableSaveButton(enable = false)
        return
      }

      // fallthrough
    }

    enableDisableSaveButton(enable = true)
  }

  private fun enableDisableSaveButton(enable: Boolean) {
    saveButton.isEnabled = enable
    saveButton.isClickable = enable
    saveButton.isFocusable = enable
  }

  sealed class Options {
    fun chanPostImageOrNull(): ChanPostImage? {
      return when (this) {
        is SingleImage -> chanPostImage
        is ResultDirAccessProblems -> null
        is MultipleImages -> null
      }
    }

    data class SingleImage(
      val chanPostImage: ChanPostImage,
      val onSaveClicked: (ImageSaverV2Options, String?) -> Unit
    ) : Options()

    data class MultipleImages(
      val onSaveClicked: (ImageSaverV2Options) -> Unit
    ) : Options()

    data class ResultDirAccessProblems(
      val uniqueId: String,
      val onRetryClicked: (ImageSaverV2Options) -> Unit,
      val onCancelClicked: () -> Unit
    ) : Options()
  }

  companion object {
    private val DESIRED_WIDTH = dp(600f)
  }

}