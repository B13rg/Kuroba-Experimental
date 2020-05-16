package com.github.adamantcheese.chan.core.cache

import android.annotation.SuppressLint
import android.net.ConnectivityManager
import com.github.adamantcheese.chan.core.cache.downloader.*
import com.github.adamantcheese.chan.core.model.PostImage
import com.github.adamantcheese.chan.core.model.orm.Loadable
import com.github.adamantcheese.chan.core.settings.ChanSettings
import com.github.adamantcheese.chan.core.site.SiteResolver
import com.github.adamantcheese.chan.ui.settings.base_directory.LocalThreadsBaseDirectory
import com.github.adamantcheese.chan.utils.AndroidUtils.getNetworkClass
import com.github.adamantcheese.chan.utils.BackgroundUtils
import com.github.adamantcheese.chan.utils.BackgroundUtils.runOnMainThread
import com.github.adamantcheese.chan.utils.Logger
import com.github.adamantcheese.chan.utils.PostUtils
import com.github.adamantcheese.chan.utils.StringUtils.maskImageUrl
import com.github.adamantcheese.chan.utils.exhaustive
import com.github.k1rakishou.fsaf.FileManager
import com.github.k1rakishou.fsaf.file.AbstractFile
import com.github.k1rakishou.fsaf.file.FileSegment
import com.github.k1rakishou.fsaf.file.RawFile
import com.github.k1rakishou.fsaf.file.Segment
import io.reactivex.Flowable
import io.reactivex.Single
import io.reactivex.processors.PublishProcessor
import io.reactivex.schedulers.Schedulers
import okhttp3.OkHttpClient
import java.io.IOException
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.collections.ArrayList

