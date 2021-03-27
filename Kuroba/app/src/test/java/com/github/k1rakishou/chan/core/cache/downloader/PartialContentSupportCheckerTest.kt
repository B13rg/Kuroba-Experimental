package com.github.k1rakishou.chan.core.cache.downloader

import com.github.k1rakishou.chan.core.base.TestModule
import com.github.k1rakishou.chan.core.cache.CacheHandler
import com.github.k1rakishou.chan.core.cache.createFileDownloadRequest
import com.github.k1rakishou.chan.core.cache.withServer
import com.github.k1rakishou.chan.core.site.ChunkDownloaderSiteProperties
import com.github.k1rakishou.chan.core.site.Site
import com.github.k1rakishou.chan.core.site.SiteResolver
import com.github.k1rakishou.common.AndroidUtils
import com.nhaarman.mockitokotlin2.whenever
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import java.net.SocketException
import java.util.concurrent.TimeUnit


@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class PartialContentSupportCheckerTest {
  private val testModule = TestModule()

  private lateinit var okHttpClient: OkHttpClient
  private lateinit var activeDownloads: ActiveDownloads
  private lateinit var partialContentSupportChecker: PartialContentSupportChecker
  private lateinit var cacheHandler: CacheHandler
  private lateinit var siteResolver: SiteResolver

  @Before
  fun setUp() {
    AndroidUtils.init(testModule.provideApplication())
    ShadowLog.stream = System.out

    okHttpClient = testModule.provideDownloaderOkHttpClient().okHttpClient()
    siteResolver = testModule.provideSiteResolver()
    activeDownloads = testModule.provideActiveDownloads()
    partialContentSupportChecker = testModule.providePartialContentSupportChecker()
    cacheHandler = testModule.provideCacheHandler()
  }

  @After
  fun tearDown() {
    okHttpClient.dispatcher.cancelAll()
    activeDownloads.clear()
    partialContentSupportChecker.clear()
  }

  @Test
  fun `test check for batch request should return supportsPartialContentDownload == false`() {
    val url = "http://4chan.org/image1.jpg"
    val host = url.toHttpUrl().host
    val output = cacheHandler.getOrCreateCacheFile(url)!!
    val request = createFileDownloadRequest(url, isBatchDownload = true, file = output)
    activeDownloads.put(url, request)

    val mockSite = Mockito.mock(Site::class.java)
    val chunkDownloaderSiteProperties = ChunkDownloaderSiteProperties(
      enabled = true,
      siteSendsCorrectFileSizeInBytes = true
    )
    whenever(siteResolver.findSiteForUrl(host)).thenReturn(mockSite)
    whenever(mockSite.getChunkDownloaderSiteProperties()).thenReturn(chunkDownloaderSiteProperties)

    partialContentSupportChecker.check(url)
      .test()
      .assertValue { value ->
        assertFalse(value.supportsPartialContentDownload)
        true
      }
      .assertComplete()
      .assertNoErrors()
      .assertNoTimeout()
  }

  @Test
  fun `test small body size should return supportsPartialContentDownload == false with actual body size`() {
    withServer { server ->
      server.enqueue(
        MockResponse()
          .setResponseCode(200)
          .addHeader("Accept-Ranges", "bytes")
          .addHeader("Content-Length", "1024")
          .addHeader("CF-Cache-Status", "HIT")
      )

      server.start()

      val url = server.url("/image1.jpg").toString()
      val host = url.toHttpUrl().host
      val output = cacheHandler.getOrCreateCacheFile(url)!!
      val request = createFileDownloadRequest(url, file = output)
      activeDownloads.put(url, request)

      val mockSite = Mockito.mock(Site::class.java)
      val chunkDownloaderSiteProperties = ChunkDownloaderSiteProperties(
        enabled = true,
        siteSendsCorrectFileSizeInBytes = true
      )

      whenever(siteResolver.findSiteForUrl(host)).thenReturn(mockSite)
      whenever(mockSite.getChunkDownloaderSiteProperties()).thenReturn(chunkDownloaderSiteProperties)

      partialContentSupportChecker.check(url)
        .test()
        .awaitCount(1)
        .assertValue { value ->
          assertFalse(value.supportsPartialContentDownload)
          assertEquals(1024L, value.length)
          true
        }
        .assertComplete()
        .assertNoErrors()
        .assertNoTimeout()
        .await()
    }
  }

  @Test
  fun `test HEAD request timeout should return supportsPartialContentDownload == false`() {
    withServer { server ->
      server.enqueue(
        MockResponse()
          .setResponseCode(200)
          .addHeader("Accept-Ranges", "bytes")
          .addHeader("Content-Length", "9999")
          .addHeader("CF-Cache-Status", "HIT")
          .setHeadersDelay(300L, TimeUnit.MILLISECONDS)
      )

      server.start()

      val url = server.url("/image1.jpg").toString()
      val host = url.toHttpUrl().host
      val output = cacheHandler.getOrCreateCacheFile(url)!!
      val request = createFileDownloadRequest(url, file = output)
      activeDownloads.put(url, request)

      val mockSite = Mockito.mock(Site::class.java)
      val chunkDownloaderSiteProperties = ChunkDownloaderSiteProperties(
        enabled = true,
        siteSendsCorrectFileSizeInBytes = true
      )
      whenever(siteResolver.findSiteForUrl(host)).thenReturn(mockSite)
      whenever(mockSite.getChunkDownloaderSiteProperties()).thenReturn(chunkDownloaderSiteProperties)

      partialContentSupportChecker.check(url)
        .test()
        .awaitCount(1)
        .assertValue { value ->
          assertFalse(value.supportsPartialContentDownload)
          true
        }
        .assertComplete()
        .assertNoErrors()
        .assertNoTimeout()
        .await()
    }
  }

  @Test
  fun `test request cancellation`() {
    withServer { server ->
      server.enqueue(
        MockResponse()
          .setResponseCode(200)
          .addHeader("Accept-Ranges", "bytes")
          .addHeader("Content-Length", "9999")
          .addHeader("CF-Cache-Status", "HIT")
          .setHeadersDelay(300L, TimeUnit.MILLISECONDS)
      )

      server.start()

      val url = server.url("/image1.jpg").toString()
      val host = url.toHttpUrl().host
      val output = cacheHandler.getOrCreateCacheFile(url)!!
      val request = createFileDownloadRequest(url, file = output)
      activeDownloads.put(url, request)

      val mockSite = Mockito.mock(Site::class.java)
      val chunkDownloaderSiteProperties = ChunkDownloaderSiteProperties(
        enabled = true,
        siteSendsCorrectFileSizeInBytes = true
      )
      whenever(siteResolver.findSiteForUrl(host)).thenReturn(mockSite)
      whenever(mockSite.getChunkDownloaderSiteProperties()).thenReturn(chunkDownloaderSiteProperties)

      val testObserver = partialContentSupportChecker.check(url)
        .test()

      Thread.sleep(150)
      request.cancelableDownload.cancel()

      val (events, errors, completes) = testObserver
        .awaitDone(MAX_AWAIT_TIME_SECONDS, TimeUnit.SECONDS)
        .events

      assertTrue(completes.isEmpty())
      assertTrue(events.isEmpty())
      assertEquals(1, errors.size)
      assertTrue(errors.first() is SocketException)
      assertTrue((errors.first() as SocketException).message.equals("Socket closed", ignoreCase = true))

      val state = activeDownloads.get(url)!!.cancelableDownload.getState()
      assertTrue(state is DownloadState.Canceled)
    }
  }

  @Test
  fun `test request stop`() {
    withServer { server ->
      server.enqueue(
        MockResponse()
          .setResponseCode(200)
          .addHeader("Accept-Ranges", "bytes")
          .addHeader("Content-Length", "9999")
          .addHeader("CF-Cache-Status", "HIT")
          .setHeadersDelay(300L, TimeUnit.MILLISECONDS)
      )

      server.start()

      val url = server.url("/image1.jpg").toString()
      val host = url.toHttpUrl().host
      val output = cacheHandler.getOrCreateCacheFile(url)!!
      val request = createFileDownloadRequest(url, file = output)
      activeDownloads.put(url, request)

      val mockSite = Mockito.mock(Site::class.java)
      val chunkDownloaderSiteProperties = ChunkDownloaderSiteProperties(
        enabled = true,
        siteSendsCorrectFileSizeInBytes = true
      )
      whenever(siteResolver.findSiteForUrl(host)).thenReturn(mockSite)
      whenever(mockSite.getChunkDownloaderSiteProperties()).thenReturn(chunkDownloaderSiteProperties)

      val testObserver = partialContentSupportChecker.check(url)
        .test()

      Thread.sleep(150)
      request.cancelableDownload.stop()

      val (events, errors, completes) = testObserver
        .awaitDone(MAX_AWAIT_TIME_SECONDS, TimeUnit.SECONDS)
        .events

      assertTrue(completes.isEmpty())
      assertTrue(events.isEmpty())
      assertEquals(1, errors.size)
      assertTrue(errors.first() is SocketException)
      assertTrue((errors.first() as SocketException).message.equals("Socket closed", ignoreCase = true))

      val state = activeDownloads.get(url)!!.cancelableDownload.getState()
      assertTrue(state is DownloadState.Stopped)
    }
  }

  @Test
  fun `test when server returns 404`() {
    withServer { server ->
      server.enqueue(
        MockResponse().setResponseCode(404)
      )

      server.start()

      val url = server.url("/image1.jpg").toString()
      val host = url.toHttpUrl().host
      val output = cacheHandler.getOrCreateCacheFile(url)!!
      val request = createFileDownloadRequest(url, file = output)
      activeDownloads.put(url, request)

      val mockSite = Mockito.mock(Site::class.java)
      val chunkDownloaderSiteProperties = ChunkDownloaderSiteProperties(
        enabled = true,
        siteSendsCorrectFileSizeInBytes = true
      )
      whenever(siteResolver.findSiteForUrl(host)).thenReturn(mockSite)
      whenever(mockSite.getChunkDownloaderSiteProperties()).thenReturn(chunkDownloaderSiteProperties)

      partialContentSupportChecker.check(url)
        .test()
        .awaitCount(1)
        .assertError { error ->
          assertTrue(error is FileCacheException.FileNotFoundOnTheServerException)
          true
        }
        .assertNotComplete()
        .assertNoTimeout()
        .assertNoValues()
        .await()
    }
  }

  @Test
  fun `test everything ok`() {
    withServer { server ->
      server.enqueue(
        MockResponse()
          .setResponseCode(200)
          .addHeader("Accept-Ranges", "bytes")
          .addHeader("Content-Length", "9999")
          .addHeader("CF-Cache-Status", "HIT")
      )

      server.start()

      val url = server.url("/image1.jpg").toString()
      val host = url.toHttpUrl().host
      val output = cacheHandler.getOrCreateCacheFile(url)!!
      val request = createFileDownloadRequest(url, file = output)
      activeDownloads.put(url, request)

      val mockSite = Mockito.mock(Site::class.java)
      val chunkDownloaderSiteProperties = ChunkDownloaderSiteProperties(
        enabled = true,
        siteSendsCorrectFileSizeInBytes = true
      )

      whenever(siteResolver.findSiteForUrl(host)).thenReturn(mockSite)
      whenever(mockSite.getChunkDownloaderSiteProperties()).thenReturn(chunkDownloaderSiteProperties)

      partialContentSupportChecker.check(url)
        .test()
        .awaitCount(1)
        .assertValue { value ->
          assertTrue(value.supportsPartialContentDownload)
          assertFalse(value.notFoundOnServer)
          assertEquals(9999L, value.length)
          true
        }
        .assertComplete()
        .assertNoErrors()
        .assertNoTimeout()
        .await()
    }
  }

  companion object {
    private const val MAX_AWAIT_TIME_SECONDS = 5L
  }
}