package com.github.k1rakishou.chan.features.image_saver

import android.net.Uri
import androidx.annotation.GuardedBy
import androidx.core.app.NotificationManagerCompat
import com.github.k1rakishou.chan.core.base.SerializedCoroutineExecutor
import com.github.k1rakishou.chan.core.base.okhttp.RealDownloaderOkHttpClient
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.doIoTaskWithAttempts
import com.github.k1rakishou.common.isNotNullNorBlank
import com.github.k1rakishou.common.isOutOfDiskSpaceError
import com.github.k1rakishou.common.suspendCall
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.fsaf.FileManager
import com.github.k1rakishou.fsaf.file.AbstractFile
import com.github.k1rakishou.fsaf.file.DirectorySegment
import com.github.k1rakishou.fsaf.file.FileSegment
import com.github.k1rakishou.fsaf.file.Segment
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.download.ImageDownloadRequest
import com.github.k1rakishou.model.data.post.ChanPostImage
import com.github.k1rakishou.model.repository.ChanPostImageRepository
import com.github.k1rakishou.model.repository.ImageDownloadRequestRepository
import com.github.k1rakishou.persist_state.ImageSaverV2Options
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.IOException
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@OptIn(ObsoleteCoroutinesApi::class)
class ImageSaverV2ServiceDelegate(
  private val verboseLogs: Boolean,
  private val appScope: CoroutineScope,
  private val appConstants: AppConstants,
  private val downloaderOkHttpClient: RealDownloaderOkHttpClient,
  private val notificationManagerCompat: NotificationManagerCompat,
  private val fileManager: FileManager,
  private val chanPostImageRepository: ChanPostImageRepository,
  private val imageDownloadRequestRepository: ImageDownloadRequestRepository
) {
  private val mutex = Mutex()

  @GuardedBy("mutex")
  private val activeDownloads = hashMapOf<String, DownloadContext>()

  @GuardedBy("mutex")
  private val activeNotificationIdQueue = LinkedList<String>()

  private val serializedCoroutineExecutor = SerializedCoroutineExecutor(appScope)

  private val notificationUpdatesFlow = MutableSharedFlow<ImageSaverDelegateResult>(
    extraBufferCapacity = 128,
    onBufferOverflow = BufferOverflow.SUSPEND
  )

  private val stopServiceFlow = MutableSharedFlow<Unit>(
    extraBufferCapacity = 1,
    onBufferOverflow = BufferOverflow.DROP_OLDEST
  )

  fun listenForNotificationUpdates(): SharedFlow<ImageSaverDelegateResult> {
    return notificationUpdatesFlow.asSharedFlow()
  }

  fun listenForStopServiceEvent(): SharedFlow<Unit> {
    return stopServiceFlow.asSharedFlow()
  }

  suspend fun deleteDownload(uniqueId: String) {
    ImageSaverV2Service.cancelNotification(notificationManagerCompat, uniqueId)

    imageDownloadRequestRepository.deleteByUniqueId(uniqueId)
      .peekError { error -> Logger.e(TAG, "imageDownloadRequestRepository.deleteByUniqueId($uniqueId) error", error) }
      .ignore()

    mutex.withLock {
      activeNotificationIdQueue.remove(uniqueId)
      activeDownloads.remove(uniqueId)
    }
  }

  suspend fun cancelDownload(uniqueId: String) {
    ImageSaverV2Service.cancelNotification(notificationManagerCompat, uniqueId)
    mutex.withLock { activeDownloads[uniqueId]?.cancel() }
  }

  suspend fun createDownloadContext(uniqueId: String): Int {
    return mutex.withLock {
      if (!activeDownloads.containsKey(uniqueId)) {
        activeDownloads[uniqueId] = DownloadContext()
      }

      // Otherwise the download already exist, just wait until it's completed. But this shouldn't
      // really happen since we always check duplicate requests in the database before even starting
      // the service.

      return@withLock activeDownloads.size
    }
  }

  fun downloadImages(imageDownloadInputData: ImageSaverV2Service.ImageDownloadInputData) {
    serializedCoroutineExecutor.post {
      try {
        val activeDownloadsCountAfter = withContext(Dispatchers.Default) {
          downloadImagesInternal(imageDownloadInputData)
        }

        if (verboseLogs) {
          Logger.d(TAG, "onStartCommand() end, activeDownloadsCount=$activeDownloadsCountAfter")
        }

        if (activeDownloadsCountAfter <= 0) {
          Logger.d(TAG, "onStartCommand() stopping service")
          stopServiceFlow.emit(Unit)
        }
      } catch (error: Throwable) {
        Logger.e(TAG, "Unhandled exception", error)
      }
    }
  }

  private suspend fun downloadImagesInternal(imageDownloadInputData: ImageSaverV2Service.ImageDownloadInputData): Int {
    BackgroundUtils.ensureBackgroundThread()

    val outputDirUri = AtomicReference<Uri>(null)
    val firstChanPostImage = AtomicReference<ChanPostImage>(null)
    val hasResultDirAccessErrors = AtomicBoolean(false)
    val hasOutOfDiskSpaceErrors = AtomicBoolean(false)
    val completedRequests = AtomicInteger(0)
    val failedRequests = AtomicInteger(0)
    val duplicates = AtomicInteger(0)
    val canceledRequests = AtomicInteger(0)

    val imageDownloadRequests = when (imageDownloadInputData) {
      is ImageSaverV2Service.SingleImageDownloadInputData -> {
        listOf(imageDownloadInputData.imageDownloadRequest)
      }
      is ImageSaverV2Service.BatchImageDownloadInputData -> {
        imageDownloadInputData.imageDownloadRequests
      }
      else -> {
        throw IllegalArgumentException(
          "Unknown imageDownloadInputData: " +
            imageDownloadInputData.javaClass.simpleName
        )
      }
    }

    try {
      val canceled = getDownloadContext(imageDownloadInputData)?.isCanceled() ?: true
      if (canceled) {
        // Canceled, no need to send even here because we do that in try/finally block
        return mutex.withLock { activeDownloads.size }
      }

      Logger.d(TAG, "downloadImagesInternal() " +
        "imageDownloadInputData=${imageDownloadInputData.javaClass.simpleName}, " +
        "imagesCount=${imageDownloadInputData.requestsCount()}")

      handleNewNotificationId(imageDownloadInputData.uniqueId)

      // Start event
      emitNotificationUpdate(
        uniqueId = imageDownloadInputData.uniqueId,
        imageSaverOptionsJson = imageDownloadInputData.imageSaverOptionsJson,
        completed = false,
        notificationSummary = null,
        totalImagesCount = imageDownloadInputData.requestsCount(),
        canceledRequests = canceledRequests.get(),
        completedRequests = completedRequestsToDownloadedImagesResult(
          completedRequests,
          outputDirUri
        ),
        duplicates = duplicates.get(),
        failedRequests = failedRequests.get(),
        hasResultDirAccessErrors = hasResultDirAccessErrors.get(),
        hasOutOfDiskSpaceErrors = hasOutOfDiskSpaceErrors.get()
      )

      val concurrency = when {
        imageDownloadRequests.size > 128 -> 8
        imageDownloadRequests.size > 64 -> 6
        else -> 4
      }

      supervisorScope {
        imageDownloadRequests
          .chunked(concurrency)
          .forEach { imageDownloadRequestBatch ->
            val updatedImageDownloadRequestBatch = imageDownloadRequestBatch.map { imageDownloadRequest ->
              return@map appScope.async(Dispatchers.IO) {
                return@async downloadSingleImage(
                  imageDownloadInputData,
                  imageDownloadRequest,
                  hasResultDirAccessErrors,
                  hasOutOfDiskSpaceErrors,
                  firstChanPostImage,
                  canceledRequests,
                  duplicates,
                  failedRequests,
                  outputDirUri,
                  completedRequests
                )
              }
            }.awaitAll()

            imageDownloadRequestRepository.completeMany(updatedImageDownloadRequestBatch)
              .peekError { error -> Logger.e(TAG, "imageDownloadRequestRepository.updateMany() error", error) }
              .ignore()

            val notificationSummary =
              extractNotificationSummaryText(firstChanPostImage, imageDownloadRequests)

            // Progress event
            emitNotificationUpdate(
              uniqueId = imageDownloadInputData.uniqueId,
              imageSaverOptionsJson = imageDownloadInputData.imageSaverOptionsJson,
              completed = false,
              notificationSummary = notificationSummary,
              totalImagesCount = imageDownloadInputData.requestsCount(),
              canceledRequests = canceledRequests.get(),
              completedRequests = completedRequestsToDownloadedImagesResult(
                completedRequests,
                outputDirUri
              ),
              duplicates = duplicates.get(),
              failedRequests = failedRequests.get(),
              hasResultDirAccessErrors = hasResultDirAccessErrors.get(),
              hasOutOfDiskSpaceErrors = hasOutOfDiskSpaceErrors.get()
            )
          }
      }
    } finally {
      val notificationSummary =
        extractNotificationSummaryText(firstChanPostImage, imageDownloadRequests)

      // End event
      emitNotificationUpdate(
        uniqueId = imageDownloadInputData.uniqueId,
        imageSaverOptionsJson = imageDownloadInputData.imageSaverOptionsJson,
        completed = true,
        notificationSummary = notificationSummary,
        totalImagesCount = imageDownloadInputData.requestsCount(),
        canceledRequests = canceledRequests.get(),
        completedRequests = completedRequestsToDownloadedImagesResult(
          completedRequests,
          outputDirUri
        ),
        duplicates = duplicates.get(),
        failedRequests = failedRequests.get(),
        hasResultDirAccessErrors = hasResultDirAccessErrors.get(),
        hasOutOfDiskSpaceErrors = hasOutOfDiskSpaceErrors.get(),
      )

      mutex.withLock { activeDownloads.remove(imageDownloadInputData.uniqueId) }
    }

    return mutex.withLock { activeDownloads.size }
  }

  private fun extractNotificationSummaryText(
    firstChanPostImage: AtomicReference<ChanPostImage>,
    imageDownloadRequests: List<ImageDownloadRequest>
  ): String? {
    return firstChanPostImage.get()?.let { chanPostImage ->
      if (imageDownloadRequests.size == 1) {
        return@let chanPostImage.imageUrl!!.toString()
      } else {
        val threadDescriptor = chanPostImage.ownerPostDescriptor.threadDescriptor()
        return@let "${threadDescriptor.siteName()}/${threadDescriptor.boardCode()}/${threadDescriptor.threadNo}"
      }
    }
  }

  private suspend fun downloadSingleImage(
    imageDownloadInputData: ImageSaverV2Service.ImageDownloadInputData,
    imageDownloadRequest: ImageDownloadRequest,
    hasResultDirAccessErrors: AtomicBoolean,
    hasOutOfDiskSpaceErrors: AtomicBoolean,
    firstChanPostImage: AtomicReference<ChanPostImage>,
    canceledRequests: AtomicInteger,
    duplicates: AtomicInteger,
    failedRequests: AtomicInteger,
    outputDirUri: AtomicReference<Uri>,
    completedRequests: AtomicInteger
  ): ImageDownloadRequest {
    BackgroundUtils.ensureBackgroundThread()

    if (verboseLogs) {
      Logger.d(TAG, "downloadImagesInternal() start uniqueId='${imageDownloadInputData.uniqueId}', " +
          "imageUrl='${imageDownloadRequest.imageFullUrl}'")
    }

    val downloadImageResult = downloadSingleImageInternal(
      hasResultDirAccessErrors,
      hasOutOfDiskSpaceErrors,
      firstChanPostImage,
      imageDownloadInputData,
      imageDownloadRequest
    )

    when (downloadImageResult) {
      is DownloadImageResult.Canceled -> {
        // Canceled by user by clicking "Cancel" notification action
        canceledRequests.incrementAndGet()
      }
      is DownloadImageResult.DuplicateFound -> {
        // Duplicate image (the same result file name) was found on disk and DuplicateResolution
        // setting is set to ask the user what to do.
        duplicates.incrementAndGet()
      }
      is DownloadImageResult.Failure -> {
        // Some error happened
        failedRequests.incrementAndGet()
      }
      is DownloadImageResult.Success -> {
        // Image successfully downloaded
        if (!outputDirUri.compareAndSet(null, downloadImageResult.outputDirUri)) {
          check(outputDirUri.get() == downloadImageResult.outputDirUri) {
            "outputDirUris differ! Expected: $outputDirUri, actual: ${downloadImageResult.outputDirUri}"
          }
        }

        completedRequests.incrementAndGet()
      }
      is DownloadImageResult.OutOfDiskSpaceError -> {
        hasOutOfDiskSpaceErrors.set(true)
        canceledRequests.incrementAndGet()
      }
      is DownloadImageResult.ResultDirectoryError -> {
        // Something have happened with the output directory (it was deleted or something like that)
        hasResultDirAccessErrors.set(true)
        canceledRequests.incrementAndGet()
      }
    }

    if (verboseLogs) {
      Logger.d(TAG, "downloadImagesInternal() end uniqueId='${imageDownloadInputData.uniqueId}', " +
        "imageUrl='${imageDownloadRequest.imageFullUrl}', result=$downloadImageResult")
    }

    return ImageDownloadRequest(
      imageDownloadRequest.uniqueId,
      imageDownloadRequest.imageFullUrl,
      imageDownloadRequest.newFileName,
      downloadImageResultToStatus(downloadImageResult),
      getDuplicateUriOrNull(downloadImageResult),
      imageDownloadRequest.duplicatesResolution,
      imageDownloadRequest.createdOn
    )
  }

  private suspend fun handleNewNotificationId(uniqueId: String) {
    val notificationsIdToDelete = mutex.withLock {
      if (!activeNotificationIdQueue.contains(uniqueId)) {
        activeNotificationIdQueue.push(uniqueId)
      }

      val maxNotifications = if (AppModuleAndroidUtils.isDevBuild()) {
        MAX_VISIBLE_NOTIFICATIONS_TEST
      } else {
        MAX_VISIBLE_NOTIFICATIONS_PROD
      }

      if (activeNotificationIdQueue.size > maxNotifications) {
        var count = (maxNotifications - activeNotificationIdQueue.size).coerceAtLeast(1)
        val notificationsIdToDelete = mutableListOf<String>()

        for (notificationId in activeNotificationIdQueue.asReversed()) {
          if (count <= 0) {
            break
          }

          val notExistsOrCanceled = activeDownloads[notificationId]?.isCanceled()
            ?: true

          if (notExistsOrCanceled) {
            notificationsIdToDelete += notificationId
            --count
          }
        }

        return@withLock notificationsIdToDelete
      }

      return@withLock emptyList<String>()
    }

    notificationsIdToDelete.forEach { notificationIdToDelete ->
      deleteDownload(notificationIdToDelete)
    }
  }

  private fun completedRequestsToDownloadedImagesResult(
    completedRequests: AtomicInteger,
    outputDirUri: AtomicReference<Uri>
  ): DownloadedImages {
    return if (completedRequests.get() == 0) {
      DownloadedImages.Empty
    } else {
      DownloadedImages.Multiple(outputDirUri.get(), completedRequests.get())
    }
  }

  private fun getDuplicateUriOrNull(downloadImageResult: DownloadImageResult): Uri? {
    if (downloadImageResult is DownloadImageResult.DuplicateFound) {
      return downloadImageResult.duplicate.imageOnDiskUri
    }

    return null
  }

  private fun downloadImageResultToStatus(
    downloadImageResult: DownloadImageResult
  ): ImageDownloadRequest.Status {
    return when (downloadImageResult) {
      is DownloadImageResult.Canceled -> ImageDownloadRequest.Status.Canceled
      is DownloadImageResult.DuplicateFound -> ImageDownloadRequest.Status.ResolvingDuplicate
      is DownloadImageResult.Success -> ImageDownloadRequest.Status.Downloaded
      is DownloadImageResult.Failure,
      is DownloadImageResult.ResultDirectoryError,
      is DownloadImageResult.OutOfDiskSpaceError -> ImageDownloadRequest.Status.DownloadFailed
    }
  }

  private suspend fun emitNotificationUpdate(
    uniqueId: String,
    imageSaverOptionsJson: String,
    completed: Boolean,
    notificationSummary: String?,
    totalImagesCount: Int,
    canceledRequests: Int,
    completedRequests: DownloadedImages,
    duplicates: Int,
    failedRequests: Int,
    hasResultDirAccessErrors: Boolean,
    hasOutOfDiskSpaceErrors: Boolean
  ) {
    BackgroundUtils.ensureBackgroundThread()

    val imageSaverDelegateResult = ImageSaverDelegateResult(
      uniqueId = uniqueId,
      imageSaverOptionsJson = imageSaverOptionsJson,
      completed = completed,
      notificationSummary = notificationSummary,
      totalImagesCount = totalImagesCount,
      canceledRequests = canceledRequests,
      downloadedImages = completedRequests,
      duplicates = duplicates,
      failedRequests = failedRequests,
      hasResultDirAccessErrors = hasResultDirAccessErrors,
      hasOutOfDiskSpaceErrors =  hasOutOfDiskSpaceErrors
    )

    notificationUpdatesFlow.emit(imageSaverDelegateResult)
  }

  private fun formatFileName(
    imageSaverV2Options: ImageSaverV2Options,
    postImage: ChanPostImage,
    imageDownloadRequest: ImageDownloadRequest
  ): String {
    var fileName = when (ImageSaverV2Options.ImageNameOptions.fromRawValue(imageSaverV2Options.imageNameOptions)) {
      ImageSaverV2Options.ImageNameOptions.UseServerFileName -> postImage.serverFilename
      ImageSaverV2Options.ImageNameOptions.UseOriginalFileName -> postImage.filename
        ?: postImage.serverFilename
    }

    if (imageDownloadRequest.newFileName.isNotNullNorBlank()) {
      fileName = imageDownloadRequest.newFileName!!
    }

    val extension = postImage.extension ?: "jpg"

    return "$fileName.$extension"
  }

  @Suppress("MoveVariableDeclarationIntoWhen")
  private fun getFullFileUri(
    imageSaverV2Options: ImageSaverV2Options,
    imageDownloadRequest: ImageDownloadRequest,
    postDescriptor: PostDescriptor,
    fileName: String
  ): ResultFile {
    val rootDirectoryUri = Uri.parse(checkNotNull(imageSaverV2Options.rootDirectoryUri))

    val rootDirectory = fileManager.fromUri(rootDirectoryUri)
      ?: return ResultFile.FailedToOpenResultDir(rootDirectoryUri.toString())

    val segments = mutableListOf<Segment>()

    if (imageSaverV2Options.appendSiteName) {
      segments += DirectorySegment(postDescriptor.siteDescriptor().siteName)
    }

    if (imageSaverV2Options.appendBoardCode) {
      segments += DirectorySegment(postDescriptor.boardDescriptor().boardCode)
    }

    if (imageSaverV2Options.appendThreadId) {
      segments += DirectorySegment(postDescriptor.getThreadNo().toString())
    }

    if (imageSaverV2Options.subDirs.isNotNullNorBlank()) {
      val subDirs = imageSaverV2Options.subDirs!!.split('\\')

      subDirs.forEach { subDir -> segments += DirectorySegment(subDir) }
    }

    val resultDir = fileManager.create(rootDirectory, segments)
    if (resultDir == null) {
      return ResultFile.FailedToOpenResultDir(rootDirectory.clone(segments).getFullPath())
    }

    segments += FileSegment(fileName)

    val resultFile = rootDirectory.clone(segments)
    val resultFileUri = Uri.parse(resultFile.getFullPath())
    val resultDirUri = Uri.parse(resultDir.getFullPath())

    if (fileManager.exists(resultFile)) {
      var duplicatesResolution =
        ImageSaverV2Options.DuplicatesResolution.fromRawValue(imageSaverV2Options.duplicatesResolution)

      // If the setting is set to DuplicatesResolution.AskWhatToDo then check the duplicatesResolution
      // of imageDownloadRequest
      if (duplicatesResolution == ImageSaverV2Options.DuplicatesResolution.AskWhatToDo) {
        duplicatesResolution = imageDownloadRequest.duplicatesResolution
      }

      when (duplicatesResolution) {
        ImageSaverV2Options.DuplicatesResolution.AskWhatToDo -> {
          return ResultFile.DuplicateFound(resultFileUri)
        }
        ImageSaverV2Options.DuplicatesResolution.Skip -> {
          val fileIsNotEmpty = fileManager.getLength(resultFile) > 0
          return ResultFile.Skipped(resultDirUri, resultFileUri, fileIsNotEmpty)
        }
        ImageSaverV2Options.DuplicatesResolution.Overwrite -> {
          if (!fileManager.delete(resultFile)) {
            return ResultFile.FailedToOpenResultDir(resultFile.getFullPath())
          }

          // Fallthrough, continue downloading the file
        }
      }
    }

    return ResultFile.File(
      resultDirUri,
      resultFileUri,
      resultFile
    )
  }

  sealed class ResultFile {
    data class File(
      val outputDirUri: Uri,
      val outputFileUri: Uri,
      val file: AbstractFile
    ) : ResultFile()

    data class DuplicateFound(val fileUri: Uri) : ResultFile()

    data class FailedToOpenResultDir(val dirPath: String) : ResultFile()

    data class Skipped(
      val outputDirUri: Uri,
      val outputFileUri: Uri,
      val fileIsNotEmpty: Boolean
    ) : ResultFile()
  }

  private suspend fun downloadSingleImageInternal(
    hasResultDirAccessErrors: AtomicBoolean,
    hasOutOfDiskSpaceErrors: AtomicBoolean,
    firstChanPostImage: AtomicReference<ChanPostImage>,
    imageDownloadInputData: ImageSaverV2Service.ImageDownloadInputData,
    imageDownloadRequest: ImageDownloadRequest
  ): DownloadImageResult {
    BackgroundUtils.ensureBackgroundThread()

    return ModularResult.Try {
      val canceled = getDownloadContext(imageDownloadInputData)?.isCanceled() ?: true
      if (hasResultDirAccessErrors.get() || hasOutOfDiskSpaceErrors.get() || canceled) {
        return@Try DownloadImageResult.Canceled(imageDownloadRequest)
      }

      val imageSaverV2Options = imageDownloadInputData.imageSaverV2Options
      val imageFullUrl = imageDownloadRequest.imageFullUrl

      val chanPostImageResult = chanPostImageRepository.selectPostImageByUrl(imageFullUrl)
      if (chanPostImageResult is ModularResult.Error) {
        return@Try DownloadImageResult.Failure(chanPostImageResult.error, true)
      }

      val chanPostImage = (chanPostImageResult as ModularResult.Value).value
      if (chanPostImage == null) {
        val error = IOException("Image $imageFullUrl already deleted from the database")
        return@Try DownloadImageResult.Failure(error, false)
      }

      firstChanPostImage.compareAndSet(null, chanPostImage)

      val fileName = formatFileName(
        imageSaverV2Options,
        chanPostImage,
        imageDownloadRequest
      )

      val postDescriptor = chanPostImage.ownerPostDescriptor

      val outputFileResult = getFullFileUri(
        imageSaverV2Options = imageSaverV2Options,
        imageDownloadRequest = imageDownloadRequest,
        postDescriptor = postDescriptor,
        fileName = fileName
      )

      when (outputFileResult) {
        is ResultFile.DuplicateFound -> {
          val duplicateImage = DuplicateImage(
            imageDownloadRequest,
            outputFileResult.fileUri
          )

          return DownloadImageResult.DuplicateFound(duplicateImage)
        }
        is ResultFile.FailedToOpenResultDir -> {
          return@Try DownloadImageResult.ResultDirectoryError(
            outputFileResult.dirPath,
            imageDownloadRequest
          )
        }
        is ResultFile.Skipped -> {
          if (outputFileResult.fileIsNotEmpty) {
            return@Try DownloadImageResult.Success(
              outputFileResult.outputDirUri,
              imageDownloadRequest
            )
          }

          val error = IOException("Duplicate file is empty")
          return@Try DownloadImageResult.Failure(error, true)
        }
        is ResultFile.File -> {
          // no-op
        }
      }

      val outputFile = outputFileResult.file
      val outputDirUri = outputFileResult.outputDirUri

      val actualOutputFile = fileManager.create(outputFile)
      if (actualOutputFile == null) {
        return@Try DownloadImageResult.ResultDirectoryError(
          outputFile.getFullPath(),
          imageDownloadRequest
        )
      }

      try {
        doIoTaskWithAttempts(MAX_IO_ERROR_RETRIES_COUNT) {
          try {
            downloadFileInternal(chanPostImage, actualOutputFile)
          } catch (error: IOException) {
            if (error.isOutOfDiskSpaceError()) {
              throw OutOfDiskSpaceException()
            }

            throw error
          }
        }
      } catch (error: Throwable) {
        fileManager.delete(actualOutputFile)

        return@Try when (error) {
          is OutOfDiskSpaceException -> {
            DownloadImageResult.OutOfDiskSpaceError(imageDownloadRequest)
          }
          is ResultFileAccessError -> {
            DownloadImageResult.ResultDirectoryError(error.resultFileUri, imageDownloadRequest)
          }
          is NotFoundException -> DownloadImageResult.Failure(error, false)
          is IOException -> DownloadImageResult.Failure(error, true)
          else -> throw error
        }
      }

      return@Try DownloadImageResult.Success(outputDirUri, imageDownloadRequest)
    }.mapErrorToValue { error -> DownloadImageResult.Failure(error, true) }
  }

  @Throws(ResultFileAccessError::class, IOException::class, NotFoundException::class)
  private suspend fun downloadFileInternal(chanPostImage: ChanPostImage, outputFile: AbstractFile) {
    BackgroundUtils.ensureBackgroundThread()
    val imageUrl = checkNotNull(chanPostImage.imageUrl) { "Image url is empty!" }

    val request = Request.Builder()
      .url(imageUrl)
      .header("User-Agent", appConstants.userAgent)
      .build()

    val response = downloaderOkHttpClient.okHttpClient().suspendCall(request)

    if (!response.isSuccessful) {
      if (response.code == 404) {
        throw NotFoundException()
      }

      throw IOException("Unsuccessful response, code: ${response.code}")
    }

    val responseBody = response.body
      ?: throw IOException("Response has no body")

    val outputFileStream = fileManager.getOutputStream(outputFile)
      ?: throw ResultFileAccessError(outputFile.getFullPath())

    runInterruptible {
      responseBody.source().inputStream().use { inputStream ->
        outputFileStream.use { outputStream ->
          inputStream.copyTo(outputStream)
        }
      }
    }
  }

  private suspend fun getDownloadContext(
    imageDownloadInputData: ImageSaverV2Service.ImageDownloadInputData
  ): DownloadContext? {
    return mutex.withLock { activeDownloads.get(imageDownloadInputData.uniqueId) }
  }

  class ResultFileAccessError(val resultFileUri: String) : Exception("Failed to access result file: $resultFileUri")
  class NotFoundException : Exception("Not found on server")
  class OutOfDiskSpaceException : Exception("Out of disk space")

  class DownloadContext(
    private val canceled: AtomicBoolean = AtomicBoolean(false)
  ) {

    fun isCanceled(): Boolean = canceled.get()

    fun cancel() {
      canceled.compareAndSet(false, true)
    }

  }

  sealed class DownloadImageResult {
    data class Success(
      val outputDirUri: Uri,
      val imageDownloadRequest: ImageDownloadRequest,
    ) : DownloadImageResult()

    data class Failure(
      val error: Throwable,
      val canRetry: Boolean
    ) : DownloadImageResult()

    data class Canceled(val imageDownloadRequest: ImageDownloadRequest) : DownloadImageResult()

    data class DuplicateFound(val duplicate: DuplicateImage) : DownloadImageResult()

    data class OutOfDiskSpaceError(val imageDownloadRequest: ImageDownloadRequest) : DownloadImageResult()

    data class ResultDirectoryError(
      val path: String,
      val imageDownloadRequest: ImageDownloadRequest
    ) : DownloadImageResult()
  }

  data class ImageSaverDelegateResult(
    val uniqueId: String,
    val imageSaverOptionsJson: String,
    val completed: Boolean,
    val notificationSummary: String?,
    val totalImagesCount: Int,
    val canceledRequests: Int,
    val downloadedImages: DownloadedImages,
    val duplicates: Int,
    val failedRequests: Int,
    val hasResultDirAccessErrors: Boolean,
    val hasOutOfDiskSpaceErrors: Boolean
  ) {

    fun hasAnyErrors(): Boolean {
      return duplicates > 0 || failedRequests > 0 || hasResultDirAccessErrors || hasOutOfDiskSpaceErrors
    }

    fun hasOnlyCompletedRequests(): Boolean {
      // We allow notification auto-hide after the user manually cancels the download
      return duplicates == 0 && failedRequests == 0 && downloadedImages.count() > 0
    }

    fun processedCount(): Int {
      return canceledRequests + downloadedImages.count() + duplicates + failedRequests
    }
  }

  sealed class DownloadedImages {
    abstract val outputDirUri: Uri?
    abstract fun count(): Int

    fun isNotEmpty(): Boolean = count() > 0

    object Empty : DownloadedImages() {
      override val outputDirUri: Uri?
        get() = null

      override fun count(): Int = 0
    }

    data class Multiple(
      override val outputDirUri: Uri?,
      val imageDownloadRequestsCount: Int
    ) : DownloadedImages() {
      override fun count(): Int {
        return imageDownloadRequestsCount
      }
    }
  }

  data class DuplicateImage(
    val imageDownloadRequest: ImageDownloadRequest,
    val imageOnDiskUri: Uri
  )

  companion object {
    private const val TAG = "ImageSaverV2ServiceDelegate"
    private const val MAX_VISIBLE_NOTIFICATIONS_PROD = 12
    private const val MAX_VISIBLE_NOTIFICATIONS_TEST = 3

    private const val MAX_IO_ERROR_RETRIES_COUNT = 3
  }
}