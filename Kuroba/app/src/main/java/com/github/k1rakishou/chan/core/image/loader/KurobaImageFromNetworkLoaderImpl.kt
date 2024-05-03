package com.github.k1rakishou.chan.core.image.loader

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import coil.ImageLoader
import coil.network.HttpException
import coil.size.Scale
import coil.transform.Transformation
import com.github.k1rakishou.chan.core.base.okhttp.CoilOkHttpClient
import com.github.k1rakishou.chan.core.cache.CacheFileType
import com.github.k1rakishou.chan.core.cache.CacheHandler
import com.github.k1rakishou.chan.core.cache.downloader.ChunkedMediaDownloader
import com.github.k1rakishou.chan.core.site.SiteResolver
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.common.BadContentTypeException
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.suspendCall
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.fsaf.FileManager
import dagger.Lazy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.IOException

class KurobaImageFromNetworkLoaderImpl(
  private val cacheHandlerLazy: Lazy<CacheHandler>,
  private val chunkedMediaDownloaderLazy: Lazy<ChunkedMediaDownloader>,
  private val siteResolverLazy: Lazy<SiteResolver>,
  private val coilOkHttpClientLazy: Lazy<CoilOkHttpClient>,
  private val coilImageLoaderLazy: Lazy<ImageLoader>,
  private val fileManagerLazy: Lazy<FileManager>
) : KurobaImageFromNetworkLoader {
  private val cacheHandler: CacheHandler
    get() = cacheHandlerLazy.get()
  private val chunkedMediaDownloader: ChunkedMediaDownloader
    get() = chunkedMediaDownloaderLazy.get()
  private val siteResolver: SiteResolver
    get() = siteResolverLazy.get()
  private val coilOkHttpClient: CoilOkHttpClient
    get() = coilOkHttpClientLazy.get()
  private val coilImageLoader: ImageLoader
    get() = coilImageLoaderLazy.get()
  private val fileManager: FileManager
    get() = fileManagerLazy.get()

  override suspend fun loadFromNetwork(
    context: Context,
    url: String,
    scale: Scale,
    cacheFileType: CacheFileType,
    imageSize: KurobaImageSize,
    transformations: List<Transformation>
  ): ModularResult<BitmapDrawable> {
    return withContext(Dispatchers.IO) {
      return@withContext ModularResult.Try {
        val imageFile = loadFromNetworkIntoFile(
          cacheFileType = cacheFileType,
          url = url
        ).unwrap()

        return@Try applyTransformationsToDrawable(
          coilImageLoader = coilImageLoader,
          chunkedMediaDownloader = chunkedMediaDownloader,
          cacheHandler = cacheHandler,
          context = context,
          imageFile = fileManager.fromRawFile(imageFile),
          url = url,
          cacheFileType = cacheFileType,
          scale = scale,
          imageSize = imageSize,
          transformations = transformations
        ).unwrap()
      }
    }
  }

  @Throws(HttpException::class)
  private suspend fun loadFromNetworkIntoFile(
    cacheFileType: CacheFileType,
    url: String
  ): ModularResult<File> {
    return ModularResult.Try {
      BackgroundUtils.ensureBackgroundThread()

      val cacheFile = cacheHandler.getOrCreateCacheFile(cacheFileType, url)
      if (cacheFile == null) {
        Logger.e(TAG, "loadFromNetworkIntoFile() cacheHandler.getOrCreateCacheFile('$url') -> null")
        throw KurobaImageLoaderException("Failed to get or create cache file")
      }

      val success = try {
        loadFromNetworkIntoFileInternal(
          url = url,
          cacheFileType = cacheFileType,
          cacheFile = cacheFile
        )
      } catch (error: Throwable) {
        if (!chunkedMediaDownloader.isRunning(url)) {
          cacheHandler.deleteCacheFile(cacheFileType, cacheFile)
        }

        throw error
      }

      if (!success) {
        if (!chunkedMediaDownloader.isRunning(url)) {
          cacheHandler.deleteCacheFile(cacheFileType, cacheFile)
        }

        throw KurobaImageLoaderException("Failed to download image into a file")
      }

      return@Try cacheFile
    }
  }

  private suspend fun loadFromNetworkIntoFileInternal(
    url: String,
    cacheFileType: CacheFileType,
    cacheFile: File
  ): Boolean {
    BackgroundUtils.ensureBackgroundThread()

    val site = siteResolver.findSiteForUrl(url)
    val requestModifier = site?.requestModifier()

    val requestBuilder = Request.Builder()
      .url(url)
      .get()

    if (site != null && requestModifier != null) {
      requestModifier.modifyThumbnailGetRequest(site, requestBuilder)
    }

    val response = coilOkHttpClient.okHttpClient().suspendCall(requestBuilder.build())
    if (!response.isSuccessful) {
      Logger.e(TAG, "loadFromNetworkInternalIntoFile() bad response code: ${response.code}")

      if (response.code == 404) {
        throw HttpException(response)
      }

      return false
    }

    runInterruptible {
      val responseBody = response.body
        ?: throw IOException("Response body is null")

      val contentMainType = responseBody.contentType()?.type
      val contentSubType = responseBody.contentType()?.subtype

      if (contentMainType != "image" && contentMainType != "video" && !faviconUrlWithInvalidMimeType(url)) {
        throw BadContentTypeException("${contentMainType}/${contentSubType}")
      }

      responseBody.byteStream().use { inputStream ->
        cacheFile.outputStream().use { os ->
          inputStream.copyTo(os)
        }
      }
    }

    if (!cacheHandler.markFileDownloaded(cacheFileType, cacheFile)) {
      throw IOException("Failed to mark file '${cacheFile.absolutePath}' as downloaded")
    }

    val fileLength = cacheFile.length()
    if (fileLength <= 0) {
      return false
    }

    cacheHandler.fileWasAdded(cacheFileType, fileLength)

    return true
  }

  // Super hack.
  // Some sites send their favicons without the content type which breaks our content type checks so
  // we have to check the urls manually...
  private fun faviconUrlWithInvalidMimeType(url: String): Boolean {
    return url == "https://endchan.net/favicon.ico"
      || url == "https://endchan.org/favicon.ico"
      || url == "https://yeshoney.xyz/favicon.ico"
  }

  companion object {
    private const val TAG = "KurobaImageFromNetworkLoaderImpl"
  }

}