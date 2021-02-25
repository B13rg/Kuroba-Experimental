package com.github.k1rakishou.chan.core.manager

import androidx.annotation.GuardedBy
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.core.base.SerializedCoroutineExecutor
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.SuspendableInitializer
import com.github.k1rakishou.common.mutableListWithCap
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.navigation.NavHistoryElement
import com.github.k1rakishou.model.data.navigation.NavHistoryElementInfo
import com.github.k1rakishou.model.repository.HistoryNavigationRepository
import io.reactivex.Flowable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.processors.PublishProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.asFlow
import okhttp3.HttpUrl
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.time.ExperimentalTime
import kotlin.time.seconds

class HistoryNavigationManager(
  private val appScope: CoroutineScope,
  private val historyNavigationRepository: HistoryNavigationRepository,
  private val applicationVisibilityManager: ApplicationVisibilityManager
) {
  private val navigationStackChangesSubject = PublishProcessor.create<Unit>()
  private val persistTaskSubject = PublishProcessor.create<Unit>()
  private val persistRunning = AtomicBoolean(false)
  private val suspendableInitializer = SuspendableInitializer<Unit>("HistoryNavigationManager")

  private val serializedCoroutineExecutor = SerializedCoroutineExecutor(appScope)

  private val lock = ReentrantReadWriteLock()
  @GuardedBy("lock")
  private val navigationStack = mutableListWithCap<NavHistoryElement>(64)

  @OptIn(ExperimentalTime::class)
  fun initialize() {
    Logger.d(TAG, "HistoryNavigationManager.initialize()")

    appScope.launch {
      applicationVisibilityManager.addListener { visibility ->
        if (!suspendableInitializer.isInitialized()) {
          return@addListener
        }

        if (visibility != ApplicationVisibility.Background) {
          return@addListener
        }

        persisNavigationStack(eager = true)
      }

      appScope.launch {
        persistTaskSubject
          .onBackpressureLatest()
          .asFlow()
          .debounce(1.seconds)
          .collect {
            persisNavigationStack(eager = false)
          }
      }

      appScope.launch(Dispatchers.Default) {
        @Suppress("MoveVariableDeclarationIntoWhen")
        val loadedNavElementsResult = historyNavigationRepository.initialize(MAX_NAV_HISTORY_ENTRIES)
        when (loadedNavElementsResult) {
          is ModularResult.Value -> {
            lock.write {
              navigationStack.clear()
              navigationStack.addAll(loadedNavElementsResult.value)
            }

            suspendableInitializer.initWithValue(Unit)
            Logger.d(TAG, "HistoryNavigationManager initialized!")
          }
          is ModularResult.Error -> {
            Logger.e(TAG, "Exception while initializing HistoryNavigationManager", loadedNavElementsResult.error)
            suspendableInitializer.initWithError(loadedNavElementsResult.error)
          }
        }

        navStackChanged()
      }
    }
  }

  fun getAll(reversed: Boolean = false): List<NavHistoryElement> {
    BackgroundUtils.ensureMainThread()

    return lock.read {
      if (reversed) {
        return@read navigationStack.toList().reversed()
      } else {
        return@read navigationStack.toList()
      }
    }
  }

  fun getNavElementAtTop(): NavHistoryElement? {
    BackgroundUtils.ensureMainThread()

    return lock.read { navigationStack.firstOrNull() }
  }

  fun getFirstCatalogNavElement(): NavHistoryElement? {
    BackgroundUtils.ensureMainThread()

    return lock.read {
      return@read navigationStack.firstOrNull { navHistoryElement ->
        navHistoryElement is NavHistoryElement.Catalog
      }
    }
  }

  fun listenForNavigationStackChanges(): Flowable<Unit> {
    BackgroundUtils.ensureMainThread()

    return navigationStackChangesSubject
      .observeOn(AndroidSchedulers.mainThread())
      .doOnError { error -> Logger.e(TAG, "listenForNavigationStackChanges error", error) }
      .hide()
  }

  suspend fun awaitUntilInitialized() = suspendableInitializer.awaitUntilInitialized()

  fun isReady() = suspendableInitializer.isInitialized()

  fun canCreateNavElement(
    bookmarksManager: BookmarksManager,
    chanDescriptor: ChanDescriptor
  ): Boolean {
    if (chanDescriptor is ChanDescriptor.CatalogDescriptor) {
      return ChanSettings.drawerShowNavigationHistory.get()
    }

    val threadDescriptor = chanDescriptor as ChanDescriptor.ThreadDescriptor

    if (!ChanSettings.drawerShowBookmarkedThreads.get() && !ChanSettings.drawerShowNavigationHistory.get()) {
      return false
    }

    if (!ChanSettings.drawerShowBookmarkedThreads.get()) {
      return !bookmarksManager.exists(threadDescriptor)
    }

    if (!ChanSettings.drawerShowNavigationHistory.get()) {
      return bookmarksManager.exists(threadDescriptor)
    }

    return true
  }

  fun createNewNavElement(
    descriptor: ChanDescriptor,
    thumbnailImageUrl: HttpUrl,
    title: String,
    createdByBookmarkCreation: Boolean
  ) {
    BackgroundUtils.ensureMainThread()

    val newNavigationElement = NewNavigationElement(descriptor, thumbnailImageUrl, title)
    createNewNavElements(listOf(newNavigationElement), createdByBookmarkCreation)
  }

  fun createNewNavElements(
    newNavigationElements: Collection<NewNavigationElement>,
    createdByBookmarkCreation: Boolean
  ) {
    BackgroundUtils.ensureMainThread()

    if (newNavigationElements.isEmpty()) {
      return
    }

    var created = false

    newNavigationElements.forEach { newNavigationElement ->
      val descriptor = newNavigationElement.descriptor
      val thumbnailImageUrl = newNavigationElement.thumbnailImageUrl
      val title = newNavigationElement.title

      val navElementInfo = NavHistoryElementInfo(thumbnailImageUrl, title)
      val navElement = when (descriptor) {
        is ChanDescriptor.ThreadDescriptor -> NavHistoryElement.Thread(descriptor, navElementInfo)
        is ChanDescriptor.CatalogDescriptor -> NavHistoryElement.Catalog(descriptor, navElementInfo)
      }

      if (addNewOrIgnore(navElement, createdByBookmarkCreation)) {
        created = true
      }
    }

    if (!created) {
      return
    }

    navStackChanged()
  }

  fun moveNavElementToTop(descriptor: ChanDescriptor) {
    BackgroundUtils.ensureMainThread()

    if (!ChanSettings.drawerMoveLastAccessedThreadToTop.get()) {
      return
    }

    lock.write {
      val indexOfElem = navigationStack.indexOfFirst { navHistoryElement ->
        return@indexOfFirst when (navHistoryElement) {
          is NavHistoryElement.Catalog -> navHistoryElement.descriptor == descriptor
          is NavHistoryElement.Thread -> navHistoryElement.descriptor == descriptor
        }
      }

      if (indexOfElem < 0) {
        return@write
      }

      // Move the existing navigation element at the top of the list
      navigationStack.add(0, navigationStack.removeAt(indexOfElem))
    }

    navStackChanged()
  }

  fun onNavElementSwipedAway(descriptor: ChanDescriptor) {
    BackgroundUtils.ensureMainThread()
    removeNavElements(listOf(descriptor))
  }

  fun removeNavElements(descriptors: Collection<ChanDescriptor>) {
    BackgroundUtils.ensureMainThread()

    if (descriptors.isEmpty()) {
      return
    }

    val removed = lock.write {
      var removed = false

      descriptors.forEach { chanDescriptor ->
        val indexOfElem = navigationStack.indexOfFirst { navHistoryElement ->
          return@indexOfFirst when (navHistoryElement) {
            is NavHistoryElement.Catalog -> navHistoryElement.descriptor == chanDescriptor
            is NavHistoryElement.Thread -> navHistoryElement.descriptor == chanDescriptor
          }
        }

        if (indexOfElem < 0) {
          return@forEach
        }

        navigationStack.removeAt(indexOfElem)
        removed = true
      }

      return@write removed
    }

    if (!removed) {
      return
    }

    navStackChanged()
  }

  fun clear() {
    BackgroundUtils.ensureMainThread()

    val cleared = lock.write {
      if (navigationStack.isEmpty()) {
        return@write false
      }

      navigationStack.clear()
      return@write true
    }

    if (!cleared) {
      return
    }

    navStackChanged()
  }

  private fun persisNavigationStack(eager: Boolean = false) {
    BackgroundUtils.ensureMainThread()

    if (!suspendableInitializer.isInitialized()) {
      return
    }

    if (!persistRunning.compareAndSet(false, true)) {
      return
    }

    if (eager) {
      appScope.launch(Dispatchers.Default) {
        Logger.d(TAG, "persistNavigationStack eager called")

        try {
          val navStackCopy = lock.read { navigationStack.toList() }

          historyNavigationRepository.persist(navStackCopy)
            .safeUnwrap { error ->
              Logger.e(TAG, "Error while trying to persist navigation stack", error)
              return@launch
            }
        } finally {
          Logger.d(TAG, "persistNavigationStack eager finished")
          persistRunning.set(false)
        }
      }
    } else {
      serializedCoroutineExecutor.post {
        Logger.d(TAG, "persistNavigationStack async called")

        try {
          val navStackCopy = lock.read { navigationStack.toList() }

          historyNavigationRepository.persist(navStackCopy)
            .safeUnwrap { error ->
              Logger.e(TAG, "Error while trying to persist navigation stack", error)
              return@post
            }
        } finally {
          Logger.d(TAG, "persistNavigationStack async finished")
          persistRunning.set(false)
        }
      }
    }
  }

  private fun addNewOrIgnore(navElement: NavHistoryElement, createdByBookmarkCreation: Boolean): Boolean {
    BackgroundUtils.ensureMainThread()

    return lock.write {
      val indexOfElem = navigationStack.indexOf(navElement)
      if (indexOfElem >= 0) {
        return@write false
      }

      if (navigationStack.isEmpty() || !createdByBookmarkCreation) {
        navigationStack.add(0, navElement)
      } else {
        // Do not overwrite the top of nav stack that we use to restore previously opened thread.
        // Otherwise this may lead to unexpected behaviors, for example, a situation when starting
        // the app after a bookmark was created when the app was in background (filter watching)
        // the user will see the last bookmarked thread instead of last opened thread.
        navigationStack.add(1, navElement)
      }

      return@write true
    }
  }

  private fun navStackChanged() {
    navigationStackChangesSubject.onNext(Unit)
    persistTaskSubject.onNext(Unit)
  }

  data class NewNavigationElement(
    val descriptor: ChanDescriptor,
    val thumbnailImageUrl: HttpUrl,
    val title: String
  )

  companion object {
    private const val TAG = "HistoryNavigationManager"
    private const val MAX_NAV_HISTORY_ENTRIES = 128
  }
}