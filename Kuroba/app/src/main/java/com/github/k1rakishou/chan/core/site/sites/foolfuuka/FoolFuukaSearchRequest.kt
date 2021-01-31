package com.github.k1rakishou.chan.core.site.sites.foolfuuka

import android.text.SpannableStringBuilder
import com.github.k1rakishou.chan.core.base.okhttp.ProxiedOkHttpClient
import com.github.k1rakishou.chan.core.net.HtmlReaderRequest
import com.github.k1rakishou.chan.core.site.sites.search.FoolFuukaSearchParams
import com.github.k1rakishou.chan.core.site.sites.search.PageCursor
import com.github.k1rakishou.chan.core.site.sites.search.SearchEntry
import com.github.k1rakishou.chan.core.site.sites.search.SearchEntryPost
import com.github.k1rakishou.chan.core.site.sites.search.SearchError
import com.github.k1rakishou.chan.core.site.sites.search.SearchResult
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.common.groupOrNull
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_parser.html.ExtractedAttributeValues
import com.github.k1rakishou.core_parser.html.KurobaHtmlParserCollector
import com.github.k1rakishou.core_parser.html.KurobaHtmlParserCommandBufferBuilder
import com.github.k1rakishou.core_parser.html.KurobaHtmlParserCommandExecutor
import com.github.k1rakishou.core_parser.html.KurobaMatcher
import com.github.k1rakishou.core_parser.html.KurobaParserCommandBuilder
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.joda.time.DateTime
import org.jsoup.nodes.Document
import java.util.regex.Pattern

