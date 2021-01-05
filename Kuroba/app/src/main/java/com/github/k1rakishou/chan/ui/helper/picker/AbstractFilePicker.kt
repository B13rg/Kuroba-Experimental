package com.github.k1rakishou.chan.ui.helper.picker

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.github.k1rakishou.chan.core.manager.ReplyManager
import com.github.k1rakishou.chan.features.reply.data.ReplyFile
import com.github.k1rakishou.chan.utils.IOUtils
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.fsaf.FileManager
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import java.io.FileInputStream
import java.io.IOException

abstract class AbstractFilePicker<T>(
  protected val appConstants: AppConstants,
  protected val replyManager: ReplyManager,
  protected val fileManager: FileManager
) {
  abstract suspend fun pickFile(filePickerInput: T): ModularResult<PickedFile>

  protected fun copyExternalFileToReplyFileStorage(
    appContext: Context,
    externalFileUri: Uri,
    addedOn: Long
  ): ModularResult<ReplyFile> {
    return ModularResult.Try {
      val uniqueFileName = replyManager.generateUniqueFileName(appConstants)
      val originalFileName = tryExtractFileNameOrDefault(externalFileUri, appContext)

      val replyFile = replyManager.createNewEmptyAttachFile(
        uniqueFileName,
        originalFileName,
        addedOn
      )

      if (replyFile == null) {
        throw IOException("Failed to get attach file")
      }

      val fileUuid = replyFile.getReplyFileMeta().valueOrNull()?.fileUuid
      if (fileUuid == null) {
        throw IOException("Failed to get file meta")
      }

      try {
        copyExternalFileIntoReplyFile(appContext, externalFileUri, replyFile)
      } catch (error: Throwable) {
        replyFile.deleteFromDisk()
        throw error
      }

      return@Try replyFile
    }
  }

  private fun tryExtractFileNameOrDefault(uri: Uri, appContext: Context): String {
    var fileName = appContext.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
      val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
      if (nameIndex > -1 && cursor.moveToFirst()) {
        return@use cursor.getString(nameIndex)
      }

      return@use null
    }

    if (fileName == null) {
      // As per the comment on OpenableColumns.DISPLAY_NAME:
      // If this is not provided then the name should default to the last segment
      // of the file's URI.
      fileName = uri.lastPathSegment
        ?: getDefaultFileName()
    }

    return fileName
  }

  protected fun getDefaultFileName(): String {
    return System.nanoTime().toString()
  }

  private fun copyExternalFileIntoReplyFile(
    appContext: Context,
    uri: Uri,
    replyFile: ReplyFile
  ) {
    val cacheFile = fileManager.fromRawFile(replyFile.fileOnDisk)
    val contentResolver = appContext.contentResolver

    contentResolver.openFileDescriptor(uri, "r").use { fileDescriptor ->
      if (fileDescriptor == null) {
        throw IOException("Couldn't open file descriptor for uri = $uri")
      }

      FileInputStream(fileDescriptor.fileDescriptor).use { inputStream ->
        val os = fileManager.getOutputStream(cacheFile)
        if (os == null) {
          throw IOException("Failed to get output stream (filePath='${cacheFile.getFullPath()}')")
        }

        os.use { outputStream ->
          if (!IOUtils.copy(inputStream, outputStream, MAX_FILE_SIZE)) {
            throw IOException(
              "Failed to copy external file (uri='$uri') into reply file " +
                "(filePath='${cacheFile.getFullPath()}')"
            )
          }
        }
      }
    }
  }

  sealed class FilePickerError(message: String) : Exception(message) {
    // Common errors
    class UnknownError(cause: Throwable) : FilePickerError("Unknown error: ${cause.errorMessageOrClassName()}")
    class Canceled : FilePickerError("Canceled")
    class NotImplemented : FilePickerError("Not implemented")
    class NoReplyFound(chanDescriptor: ChanDescriptor) : FilePickerError("No reply found for chanDescriptor='$chanDescriptor'")

    // Local errors
    class ActivityIsNotSet : FilePickerError("Activity is not set")
    class NoFilePickersFound : FilePickerError("No file picker applications were found")
    class BadResultCode(code: Int) : FilePickerError("Bad result code (not OK) code='$code'")
    class NoDataReturned : FilePickerError("Picked activity returned no data back")
    class FailedToExtractUri : FilePickerError("Failed to extract uri from returned intent")
    class FailedToGetAttachFile : FilePickerError("Failed to get attach file")
    class FailedToCreateFileMeta : FilePickerError("Failed to create file meta information")
    class FailedToReadFileMeta : FilePickerError("Failed to read file meta information")
    class FailedToAddNewReplyFileIntoStorage : FilePickerError("Failed to add new reply file into reply storage")

    // Remote errors
    class BadUrl(url: String) : FilePickerError("Bad url \"$url\"")
    class FileNotFound(url: String) : FilePickerError("Remote file \"$url\" not found")
    class FailedToDownloadFile(url: String, reason: Throwable) : FilePickerError("Failed to download file \"$url\", reason: ${reason.errorMessageOrClassName()}")

    // Sharing errors
    class UnknownIntent : FilePickerError("Unknown intent")
    class NoUrisFoundInIntent : FilePickerError("No Uris found in intent")
  }

  companion object {
    const val MAX_FILE_SIZE = 50 * 1024 * 1024.toLong()
  }
}