class FileCacheV2(
        private val fileManager: FileManager,
        private val cacheHandler: CacheHandler,
        private val siteResolver: SiteResolver,
        private val okHttpClient: OkHttpClient,
        private val connectivityManager: ConnectivityManager
) {
    private val activeDownloads = ActiveDownloads()

    private val normalRequestQueue = PublishProcessor.create<String>().toSerialized()
    private val chunksCount = ChanSettings.concurrentDownloadChunkCount.get().toInt()
    private val threadsCount = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(4)
    private val requestCancellationThread = Executors.newSingleThreadExecutor()
    private val verboseLogs = ChanSettings.verboseLogs.get()

    private val normalThreadIndex = AtomicInteger(0)
    private val workerScheduler = Schedulers.from(
            Executors.newFixedThreadPool(threadsCount) { runnable ->
                return@newFixedThreadPool Thread(
                        runnable,
                        String.format(
                                Locale.ENGLISH,
                                NORMAL_THREAD_NAME_FORMAT,
                                normalThreadIndex.getAndIncrement()
                        )
                )
            }
    )

    private val partialContentSupportChecker = PartialContentSupportChecker(
            okHttpClient,
            activeDownloads,
            siteResolver,
            MAX_TIMEOUT_MS
    )

    private val chunkDownloader = ChunkDownloader(
            okHttpClient,
            activeDownloads,
            verboseLogs
    )

    private val chunkReader = ChunkPersister(
            fileManager,
            cacheHandler,
            activeDownloads,
            verboseLogs
    )

    private val chunkPersister = ChunkMerger(
            fileManager,
            cacheHandler,
            siteResolver,
            activeDownloads,
            verboseLogs
    )

    private val concurrentChunkedFileDownloader = ConcurrentChunkedFileDownloader(
            fileManager,
            chunkDownloader,
            chunkReader,
            chunkPersister,
            workerScheduler,
            verboseLogs,
            activeDownloads,
            cacheHandler
    )

    init {
        require(chunksCount > 0) { "Chunks count is zero or less ${chunksCount}" }
        log(TAG, "chunksCount = $chunksCount")

        initNormalRxWorkerQueue()
    }

    /**
     * This is a singleton class so we don't care about the disposable since we will never should
     * dispose of this stream
     * */
    @SuppressLint("CheckResult")
    private fun initNormalRxWorkerQueue() {
        normalRequestQueue
                .observeOn(workerScheduler)
                .onBackpressureBuffer()
                .flatMap { url ->
                    return@flatMap Flowable.defer { handleFileDownload(url) }
                            .subscribeOn(workerScheduler)
                            .onErrorReturn { throwable ->
                                ErrorMapper.mapError(url, throwable, activeDownloads)
                            }
                            .map { result -> Pair(url, result) }
                            .doOnNext { (url, result) -> handleResults(url, result) }
                }
                .subscribe({
                    // Do nothing
                }, { error ->
                    throw RuntimeException("$TAG Uncaught exception!!! " +
                            "workerQueue is in error state now!!! " +
                            "This should not happen!!!, original error = " + error.message)
                }, {
                    throw RuntimeException(
                            "$TAG workerQueue stream has completed!!! This should not happen!!!"
                    )
                })
    }

    // For now it is only used in the developer settings so it's okay to block the UI
    fun clearCache() {
        activeDownloads.clear()
        cacheHandler.clearCache()
    }

    fun isRunning(url: String): Boolean {
        return synchronized(activeDownloads) {
            activeDownloads.getState(url) == DownloadState.Running
        }
    }

    fun enqueueMediaPrefetchRequest(loadable: Loadable, postImage: PostImage): CancelableDownload? {
        if (loadable.isLocal || loadable.isDownloading) {
            throw IllegalArgumentException("Cannot use local thread loadable for prefetching!")
        }

        val imageUrl = postImage.imageUrl
                ?: return null

        if (postImage.isInlined) {
            throw IllegalAccessException("Cannot prefetch inlined files! url = $imageUrl")
        }

        val url = imageUrl.toString()

        val (alreadyActive, cancelableDownload) = getOrCreateCancelableDownload(
          url = url,
          callback = null,
          // Always 1 for media prefetching
          chunksCount = 1,
          isGalleryBatchDownload = true,
          isPrefetchDownload = true,
          // Prefetch downloads always have default extra info (no file size, no file hash)
          extraInfo = DownloadRequestExtraInfo(),
          localThreadInfo = LocalThreadInfo.create(loadable, postImage)
        )

        if (alreadyActive) {
            return null
        }

        normalRequestQueue.onNext(url)
        return cancelableDownload
    }

    fun enqueueChunkedDownloadFileRequest(
        loadable: Loadable,
        postImage: PostImage,
        extraInfo: DownloadRequestExtraInfo,
        callback: FileCacheListener?
    ): CancelableDownload? {
        return enqueueDownloadFileRequest(
          postImage = postImage,
          extraInfo = extraInfo,
          localThreadInfo = LocalThreadInfo.create(loadable, postImage),
          chunksCount = chunksCount,
          isBatchDownload = false,
          callback = callback
        )
    }

    fun enqueueNormalDownloadFileRequest(
      loadable: Loadable,
      postImage: PostImage,
      isBatchDownload: Boolean,
      callback: FileCacheListener?
    ): CancelableDownload? {
        return enqueueDownloadFileRequest(
          postImage = postImage,
          // Normal downloads (not chunked) always have default extra info
          // (no file size, no file hash)
          extraInfo = DownloadRequestExtraInfo(),
          localThreadInfo = LocalThreadInfo.create(loadable, postImage),
          chunksCount = 1,
          isBatchDownload = isBatchDownload,
          callback = callback
        )
    }

    fun enqueueNormalDownloadFileRequest(
        url: String,
        callback: FileCacheListener?
    ): CancelableDownload? {
        // Normal downloads (not chunked) always have default extra info (no file size, no file hash)
        return enqueueDownloadFileRequestInternal(
          url = url,
          chunksCount = 1,
          isBatchDownload = false,
          extraInfo = DownloadRequestExtraInfo(),
          localThreadInfo = LocalThreadInfo.empty(),
          callback = callback
        )
    }

    @SuppressLint("CheckResult")
    private fun enqueueDownloadFileRequest(
      localThreadInfo: LocalThreadInfo,
      postImage: PostImage,
      extraInfo: DownloadRequestExtraInfo,
      chunksCount: Int,
      isBatchDownload: Boolean,
      callback: FileCacheListener?
    ): CancelableDownload? {
        val url = postImage.imageUrl?.toString()
          ?: return null

        return enqueueDownloadFileRequestInternal(
          url = url,
          chunksCount = chunksCount,
          isBatchDownload = isBatchDownload,
          extraInfo = extraInfo,
          localThreadInfo = localThreadInfo,
          callback = callback
        )
    }

    private fun enqueueDownloadFileRequestInternal(
        url: String,
        chunksCount: Int,
        isBatchDownload: Boolean,
        extraInfo: DownloadRequestExtraInfo,
        localThreadInfo: LocalThreadInfo,
        callback: FileCacheListener?
    ): CancelableDownload? {
        val (alreadyActive, cancelableDownload) = getOrCreateCancelableDownload(
          url = url,
          callback = callback,
          chunksCount = chunksCount,
          isGalleryBatchDownload = isBatchDownload,
          isPrefetchDownload = false,
          extraInfo = extraInfo,
          localThreadInfo = localThreadInfo
        )

        if (alreadyActive) {
            return cancelableDownload
        }

        log(TAG, "Downloading a file, url = ${maskImageUrl(url)}")
        normalRequestQueue.onNext(url)

        return cancelableDownload
    }

    // FIXME: if a request is added, then immediately canceled, and after that the same request is
    //  added again, then in case of the first one not being fast enough to get cancelled before
    //  the second one is added - the two of them will get merged and get canceled together.
    //  Maybe I could add a new flag and right in the end when handling terminal events
    //  I could check whether this flag is true or not and if it is re-add this request again?
    private fun getOrCreateCancelableDownload(
            url: String,
            callback: FileCacheListener?,
            chunksCount: Int,
            isGalleryBatchDownload: Boolean,
            isPrefetchDownload: Boolean,
            extraInfo: DownloadRequestExtraInfo,
            localThreadInfo: LocalThreadInfo
    ): Pair<Boolean, CancelableDownload> {
        if (chunksCount > 1 && (isGalleryBatchDownload || isPrefetchDownload)) {
            throw IllegalArgumentException("Cannot download file in chunks for media " +
                    "prefetching or gallery downloading!")
        }

        return synchronized(activeDownloads) {
            val prevRequest = activeDownloads.get(url)
            if (prevRequest != null) {
                log(TAG, "Request ${maskImageUrl(url)} is already active, re-subscribing to it")

                val prevCancelableDownload = prevRequest.cancelableDownload
                if (callback != null) {
                    prevCancelableDownload.addCallback(callback)
                }

                // true means that this request has already been started before and hasn't yet
                // completed so we can just resubscribe to it instead of creating a new one
                return@synchronized true to prevCancelableDownload
            }

            val cancelableDownload = CancelableDownload(
                    url = url,
                    requestCancellationThread = requestCancellationThread,
                    downloadType = CancelableDownload.DownloadType(
                      isPrefetchDownload,
                      isGalleryBatchDownload
                    )
            )

            if (callback != null) {
                cancelableDownload.addCallback(callback)
            }

            val request = FileDownloadRequest(
              url = url,
              chunksCount = AtomicInteger(chunksCount),
              downloaded = AtomicLong(0L),
              total = AtomicLong(0L),
              cancelableDownload = cancelableDownload,
              extraInfo = extraInfo,
              localThreadInfo = localThreadInfo
            )

            activeDownloads.put(url, request)
            return@synchronized false to cancelableDownload
        }
    }

    fun loadLocalThreadFile(localThreadInfo: LocalThreadInfo): Single<RawFile> {
        require(localThreadInfo.isValid()) { "localThreadInfo is not valid: ${localThreadInfo}" }

        val filename = localThreadInfo.filename!!
        val imagesSubDirSegments = localThreadInfo.imagesSubDirSegments
        val imageUrl = localThreadInfo.imageUrl!!

        return Single.fromCallable {
            BackgroundUtils.ensureBackgroundThread()

            if (!fileManager.baseDirectoryExists(LocalThreadsBaseDirectory::class.java)) {
                logError(TAG, "handleLocalThreadFile() Base local threads directory does not exist")
                throw IOException("Base local threads directory does not exist")
            }

            val baseDirFile = fileManager.newBaseDirectoryFile(
                    LocalThreadsBaseDirectory::class.java
            )

            if (baseDirFile == null) {
                logError(TAG, "handleLocalThreadFile() fileManager.newLocalThreadFile() returned null")
                throw IOException("Couldn't create a file inside local threads base directory")
            }

            val segments: MutableList<Segment> = ArrayList(imagesSubDirSegments).apply {
                add(FileSegment(filename))
            }

            val localImgFile = baseDirFile.clone(segments)
            val isLocalFileOk = fileManager.exists(localImgFile)
                    && fileManager.isFile(localImgFile)
                    && fileManager.canRead(localImgFile)

            if (!isLocalFileOk) {
                val path = localImgFile.getFullPath()

                logError(TAG, "Cannot load saved image from the disk, path: $path")
                throw IOException("Couldn't load saved image from the disk, path: $path")
            }

            return@fromCallable localImgFile
        }
          .flatMap { localImgFile -> handleLocalThreadFileImmediatelyAvailable(localImgFile, imageUrl) }
          .subscribeOn(workerScheduler)
    }

    private fun handleLocalThreadFileImmediatelyAvailable(file: AbstractFile, imageUrl: String): Single<RawFile> {
        return Single.fromCallable {
            if (file is RawFile) {
                // Regular Java File
                return@fromCallable file as RawFile
            }

            // SAF file
            try {
                val resultFile = cacheHandler.getOrCreateCacheFile(imageUrl.toString())
                        ?: throw IOException("Couldn't get or create cache file")

                if (!fileManager.copyFileContents(file, resultFile)) {
                    if (!cacheHandler.deleteCacheFile(resultFile)) {
                        Logger.e(TAG, "Couldn't delete cache file ${resultFile.getFullPath()}")
                    }

                    val error = IOException(
                            "Could not copy external SAF file into internal cache file, " +
                                    "externalFile = " + file.getFullPath() +
                                    ", resultFile = " + resultFile.getFullPath()
                    )

                    throw error
                }

                if (!cacheHandler.markFileDownloaded(resultFile)) {
                    throw FileCacheException.CouldNotMarkFileAsDownloaded(resultFile)
                }

                return@fromCallable resultFile as RawFile
            } catch (e: IOException) {
                logError(TAG, "Error while trying to create a new random cache file", e)
                throw e
            }
        }
    }

    private fun handleResults(url: String, result: FileDownloadResult) {
        BackgroundUtils.ensureBackgroundThread()

        try {
            val request = activeDownloads.get(url)
                    ?: return

            if (result.isErrorOfAnyKind()) {
                // Only call cancel when not already canceled and not stopped
                if (result !is FileDownloadResult.Canceled
                        && result !is FileDownloadResult.Stopped) {
                    activeDownloads.get(url)?.cancelableDownload?.cancel()
                }

                purgeOutput(request.url, request.getOutputFile())
            }

            val networkClass = getNetworkClassOrDefaultText(result)
            val activeDownloadsCount = activeDownloads.count() - 1

            when (result) {
                is FileDownloadResult.Start -> {
                    log(TAG, "Download (${request}) has started. " +
                            "Chunks count = ${result.chunksCount}. " +
                            "Network class = $networkClass. " +
                            "Downloads = $activeDownloadsCount")

                    // Start is not a terminal event so we don't want to remove request from the
                    // activeDownloads
                    resultHandler(url, request, false) {
                        onStart(result.chunksCount)
                    }
                }

                // Success
                is FileDownloadResult.Success -> {
                    val (downloaded, total) = synchronized(activeDownloads) {
                        val activeDownload = activeDownloads.get(url)

                        val downloaded = activeDownload?.downloaded?.get()
                        val total = activeDownload?.total?.get()

                        Pair(downloaded, total)
                    }

                    if (downloaded == null || total == null) {
                        return
                    }

                    val downloadedString = PostUtils.getReadableFileSize(downloaded)
                    val totalString = PostUtils.getReadableFileSize(total)

                    log(TAG, "Success (" +
                            "downloaded = ${downloadedString} ($downloaded B), " +
                            "total = ${totalString} ($total B), " +
                            "took ${result.requestTime}ms, " +
                            "network class = $networkClass, " +
                            "downloads = $activeDownloadsCount" +
                            ") for request ${request}"
                    )

                    // Trigger cache trimmer after a file has been successfully downloaded
                    cacheHandler.fileWasAdded(total)

                    resultHandler(url, request, true) {
                        onSuccess(result.file)
                        onEnd()
                    }
                }
                // Progress
                is FileDownloadResult.Progress -> {
                    val chunkSize = if (result.chunkSize == 0L) {
                        1L
                    } else {
                        result.chunkSize
                    }

                    if (ChanSettings.verboseLogs.get()) {
                        val percents = (result.downloaded.toFloat() / chunkSize.toFloat()) * 100f
                        val downloadedString = PostUtils.getReadableFileSize(result.downloaded)
                        val totalString = PostUtils.getReadableFileSize(chunkSize)

                        log(TAG,
                                "Progress " +
                                        "chunkIndex = ${result.chunkIndex}, downloaded: (${downloadedString}) " +
                                        "(${result.downloaded} B) / ${totalString} (${chunkSize} B), " +
                                        "${percents}%) for request ${request}"
                        )
                    }

                    // Progress is not a terminal event so we don't want to remove request from the
                    // activeDownloads
                    resultHandler(url, request, false) {
                        onProgress(result.chunkIndex, result.downloaded, chunkSize)
                    }
                }

                // Cancel
                is FileDownloadResult.Canceled,
                // Stop (called by WebmStreamingSource to stop downloading a file via FileCache and
                // continue downloading it via WebmStreamingDataSource)
                is FileDownloadResult.Stopped -> {
                    val (downloaded, total, output) = synchronized(activeDownloads) {
                        val activeDownload = activeDownloads.get(url)

                        val downloaded = activeDownload?.downloaded?.get()
                        val total = activeDownload?.total?.get()
                        val output = activeDownload?.getOutputFile()

                        Triple(downloaded, total, output)
                    }

                    val isCanceled = when (result) {
                        is FileDownloadResult.Canceled -> true
                        is FileDownloadResult.Stopped -> false
                        else -> throw RuntimeException("Must be either Canceled or Stopped")
                    }

                    val causeText = if (isCanceled) {
                        "canceled"
                    } else {
                        "stopped"
                    }

                    log(TAG, "Request ${request} $causeText, " +
                            "downloaded = $downloaded, " +
                            "total = $total, " +
                            "network class = $networkClass, " +
                            "downloads = $activeDownloadsCount")

                    resultHandler(url, request, true) {
                        if (isCanceled) {
                            onCancel()
                        } else {
                            onStop(output)
                        }

                        onEnd()
                    }
                }
                is FileDownloadResult.KnownException -> {
                    val message = "Exception for request ${request}, " +
                            "network class = $networkClass, downloads = $activeDownloadsCount"

                    if (verboseLogs) {
                        logError(TAG, message, result.fileCacheException)
                    } else {
                        logError(TAG, message)
                    }

                    resultHandler(url, request, true) {
                        when (result.fileCacheException) {
                            is FileCacheException.CancellationException -> {
                                throw RuntimeException("Not used")
                            }
                            is FileCacheException.FileNotFoundOnTheServerException -> {
                                onNotFound()
                            }
                            is FileCacheException.FileHashesAreDifferent,
                            is FileCacheException.CouldNotMarkFileAsDownloaded,
                            is FileCacheException.NoResponseBodyException,
                            is FileCacheException.CouldNotCreateOutputCacheFile,
                            is FileCacheException.CouldNotGetInputStreamException,
                            is FileCacheException.CouldNotGetOutputStreamException,
                            is FileCacheException.OutputFileDoesNotExist,
                            is FileCacheException.ChunkFileDoesNotExist,
                            is FileCacheException.HttpCodeException,
                            is FileCacheException.BadOutputFileException -> {
                                if (result.fileCacheException is FileCacheException.HttpCodeException
                                        && result.fileCacheException.statusCode == 404) {
                                    throw RuntimeException("This shouldn't be handled here!")
                                }

                                onFail(IOException(result.fileCacheException.message))
                            }
                        }.exhaustive

                        onEnd()
                    }
                }
                is FileDownloadResult.UnknownException -> {
                    val message = logErrorsAndExtractErrorMessage(
                            TAG,
                            "Unknown exception",
                            result.error
                    )

                    resultHandler(url, request, true) {
                        onFail(IOException(message))
                        onEnd()
                    }
                }
            }.exhaustive
        } catch (error: Throwable) {
            Logger.e(TAG, "An error in result handler", error)
        }
    }

    private fun getNetworkClassOrDefaultText(result: FileDownloadResult): String {
        return when (result) {
            is FileDownloadResult.Start,
            is FileDownloadResult.Success,
            FileDownloadResult.Canceled,
            FileDownloadResult.Stopped,
            is FileDownloadResult.KnownException -> getNetworkClass(connectivityManager)
            is FileDownloadResult.Progress,
            is FileDownloadResult.UnknownException -> {
                "Unsupported result: ${result::class.java.simpleName}"
            }
        }.exhaustive
    }

    private fun resultHandler(
            url: String,
            request: FileDownloadRequest,
            isTerminalEvent: Boolean,
            func: FileCacheListener.() -> Unit
    ) {
        try {
            request.cancelableDownload.forEachCallback {
                runOnMainThread {
                    func()
                }
            }
        } finally {
            if (isTerminalEvent) {
                request.cancelableDownload.clearCallbacks()
                activeDownloads.remove(url)
            }
        }
    }

    private fun handleFileDownload(url: String): Flowable<FileDownloadResult> {
        BackgroundUtils.ensureBackgroundThread()

        val request = activeDownloads.get(url)
        if (request == null || !request.cancelableDownload.isRunning()) {
            val state = request?.cancelableDownload?.getState()
                    ?: DownloadState.Canceled

            return Flowable.error(FileCacheException.CancellationException(state, url))
        }

        if (!request.localThreadInfo.isInlinedImage && (request.localThreadInfo.isLocalOrDownloading)) {
            log(TAG, "Handling local thread file, url = ${maskImageUrl(url)}")

            return loadLocalThreadFile(request.localThreadInfo)
              .toFlowable()
              .map { localFile -> FileDownloadResult.Success(localFile, 0L) as FileDownloadResult }
              .onErrorReturn { error -> FileDownloadResult.UnknownException(error) }
        }

        val outputFile = cacheHandler.getOrCreateCacheFile(url)
        if (outputFile == null) {
            return Flowable.error(FileCacheException.CouldNotCreateOutputCacheFile(url))
        }

        if (cacheHandler.isAlreadyDownloaded(outputFile)) {
            return Flowable.just(FileDownloadResult.Success(outputFile, 0L))
        }

        val fullPath = outputFile.getFullPath()
        val exists = fileManager.exists(outputFile)
        val isFile = fileManager.isFile(outputFile)
        val canWrite = fileManager.canWrite(outputFile)

        if (!exists || !isFile || !canWrite) {
            return Flowable.error(
                    FileCacheException.BadOutputFileException(fullPath, exists, isFile, canWrite)
            )
        }

        request.setOutputFile(outputFile)

        return partialContentSupportChecker.check(url)
                .observeOn(workerScheduler)
                .toFlowable()
                .flatMap { result ->
                    if (result.notFoundOnServer) {
                        throw FileCacheException.FileNotFoundOnTheServerException()
                    }

                    return@flatMap concurrentChunkedFileDownloader.download(
                            result,
                            url,
                            result.supportsPartialContentDownload
                    )
                }
    }

    private fun purgeOutput(url: String, output: RawFile?) {
        BackgroundUtils.ensureBackgroundThread()

        val request = activeDownloads.get(url)
                ?: return

        if (request.cancelableDownload.getState() != DownloadState.Canceled) {
            // Not canceled, only purge output when canceled. Do not purge the output file when
            // the state stopped too, because we are gonna use the file for the webm streaming cache.
            return
        }

        if (output == null) {
            return
        }

        log(TAG, "Purging ${maskImageUrl(url)}, file = ${output.getFullPath()}")

        if (!cacheHandler.deleteCacheFile(output)) {
            logError(TAG, "Could not delete the file in purgeOutput, output = ${output.getFullPath()}")
        }
    }

    companion object {
        private const val TAG = "FileCacheV2"
        private const val NORMAL_THREAD_NAME_FORMAT = "NormalFileCacheV2Thread-%d"
        private const val MAX_TIMEOUT_MS = 1000L

        const val MIN_CHUNK_SIZE = 1024L * 8L // 8 KB
    }
}