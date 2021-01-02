package com.github.k1rakishou.chan.ui.helper.picker

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import coil.size.Scale
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.image.ImageLoaderV2
import com.github.k1rakishou.chan.core.manager.ReplyManager
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.core_logger.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import java.util.*

class ImagePickHelper(
  private val appContext: Context,
  private val replyManager: ReplyManager,
  private val imageLoaderV2: ImageLoaderV2,
  private val shareFilePicker: ShareFilePicker,
  private val localFilePicker: LocalFilePicker,
  private val remoteFilePicker: RemoteFilePicker
) {
  private val pickedFilesUpdatesState = MutableSharedFlow<UUID>()

  fun listenForNewPickedFiles(): Flow<UUID> {
    return pickedFilesUpdatesState.asSharedFlow()
  }

  suspend fun pickFilesFromIntent(
    filePickerInput: ShareFilePicker.ShareFilePickerInput
  ): ModularResult<PickedFile> {
    val result = shareFilePicker.pickFile(filePickerInput)
    if (result is ModularResult.Error) {
      Logger.e(TAG, "pickFilesFromIntent() error", result.error)
      return result
    }

    val pickedFile = (result as ModularResult.Value).value
    if (pickedFile is PickedFile.Failure) {
      if (pickedFile.reason is AbstractFilePicker.FilePickerError.BadResultCode) {
        Logger.e(TAG, "pickFilesFromIntent() pickedFile is " +
          "PickedFile.BadResultCode: ${pickedFile.reason.errorMessageOrClassName()}")
      } else {
        Logger.e(TAG, "pickFilesFromIntent() pickedFile is PickedFile.Failure", pickedFile.reason)
      }

      return result
    }

    val sharedFiles = (pickedFile as PickedFile.Result).replyFiles

    sharedFiles.forEach { sharedFile ->
      val replyFileMeta = sharedFile.getReplyFileMeta().safeUnwrap { error ->
        Logger.e(TAG, "pickFilesFromIntent() replyFile.getReplyFileMeta() error", error)
        sharedFile.deleteFromDisk()
        return@forEach
      }

      if (!replyManager.addNewReplyFileIntoStorage(sharedFile)) {
        Logger.e(TAG, "pickFilesFromIntent() addNewReplyFileIntoStorage() failure")
        sharedFile.deleteFromDisk()

        return@forEach
      }

      withContext(Dispatchers.IO) {
        imageLoaderV2.calculateFilePreviewAndStoreOnDisk(
          appContext,
          replyFileMeta.fileUuid,
          Scale.FILL
        )
      }

      Logger.d(TAG, "pickFilesFromIntent() success! Picked new local file " +
        "with UUID='${replyFileMeta.fileUuid}'")

      pickedFilesUpdatesState.emit(replyFileMeta.fileUuid)
    }

    return result
  }

  suspend fun pickLocalFile(
    filePickerInput: LocalFilePicker.LocalFilePickerInput
  ): ModularResult<PickedFile> {
    // We can only pick one local file at a time (for now)
    val result = localFilePicker.pickFile(filePickerInput)
    if (result is ModularResult.Error) {
      Logger.e(TAG, "pickLocalFile() error", result.error)
      return result
    }

    val pickedFile = (result as ModularResult.Value).value
    if (pickedFile is PickedFile.Failure) {
      if (pickedFile.reason is AbstractFilePicker.FilePickerError.BadResultCode) {
        Logger.e(TAG, "pickLocalFile() pickedFile is " +
          "PickedFile.BadResultCode: ${pickedFile.reason.errorMessageOrClassName()}")
      } else {
        Logger.e(TAG, "pickLocalFile() pickedFile is PickedFile.Failure", pickedFile.reason)
      }

      return result
    }

    val replyFile = (pickedFile as PickedFile.Result).replyFiles.first()

    val replyFileMeta = replyFile.getReplyFileMeta().safeUnwrap { error ->
      Logger.e(TAG, "pickLocalFile() replyFile.getReplyFileMeta() error", error)

      replyFile.deleteFromDisk()

      val pickedFileFailure =
        PickedFile.Failure(AbstractFilePicker.FilePickerError.FailedToReadFileMeta())

      return ModularResult.value(pickedFileFailure)
    }

    if (!replyManager.addNewReplyFileIntoStorage(replyFile)) {
      Logger.e(TAG, "pickLocalFile() addNewReplyFileIntoStorage() failure")

      replyFile.deleteFromDisk()

      val pickedFileFailure =
        PickedFile.Failure(AbstractFilePicker.FilePickerError.FailedToAddNewReplyFileIntoStorage())

      return ModularResult.value(pickedFileFailure)
    }

    filePickerInput.showLoadingView()

    try {
      withContext(Dispatchers.IO) {
        imageLoaderV2.calculateFilePreviewAndStoreOnDisk(
          appContext,
          replyFileMeta.fileUuid,
          Scale.FILL
        )
      }
    } finally {
      filePickerInput.hideLoadingView()
    }

    Logger.d(TAG, "pickLocalFile() success! Picked new local file " +
      "with UUID='${replyFileMeta.fileUuid}'")

    pickedFilesUpdatesState.emit(replyFileMeta.fileUuid)

    return result
  }

  suspend fun pickRemoteFile(
    filePickerInput: RemoteFilePicker.RemoteFilePickerInput
  ): ModularResult<PickedFile> {
    val result = remoteFilePicker.pickFile(filePickerInput)
    if (result is ModularResult.Error) {
      Logger.e(TAG, "pickRemoteFile() error", result.error)
      return result
    }

    val pickedFile = (result as ModularResult.Value).value
    if (pickedFile is PickedFile.Failure) {
      if (pickedFile.reason is AbstractFilePicker.FilePickerError.BadResultCode) {
        Logger.e(TAG, "pickRemoteFile() pickedFile is " +
          "PickedFile.BadResultCode: ${pickedFile.reason.errorMessageOrClassName()}")
      } else {
        Logger.e(TAG, "pickRemoteFile() pickedFile is PickedFile.Failure", pickedFile.reason)
      }

      return result
    }

    val replyFile = (pickedFile as PickedFile.Result).replyFiles.first()

    val replyFileMeta = replyFile.getReplyFileMeta().safeUnwrap { error ->
      Logger.e(TAG, "pickRemoteFile() replyFile.getReplyFileMeta() error", error)

      replyFile.deleteFromDisk()

      val pickedFileFailure =
        PickedFile.Failure(AbstractFilePicker.FilePickerError.FailedToReadFileMeta())

      return ModularResult.value(pickedFileFailure)
    }

    if (!replyManager.addNewReplyFileIntoStorage(replyFile)) {
      Logger.e(TAG, "pickRemoteFile() addNewReplyFileIntoStorage() failure")

      replyFile.deleteFromDisk()

      val pickedFileFailure =
        PickedFile.Failure(AbstractFilePicker.FilePickerError.FailedToAddNewReplyFileIntoStorage())

      return ModularResult.value(pickedFileFailure)
    }

    filePickerInput.showLoadingView(R.string.decoding_reply_file_preview)

    try {
      withContext(Dispatchers.IO) {
        imageLoaderV2.calculateFilePreviewAndStoreOnDisk(
          appContext,
          replyFileMeta.fileUuid,
          Scale.FILL
        )
      }
    } finally {
      filePickerInput.hideLoadingView()
    }

    Logger.d(TAG, "pickRemoteFile() success! Picked new remote file with UUID='${replyFileMeta.fileUuid}'")
    pickedFilesUpdatesState.emit(replyFileMeta.fileUuid)

    return result
  }

  fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    localFilePicker.onActivityResult(requestCode, resultCode, data)
  }

  fun onActivityCreated(activity: AppCompatActivity) {
    localFilePicker.onActivityCreated(activity)
  }

  fun onActivityDestroyed() {
    localFilePicker.onActivityDestroyed()
  }

  companion object {
    private const val TAG = "ImagePickHelper"
  }

}