class FoolFuukaSearchRequest(
  private val searchParams: FoolFuukaSearchParams,
  request: Request,
  proxiedOkHttpClient: ProxiedOkHttpClient
) : HtmlReaderRequest<SearchResult>(request, proxiedOkHttpClient) {
  private val commandBuffer = KurobaHtmlParserCommandBufferBuilder<FoolFuukaSearchPageCollector>()
    .start {
      html()

      nest {
        body()

        nest {
          div(matchableBuilderFunc = { className(KurobaMatcher.PatternMatcher.stringEquals("container-fluid")) })

          nest {
            div(matchableBuilderFunc = { attr("role", KurobaMatcher.PatternMatcher.stringEquals("main")) })


            nest {
              // <article class="clearfix thread"> ... </article> may not exist when there are no
              // entries for a query
              executeIf(
                predicate = { tag(KurobaMatcher.TagMatcher.tagWithAttributeMatcher("article", "class", "clearfix thread")) },
                resetNodeIndex = true
              ) {
                heading(
                  headingNum = 3,
                  matchableBuilderFunc = { className(KurobaMatcher.PatternMatcher.stringEquals("section_title")) },
                  attrExtractorBuilderFunc = { extractHtml() },
                  extractorFunc = { node, extractedAttributeValues, foolFuukaSearchPageCollector ->
                    foolFuukaSearchPageCollector.foundEntriesRaw =
                      extractedAttributeValues.getHtml()
                  }
                )

                article(matchableBuilderFunc = { attr("class", KurobaMatcher.PatternMatcher.stringEquals("clearfix thread")) })

                nest {
                  tag(
                    tagName = "aside",
                    matchableBuilderFunc = { attr("class", KurobaMatcher.PatternMatcher.stringEquals("posts")) }
                  )

                  nest {
                    loop {
                      parseSinglePost()
                    }
                  }
                }

                parsePages()
              }
            }
          }
        }
      }
    }.build()

  private fun KurobaParserCommandBuilder<FoolFuukaSearchPageCollector>.parseSinglePost(): KurobaParserCommandBuilder<FoolFuukaSearchPageCollector> {
    div(matchableBuilderFunc = { className(KurobaMatcher.PatternMatcher.stringContains("post stub stub_doc_id")) })

    nest {
      tag(
        tagName = "button",
        matchableBuilderFunc = { className(KurobaMatcher.PatternMatcher.stringEquals("btn-toggle-post")) },
        attrExtractorBuilderFunc = { extractAttrValueByKey("data-thread-num") },
        extractorFunc = { node, extractedAttributeValues, foolFuukaSearchPageCollector ->
          foolFuukaSearchPageCollector.searchResults.add(SearchEntryPostBuilder())

          foolFuukaSearchPageCollector.lastOrNull()!!.threadNo =
            extractedAttributeValues.getAttrValue("data-thread-num")?.toLongOrNull()
        }
      )
    }

    article(
      matchableBuilderFunc = { attr("class", KurobaMatcher.PatternMatcher.stringContains("post doc_id")) },
      attrExtractorBuilderFunc = {
        extractAttrValueByKey("id")
        extractAttrValueByKey("data-board")
      },
      extractorFunc = { _, extractedAttributeValues, collector ->
        extractPostNoAndBoardCode(extractedAttributeValues, collector)
      }
    )

    nest {
      div(matchableBuilderFunc = { className(KurobaMatcher.PatternMatcher.stringEquals("post_wrapper")) })

      nest {
        // thread_image_box may not exist if a post has no images
        executeIf(predicate = { className(KurobaMatcher.PatternMatcher.stringEquals("thread_image_box")) }) {
          div(matchableBuilderFunc = { className(KurobaMatcher.PatternMatcher.stringEquals("thread_image_box")) })

          nest {
            a(matchableBuilderFunc = { className(KurobaMatcher.PatternMatcher.stringEquals("thread_image_link")) })

            nest {
              // Different archives use different tags for thumbnail url
              val imgTagAttrKeys = listOf("src", "data-src")

              tag(
                tagName = "img",
                matchableBuilderFunc = { tag(KurobaMatcher.TagMatcher.tagHasAnyOfAttributes(imgTagAttrKeys)) },
                attrExtractorBuilderFunc = { extractAttrValueByAnyKey(imgTagAttrKeys) },
                extractorFunc = { _, extractedAttributeValues, testCollector ->
                  testCollector.lastOrNull()?.let { postBuilder ->
                    val imageThumbnailUrl =
                      requireNotNull(extractedAttributeValues.getAnyAttrValue(imgTagAttrKeys)).toHttpUrl()

                    postBuilder.postImageUrlRawList += imageThumbnailUrl
                  }
                }
              )
            }
          }
        }

        header()

        nest {
          div(matchableBuilderFunc = { className(KurobaMatcher.PatternMatcher.stringEquals("post_data")) })

          nest {
            // post_title exists on archived.moe when the h2 tag is empty but does not exist on fireden.net
            executeIf(predicate = { className(KurobaMatcher.PatternMatcher.stringEquals("post_title")) }) {
              heading(
                headingNum = 2,
                matchableBuilderFunc = { className(KurobaMatcher.PatternMatcher.stringEquals("post_title")) },
                attrExtractorBuilderFunc = { extractText() },
                extractorFunc = { _, extractedAttributeValues, testCollector ->
                  testCollector.lastOrNull()?.let { postBuilder ->
                    postBuilder.subject = extractedAttributeValues.getText()
                  }
                }
              )
            }

            span(matchableBuilderFunc = { className(KurobaMatcher.PatternMatcher.stringEquals("post_poster_data")) })

            nest {
              executeIf(predicate = { className(KurobaMatcher.PatternMatcher.stringEquals("post_author")) }) {
                span(
                  matchableBuilderFunc = { className(KurobaMatcher.PatternMatcher.stringEquals("post_author")) },
                  attrExtractorBuilderFunc = { extractText() },
                  extractorFunc = { _, extractedAttributeValues, testCollector ->
                    testCollector.lastOrNull()?.let { postBuilder ->
                      val name = extractedAttributeValues.getText()
                      if (name.isNullOrEmpty()) {
                        return@let
                      }

                      postBuilder.name = name
                    }
                  }
                )
              }

              executeIf(predicate = { className(KurobaMatcher.PatternMatcher.stringEquals("post_tripcode")) }) {
                span(
                  matchableBuilderFunc = { className(KurobaMatcher.PatternMatcher.stringEquals("post_tripcode")) },
                  attrExtractorBuilderFunc = { extractText() },
                  extractorFunc = { _, extractedAttributeValues, testCollector ->
                    testCollector.lastOrNull()?.let { postBuilder ->
                      val tripcode = extractedAttributeValues.getText()
                      if (tripcode.isNullOrEmpty()) {
                        return@let
                      }

                      postBuilder.tripcode = tripcode
                    }
                  }
                )
              }
            }

            span(matchableBuilderFunc = { className(KurobaMatcher.PatternMatcher.stringEquals("time_wrap")) })

            nest {
              tag(
                tagName = "time",
                matchableBuilderFunc = { anyTag() },
                attrExtractorBuilderFunc = { extractAttrValueByKey("datetime") },
                extractorFunc = { _, extractedAttributeValues, testCollector ->
                  testCollector.lastOrNull()?.let { postBuilder ->
                    val dateTimeRaw = requireNotNull(extractedAttributeValues.getAttrValue("datetime"))
                    postBuilder.dateTime = DateTime.parse(dateTimeRaw)
                  }
                }
              )
            }

            a(
              matchableBuilderFunc = { attr("data-function", KurobaMatcher.PatternMatcher.stringEquals("quote")) },
              attrExtractorBuilderFunc = { extractAttrValueByKey("href") },
              extractorFunc = { _, extractedAttributeValues, testCollector ->
                tryExtractIsOpFlag(testCollector, extractedAttributeValues)
              }
            )
          }
        }

        div(
          matchableBuilderFunc = { className(KurobaMatcher.PatternMatcher.stringContains("text")) },
          attrExtractorBuilderFunc = { extractHtml() },
          extractorFunc = { _, extractedAttributeValues, testCollector ->
            testCollector.lastOrNull()?.let { postBuilder ->
              postBuilder.commentRaw = extractedAttributeValues.getHtml()
            }
          }
        )
      }
    }

    return this
  }

  private fun KurobaParserCommandBuilder<FoolFuukaSearchPageCollector>.parsePages(): KurobaParserCommandBuilder<FoolFuukaSearchPageCollector> {
    div(matchableBuilderFunc = { className(KurobaMatcher.PatternMatcher.stringEquals("paginate")) })

    return nest {
      tag(
        tagName = "ul",
        matchableBuilderFunc = { tag(KurobaMatcher.TagMatcher.tagNoAttributesMatcher()) }
      )

      nest {
        loop {
          tag(
            tagName = "li",
            matchableBuilderFunc = { tag(KurobaMatcher.TagMatcher.tagAnyAttributeMatcher()) }
          )

          nest {
            executeIf(predicate = { attr("href", KurobaMatcher.PatternMatcher.patternFind(PAGE_URL_PATTERN)) }) {

              a(
                matchableBuilderFunc = { attr("href", KurobaMatcher.PatternMatcher.patternFind(PAGE_URL_PATTERN)) },
                attrExtractorBuilderFunc = {
                  extractText()
                  extractAttrValueByKey("href")
                },
                extractorFunc = { _, extractedAttributeValues, testCollector ->
                  extractPages(extractedAttributeValues, testCollector)
                }
              )
            }
          }
        }
      }
    }
  }

  private fun extractPages(
    extractedAttributeValues: ExtractedAttributeValues,
    testCollector: FoolFuukaSearchPageCollector
  ) {
    val pageUrl = extractedAttributeValues.getAttrValue("href")
    val tagText = extractedAttributeValues.getText()

    if (pageUrl != null && tagText != null) {
      val pageUrlMatcher = PAGE_URL_PATTERN.matcher(pageUrl)
      if (pageUrlMatcher.find()) {
        val pageNumberMatcher = NUMBER_PATTERN.matcher(tagText)
        if (pageNumberMatcher.matches()) {
          val possiblePage = pageUrlMatcher.group(1)?.toIntOrNull()
          if (possiblePage != null) {
            testCollector.pages += possiblePage
          }
        }
      }
    }
  }

  private fun extractPostNoAndBoardCode(
    extractedAttributeValues: ExtractedAttributeValues,
    collector: FoolFuukaSearchPageCollector
  ) {
    val postId = extractedAttributeValues.getAttrValue("id")?.toLongOrNull()
    val boardCode = extractedAttributeValues.getAttrValue("data-board")
      ?: searchParams.boardDescriptor.boardCode


    collector.lastOrNull()!!.siteName = searchParams.boardDescriptor.siteName()
    collector.lastOrNull()!!.boardCode = boardCode
    collector.lastOrNull()!!.postNo = postId
  }

  private fun tryExtractIsOpFlag(
    testCollector: FoolFuukaSearchPageCollector,
    extractedAttributeValues: ExtractedAttributeValues
  ) {
    testCollector.lastOrNull()?.let { postBuilder ->
      val postLink = requireNotNull(extractedAttributeValues.getAttrValue("href"))

      val matcher = POST_LINK_PATTERN.matcher(postLink)
      if (!matcher.find()) {
        throw KurobaHtmlParserCommandExecutor.HtmlParsingException(
          "Bad post link: \'$postLink\'"
        )
      }

      val threadNo = matcher.group(1)?.toLongOrNull()
      val postNo = matcher.group(2)?.toLongOrNull()

      if (threadNo == null || postNo == null) {
        throw KurobaHtmlParserCommandExecutor.HtmlParsingException(
          "Bad post link: \'$postLink\', threadNo: $threadNo, postNo: $postNo"
        )
      }

      postBuilder.isOp = threadNo == postNo

      check(postBuilder.threadNo == threadNo) {
        "Bad threadNo! postBuilder.threadNo=${postBuilder.threadNo}, threadNo=$threadNo"
      }
      check(postBuilder.postNo == postNo) {
        "Bad threadNo! postBuilder.postNo=${postBuilder.postNo}, postNo=$postNo"
      }
    }
  }

  override suspend fun readHtml(document: Document): SearchResult {
    val collector = FoolFuukaSearchPageCollector()
    val parserCommandExecutor = KurobaHtmlParserCommandExecutor<FoolFuukaSearchPageCollector>()

    try {
      parserCommandExecutor.executeCommands(
        document,
        commandBuffer,
        collector
      )
    } catch (error: Throwable) {
      Logger.e(TAG, "parserCommandExecutor.executeCommands() error", error)
      return SearchResult.Failure(SearchError.HtmlParsingError(error.errorMessageOrClassName()))
    }

    val searchEntries = collector.searchResults.mapNotNull { searchEntryPostBuilder ->
      if (searchEntryPostBuilder.hasMissingInfo()) {
        return@mapNotNull null
      }

      return@mapNotNull SearchEntry(listOf(searchEntryPostBuilder.toSearchEntryPost()))
    }

    val nextPageCursor = getNextPageCursor(
      searchParams.page,
      collector
    )

    val entriesCount = parseFoundEntries(collector.foundEntriesRaw)

    val successResult = SearchResult.Success(
      searchParams,
      searchEntries,
      nextPageCursor,
      entriesCount
    )

    Logger.d(TAG, "searchParams=${searchParams}, foundEntriesPage=${successResult.searchEntries.size}, " +
      "pageCursor=${successResult.nextPageCursor}, totalFoundEntries=${successResult.totalFoundEntries}")

    return successResult
  }

  private fun parseFoundEntries(foundEntriesRaw: String?): Int? {
    if (foundEntriesRaw == null) {
      return null
    }

    val matcher = ACTUAL_ENTRIES_PATTERN.matcher(foundEntriesRaw)
    if (!matcher.find()) {
      return null
    }

    val actualEntriesString = matcher.groupOrNull(1)
      ?: return null

    val matcher1 = TOO_MANY_ENTRIES_FOUND_AMOUNT_PATTERN.matcher(actualEntriesString)
    if (matcher1.find()) {
      return matcher1.groupOrNull(1)?.toIntOrNull()
    }

    val matcher2 = REGULAR_ENTRIES_FOUND_AMOUNT_PATTERN.matcher(actualEntriesString)
    if (matcher2.find()) {
      return matcher2.groupOrNull(1)?.toIntOrNull()
    }

    return null
  }

  private fun getNextPageCursor(currentPage: Int?, collector: FoolFuukaSearchPageCollector): PageCursor {
    val currentPageIndex = if (currentPage == null) {
      0
    } else {
      collector.pages.indexOfFirst { page -> page == currentPage }
    }

    if (currentPageIndex >= 0) {
      val nextPageValue = collector.pages.getOrNull(currentPageIndex + 1)
      if (nextPageValue != null) {
        return PageCursor.Page(nextPageValue)
      }
    }

    return PageCursor.End
  }

  internal data class FoolFuukaSearchPageCollector(
    val searchResults: MutableList<SearchEntryPostBuilder> = mutableListOf(),
    val pages: MutableList<Int> = mutableListOf(),
    var foundEntriesRaw: String? = null
  ) : KurobaHtmlParserCollector {
    fun lastOrNull(): SearchEntryPostBuilder? = searchResults.lastOrNull()
  }

  internal class SearchEntryPostBuilder {
    var siteName: String? = null
    var boardCode: String? = null
    var threadNo: Long? = null
    var postNo: Long? = null

    var isOp: Boolean? = null
    var name: String? = null
    var tripcode: String? = null
    var subject: String? = null
    var dateTime: DateTime? = null
    var commentRaw: String? = null

    val postImageUrlRawList = mutableListOf<HttpUrl>()

    val postDescriptor: PostDescriptor?
      get() {
        if (siteName == null || boardCode == null || threadNo == null || postNo == null) {
          return null
        }

        return PostDescriptor.Companion.create(siteName!!, boardCode!!, threadNo!!, postNo!!)
      }

    fun threadDescriptor(): ChanDescriptor.ThreadDescriptor {
      checkNotNull(isOp) { "isOp is null!" }
      checkNotNull(postDescriptor) { "postDescriptor is null!" }
      check(isOp!!) { "Must be OP!" }

      return postDescriptor!!.threadDescriptor()
    }

    fun hasMissingInfo(): Boolean {
      return isOp == null || postDescriptor == null || dateTime == null
    }

    fun toSearchEntryPost(): SearchEntryPost {
      if (hasMissingInfo()) {
        throw IllegalStateException("Some info is missing! isOp=$isOp, postDescriptor=$postDescriptor, " +
          "dateTime=$dateTime, commentRaw=$commentRaw")
      }

      return SearchEntryPost(
        isOp!!,
        buildFullName(name, tripcode),
        subject?.let { SpannableStringBuilder(it) },
        postDescriptor!!,
        dateTime!!,
        postImageUrlRawList,
        commentRaw?.let { SpannableStringBuilder(it) }
      )
    }

    private fun buildFullName(name: String?, tripcode: String?): SpannableStringBuilder? {
      if (name.isNullOrEmpty() && tripcode.isNullOrEmpty()) {
        return null
      }

      val ssb = SpannableStringBuilder()

      if (name != null) {
        ssb.append(name)
      }

      if (tripcode != null) {
        if (ssb.isNotEmpty()) {
          ssb.append(" ")
        }

        ssb.append("'")
          .append(tripcode)
          .append("'")
      }

      return ssb
    }

    override fun toString(): String {
      return "SearchEntryPostBuilder(isOp=$isOp, postDescriptor=$postDescriptor, dateTime=${dateTime?.millis}, " +
        "postImageUrlRawList=$postImageUrlRawList, commentRaw=$commentRaw)"
    }

  }

  companion object {
    private const val TAG = "FoolFuukaSearchRequest"

    private val POST_LINK_PATTERN = Pattern.compile("thread\\/(\\d+)\\/#q(\\d+)")
    private val PAGE_URL_PATTERN = Pattern.compile("/page/(\\d+)/$")
    private val NUMBER_PATTERN = Pattern.compile("\\d+")
    private val ACTUAL_ENTRIES_PATTERN = Pattern.compile("<small>(.*)<\\/small>")

    private val TOO_MANY_ENTRIES_FOUND_AMOUNT_PATTERN = Pattern.compile("(\\d+) of \\d+")
    private val REGULAR_ENTRIES_FOUND_AMOUNT_PATTERN = Pattern.compile("(\\d+) results found.\$")
  }

}