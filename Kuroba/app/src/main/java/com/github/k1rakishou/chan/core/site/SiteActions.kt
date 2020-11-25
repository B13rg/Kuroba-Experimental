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
package com.github.k1rakishou.chan.core.site

import com.github.k1rakishou.chan.core.net.HtmlReaderRequest
import com.github.k1rakishou.chan.core.net.JsonReaderRequest
import com.github.k1rakishou.chan.core.site.http.DeleteRequest
import com.github.k1rakishou.chan.core.site.http.DeleteResponse
import com.github.k1rakishou.chan.core.site.http.ReplyResponse
import com.github.k1rakishou.chan.core.site.http.login.AbstractLoginRequest
import com.github.k1rakishou.chan.core.site.http.login.AbstractLoginResponse
import com.github.k1rakishou.chan.core.site.limitations.PasscodePostingLimitationsInfo
import com.github.k1rakishou.chan.core.site.sites.chan4.Chan4PagesRequest
import com.github.k1rakishou.chan.core.site.sites.search.SearchError
import com.github.k1rakishou.chan.core.site.sites.search.SearchParams
import com.github.k1rakishou.chan.core.site.sites.search.SearchResult
import com.github.k1rakishou.model.data.board.ChanBoard
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.site.SiteBoards
import kotlinx.coroutines.flow.Flow

interface SiteActions {
  suspend fun boards(): JsonReaderRequest.JsonReaderResponse<SiteBoards>
  suspend fun pages(board: ChanBoard): JsonReaderRequest.JsonReaderResponse<Chan4PagesRequest.BoardPages>
  suspend fun post(replyChanDescriptor: ChanDescriptor): Flow<PostResult>
  suspend fun delete(deleteRequest: DeleteRequest): DeleteResult
  suspend fun <T : AbstractLoginRequest> login(loginRequest: T): LoginResult
  fun postRequiresAuthentication(): Boolean
  fun postAuthenticate(): SiteAuthentication
  fun logout()
  fun isLoggedIn(): Boolean
  fun loginDetails(): AbstractLoginRequest?

  suspend fun search(searchParams: SearchParams): HtmlReaderRequest.HtmlReaderResponse<SearchResult> =
    HtmlReaderRequest.HtmlReaderResponse.Success(SearchResult.Failure(SearchError.NotImplemented))

  suspend fun getOrRefreshPasscodeInfo(resetCached: Boolean): GetPasscodeInfoResult? = null

  enum class LoginType {
    Passcode,
    TokenAndPass
  }

  sealed class PostResult {
    class PostComplete(val replyResponse: ReplyResponse) : PostResult()
    class UploadingProgress(val fileIndex: Int, val totalFiles: Int, val percent: Int) : PostResult()
    class PostError(val error: Throwable) : PostResult()
  }

  sealed class DeleteResult {
    class DeleteComplete(val deleteResponse: DeleteResponse) : DeleteResult()
    class DeleteError(val error: Throwable) : DeleteResult()
  }

  sealed class LoginResult {
    class LoginComplete(val loginResponse: AbstractLoginResponse) : LoginResult()
    class LoginError(val errorMessage: String) : LoginResult()
  }

  sealed class GetPasscodeInfoResult {
    object NotLoggedIn : GetPasscodeInfoResult()
    class Success(val postingLimitationsInfo: PasscodePostingLimitationsInfo) : GetPasscodeInfoResult()
    class Failure(val error: Throwable) : GetPasscodeInfoResult()
  }
}