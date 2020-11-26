package com.github.k1rakishou.chan.ui.helper.picker

import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.base.SerializedCoroutineExecutor
import com.github.k1rakishou.chan.core.cache.CacheHandler
import com.github.k1rakishou.chan.core.cache.FileCacheListener
import com.github.k1rakishou.chan.core.cache.FileCacheV2
import com.github.k1rakishou.chan.core.manager.ReplyManager
import com.github.k1rakishou.chan.features.reply.data.ReplyFile
import com.github.k1rakishou.chan.utils.IOUtils
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.fsaf.FileManager
import com.github.k1rakishou.fsaf.file.RawFile
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.IOException
import kotlin.coroutines.resume

class RemoteFilePicker(
  appConstants: AppConstants,
  fileManager: FileManager,
  replyManager: ReplyManager,
  private val appScope: CoroutineScope,
  private val fileCacheV2: FileCacheV2,
  private val cacheHandler: CacheHandler
) : AbstractFilePicker<RemoteFilePicker.RemoteFilePickerInput>(appConstants, replyManager, fileManager) {
  private val serializedCoroutineExecutor = SerializedCoroutineExecutor(appScope)

  override suspend fun pickFile(filePickerInput: RemoteFilePickerInput): ModularResult<PickedFile> {
    if (filePickerInput.imageUrl.isEmpty()) {
      return ModularResult.error(FilePickerError.BadUrl(filePickerInput.imageUrl))
    }

    val imageUrl = filePickerInput.imageUrl.toHttpUrlOrNull()
    if (imageUrl == null) {
      return ModularResult.error(FilePickerError.BadUrl(filePickerInput.imageUrl))
    }

    return withContext(Dispatchers.IO) {
      val downloadedFileMaybe = downloadFile(
        imageUrl = imageUrl,
        showLoadingView = filePickerInput.showLoadingView,
        hideLoadingView = filePickerInput.hideLoadingView
      )

      if (downloadedFileMaybe is ModularResult.Error) {
        val error = FilePickerError.FailedToDownloadFile(
          filePickerInput.imageUrl,
          downloadedFileMaybe.error
        )

        return@withContext ModularResult.error(error)
      }

      val downloadedFile = (downloadedFileMaybe as ModularResult.Value).value

      val fileName = imageUrl.pathSegments.lastOrNull()
        ?: DEFAULT_FILE_NAME

      val copyResult = copyDownloadedFileToReplyFileStorage(
        downloadedFile,
        fileName,
        filePickerInput.replyChanDescriptor
      )

      if (copyResult is ModularResult.Error) {
        return@withContext ModularResult.error(FilePickerError.UnknownError(copyResult.error))
      }

      return@withContext copyResult
    }
  }

  private fun copyDownloadedFileToReplyFileStorage(
    downloadedFile: RawFile,
    originalFileName: String,
    replyChanDescriptor: ChanDescriptor
  ): ModularResult<PickedFile> {
    return ModularResult.Try {
      val reply = replyManager.getReplyOrNull(replyChanDescriptor)
      if (reply == null) {
        return@Try PickedFile.Failure(FilePickerError.NoReplyFound(replyChanDescriptor))
      }

      val uniqueFileName = replyManager.generateUniqueFileName(appConstants)

      val replyFile = replyManager.createNewEmptyAttachFile(
        uniqueFileName,
        originalFileName,
        System.currentTimeMillis()
      )

      if (replyFile == null) {
        return@Try PickedFile.Failure(FilePickerError.FailedToGetAttachFile())
      }

      val fileUuid = replyFile.getReplyFileMeta().valueOrNull()?.fileUuid
      if (fileUuid == null) {
        return@Try PickedFile.Failure(FilePickerError.FailedToCreateFileMeta())
      }

      copyDownloadedFileIntoReplyFile(downloadedFile, replyFile)
      cacheHandler.deleteCacheFile(downloadedFile)

      return@Try PickedFile.Result(listOf(replyFile))
    }
  }

  private fun copyDownloadedFileIntoReplyFile(
    downloadedFile: RawFile,
    replyFile: ReplyFile
  ) {
    val cacheFile = fileManager.fromRawFile(replyFile.fileOnDisk)

    val input = fileManager.getInputStream(downloadedFile)
    if (input == null) {
      throw IOException("Failed to get input stream (downloadedFile='${downloadedFile.getFullPath()}')")
    }

    val output = fileManager.getOutputStream(cacheFile)
    if (output == null) {
      throw IOException("Failed to get output stream (filePath='${cacheFile.getFullPath()}')")
    }

    input.use { inputStream ->
      output.use { outputStream ->
        if (!IOUtils.copy(inputStream, outputStream, MAX_FILE_SIZE)) {
          throw IOException(
            "Failed to copy downloaded file (downloadedFile='${downloadedFile.getFullPath()}') " +
              "into reply file (filePath='${cacheFile.getFullPath()}')"
          )
        }
      }
    }
  }

  private suspend fun downloadFile(
    imageUrl: HttpUrl,
    showLoadingView: suspend (Int) -> Unit,
    hideLoadingView: suspend () -> Unit
  ): ModularResult<RawFile> {
    val urlString = imageUrl.toString()

    serializedCoroutineExecutor.post {
      showLoadingView.invoke(R.string.downloading_file)
    }

    return suspendCancellableCoroutine { cancellableContinuation ->
      val cancelableDownload = fileCacheV2.enqueueNormalDownloadFileRequest(
        urlString,
        object : FileCacheListener() {
          override fun onSuccess(file: RawFile) {
            super.onSuccess(file)

            cancellableContinuation.resume(ModularResult.value(file))
          }

          override fun onNotFound() {
            super.onNotFound()

            onError(FilePickerError.FileNotFound(urlString))
          }

          override fun onFail(exception: Exception) {
            super.onFail(exception)

            onError(FilePickerError.UnknownError(exception))
          }

          override fun onCancel() {
            super.onCancel()

            onError(FilePickerError.Canceled())
          }

          override fun onEnd() {
            super.onEnd()

            serializedCoroutineExecutor.post {
              hideLoadingView.invoke()
            }
          }

          private fun onError(error: FilePickerError) {
            cancellableContinuation.resume(ModularResult.error(error))
          }
        })

      cancellableContinuation.invokeOnCancellation { cancelableDownload?.cancel() }
    }
  }

  data class RemoteFilePickerInput(
    val replyChanDescriptor: ChanDescriptor,
    val imageUrl: String,
    val showLoadingView: suspend (Int) -> Unit,
    val hideLoadingView: suspend () -> Unit
  )

}