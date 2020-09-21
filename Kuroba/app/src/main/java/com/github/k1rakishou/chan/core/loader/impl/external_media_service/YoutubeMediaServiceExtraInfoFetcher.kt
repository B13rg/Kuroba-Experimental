package com.github.k1rakishou.chan.core.loader.impl.external_media_service

import android.graphics.BitmapFactory
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.loader.impl.post_comment.CommentPostLinkableSpan
import com.github.k1rakishou.chan.core.loader.impl.post_comment.ExtraLinkInfo
import com.github.k1rakishou.chan.core.loader.impl.post_comment.LinkInfoRequest
import com.github.k1rakishou.chan.core.loader.impl.post_comment.SpanUpdateBatch
import com.github.k1rakishou.chan.core.settings.ChanSettings
import com.github.k1rakishou.chan.utils.AndroidUtils
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.chan.utils.Logger
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.groupOrNull
import com.github.k1rakishou.model.data.video_service.MediaServiceLinkExtraContent
import com.github.k1rakishou.model.data.video_service.MediaServiceType
import com.github.k1rakishou.model.repository.MediaServiceLinkExtraContentRepository
import io.reactivex.Single
import kotlinx.coroutines.rx2.rxSingle
import java.util.regex.Pattern

internal class YoutubeMediaServiceExtraInfoFetcher(
  private val mediaServiceLinkExtraContentRepository: MediaServiceLinkExtraContentRepository
) : ExternalMediaServiceExtraInfoFetcher {

  override val mediaServiceType: MediaServiceType
    get() = MediaServiceType.Youtube

  override fun isEnabled(): Boolean {
    return ChanSettings.parseYoutubeTitlesAndDuration.get()
  }

  override fun isCached(videoId: String): Single<Boolean> {
    return rxSingle {
      return@rxSingle mediaServiceLinkExtraContentRepository.isCached(videoId)
        .unwrap()
    }
  }

  override fun fetch(
    requestUrl: String,
    linkInfoRequest: LinkInfoRequest
  ): Single<ModularResult<SpanUpdateBatch>> {
    BackgroundUtils.ensureBackgroundThread()

    return rxSingle {
      if (!ChanSettings.parseYoutubeTitlesAndDuration.get()) {
        return@rxSingle ModularResult.value(
          SpanUpdateBatch(
            requestUrl,
            ExtraLinkInfo.Success(null, null),
            linkInfoRequest.oldPostLinkableSpans,
            youtubeIcon
          )
        )
      }

      val getLinkExtraContentResult = mediaServiceLinkExtraContentRepository.getLinkExtraContent(
        mediaServiceType,
        requestUrl,
        linkInfoRequest.videoId
      )

      return@rxSingle processResponse(
        requestUrl,
        getLinkExtraContentResult,
        linkInfoRequest.oldPostLinkableSpans
      )
    }
  }

  private fun processResponse(
    url: String,
    mediaServiceLinkExtraContentResult: ModularResult<MediaServiceLinkExtraContent>,
    oldPostLinkableSpans: List<CommentPostLinkableSpan>
  ): ModularResult<SpanUpdateBatch> {
    BackgroundUtils.ensureBackgroundThread()

    return ModularResult.Try {
      val extraLinkInfo = when (mediaServiceLinkExtraContentResult) {
        is ModularResult.Error -> ExtraLinkInfo.Error
        is ModularResult.Value -> {
          if (mediaServiceLinkExtraContentResult.value.videoDuration == null
            && mediaServiceLinkExtraContentResult.value.videoDuration == null) {
            ExtraLinkInfo.NotAvailable
          } else {
            ExtraLinkInfo.Success(
              mediaServiceLinkExtraContentResult.value.videoTitle,
              mediaServiceLinkExtraContentResult.value.videoDuration
            )
          }
        }
      }

      return@Try SpanUpdateBatch(
        url,
        extraLinkInfo,
        oldPostLinkableSpans,
        youtubeIcon
      )
    }.peekError { error ->
      Logger.e(TAG, "Error while processing response", error)
    }
  }

  override fun linkMatchesToService(link: String): Boolean {
    return youtubeLinkPattern.matcher(link).matches()
  }

  override fun extractLinkUniqueIdentifier(link: String): String {
    return extractVideoId(link)
  }

  override fun formatRequestUrl(link: String): String {
    return formatGetYoutubeLinkInfoUrl(extractVideoId(link))
  }

  private fun extractVideoId(link: String): String {
    val matcher = youtubeLinkPattern.matcher(link)
    if (!matcher.find()) {
      throw IllegalStateException("Couldn't match link ($link) with the current service." +
        " Did you forget to call linkMatchesToService() first?")
    }

    return checkNotNull(matcher.groupOrNull(1)) {
      "Couldn't extract videoId out of the input link ($link)"
    }
  }

  private fun formatGetYoutubeLinkInfoUrl(videoId: String): String {
    return buildString {
      append("https://www.googleapis.com/youtube/v3/videos?part=snippet%2CcontentDetails&id=")

      append(videoId)
      append("&fields=items%28id%2Csnippet%28title%29%2CcontentDetails%28duration%29%29&key=")

      append(ChanSettings.parseYoutubeAPIKey.get())
    }
  }

  companion object {
    private const val TAG = "YoutubeMediaServiceExtraInfoFetcher"

    private val youtubeLinkPattern =
      Pattern.compile("\\b\\w+://(?:youtu\\.be/|[\\w.]*youtube[\\w.]*/.*?(?:v=|\\bembed/|\\bv/))([\\w\\-]{11})(.*)\\b")
    private val youtubeIcon = BitmapFactory.decodeResource(AndroidUtils.getRes(), R.drawable.youtube_icon)
  }
}