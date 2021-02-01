package com.github.k1rakishou.chan.core.usecase

import android.text.SpannableString
import com.github.k1rakishou.chan.core.base.okhttp.CloudFlareHandlerInterceptor
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.net.HtmlReaderRequest
import com.github.k1rakishou.chan.core.site.parser.search.SimpleCommentParser
import com.github.k1rakishou.chan.core.site.sites.search.Chan4SearchParams
import com.github.k1rakishou.chan.core.site.sites.search.FoolFuukaSearchParams
import com.github.k1rakishou.chan.core.site.sites.search.FuukaSearchParams
import com.github.k1rakishou.chan.core.site.sites.search.SearchError
import com.github.k1rakishou.chan.core.site.sites.search.SearchParams
import com.github.k1rakishou.chan.core.site.sites.search.SearchResult
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.ModularResult.Companion.Try
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_spannable.BackgroundColorSpanHashed
import com.github.k1rakishou.core_spannable.ForegroundColorSpanHashed
import com.github.k1rakishou.core_themes.ChanTheme
import com.github.k1rakishou.core_themes.ThemeEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.regex.Pattern

class GlobalSearchUseCase(
  private val siteManager: SiteManager,
  private val themeEngine: ThemeEngine,
  private val simpleCommentParser: SimpleCommentParser
) : ISuspendUseCase<SearchParams, SearchResult> {

  override suspend fun execute(parameter: SearchParams): SearchResult {
    return try {
      withContext(Dispatchers.IO) { doSearch(parameter) }
    } catch (error: Throwable) {
      SearchResult.Failure(SearchError.UnknownError(error))
    }
  }

  @Suppress("MoveVariableDeclarationIntoWhen")
  private suspend fun doSearch(parameter: SearchParams): SearchResult {
    siteManager.awaitUntilInitialized()

    val site = siteManager.bySiteDescriptor(parameter.siteDescriptor)
    if (site == null) {
      Logger.e(TAG, "doSearch() Failed to find ${parameter.siteDescriptor}")
      return SearchResult.Failure(SearchError.SiteNotFound(parameter.siteDescriptor))
    }

    val result = Try { site.actions().search(parameter) }

    val htmlReaderResponse =  when (result) {
      is ModularResult.Value -> result.value
      is ModularResult.Error -> {
        Logger.e(TAG, "doSearch() Unknown error", result.error)
        return SearchResult.Failure(SearchError.UnknownError(result.error))
      }
    }

    when (htmlReaderResponse) {
      is HtmlReaderRequest.HtmlReaderResponse.Success -> {
        if (htmlReaderResponse.result is SearchResult.Failure) {
          val searchError = htmlReaderResponse.result.searchError
          Logger.d(TAG, "doSearch() Failure, searchError=$searchError")

          return htmlReaderResponse.result
        }

        Logger.d(TAG, "doSearch() Success")
        return processFoundSearchEntries(htmlReaderResponse.result as SearchResult.Success)
      }
      is HtmlReaderRequest.HtmlReaderResponse.ServerError -> {
        Logger.e(TAG, "doSearch() ServerError: ${htmlReaderResponse.statusCode}")
        return SearchResult.Failure(SearchError.ServerError(htmlReaderResponse.statusCode))
      }
      is HtmlReaderRequest.HtmlReaderResponse.UnknownServerError -> {
        if (htmlReaderResponse.isCloudFlareException()) {
          val cloudFlareDetectedException =
            htmlReaderResponse.error as CloudFlareHandlerInterceptor.CloudFlareDetectedException
          val searchError = SearchError.CloudFlareDetectedError(cloudFlareDetectedException.requestUrl)

          return SearchResult.Failure(searchError)
        }

        Logger.e(TAG, "doSearch() UnknownServerError", htmlReaderResponse.error)
        return SearchResult.Failure(SearchError.UnknownError(htmlReaderResponse.error))
      }
      is HtmlReaderRequest.HtmlReaderResponse.ParsingError -> {
        Logger.d(TAG, "doSearch() ParsingError", htmlReaderResponse.error)
        return SearchResult.Failure(SearchError.UnknownError(htmlReaderResponse.error))
      }
    }
  }

  private fun processFoundSearchEntries(searchResult: SearchResult.Success): SearchResult {
    val theme = themeEngine.chanTheme

    searchResult.searchEntries.forEach { searchEntry ->
      searchEntry.posts.forEach { searchEntryPost ->
        searchEntryPost.commentRaw?.let { commentRaw ->
          val parsedComment = simpleCommentParser.parseComment(commentRaw.toString()) ?: ""
          val spannedComment = SpannableString(parsedComment)

          findAllQueryEntriesInsideSpannableStringAndMarkThem(
            inputQueries = getAllQueries(searchResult.searchParams),
            spannableString = spannedComment,
            theme = theme
          )

          findAllQuotesAndMarkThem(spannedComment, theme)

          commentRaw.clear()
          commentRaw.append(spannedComment)
        }

        searchEntryPost.subject?.let { subject ->
          val spannedSubject = SpannableString(subject)

          findAllQueryEntriesInsideSpannableStringAndMarkThem(
            inputQueries = getAllQueries(searchResult.searchParams),
            spannableString = spannedSubject,
            theme = theme
          )

          subject.clear()
          subject.append(spannedSubject)
        }
      }
    }

    return searchResult
  }

  private fun findAllQuotesAndMarkThem(spannedComment: SpannableString, theme: ChanTheme): Boolean {
    val matcher = SIMPLE_QUOTE_PATTERN.matcher(spannedComment)
    var found = false

    while (matcher.find()) {
      val span = ForegroundColorSpanHashed(theme.postLinkColor)
      spannedComment.setSpan(span, matcher.start(), matcher.end(), 0)

      found = true
    }

    return found
  }

  private fun getAllQueries(searchParams: SearchParams): Set<String> {
    val queries = mutableSetOf<String>()

    when (searchParams) {
      is Chan4SearchParams -> {
        queries += searchParams.query
      }
      is FuukaSearchParams -> {
        if (searchParams.query.isNotEmpty()) {
          queries += searchParams.query
        }

        if (searchParams.subject.isNotEmpty()) {
          queries += searchParams.subject
        }
      }
      is FoolFuukaSearchParams -> {
        if (searchParams.query.isNotEmpty()) {
          queries += searchParams.query
        }

        if (searchParams.subject.isNotEmpty()) {
          queries += searchParams.subject
        }
      }
      else -> throw IllegalStateException("Unknown searchParams type: ${searchParams.javaClass.simpleName}")
    }

    return queries
  }

  private fun findAllQueryEntriesInsideSpannableStringAndMarkThem(
    inputQueries: Collection<String>,
    spannableString: SpannableString,
    theme: ChanTheme
  ) {
    val queries = inputQueries
      .filter { query -> query.isNotEmpty() && query.length <= spannableString.length }

    if (queries.isEmpty()) {
      return
    }

    for (query in queries) {
      var offset = 0
      val spans = mutableListOf<SpanToAdd>()

      while (offset < spannableString.length) {
        if (query[0].equals(spannableString[offset], ignoreCase = true)) {
          val compared = compare(query, spannableString, offset)
          if (compared < 0) {
            break
          }

          if (compared == query.length) {
            spans += SpanToAdd(offset, query.length, BackgroundColorSpanHashed(theme.accentColor))
          }

          offset += compared
          continue
        }

        ++offset
      }

      spans.forEach { spanToAdd ->
        spannableString.setSpan(
          spanToAdd.span,
          spanToAdd.position,
          spanToAdd.position + spanToAdd.length,
          0
        )
      }
    }
  }

  private fun compare(query: String, parsedComment: CharSequence, currentPosition: Int): Int {
    var compared = 0

    for (index in query.indices) {
      val ch = parsedComment.getOrNull(currentPosition + index)
        ?: return -1

      if (!query[index].equals(ch, ignoreCase = true)) {
        return compared
      }

      ++compared
    }

    return compared
  }

  private data class SpanToAdd(
    val position: Int,
    val length: Int,
    val span: BackgroundColorSpanHashed
  )

  companion object {
    private const val TAG = "GlobalSearchUseCase"

    private val SIMPLE_QUOTE_PATTERN = Pattern.compile(">>\\d+")
  }
}