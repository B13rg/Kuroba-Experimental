/*
 * KurobaEx - *chan browser https://github.com/K1rakishou/Kuroba-Experimental/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.k1rakishou.chan.features.reply

import android.content.Context
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.base.Debouncer
import com.github.k1rakishou.chan.core.helper.CommentEditingHistory
import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.BookmarksManager
import com.github.k1rakishou.chan.core.manager.ChanThreadManager
import com.github.k1rakishou.chan.core.manager.ReplyManager
import com.github.k1rakishou.chan.core.manager.SavedReplyManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.repository.LastReplyRepository
import com.github.k1rakishou.chan.core.site.Site
import com.github.k1rakishou.chan.core.site.SiteActions
import com.github.k1rakishou.chan.core.site.SiteAuthentication
import com.github.k1rakishou.chan.core.site.http.ReplyResponse
import com.github.k1rakishou.chan.ui.captcha.AuthenticationLayoutCallback
import com.github.k1rakishou.chan.ui.helper.PostHelper
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.board.ChanBoard
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.model.data.post.ChanSavedReply
import com.github.k1rakishou.model.util.ChanPostUtils
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext

class ReplyPresenter @Inject constructor(
  private val context: Context,
  private val replyManager: ReplyManager,
  private val savedReplyManager: SavedReplyManager,
  private val lastReplyRepository: LastReplyRepository,
  private val siteManager: SiteManager,
  private val boardManager: BoardManager,
  private val bookmarksManager: BookmarksManager,
  private val chanThreadManager: ChanThreadManager
) : AuthenticationLayoutCallback,
  CoroutineScope,
  CommentEditingHistory.CommentEditingHistoryListener {

  enum class Page {
    INPUT, AUTHENTICATION, LOADING
  }

  var page = Page.INPUT
    private set

  private var currentChanDescriptor: ChanDescriptor? = null
  private var previewOpen = false

  private val job = SupervisorJob()
  private val commentEditingHistory = CommentEditingHistory(this)

  private lateinit var callback: ReplyPresenterCallback
  private lateinit var chanBoard: ChanBoard
  private lateinit var site: Site

  private val highlightQuotesDebouncer = Debouncer(false)

  var isExpanded = false
    private set

  override val coroutineContext: CoroutineContext
    get() = job + Dispatchers.Main + CoroutineName("ReplyPresenter")

  fun create(callback: ReplyPresenterCallback) {
    this.callback = callback
  }

  suspend fun bindChanDescriptor(chanDescriptor: ChanDescriptor): Boolean {
    val thisSite = siteManager.bySiteDescriptor(chanDescriptor.siteDescriptor())
    if (thisSite == null) {
      Logger.e(TAG, "bindChanDescriptor couldn't find ${chanDescriptor.siteDescriptor()}")
      return false
    }

    val thisBoard = boardManager.byBoardDescriptor(chanDescriptor.boardDescriptor())
    if (thisBoard == null) {
      Logger.e(TAG, "bindChanDescriptor couldn't find ${chanDescriptor.boardDescriptor()}")
      return false
    }

    if (currentChanDescriptor != null) {
      unbindChanDescriptor()
    }

    this.chanBoard = thisBoard
    this.site = thisSite
    this.currentChanDescriptor = chanDescriptor

    callback.bindReplyImages(chanDescriptor)

    val stringId = if (chanDescriptor.isThreadDescriptor()) {
      R.string.reply_comment_thread
    } else {
      R.string.reply_comment_board
    }

    replyManager.awaitUntilFilesAreLoaded()
    callback.loadDraftIntoViews(chanDescriptor)

    if (thisBoard.maxCommentChars > 0) {
      callback.updateCommentCount(0, thisBoard.maxCommentChars, false)
    }

    callback.setCommentHint(getString(stringId))
    callback.showCommentCounter(thisBoard.maxCommentChars > 0)

    switchPage(Page.INPUT)
    return true
  }

  fun unbindReplyImages() {
    currentChanDescriptor?.let { chanDescriptor ->
      callback.unbindReplyImages(chanDescriptor)
    }
  }

  fun unbindChanDescriptor() {
    closeAll()
    currentChanDescriptor = null

    job.cancelChildren()
  }

  fun isCatalogReplyLayout(): Boolean? {
    if (currentChanDescriptor == null) {
      return null
    }

    return currentChanDescriptor is ChanDescriptor.CatalogDescriptor
  }

  fun onOpen(open: Boolean) {
    if (open) {
      callback.focusComment()
    }
  }

  fun onBack(): Boolean {
    if (page == Page.LOADING) {
      return true
    }

    if (page == Page.AUTHENTICATION) {
      switchPage(Page.INPUT)
      return true
    }

    if (callback.filesAreaOnBackPressed()) {
      return true
    }

    if (isExpanded) {
      onMoreClicked()
      return true
    }

    return false
  }

  fun onMoreClicked() {
    this.isExpanded = this.isExpanded.not()

    callback.setExpanded(expanded = isExpanded, isCleaningUp = false)
    callback.openNameOptions(isExpanded)

    if (currentChanDescriptor!!.isCatalogDescriptor()) {
      callback.openSubject(isExpanded)
    }

    val is4chan = chanBoard.boardDescriptor.siteDescriptor.is4chan()
    callback.openCommentQuoteButton(isExpanded)

    if (chanBoard.spoilers) {
      callback.openCommentSpoilerButton(isExpanded)
    }

    if (is4chan && chanBoard.boardCode() == "g") {
      callback.openCommentCodeButton(isExpanded)
    }

    if (is4chan && chanBoard.boardCode() == "sci") {
      callback.openCommentEqnButton(isExpanded)
      callback.openCommentMathButton(isExpanded)
    }

    if (is4chan && (chanBoard.boardCode() == "jp" || chanBoard.boardCode() == "vip")) {
      callback.openCommentSJISButton(isExpanded)
    }

    if (is4chan && chanBoard.boardCode() == "pol") {
      callback.openFlag(isExpanded)
    }
  }

  fun onAuthenticateCalled() {
    if (!site.actions().postRequiresAuthentication()) {
      return
    }

    if (!onPrepareToSubmit(true)) {
      return
    }

    switchPage(Page.AUTHENTICATION, useV2NoJsCaptcha = true, autoReply = false)
  }

  fun onSubmitClicked(longClicked: Boolean) {
    val chanDescriptor = currentChanDescriptor
      ?: return

    if (!onPrepareToSubmit(false)) {
      return
    }

    // only 4chan seems to have the post delay, this is a hack for that
    if (!chanDescriptor.siteDescriptor().is4chan() || longClicked) {
      submitOrAuthenticate()
      return
    }

    if (chanDescriptor.isThreadDescriptor()) {
      val hasAtLeastOneFile = replyManager.readReply(chanDescriptor) { reply -> reply.hasFiles() }

      val timeLeft = lastReplyRepository.getTimeUntilReply(
        chanDescriptor.boardDescriptor(),
        hasAtLeastOneFile
      )

      if (timeLeft < 0L) {
        submitOrAuthenticate()
      } else {
        val errorMessage = getString(R.string.reply_error_message_timer_reply, timeLeft)
        switchPage(Page.INPUT)
        callback.openMessage(errorMessage)
      }

      return
    }

    val timeLeft = lastReplyRepository.getTimeUntilThread(chanDescriptor.boardDescriptor())
    if (timeLeft < 0L) {
      submitOrAuthenticate()
    } else {
      val errorMessage = getString(R.string.reply_error_message_timer_thread, timeLeft)
      switchPage(Page.INPUT)
      callback.openMessage(errorMessage)
    }
  }

  private fun submitOrAuthenticate() {
    if (site.actions().postRequiresAuthentication()) {
      val token = callback.getTokenOrNull()
      if (token.isNullOrEmpty()) {
        switchPage(Page.AUTHENTICATION)
        return
      }

      onAuthenticationComplete(null, token, true)
      return
    }

    makeSubmitCall()
  }

  private fun onPrepareToSubmit(isAuthenticateOnly: Boolean): Boolean {
    val chanDescriptor = currentChanDescriptor
      ?: return false

    return replyManager.readReply(chanDescriptor) { reply ->
      callback.loadViewsIntoDraft(chanDescriptor)

      if (!isAuthenticateOnly && !reply.hasFiles() && reply.isCommentEmpty()) {
        callback.openMessage(getString(R.string.reply_comment_empty))
        return@readReply false
      }

      reply.resetCaptchaResponse()
      return@readReply true
    }
  }

  override fun onAuthenticationComplete(
    challenge: String?,
    response: String?,
    autoReply: Boolean
  ) {
    val chanDescriptor = currentChanDescriptor
      ?: return

    replyManager.readReply(chanDescriptor) { reply ->
      reply.initCaptchaInfo(challenge, response)
    }

    if (autoReply) {
      makeSubmitCall()
    } else {
      switchPage(Page.INPUT)
    }
  }

  override fun onAuthenticationFailed(error: Throwable) {
    callback.showAuthenticationFailedError(error)
    switchPage(Page.INPUT)
  }

  override fun onFallbackToV1CaptchaView(autoReply: Boolean) {
    callback.onFallbackToV1CaptchaView(autoReply)
  }

  fun updateCommentEditingHistory(commentInputState: CommentEditingHistory.CommentInputState) {
    commentEditingHistory.updateCommentEditingHistory(commentInputState)
  }

  fun onRevertChangeButtonClicked() {
    commentEditingHistory.onRevertChangeButtonClicked()
  }

  fun clearCommentChangeHistory(lastCommentState: CommentEditingHistory.CommentInputState) {
    commentEditingHistory.clear(lastCommentState)
  }

  override fun updateRevertChangeButtonVisibility(isBufferEmpty: Boolean) {
    callback.updateRevertChangeButtonVisibility(isBufferEmpty)
  }

  override fun restoreComment(prevCommentInputState: CommentEditingHistory.CommentInputState) {
    callback.restoreComment(prevCommentInputState)
  }

  fun updateCommentCounter(text: CharSequence?) {
    if (text == null) {
      return
    }

    if (chanBoard.maxCommentChars < 0) {
      return
    }

    val length = text.toString().toByteArray(UTF_8).size

    callback.updateCommentCount(
      length,
      chanBoard.maxCommentChars,
      length > chanBoard.maxCommentChars
    )
  }

  fun onSelectionChanged() {
    val chanDescriptor = currentChanDescriptor
      ?: return

    callback.loadViewsIntoDraft(chanDescriptor)
    highlightQuotes()
  }

  fun quote(post: ChanPost, withText: Boolean) {
    val comment = if (withText) {
      post.postComment.comment.toString()
    } else {
      null
    }

    handleQuote(post, comment)
  }

  fun quote(post: ChanPost, text: CharSequence) {
    handleQuote(post, text.toString())
  }

  private fun handleQuote(post: ChanPost, textQuote: String?) {
    val chanDescriptor = currentChanDescriptor
      ?: return

    callback.loadViewsIntoDraft(chanDescriptor)
    val selectStart = callback.selectionStart

    val resultLength = replyManager.readReply(chanDescriptor) { reply ->
      return@readReply reply.handleQuote(selectStart, post.postNo(), textQuote)
    }

    callback.loadDraftIntoViews(chanDescriptor)
    callback.adjustSelection(selectStart, resultLength)
    highlightQuotes()
  }

  private fun closeAll() {
    isExpanded = false
    previewOpen = false

    commentEditingHistory.reset()

    callback.highlightPostNos(emptySet())
    callback.openMessage(null)
    callback.setExpanded(expanded = false, isCleaningUp = true)
    callback.openSubject(false)
    callback.openFlag(false)
    callback.openCommentQuoteButton(false)
    callback.openCommentSpoilerButton(false)
    callback.openCommentCodeButton(false)
    callback.openCommentEqnButton(false)
    callback.openCommentMathButton(false)
    callback.openCommentSJISButton(false)
    callback.openNameOptions(false)
    callback.destroyCurrentAuthentication()
    callback.updateRevertChangeButtonVisibility(isBufferEmpty = true)
  }

  private fun makeSubmitCall() {
    launch {
      val chanDescriptor = currentChanDescriptor
        ?: return@launch

      switchPage(Page.LOADING)
      Logger.d(TAG, "makeSubmitCall() chanDescriptor=${chanDescriptor}")

      val takeFilesResult = replyManager.takeSelectedFiles(chanDescriptor)
        .safeUnwrap { error ->
          Logger.e(TAG, "makeSubmitCall() takeSelectedFiles(${chanDescriptor}) error")
          onPostError(chanDescriptor, error)
          return@launch
        }

      if (!takeFilesResult) {
        onPostError(chanDescriptor, IOException("Failed to move attached files into reply"))
        return@launch
      }

      site.actions().post(chanDescriptor)
        .catch { error -> onPostError(chanDescriptor, error) }
        .collect { postResult ->
          withContext(Dispatchers.Main) {
            when (postResult) {
              is SiteActions.PostResult.PostComplete -> {
                onPostComplete(chanDescriptor, postResult.replyResponse)
              }
              is SiteActions.PostResult.UploadingProgress -> {
                onUploadingProgress(
                  postResult.fileIndex,
                  postResult.totalFiles,
                  postResult.percent
                )
              }
              is SiteActions.PostResult.PostError -> {
                onPostError(chanDescriptor, postResult.error)
              }
            }
          }
        }
    }
  }

  private fun onUploadingProgress(fileIndex: Int, totalFiles: Int, percent: Int) {
    // called on a background thread!
    BackgroundUtils.runOnMainThread {
      callback.onUploadingProgress(fileIndex, totalFiles, percent)
    }
  }

  private fun onPostError(chanDescriptor: ChanDescriptor, exception: Throwable?) {
    Logger.e(TAG, "onPostError", exception)
    switchPage(Page.INPUT)

    replyManager.restoreFiles(chanDescriptor)

    var errorMessage = getString(R.string.reply_error)
    if (exception != null) {
      val message = exception.message
      if (message != null) {
        errorMessage = getString(R.string.reply_error_message, message)
      }
    }

    callback.openMessage(errorMessage)
  }

  private suspend fun onPostComplete(chanDescriptor: ChanDescriptor, replyResponse: ReplyResponse) {
    when {
      replyResponse.posted -> {
        Logger.d(TAG, "onPostComplete() replyResponse=$replyResponse")
        onPostedSuccessfully(chanDescriptor, replyResponse)
      }
      replyResponse.requireAuthentication -> {
        Logger.d(TAG, "onPostComplete() requireAuthentication==true replyResponse=$replyResponse")
        switchPage(Page.AUTHENTICATION)
      }
      else -> {
        var errorMessage = getString(R.string.reply_error)
        if (replyResponse.errorMessage != null) {
          errorMessage = getString(
            R.string.reply_error_message,
            replyResponse.errorMessage
          )
        }

        replyManager.restoreFiles(chanDescriptor)

        Logger.e(TAG, "onPostComplete() error: $errorMessage")
        switchPage(Page.INPUT)

        callback.openMessage(errorMessage)
      }
    }
  }

  private suspend fun onPostedSuccessfully(
    prevChanDescriptor: ChanDescriptor,
    replyResponse: ReplyResponse
  ) {
    replyManager.cleanupFiles(prevChanDescriptor)

    val siteDescriptor = replyResponse.siteDescriptor
      ?: return

    // if the thread being presented has changed in the time waiting for this call to
    // complete, the loadable field in ReplyPresenter will be incorrect; reconstruct
    // the loadable (local to this method) from the reply response
    val localSite = siteManager.bySiteDescriptor(siteDescriptor)
      ?: return

    val boardDescriptor = BoardDescriptor(siteDescriptor, replyResponse.boardCode)
    val localBoard = boardManager.byBoardDescriptor(boardDescriptor)
      ?: return

    val threadNo = if (replyResponse.threadNo <= 0L) {
      replyResponse.postNo
    } else {
      replyResponse.threadNo
    }

    val newThreadDescriptor = ChanDescriptor.ThreadDescriptor.create(
      localSite.name(),
      localBoard.boardCode(),
      threadNo
    )

    lastReplyRepository.putLastReply(newThreadDescriptor.boardDescriptor)

    if (prevChanDescriptor != null && prevChanDescriptor.isCatalogDescriptor()) {
      lastReplyRepository.putLastThread(prevChanDescriptor.boardDescriptor())
    }

    if (ChanSettings.postPinThread.get()) {
      bookmarkThread(newThreadDescriptor, threadNo)
    }

    val responsePostDescriptor = replyResponse.postDescriptorOrNull
    if (responsePostDescriptor != null) {
      val password = if (replyResponse.password.isNotEmpty()) {
        replyResponse.password
      } else {
        null
      }

      savedReplyManager.saveReply(ChanSavedReply(responsePostDescriptor, password))
    } else {
      Logger.e(TAG, "Couldn't create responsePostDescriptor, replyResponse=${replyResponse}")
    }

    switchPage(Page.INPUT)
    closeAll()
    highlightQuotes()

    replyManager.readReply(newThreadDescriptor) { newReply ->
      replyManager.readReply(prevChanDescriptor) { prevReply ->
        val prevName = prevReply.postName

        newReply.resetAfterPosting()
        newReply.postName = prevName
      }
    }

    callback.loadDraftIntoViews(newThreadDescriptor)
    callback.onPosted()

    if (prevChanDescriptor.isCatalogDescriptor()) {
      callback.showThread(newThreadDescriptor)
    }
  }

  private suspend fun bookmarkThread(newThreadDescriptor: ChanDescriptor, threadNo: Long) {
    val chanDescriptor = currentChanDescriptor
      ?: return

    if (chanDescriptor is ChanDescriptor.ThreadDescriptor) {
      val thread = chanThreadManager.getChanThread(chanDescriptor)

      // reply
      if (thread != null) {
        val originalPost = thread.getOriginalPost()
        val title = ChanPostUtils.getTitle(originalPost, currentChanDescriptor)
        val thumbnail = originalPost.firstImage()?.actualThumbnailUrl

        bookmarksManager.createBookmark(newThreadDescriptor.toThreadDescriptor(threadNo), title, thumbnail)
      } else {
        bookmarksManager.createBookmark(newThreadDescriptor.toThreadDescriptor(threadNo))
      }
    } else {
      val title = replyManager.readReply(chanDescriptor) { reply ->
        PostHelper.getTitle(reply)
      }

      bindChanDescriptor(newThreadDescriptor)

      bookmarksManager.createBookmark(
        ChanDescriptor.ThreadDescriptor(newThreadDescriptor.boardDescriptor(), threadNo),
        title
      )
    }
  }

  @JvmOverloads
  fun switchPage(
    page: Page,
    useV2NoJsCaptcha: Boolean = true,
    autoReply: Boolean = true
  ) {
    if (useV2NoJsCaptcha && this.page == page) {
      return
    }

    this.page = page

    when (page) {
      Page.LOADING,
      Page.INPUT -> callback.setPage(page)
      Page.AUTHENTICATION -> {
        callback.setPage(Page.AUTHENTICATION)

        // cleanup resources tied to the new captcha layout/presenter
        callback.destroyCurrentAuthentication()

        try {
          // If the user doesn't have WebView installed it will throw an error
          callback.initializeAuthentication(
            site,
            site.actions().postAuthenticate(),
            this,
            useV2NoJsCaptcha,
            autoReply
          )
        } catch (error: Throwable) {
          onAuthenticationFailed(error)
        }
      }
    }
  }

  private fun highlightQuotes() {
    highlightQuotesDebouncer.post({
      val chanDescriptor = currentChanDescriptor
        ?: return@post

      val matcher = replyManager.readReply(chanDescriptor) { reply ->
        return@readReply QUOTE_PATTERN.matcher(reply.comment)
      }

      // Find all occurrences of >>\d+ with start and end between selectionStart
      val selectedQuotes = mutableSetOf<Long>()

      while (matcher.find()) {
        val quote = matcher.group().substring(2)
        selectedQuotes += quote.toLongOrNull()
          ?: continue
      }

      callback.highlightPostNos(selectedQuotes)
    }, 250)
  }

  interface ReplyPresenterCallback {
    val chanDescriptor: ChanDescriptor?
    val selectionStart: Int

    fun loadViewsIntoDraft(chanDescriptor: ChanDescriptor)
    fun loadDraftIntoViews(chanDescriptor: ChanDescriptor)
    fun adjustSelection(start: Int, amount: Int)
    fun setPage(page: Page)
    fun initializeAuthentication(
      site: Site,
      authentication: SiteAuthentication,
      callback: AuthenticationLayoutCallback,
      useV2NoJsCaptcha: Boolean,
      autoReply: Boolean
    )

    fun resetAuthentication()
    fun openMessage(message: String?)
    fun openMessage(message: String?, hideDelayMs: Int)
    fun onPosted()
    fun setCommentHint(hint: String?)
    fun showCommentCounter(show: Boolean)
    fun setExpanded(expanded: Boolean, isCleaningUp: Boolean)
    fun openNameOptions(open: Boolean)
    fun openSubject(open: Boolean)
    fun openFlag(open: Boolean)
    fun openCommentQuoteButton(open: Boolean)
    fun openCommentSpoilerButton(open: Boolean)
    fun openCommentCodeButton(open: Boolean)
    fun openCommentEqnButton(open: Boolean)
    fun openCommentMathButton(open: Boolean)
    fun openCommentSJISButton(open: Boolean)
    fun updateCommentCount(count: Int, maxCount: Int, over: Boolean)
    fun highlightPostNos(postNos: Set<Long>)
    fun showThread(threadDescriptor: ChanDescriptor.ThreadDescriptor)
    fun focusComment()
    fun onUploadingProgress(fileIndex: Int, totalFiles: Int,percent: Int)
    fun onFallbackToV1CaptchaView(autoReply: Boolean)
    fun destroyCurrentAuthentication()
    fun showAuthenticationFailedError(error: Throwable)
    fun getTokenOrNull(): String?
    fun updateRevertChangeButtonVisibility(isBufferEmpty: Boolean)
    fun restoreComment(prevCommentInputState: CommentEditingHistory.CommentInputState)
    suspend fun bindReplyImages(chanDescriptor: ChanDescriptor)
    fun unbindReplyImages(chanDescriptor: ChanDescriptor)
    fun filesAreaOnBackPressed(): Boolean
  }

  companion object {
    private const val TAG = "ReplyPresenter"
    // matches for >>123, >>123 (text), >>>/fit/123
    private val QUOTE_PATTERN = Pattern.compile(">>\\d+")
    private val UTF_8 = StandardCharsets.UTF_8
  }

}