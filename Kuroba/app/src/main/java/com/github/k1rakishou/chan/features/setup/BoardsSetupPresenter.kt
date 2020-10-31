package com.github.k1rakishou.chan.features.setup

import com.github.k1rakishou.chan.core.base.BasePresenter
import com.github.k1rakishou.chan.core.base.DebouncingCoroutineExecutor
import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.site.Site
import com.github.k1rakishou.chan.features.setup.data.BoardCellData
import com.github.k1rakishou.chan.features.setup.data.BoardsSetupControllerState
import com.github.k1rakishou.chan.ui.helper.BoardHelper
import com.github.k1rakishou.chan.utils.Logger
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.common.mutableListWithCap
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import io.reactivex.Flowable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.processors.PublishProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

class BoardsSetupPresenter(
  private val siteDescriptor: SiteDescriptor,
  private val siteManager: SiteManager,
  private val boardManager: BoardManager
) : BasePresenter<BoardsSetupView>() {
  private val suspendDebouncer = DebouncingCoroutineExecutor(scope)
  private val stateSubject = PublishProcessor.create<BoardsSetupControllerState>()
    .toSerialized()

  private val boardInfoLoaded = AtomicBoolean(false)

  fun listenForStateChanges(): Flowable<BoardsSetupControllerState> {
    return stateSubject
      .onBackpressureLatest()
      .observeOn(AndroidSchedulers.mainThread())
      .doOnError { error ->
        Logger.e(TAG, "Unknown error subscribed to stateSubject.listenForStateChanges()", error)
      }
      .onErrorReturn { error -> BoardsSetupControllerState.Error(error.errorMessageOrClassName()) }
      .hide()
  }

  fun updateBoardsFromServerAndDisplayActive() {
    scope.launch(Dispatchers.Default) {
      setState(BoardsSetupControllerState.Loading)

      boardManager.awaitUntilInitialized()
      siteManager.awaitUntilInitialized()

      val site = siteManager.bySiteDescriptor(siteDescriptor)
      if (site == null) {
        setState(BoardsSetupControllerState.Error("No site found by descriptor: ${siteDescriptor}"))
        boardInfoLoaded.set(true)
        return@launch
      }

      val isSiteActive = siteManager.isSiteActive(siteDescriptor)
      if (!isSiteActive) {
        setState(BoardsSetupControllerState.Error("Site with descriptor ${siteDescriptor} is not active!"))
        boardInfoLoaded.set(true)
        return@launch
      }

      loadBoardInfoSuspend(site)
        .safeUnwrap { error ->
          Logger.e(TAG, "Error loading boards for site ${siteDescriptor}", error)
          setState(BoardsSetupControllerState.Error(error.errorMessageOrClassName()))
          boardInfoLoaded.set(true)
          return@launch
        }

      withViewNormal { onBoardsLoaded() }

      displayActiveBoardsInternal()
      boardInfoLoaded.set(true)
    }
  }

  fun onBoardMoving(boardDescriptor: BoardDescriptor, fromPosition: Int, toPosition: Int) {
    if (boardManager.onBoardMoving(boardDescriptor, fromPosition, toPosition)) {
      displayActiveBoards(withLoadingState = false, withDebouncing = false)
    }
  }

  fun onBoardMoved() {
    boardManager.onBoardMoved()
  }

  fun onBoardRemoved(boardDescriptor: BoardDescriptor) {
    scope.launch {
      val deactivated = boardManager.activateDeactivateBoards(
        boardDescriptor.siteDescriptor,
        linkedSetOf(boardDescriptor),
        false
      )

      if (deactivated) {
        displayActiveBoards(withLoadingState = false, withDebouncing = true)
      }
    }
  }

  fun displayActiveBoards(withLoadingState: Boolean = true, withDebouncing: Boolean = true) {
    if (!boardInfoLoaded.get()) {
      return
    }

    if (withLoadingState) {
      setState(BoardsSetupControllerState.Loading)
    }

    if (withDebouncing) {
      suspendDebouncer.post(DEBOUNCE_TIME_MS) {
        boardManager.awaitUntilInitialized()
        siteManager.awaitUntilInitialized()

        displayActiveBoardsInternal()
      }
    } else {
      scope.launch {
        boardManager.awaitUntilInitialized()
        siteManager.awaitUntilInitialized()

        displayActiveBoardsInternal()
      }
    }
  }

  private fun displayActiveBoardsInternal() {
    val isSiteActive = siteManager.isSiteActive(siteDescriptor)
    if (!isSiteActive) {
      setState(BoardsSetupControllerState.Error("Site with descriptor ${siteDescriptor} is not active!"))
      return
    }

    val boardCellDataList = mutableListWithCap<BoardCellData>(32)

    boardManager.viewBoardsOrdered(siteDescriptor, true) { chanBoard ->
      boardCellDataList += BoardCellData(
        chanBoard.boardDescriptor,
        chanBoard.boardName(),
        BoardHelper.getDescription(chanBoard)
      )
    }

    if (boardCellDataList.isEmpty()) {
      setState(BoardsSetupControllerState.Empty)
      return
    }

    setState(BoardsSetupControllerState.Data(boardCellDataList))
  }

  private suspend fun loadBoardInfoSuspend(site: Site): ModularResult<Unit> {
    if (boardInfoLoaded.get()) {
      return ModularResult.value(Unit)
    }

    return suspendCancellableCoroutine { cancellableContinuation ->
      site.loadBoardInfo { result ->
        cancellableContinuation.resume(result.mapValue { Unit })
      }
    }
  }

  private fun setState(state: BoardsSetupControllerState) {
    stateSubject.onNext(state)
  }

  companion object {
    private const val TAG = "BoardsSetupPresenter"
    private const val DEBOUNCE_TIME_MS = 100L
  }
}