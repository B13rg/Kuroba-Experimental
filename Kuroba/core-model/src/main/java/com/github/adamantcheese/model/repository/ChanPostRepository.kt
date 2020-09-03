package com.github.adamantcheese.model.repository

import com.github.adamantcheese.common.*
import com.github.adamantcheese.common.ModularResult.Companion.Try
import com.github.adamantcheese.model.KurobaDatabase
import com.github.adamantcheese.model.common.Logger
import com.github.adamantcheese.model.data.descriptor.ArchiveDescriptor
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor
import com.github.adamantcheese.model.data.descriptor.PostDescriptor
import com.github.adamantcheese.model.data.post.ChanPost
import com.github.adamantcheese.model.source.cache.PostsCache
import com.github.adamantcheese.model.source.local.ChanPostLocalSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

class ChanPostRepository(
  database: KurobaDatabase,
  loggerTag: String,
  logger: Logger,
  private val isDevFlavor: Boolean,
  private val applicationScope: CoroutineScope,
  private val localSource: ChanPostLocalSource,
  private val appConstants: AppConstants
) : AbstractRepository(database, logger) {
  private val TAG = "$loggerTag ChanPostRepository"
  private val suspendableInitializer = SuspendableInitializer<Unit>("ChanPostRepository")
  private val postCache = PostsCache(appConstants.maxPostsCountInPostsCache)

  init {
    applicationScope.launch(Dispatchers.Default) {
      // We need to first delete the posts, so that the threads are only left with the OP
      val postDeleteResult = deleteOldPostsIfNeeded().mapValue { Unit }
      if (postDeleteResult is ModularResult.Error) {
        suspendableInitializer.initWithModularResult(postDeleteResult)
        return@launch
      }

      // Then we can delete the threads themselves
      val threadDeleteResult = deleteOldThreadsIfNeeded().mapValue { Unit }
      suspendableInitializer.initWithModularResult(threadDeleteResult)
    }
  }

  suspend fun awaitUntilInitialized() = suspendableInitializer.awaitUntilInitialized()

  suspend fun getCachedValuesCount(): Int {
    check(suspendableInitializer.isInitialized()) { "ChanPostRepository is not initialized yet!" }

    return applicationScope.myAsync {
      return@myAsync postCache.getCachedValuesCount()
    }
  }

  suspend fun createEmptyThreadIfNotExists(descriptor: ChanDescriptor.ThreadDescriptor): ModularResult<Long?> {
    check(suspendableInitializer.isInitialized()) { "ChanPostRepository is not initialized yet!" }

    return applicationScope.myAsync {
      return@myAsync tryWithTransaction {
        return@tryWithTransaction localSource.insertEmptyThread(descriptor)
      }
    }
  }

  /**
   * Returns a list of posts that differ from the cached ones and which we want to parse again and
   * show the user (otherwise show cached posts)
   * */
  suspend fun insertOrUpdateMany(
    posts: MutableList<ChanPost>,
    isCatalog: Boolean
  ): ModularResult<List<Long>> {
    check(suspendableInitializer.isInitialized()) { "ChanPostRepository is not initialized yet!" }

    return applicationScope.myAsync {
      return@myAsync tryWithTransaction {
        if (isCatalog) {
          return@tryWithTransaction insertOrUpdateCatalogOriginalPosts(posts)
        } else {
          return@tryWithTransaction insertOrUpdateThreadPosts(posts)
        }
      }
    }
  }

  suspend fun getCachedPost(postDescriptor: PostDescriptor, isOP: Boolean): ChanPost? {
    check(suspendableInitializer.isInitialized()) { "ChanPostRepository is not initialized yet!" }

    return applicationScope.myAsync {
      return@myAsync postCache.getPostFromCache(postDescriptor, isOP)
    }
  }

  suspend fun getCatalogOriginalPosts(
    descriptor: ChanDescriptor.CatalogDescriptor,
    archiveId: Long,
    count: Int
  ): ModularResult<List<ChanPost>> {
    check(suspendableInitializer.isInitialized()) { "ChanPostRepository is not initialized yet!" }
    require(count > 0) { "Bad count param: $count" }

    val archiveIds = toArchiveIdsSet(archiveId)
    logger.log(TAG, "getCatalogOriginalPosts(descriptor=$descriptor, archiveIds=${archiveIds}, count=$count)")

    return applicationScope.myAsync {
      return@myAsync tryWithTransaction {
        val originalPostsFromCache = postCache.getLatestOriginalPostsFromCache(descriptor, count)
        if (originalPostsFromCache.size == count) {
          return@tryWithTransaction originalPostsFromCache
        }

        val originalPostsFromDatabase = localSource.getCatalogOriginalPosts(
          descriptor,
          archiveIds,
          count
        )

        if (originalPostsFromDatabase.isNotEmpty()) {
          originalPostsFromDatabase.forEach { post ->
            postCache.putIntoCache(post.postDescriptor, post)
          }
        }

        return@tryWithTransaction originalPostsFromDatabase
      }
    }
  }

  suspend fun getCatalogOriginalPosts(
    descriptor: ChanDescriptor.CatalogDescriptor,
    archiveId: Long,
    threadNoList: Collection<Long>
  ): ModularResult<List<ChanPost>> {
    check(suspendableInitializer.isInitialized()) { "ChanPostRepository is not initialized yet!" }
    val archiveIds = toArchiveIdsSet(archiveId)

    return applicationScope.myAsync {
      return@myAsync tryWithTransaction {
        val originalPostsFromCache = threadNoList.mapNotNull { threadNo ->
          postCache.getOriginalPostFromCache(descriptor.toThreadDescriptor(threadNo))
        }

        val originalPostNoFromCacheSet = originalPostsFromCache.map { post ->
          post.postDescriptor.postNo
        }.toSet()

        val originalPostNoListToGetFromDatabase = threadNoList.filter { threadNo ->
          threadNo !in originalPostNoFromCacheSet
        }

        if (originalPostNoListToGetFromDatabase.isEmpty()) {
          // All posts were found in the cache
          return@tryWithTransaction originalPostsFromCache
        }

        val originalPostsFromDatabase = localSource.getCatalogOriginalPosts(
          descriptor,
          archiveIds,
          originalPostNoListToGetFromDatabase
        )

        if (originalPostsFromDatabase.isNotEmpty()) {
          originalPostsFromDatabase.forEach { post ->
            postCache.putIntoCache(post.postDescriptor, post)
          }
        }

        return@tryWithTransaction originalPostsFromCache + originalPostsFromDatabase
      }
    }
  }

  suspend fun getCatalogOriginalPosts(
    threadDescriptors: Collection<ChanDescriptor.ThreadDescriptor>,
    archiveId: Long
  ): ModularResult<Map<ChanDescriptor.ThreadDescriptor, ChanPost>> {
    check(suspendableInitializer.isInitialized()) { "ChanPostRepository is not initialized yet!" }
    val archiveIds = toArchiveIdsSet(archiveId)

    return applicationScope.myAsync {
      return@myAsync tryWithTransaction {
        val originalPostsFromCache = postCache.getOriginalPostsFromCache(threadDescriptors)

        val notCachedOriginalPostThreadDescriptors = threadDescriptors.filter { threadDescriptor ->
          !originalPostsFromCache.containsKey(threadDescriptor)
        }

        if (notCachedOriginalPostThreadDescriptors.isEmpty()) {
          // All posts were found in the cache
          return@tryWithTransaction originalPostsFromCache
        }

        val originalPostsFromDatabase = localSource.getCatalogOriginalPosts(
          archiveIds,
          notCachedOriginalPostThreadDescriptors
        )

        if (originalPostsFromDatabase.isNotEmpty()) {
          originalPostsFromDatabase.forEach { (_, chanPost) ->
            postCache.putIntoCache(chanPost.postDescriptor, chanPost)
          }
        }

        val resultMap = mutableMapWithCap<ChanDescriptor.ThreadDescriptor, ChanPost>(
          originalPostsFromCache.size + originalPostsFromDatabase.size
        )

        resultMap.putAll(originalPostsFromCache)
        resultMap.putAll(originalPostsFromDatabase)

        if (isDevFlavor) {
          resultMap.values.forEach { chanPost ->
            check(chanPost.isOp) { "getCatalogOriginalPosts() is returning a non-OP post!" }
          }
        }

        return@tryWithTransaction resultMap
      }
    }
  }

  suspend fun getThreadPosts(
    threadDescriptor: ChanDescriptor.ThreadDescriptor,
    archiveId: Long,
    postNoSet: Set<Long>
  ): ModularResult<List<ChanPost>> {
    check(suspendableInitializer.isInitialized()) { "ChanPostRepository is not initialized yet!" }
    val archiveIds = toArchiveIdsSet(archiveId)

    return applicationScope.myAsync {
      return@myAsync tryWithTransaction {
        val fromCache = postCache.getPostsFromCache(threadDescriptor, postNoSet)
        if (fromCache.size == postNoSet.size) {
          return@tryWithTransaction fromCache
        }

        val getFromDatabasePostList = postNoSet.subtract(
          fromCache.map { post -> post.postDescriptor.postNo }
        )

        val postsFromDatabase = localSource.getThreadPosts(
          threadDescriptor,
          archiveIds,
          getFromDatabasePostList
        )

        if (postsFromDatabase.isNotEmpty()) {
          postsFromDatabase.forEach { post ->
            postCache.putIntoCache(post.postDescriptor, post)
          }
        }

        return@tryWithTransaction fromCache + postsFromDatabase
      }
    }
  }

  suspend fun getThreadPostIds(
    descriptor: ChanDescriptor.ThreadDescriptor,
    archiveId: Long,
    maxCount: Int
  ): ModularResult<Set<Long>> {
    check(suspendableInitializer.isInitialized()) { "ChanPostRepository is not initialized yet!" }
    val archiveIds = toArchiveIdsSet(archiveId)

    return applicationScope.myAsync {
      return@myAsync tryWithTransaction {
        val fromCache = postCache.getLatest(descriptor, maxCount)
          .map { it.postDescriptor.postNo }
          .toSet()

        if (maxCount != Int.MAX_VALUE && fromCache.size >= maxCount) {
          return@tryWithTransaction fromCache
        }

        val postsFromDatabase = localSource.getThreadPosts(
          descriptor,
          archiveIds,
          fromCache,
          maxCount
        )

        if (postsFromDatabase.isNotEmpty()) {
          postsFromDatabase.forEach { post ->
            postCache.putIntoCache(post.postDescriptor, post)
          }
        }

        val fromDatabase = postsFromDatabase
          .map { post -> post.postDescriptor.postNo }
          .toSet()

        return@tryWithTransaction fromCache + fromDatabase
      }
    }
  }

  suspend fun getThreadPosts(
    descriptor: ChanDescriptor.ThreadDescriptor,
    archiveId: Long,
    maxCount: Int
  ): ModularResult<List<ChanPost>> {
    check(suspendableInitializer.isInitialized()) { "ChanPostRepository is not initialized yet!" }
    val archiveIds = toArchiveIdsSet(archiveId)

    logger.log(TAG, "getThreadPosts(descriptor=$descriptor, archiveIds=${archiveIds}, maxCount=$maxCount)")

    return applicationScope.myAsync {
      return@myAsync tryWithTransaction {
        val fromCache = postCache.getLatest(descriptor, maxCount)
        if (maxCount != Int.MAX_VALUE && fromCache.size >= maxCount) {
          return@tryWithTransaction fromCache
        }

        val postsNoToIgnore = fromCache.map { post -> post.postDescriptor.postNo }.toSet()

        val postsFromDatabase = localSource.getThreadPosts(
          descriptor,
          archiveIds,
          postsNoToIgnore,
          maxCount
        )

        if (postsFromDatabase.isNotEmpty()) {
          postsFromDatabase.forEach { post ->
            postCache.putIntoCache(post.postDescriptor, post)
          }
        }

        return@tryWithTransaction fromCache + postsFromDatabase
      }
    }
  }

  suspend fun deleteAll(): ModularResult<Int> {
    check(suspendableInitializer.isInitialized()) { "ChanPostRepository is not initialized yet!" }

    return applicationScope.myAsync {
      return@myAsync tryWithTransaction {
        return@tryWithTransaction localSource.deleteAll()
      }
    }
  }

  suspend fun deleteThread(threadDescriptor: ChanDescriptor.ThreadDescriptor): ModularResult<Unit> {
    check(suspendableInitializer.isInitialized()) { "ChanPostRepository is not initialized yet!" }

    return applicationScope.myAsync {
      return@myAsync tryWithTransaction {
        return@tryWithTransaction localSource.deleteThread(threadDescriptor)
      }
    }
  }

  suspend fun deletePost(postDescriptor: PostDescriptor): ModularResult<Unit> {
    check(suspendableInitializer.isInitialized()) { "ChanPostRepository is not initialized yet!" }

    return applicationScope.myAsync {
      return@myAsync tryWithTransaction {
        localSource.deletePost(postDescriptor)
        postCache.deletePost(postDescriptor)

        return@tryWithTransaction
      }
    }
  }

  suspend fun totalPostsCount(): ModularResult<Int> {
    check(suspendableInitializer.isInitialized()) { "ChanPostRepository is not initialized yet!" }

    return applicationScope.myAsync {
      return@myAsync tryWithTransaction {
        return@tryWithTransaction localSource.countTotalAmountOfPosts()
      }
    }
  }

  suspend fun totalThreadsCount(): ModularResult<Int> {
    check(suspendableInitializer.isInitialized()) { "ChanPostRepository is not initialized yet!" }

    return applicationScope.myAsync {
      return@myAsync tryWithTransaction {
        return@tryWithTransaction localSource.countTotalAmountOfThreads()
      }
    }
  }

  /**
   * Every post has it's own archiveId. If a post was not fetched from any archive it's archiveId
   * will be [ArchiveDescriptor.NO_ARCHIVE_ID]. Otherwise a real archive id will be used. When
   * loading posts from the database we want to get the archived posts for a thread as well as the
   * original posts. So that's why we use two archive ids: the currently chosen archive's id (it
   * may be [ArchiveDescriptor.NO_ARCHIVE_ID] too!) to load archived posts and
   * [ArchiveDescriptor.NO_ARCHIVE_ID] to load the regular posts. If both of them are
   * [ArchiveDescriptor.NO_ARCHIVE_ID] then the set will remove duplicates so only one id will be
   * left.
   * */
  private fun toArchiveIdsSet(archiveId: Long) =
    setOf(archiveId, ArchiveDescriptor.NO_ARCHIVE_ID)

  private suspend fun insertOrUpdateCatalogOriginalPosts(posts: MutableList<ChanPost>): List<Long> {
    if (posts.isEmpty()) {
      return emptyList()
    }

    require(posts.all { post -> post.isOp }) { "Not all posts are original posts" }
    localSource.insertManyOriginalPosts(posts)

    if (posts.isNotEmpty()) {
      posts.forEach { post ->
        postCache.putIntoCache(post.postDescriptor, post)
      }
    }

    return posts.map { it.postDescriptor.postNo }
  }

  private suspend fun insertOrUpdateThreadPosts(posts: MutableList<ChanPost>): List<Long> {
    var originalPost: ChanPost? = null
    val postsThatDifferWithCache = ArrayList<ChanPost>()

    // Figure out what posts differ from the cache that we want to update in the
    // database
    posts.forEach { chanPost ->
      val differsFromCached = postDiffersFromCached(chanPost)
      if (differsFromCached) {
        if (chanPost.isOp) {
          if (originalPost != null) {
            throw IllegalStateException("More than one OP found!")
          }

          originalPost = chanPost
        } else {
          postsThatDifferWithCache += chanPost
        }
      }
    }

    val chanThreadId = if (originalPost != null) {
      val chanThreadId = localSource.insertOriginalPost(originalPost!!)
      postCache.putIntoCache(
        originalPost!!.postDescriptor,
        originalPost!!
      )

      chanThreadId
    } else {
      if (postsThatDifferWithCache.isNotEmpty()) {
        localSource.getThreadIdByPostDescriptor(
          postsThatDifferWithCache.first().postDescriptor
        )
      } else {
        null
      }
    }

    if (chanThreadId == null) {
      return if (originalPost == null) {
        emptyList()
      } else {
        listOf(originalPost!!.postDescriptor.postNo)
      }
    }

    if (postsThatDifferWithCache.isNotEmpty()) {
      localSource.insertPosts(chanThreadId, postsThatDifferWithCache)

      postsThatDifferWithCache.forEach { post ->
        postCache.putIntoCache(post.postDescriptor, post)
      }
    }

    return postsThatDifferWithCache.map { it.postDescriptor.postNo }
  }

  @OptIn(ExperimentalTime::class)
  suspend fun deleteOldPostsIfNeeded(forced: Boolean = false): ModularResult<Int> {
    return applicationScope.myAsync {
      return@myAsync tryWithTransaction {
        val totalAmountOfPostsInDatabase = localSource.countTotalAmountOfPosts()
        if (totalAmountOfPostsInDatabase <= 0) {
          logger.log(TAG, "deleteOldPostsIfNeeded database is empty")
          return@tryWithTransaction 0
        }

        val maxPostsAmount = appConstants.maxAmountOfPostsInDatabase

        if (!forced && totalAmountOfPostsInDatabase < maxPostsAmount) {
          logger.log(TAG, "Not enough posts to start deleting, " +
            "posts in database amount: $totalAmountOfPostsInDatabase, " +
            "max allowed posts amount: $maxPostsAmount")
          return@tryWithTransaction 0
        }

        val toDeleteCount = if (forced) {
          totalAmountOfPostsInDatabase / 2
        } else {
          // Delete half of the posts in the database
          max(totalAmountOfPostsInDatabase, maxPostsAmount) / 2
        }

        logger.log(TAG, "Starting deleting $toDeleteCount posts " +
          "(totalAmountOfPostsInDatabase = $totalAmountOfPostsInDatabase, " +
          "maxPostsAmount = $maxPostsAmount)")

        val (deleteResult, time) = measureTimedValue { Try { localSource.deleteOldPosts(toDeleteCount) } }
        val deletedPostsCount = if (deleteResult is ModularResult.Error) {
          logger.logError(TAG, "Error while trying to delete old posts", deleteResult.error)
          throw deleteResult.error
        } else {
          (deleteResult as ModularResult.Value).value
        }

        val newAmount = localSource.countTotalAmountOfPosts()
        logger.log(TAG, "Deleted $deletedPostsCount posts, $newAmount posts left, took $time")

        return@tryWithTransaction deletedPostsCount
      }
    }
  }

  @OptIn(ExperimentalTime::class)
  suspend fun deleteOldThreadsIfNeeded(forced: Boolean = false): ModularResult<Int> {
    return applicationScope.myAsync {
      return@myAsync tryWithTransaction {
        val totalAmountOfThreadsInDatabase = localSource.countTotalAmountOfThreads()
        if (totalAmountOfThreadsInDatabase <= 0) {
          logger.log(TAG, "deleteOldThreadsIfNeeded database is empty")
          return@tryWithTransaction 0
        }

        val maxThreadsAmount = appConstants.maxAmountOfThreadsInDatabase

        if (!forced && totalAmountOfThreadsInDatabase < maxThreadsAmount) {
          logger.log(TAG, "Not enough threads to start deleting, " +
            "threads in database amount: $totalAmountOfThreadsInDatabase, " +
            "max allowed threads amount: $maxThreadsAmount")
          return@tryWithTransaction 0
        }

        val toDeleteCount = if (forced) {
          totalAmountOfThreadsInDatabase / 2
        } else {
          // Delete half of the posts in the database
          max(totalAmountOfThreadsInDatabase, maxThreadsAmount) / 2
        }

        logger.log(TAG, "Starting deleting $toDeleteCount threads " +
          "(totalAmountOfThreadsInDatabase = $totalAmountOfThreadsInDatabase, " +
          "maxThreadsAmount = $maxThreadsAmount)")

        val (deleteResult, time) = measureTimedValue { Try { localSource.deleteOldThreads(toDeleteCount) } }
        val deletedThreadsCount = if (deleteResult is ModularResult.Error) {
          logger.logError(TAG, "Error while trying to delete old threads", deleteResult.error)
          throw deleteResult.error
        } else {
          (deleteResult as ModularResult.Value).value
        }

        val newAmount = localSource.countTotalAmountOfThreads()
        logger.log(TAG, "Deleted $deletedThreadsCount threads, $newAmount threads left, took $time")

        return@tryWithTransaction deletedThreadsCount
      }
    }
  }

  private suspend fun postDiffersFromCached(chanPost: ChanPost): Boolean {
    val fromCache = postCache.getPostFromCache(
      chanPost.postDescriptor,
      chanPost.isOp
    )

    if (fromCache == null) {
      // Post is not cached yet - update
      return true
    }

    if (fromCache.isOp) {
      // Cached post is an original post - always update
      return true
    }

    if (fromCache != chanPost) {
      // Cached post is not the same as the fresh post - update
      return true
    }

    return false
  }

}