package com.github.k1rakishou.chan.features.settings.screens.delegate.base_directory

import android.content.Context
import android.net.Uri
import android.widget.Toast
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.settings.ChanSettings
import com.github.k1rakishou.chan.ui.settings.base_directory.SavedFilesBaseDirectory
import com.github.k1rakishou.chan.utils.AndroidUtils.getString
import com.github.k1rakishou.chan.utils.AndroidUtils.showToast
import com.github.k1rakishou.chan.utils.BackgroundUtils.runOnMainThread
import com.github.k1rakishou.chan.utils.Logger
import com.github.k1rakishou.fsaf.FileChooser
import com.github.k1rakishou.fsaf.FileManager
import com.github.k1rakishou.fsaf.TraverseMode
import com.github.k1rakishou.fsaf.callback.directory.PermanentDirectoryChooserCallback
import com.github.k1rakishou.fsaf.file.AbstractFile
import java.util.concurrent.Executors

class MediaSettingsControllerPresenter(
  private val fileManager: FileManager,
  private val fileChooser: FileChooser,
  private var context: Context
) {
  private val fileCopyingExecutor = Executors.newSingleThreadExecutor()
  private var callbacks: SharedLocationSetupDelegateCallbacks? = null
  private var initialized = false

  fun onCreate(callbacks: SharedLocationSetupDelegateCallbacks) {
    check(!initialized) { "Already initialized!" }

    this.callbacks = callbacks
    initialized = true
  }

  fun onDestroy() {
    check(initialized) { "Already destroyed!" }

    callbacks = null
    fileCopyingExecutor.shutdown()
    initialized = false
  }

  /**
   * Select a directory where saved images will be stored via the SAF
   */
  fun onSaveLocationUseSAFClicked() {
    fileChooser.openChooseDirectoryDialog(object : PermanentDirectoryChooserCallback() {
      override fun onResult(uri: Uri) {
        val oldSavedFileBaseDirectory =
          fileManager.newBaseDirectoryFile<SavedFilesBaseDirectory>()

        if (fileManager.isBaseDirAlreadyRegistered<SavedFilesBaseDirectory>(uri)) {
          showToast(context, R.string.media_settings_base_directory_is_already_registered)
          return
        }

        Logger.d(TAG, "onSaveLocationUseSAFClicked dir = $uri")
        ChanSettings.saveLocation.setSafBaseDir(uri)
        ChanSettings.saveLocation.resetFileDir()

        withCallbacks {
          updateSaveLocationViewText(uri.toString())
        }

        val newSavedFilesBaseDirectory =
          fileManager.newBaseDirectoryFile<SavedFilesBaseDirectory>()

        if (newSavedFilesBaseDirectory == null) {
          showToast(context, R.string.media_settings_new_saved_files_base_dir_not_registered)
          return
        }

        withCallbacks {
          askUserIfTheyWantToMoveOldSavedFilesToTheNewDirectory(
            oldSavedFileBaseDirectory,
            newSavedFilesBaseDirectory
          )
        }
      }

      override fun onCancel(reason: String) {
        showToast(context, reason, Toast.LENGTH_LONG)
      }
    })
  }

  fun onSaveLocationChosen(dirPath: String) {
    if (fileManager.isBaseDirAlreadyRegistered<SavedFilesBaseDirectory>(dirPath)) {
      showToast(context, R.string.media_settings_base_directory_is_already_registered)
      return
    }

    // It is important to get old base directory before assigning a new one, i.e. before
    // ChanSettings.saveLocation.setFileBaseDir(dirPath)
    val oldSaveFilesDirectory = fileManager.newBaseDirectoryFile<SavedFilesBaseDirectory>()

    Logger.d(TAG, "onSaveLocationChosen dir = $dirPath")
    ChanSettings.saveLocation.setFileBaseDir(dirPath)

    withCallbacks {
      updateSaveLocationViewText(dirPath)
    }

    if (oldSaveFilesDirectory == null) {
      showToast(context, R.string.done)
      return
    }

    val newSaveFilesDirectory = fileManager.newBaseDirectoryFile<SavedFilesBaseDirectory>()
    if (newSaveFilesDirectory == null) {
      showToast(context, R.string.media_settings_new_saved_files_base_dir_not_registered)
      return
    }

    withCallbacks {
      askUserIfTheyWantToMoveOldSavedFilesToTheNewDirectory(
        oldSaveFilesDirectory,
        newSaveFilesDirectory
      )
    }
  }

  fun moveOldFilesToTheNewDirectory(
    oldBaseDirectory: AbstractFile?,
    newBaseDirectory: AbstractFile?
  ) {
    if (oldBaseDirectory == null || newBaseDirectory == null) {
      Logger.e(TAG, "One of the directories is null, cannot copy " +
        "(oldBaseDirectory is null == " + (oldBaseDirectory == null) + ")" +
        ", newLocalThreadsDirectory is null == " + (newBaseDirectory == null) + ")")
      return
    }

    Logger.d(TAG,
      "oldLocalThreadsDirectory = " + oldBaseDirectory.getFullPath()
        + ", newLocalThreadsDirectory = " + newBaseDirectory.getFullPath()
    )

    var filesCount = 0

    fileManager.traverseDirectory(oldBaseDirectory, true, TraverseMode.OnlyFiles) {
      ++filesCount
    }

    if (filesCount == 0) {
      showToast(context, R.string.media_settings_no_files_to_copy)
      return
    }

    withCallbacks {
      showCopyFilesDialog(filesCount, oldBaseDirectory, newBaseDirectory)
    }
  }

  fun moveFilesInternal(
    oldBaseDirectory: AbstractFile,
    newBaseDirectory: AbstractFile
  ) {
    fileCopyingExecutor.execute {
      val result = fileManager.copyDirectoryWithContent(
        oldBaseDirectory,
        newBaseDirectory,
        true
      ) { fileIndex, totalFilesCount ->
        if (callbacks == null) {
          // User left the MediaSettings screen, we need to cancel the file copying
          return@copyDirectoryWithContent true
        }

        withCallbacks {
          updateLoadingViewText(getString(
            R.string.media_settings_copying_file,
            fileIndex,
            totalFilesCount
          ))
        }

        return@copyDirectoryWithContent false
      }

      withCallbacks {
        onCopyDirectoryEnded(
          oldBaseDirectory,
          newBaseDirectory,
          result
        )
      }
    }
  }

  private fun withCallbacks(func: SharedLocationSetupDelegateCallbacks.() -> Unit) {
    runOnMainThread {
      callbacks?.let {
        func(it)
      }
    }
  }

  fun resetSaveLocationBaseDir() {
    ChanSettings.saveLocation.resetActiveDir()
    ChanSettings.saveLocation.resetFileDir()
    ChanSettings.saveLocation.resetSafDir()
  }

  companion object {
    private const val TAG = "MediaSettingsControllerPresenter"
  }
}