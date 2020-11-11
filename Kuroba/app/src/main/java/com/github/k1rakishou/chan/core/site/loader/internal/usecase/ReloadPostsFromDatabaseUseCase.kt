package com.github.k1rakishou.chan.core.site.loader.internal.usecase

import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.common.mutableListWithCap
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.board.ChanBoard
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.model.repository.ChanPostRepository

class ReloadPostsFromDatabaseUseCase(
  private val chanPostRepository: ChanPostRepository,
  private val boardManager: BoardManager
) {

  suspend fun reloadPosts(chanDescriptor: ChanDescriptor): List<ChanPost> {
    BackgroundUtils.ensureBackgroundThread()
    chanPostRepository.awaitUntilInitialized()

    when (chanDescriptor) {
      is ChanDescriptor.ThreadDescriptor -> {
        chanPostRepository.preloadForThread(chanDescriptor).safeUnwrap { error ->
          Logger.e(TAG, "reloadPosts() Failed to preload posts for thread ${chanDescriptor}", error)
          return emptyList()
        }

        return chanPostRepository.getThreadPosts(chanDescriptor)
          .unwrap()
      }
      is ChanDescriptor.CatalogDescriptor -> {
        boardManager.awaitUntilInitialized()

        val board = boardManager.byBoardDescriptor(chanDescriptor.boardDescriptor)
        val postsToLoadCount = if (board != null) {
          board.pages * board.perPage
        } else {
          ChanBoard.DEFAULT_CATALOG_SIZE
        }

        return chanPostRepository.getCatalogOriginalPosts(chanDescriptor, postsToLoadCount)
          .unwrap()
      }
    }
  }

  suspend fun reloadCatalogThreads(threadDescriptors: List<ChanDescriptor.ThreadDescriptor>): List<ChanPost> {
    BackgroundUtils.ensureBackgroundThread()
    chanPostRepository.awaitUntilInitialized()

    val mapOfPosts = chanPostRepository.getCatalogOriginalPosts(threadDescriptors)
      .safeUnwrap { error ->
        Logger.e(TAG, "reloadCatalogThreads() reloadCatalogThreads failure", error)
        return emptyList()
      }

    return mutableListWithCap(mapOfPosts.values)
  }

  companion object {
    private const val TAG = "ReloadPostsFromDatabaseUseCase"
  }

}