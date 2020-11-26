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
package com.github.k1rakishou.chan.features.reply.data

import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.ModularResult.Companion.Try
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor.ThreadDescriptor
import java.util.*
import java.util.regex.Pattern

/**
 * The data needed to send a reply.
 */
class Reply(
  @JvmField
  val chanDescriptor: ChanDescriptor
) {
  private val basicReplyInfo = BasicReplyInfo()
  private val captchaInfo = CaptchaInfo()
  private val filesTakenForThisReply = mutableListOf<ReplyFile>()

  @get:Synchronized
  @set:Synchronized
  var postName: String = basicReplyInfo.name
    get() = basicReplyInfo.name
    set(value) {
      basicReplyInfo.name = value
      field = value
    }

  @get:Synchronized
  @set:Synchronized
  var options: String = basicReplyInfo.options
    get() = basicReplyInfo.options
    set(value) {
      basicReplyInfo.options = value
      field = value
    }

  @get:Synchronized
  @set:Synchronized
  var subject: String = basicReplyInfo.subject
    get() = basicReplyInfo.subject
    set(value) {
      basicReplyInfo.subject = value
      field = value
    }

  @get:Synchronized
  @set:Synchronized
  var comment: String = basicReplyInfo.comment
    get() = basicReplyInfo.comment
    set(value) {
      basicReplyInfo.comment = value
      field = value
    }

  @get:Synchronized
  @set:Synchronized
  var flag: String = basicReplyInfo.flag
    get() = basicReplyInfo.flag
    set(value) {
      basicReplyInfo.flag = value
      field = value
    }

  @get:Synchronized
  @set:Synchronized
  var password: String = basicReplyInfo.password
    get() = basicReplyInfo.password
    set(value) {
      basicReplyInfo.password = value
      field = value
    }

  @get:Synchronized
  val captchaChallenge: String?
    get() = captchaInfo.captchaChallenge

  @get:Synchronized
  val captchaResponse: String?
    get() = captchaInfo.captchaResponse

  @Synchronized
  fun threadNo(): Long {
    if (chanDescriptor is ThreadDescriptor) {
      chanDescriptor.threadNo
    }

    return 0
  }

  @Synchronized
  fun firstFileOrNull(): ReplyFile? {
    return filesTakenForThisReply.firstOrNull()
  }

  @Synchronized
  fun iterateFilesOrThrowIfEmpty(iterator: (Int, ReplyFile) -> Unit) {
    check(filesTakenForThisReply.isNotEmpty()) { "filesTakenForThisReply is empty!" }

    filesTakenForThisReply.forEachIndexed { index, replyFile -> iterator(index, replyFile) }
  }

  @Synchronized
  fun putReplyFiles(files: List<ReplyFile>) {
    filesTakenForThisReply.addAll(files)
  }

  @Synchronized
  fun hasFiles(): Boolean = filesTakenForThisReply.isNotEmpty()

  @Synchronized
  fun filesCount(): Int = filesTakenForThisReply.size

  @Synchronized
  fun getAndConsumeFiles(): List<ReplyFile> {
    val files = filesTakenForThisReply.toList()
    filesTakenForThisReply.clear()

    return files
  }

  @Synchronized
  fun cleanupFiles(): ModularResult<List<UUID>> {
    return Try {
      val fileUuids = mutableListOf<UUID>()

      filesTakenForThisReply.forEach { replyFile ->
        fileUuids += replyFile.getReplyFileMeta().unwrap().fileUuid
        replyFile.deleteFromDisk()
      }

      filesTakenForThisReply.clear()
      return@Try fileUuids
    }
  }

  @Synchronized
  fun isCommentEmpty(): Boolean {
    return basicReplyInfo.comment.trim().isEmpty()
  }

  @Synchronized
  fun initCaptchaInfo(challenge: String?, response: String?) {
    captchaInfo.captchaChallenge = challenge
    captchaInfo.captchaResponse = response
  }

  @Synchronized
  fun resetCaptchaResponse() {
    captchaInfo.captchaResponse = null
  }

  @Synchronized
  fun resetAfterPosting() {
    basicReplyInfo.resetAfterPosting()
  }

  @Synchronized
  fun handleQuote(selectStart: Int, postNo: Long, textQuote: String?): Int {
    val stringBuilder = StringBuilder()
    val comment = basicReplyInfo.comment

    if (selectStart - 1 >= 0
      && selectStart - 1 < comment.length
      && comment[selectStart - 1] != '\n'
    ) {
      stringBuilder
        .append('\n')
    }

    if (!comment.contains(">>$postNo")) {
      stringBuilder
        .append(">>")
        .append(postNo)
        .append("\n")
    }

    if (textQuote != null) {
      val lines = textQuote.split("\n+").toTypedArray()
      for (line in lines) {
        // do not include post no from quoted post
        if (!QUOTE_PATTERN_COMPLEX.matcher(line).matches()) {
          stringBuilder
            .append(">")
            .append(line)
            .append("\n")
        }
      }
    }

    basicReplyInfo.comment = StringBuilder(basicReplyInfo.comment)
      .insert(selectStart, stringBuilder)
      .toString()

    return stringBuilder.length
  }

  data class BasicReplyInfo(
    @JvmField
    var name: String = "",
    @JvmField
    var options: String = "",
    @JvmField
    var flag: String = "",
    @JvmField
    var subject: String = "",
    @JvmField
    var comment: String = "",
    @JvmField
    var password: String = "",
  ) {

    @Synchronized
    fun resetAfterPosting() {
      comment = ""
    }

  }

  data class CaptchaInfo(
    /**
     * Optional. `null` when ReCaptcha v2 was used or a 4pass
     */
    @JvmField
    var captchaChallenge: String? = null,

    /**
     * Optional. `null` when a 4pass was used.
     */
    @JvmField
    var captchaResponse: String? = null
  )

  companion object {
    private val QUOTE_PATTERN_COMPLEX = Pattern.compile("^>>(>/[a-z0-9]+/)?\\d+.*$")
  }
}