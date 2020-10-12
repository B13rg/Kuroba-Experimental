package com.github.k1rakishou.chan.core.manager

import androidx.annotation.GuardedBy
import com.github.k1rakishou.chan.core.base.SuspendDebouncer
import com.github.k1rakishou.chan.core.site.Site
import com.github.k1rakishou.chan.core.site.SiteRegistry
import com.github.k1rakishou.chan.utils.Logger
import com.github.k1rakishou.common.*
import com.github.k1rakishou.json.JsonSettings
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import com.github.k1rakishou.model.data.site.ChanSiteData
import com.github.k1rakishou.model.repository.SiteRepository
import io.reactivex.Flowable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.processors.PublishProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

@DoNotStrip
open class SiteManager(
  private val appScope: CoroutineScope,
  private val isDevFlavor: Boolean,
  private val verboseLogsEnabled: Boolean,
  private val siteRepository: SiteRepository,
  private val siteRegistry: SiteRegistry
) {
  private val suspendableInitializer = SuspendableInitializer<Unit>("SiteManager")
  private val debouncer = SuspendDebouncer(appScope)

  private val sitesChangedSubject = PublishProcessor.create<Unit>()

  private val lock = ReentrantReadWriteLock()
  @GuardedBy("lock")
  private val siteDataMap = mutableMapWithCap<SiteDescriptor, ChanSiteData>(32)
  @GuardedBy("lock")
  private val siteMap = mutableMapWithCap<SiteDescriptor, Site>(32)
  @GuardedBy("lock")
  private val orders = mutableListWithCap<SiteDescriptor>(32)

  @OptIn(ExperimentalTime::class)
  fun initialize() {
    Logger.d(TAG, "SiteManager.initialize()")

    appScope.launch(Dispatchers.Default) {
      Logger.d(TAG, "loadSites() start")
      val time = measureTime { loadSitesInternal() }
      Logger.d(TAG, "loadSites() took ${time}")
    }
  }

  @OptIn(ExperimentalTime::class)
  private suspend fun loadSitesInternal() {
    val result = siteRepository.initializeSites(siteRegistry.SITE_CLASSES_MAP.keys)
    if (result is ModularResult.Error) {
      Logger.e(TAG, "siteRepository.initializeSites() error", result.error)
      suspendableInitializer.initWithError(result.error)
      return
    }

    try {
      result as ModularResult.Value

      lock.write {
        siteDataMap.clear()
        siteMap.clear()
        orders.clear()

        result.value.forEach { chanSiteData ->
          siteDataMap[chanSiteData.siteDescriptor] = chanSiteData

          siteMap[chanSiteData.siteDescriptor] = instantiateSite(chanSiteData)
          orders.add(0, chanSiteData.siteDescriptor)
        }
      }

      ensureSitesAndOrdersConsistency()
      suspendableInitializer.initWithValue(Unit)
    } catch (error: Throwable) {
      Logger.e(TAG, "SiteManager initialization error", error)
      suspendableInitializer.initWithError(error)
    }
  }

  fun listenForSitesChanges(): Flowable<Unit> {
    return sitesChangedSubject
      .onBackpressureLatest()
      .observeOn(AndroidSchedulers.mainThread())
      .doOnError { error -> Logger.e(TAG, "Error while listening for sitesChangedSubject updates", error) }
      .hide()
  }

  fun firstSiteDescriptor(): SiteDescriptor? {
    check(isReady()) { "SiteManager is not ready yet! Use awaitUntilInitialized()" }
    ensureSitesAndOrdersConsistency()

    return lock.read {
      return@read orders.firstOrNull(this@SiteManager::isSiteActive)
    }
  }

  fun activeSiteCount(): Int {
    check(isReady()) { "SiteManager is not ready yet! Use awaitUntilInitialized()" }
    ensureSitesAndOrdersConsistency()

    return lock.read { siteMap.keys.count { siteDescriptor -> isSiteActive(siteDescriptor) } }
  }

  suspend fun activateOrDeactivateSite(
    siteDescriptor: SiteDescriptor,
    activate: Boolean,
    doAfterPersist: (() -> Unit)? = null
  ): Boolean {
    check(isReady()) { "SiteManager is not ready yet! Use awaitUntilInitialized()" }
    ensureSitesAndOrdersConsistency()

    val updated = lock.write {
      val enabled = siteMap[siteDescriptor]?.enabled() ?: false
      if (!enabled) {
        return@write false
      }

      val chanSiteData = siteDataMap[siteDescriptor]
        ?: return@write false

      if (chanSiteData.active == activate) {
        return@write false
      }

      chanSiteData.active = activate
      return@write true
    }

    if (!updated) {
      return false
    }

    debouncer.post(DEBOUNCE_TIME_MS) {
      siteRepository.persist(getSitesOrdered())
      doAfterPersist?.invoke()
    }

    sitesChanged()

    return true
  }

  fun isSiteActive(siteDescriptor: SiteDescriptor): Boolean {
    check(isReady()) { "SiteManager is not ready yet! Use awaitUntilInitialized()" }
    ensureSitesAndOrdersConsistency()

    return lock.read {
      val enabled = siteMap[siteDescriptor]?.enabled()
        ?: false

      if (!enabled) {
        return@read false
      }

      return@read siteDataMap[siteDescriptor]?.active
        ?: false
    }
  }

  fun areSitesSetup(): Boolean {
    check(isReady()) { "SiteManager is not ready yet! Use awaitUntilInitialized()" }
    ensureSitesAndOrdersConsistency()

    return lock.read {
      for ((siteDescriptor, site) in siteMap) {
        if (!site.enabled()) {
          continue
        }

        val isActive = siteDataMap[siteDescriptor]?.active ?: false
        if (!isActive) {
          continue
        }

        return@read true
      }

      return@read false
    }
  }

  open fun bySiteDescriptor(siteDescriptor: SiteDescriptor): Site? {
    check(isReady()) { "SiteManager is not ready yet! Use awaitUntilInitialized()" }
    ensureSitesAndOrdersConsistency()

    return lock.read {
      if (!isSiteActive(siteDescriptor)) {
        return@read null
      }

      return@read siteMap[siteDescriptor]
    }
  }

  fun viewSitesOrdered(viewer: (ChanSiteData, Site) -> Boolean) {
    check(isReady()) { "SiteManager is not ready yet! Use awaitUntilInitialized()" }
    ensureSitesAndOrdersConsistency()

    lock.read {
      orders.forEach { siteDescriptor ->
        val chanSiteData = requireNotNull(siteDataMap[siteDescriptor]) {
          "Couldn't find chanSiteData by siteDescriptor: $siteDescriptor in orders"
        }

        val site = requireNotNull(siteMap[siteDescriptor]) {
          "Couldn't find site by siteDescriptor: $siteDescriptor"
        }

        if (!viewer(chanSiteData, site)) {
          return@read
        }
      }
    }
  }

  fun <T> mapFirstActiveSiteOrNull(mapper: (ChanSiteData, Site) -> T?): T? {
    check(isReady()) { "SiteManager is not ready yet! Use awaitUntilInitialized()" }
    ensureSitesAndOrdersConsistency()

    return lock.read {
      for (siteDescriptor in orders) {
        if (!isSiteActive(siteDescriptor)) {
          continue
        }

        val chanSiteData = requireNotNull(siteDataMap[siteDescriptor]) {
          "Couldn't find chanSiteData by siteDescriptor: $siteDescriptor in orders"
        }

        val site = requireNotNull(siteMap[siteDescriptor]) {
          "Couldn't find site by siteDescriptor: $siteDescriptor"
        }

        val mapped = mapper(chanSiteData, site)
        if (mapped != null) {
          return@read mapped
        }
      }

      return@read null
    }
  }

  fun firstActiveSiteOrNull(predicate: (ChanSiteData, Site) -> Boolean): Site? {
    check(isReady()) { "SiteManager is not ready yet! Use awaitUntilInitialized()" }
    ensureSitesAndOrdersConsistency()

    return lock.read {
      val descriptor = orders.firstOrNull { siteDescriptor ->
        if (!isSiteActive(siteDescriptor)) {
          return@firstOrNull false
        }

        val chanSiteData = requireNotNull(siteDataMap[siteDescriptor]) {
          "Couldn't find chanSiteData by siteDescriptor: $siteDescriptor in orders"
        }

        val site = requireNotNull(siteMap[siteDescriptor]) {
          "Couldn't find site by siteDescriptor: $siteDescriptor"
        }

        return@firstOrNull predicate(chanSiteData, site)
      }

      if (descriptor == null) {
        return@read null
      }

      return@read siteMap[descriptor]
    }
  }

  fun viewActiveSitesOrderedWhile(viewer: (ChanSiteData, Site) -> Boolean) {
    check(isReady()) { "SiteManager is not ready yet! Use awaitUntilInitialized()" }
    ensureSitesAndOrdersConsistency()

    lock.read {
      orders.forEach { siteDescriptor ->
        if (!isSiteActive(siteDescriptor)) {
          return@forEach
        }

        val chanSiteData = requireNotNull(siteDataMap[siteDescriptor]) {
          "Couldn't find chanSiteData by siteDescriptor: $siteDescriptor in orders"
        }

        val site = requireNotNull(siteMap[siteDescriptor]) {
          "Couldn't find site by siteDescriptor: $siteDescriptor"
        }

        if (!viewer(chanSiteData, site)) {
          return@read
        }
      }
    }
  }

  fun onSiteMoving(from: Int, to: Int) {
    check(isReady()) { "SiteManager is not ready yet! Use awaitUntilInitialized()" }

    require(from >= 0) { "Bad from: $from" }
    require(to >= 0) { "Bad to: $to" }

    lock.write {
      orders.add(to, orders.removeAt(from))
    }

    ensureSitesAndOrdersConsistency()
  }

  fun onSiteMoved() {
    debouncer.post(SITE_MOVED_DEBOUNCE_TIME_MS) { siteRepository.persist(getSitesOrdered()) }
    sitesChanged()
  }

  fun updateUserSettings(siteDescriptor: SiteDescriptor, userSettings: JsonSettings) {
    check(isReady()) { "SiteManager is not ready yet! Use awaitUntilInitialized()" }
    ensureSitesAndOrdersConsistency()

    if (!isSiteActive(siteDescriptor)) {
      return
    }

    val shouldPersist = lock.write {
      val chanSiteData = siteDataMap[siteDescriptor]
        ?: return@write false

      if (chanSiteData.siteUserSettings == userSettings) {
        return@write false
      }

      chanSiteData.siteUserSettings = userSettings
      return@write true
    }

    if (!shouldPersist) {
      return
    }

    debouncer.post(DEBOUNCE_TIME_MS) { siteRepository.persist(getSitesOrdered()) }
    sitesChanged()
  }

  @OptIn(ExperimentalTime::class)
  open suspend fun awaitUntilInitialized() {
    if (isReady()) {
      return
    }

    Logger.d(TAG, "SiteManager is not ready yet, waiting...")
    val duration = measureTime { suspendableInitializer.awaitUntilInitialized() }
    Logger.d(TAG, "SiteManager initialization completed, took $duration")
  }

  private fun sitesChanged() {
    if (isDevFlavor) {
      ensureSitesAndOrdersConsistency()
    }

    sitesChangedSubject.onNext(Unit)
  }

  private fun getSitesOrdered(): List<ChanSiteData> {
    return lock.read {
      return@read orders.map { siteDescriptor ->
        return@map checkNotNull(siteDataMap[siteDescriptor]) {
          "Sites do not contain ${siteDescriptor} even though orders does"
        }
      }
    }
  }

  private fun instantiateSite(chanSiteData: ChanSiteData): Site {
    val clazz = siteRegistry.SITE_CLASSES_MAP[chanSiteData.siteDescriptor]
      ?: throw IllegalArgumentException("Unknown site descriptor: ${chanSiteData.siteDescriptor}")

    val site = instantiateSiteClass(clazz)
      ?: throw IllegalStateException("Couldn't instantiate site: ${clazz::class.java.simpleName}")

    // TODO(KurobaEx): make initialization lazy
    val settings = chanSiteData.siteUserSettings
      ?: JsonSettings(hashMapOf())
    
    // TODO(KurobaEx): make initialization lazy
    site.initialize(settings)
    return site
  }

  private fun instantiateSiteClass(clazz: Class<out Site>): Site? {
    return try {
      clazz.newInstance()
    } catch (e: InstantiationException) {
      throw IllegalArgumentException(e)
    } catch (e: IllegalAccessException) {
      throw IllegalArgumentException(e)
    }
  }

  private fun ensureSitesAndOrdersConsistency() {
    if (isDevFlavor) {
      lock.read {
        check(siteDataMap.size == orders.size) {
          "Inconsistency detected! siteDataMap.size (${siteDataMap.size}) != orders.size (${orders.size})"
        }

        check(siteMap.size == orders.size) {
          "Inconsistency detected! siteMap.size (${siteMap.size}) != orders.size (${orders.size})"
        }
      }
    }
  }

  fun isReady() = suspendableInitializer.isInitialized()

  companion object {
    private const val TAG = "SiteManager"

    private const val DEBOUNCE_TIME_MS = 500L
    private const val SITE_MOVED_DEBOUNCE_TIME_MS = 100L
  }
}