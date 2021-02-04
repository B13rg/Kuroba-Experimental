package com.github.k1rakishou.model

import android.app.Application
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.github.k1rakishou.common.DoNotStrip
import com.github.k1rakishou.model.converter.BitSetTypeConverter
import com.github.k1rakishou.model.converter.ChanPostImageTypeTypeConverter
import com.github.k1rakishou.model.converter.DateTimeTypeConverter
import com.github.k1rakishou.model.converter.HttpUrlTypeConverter
import com.github.k1rakishou.model.converter.JsonSettingsTypeConverter
import com.github.k1rakishou.model.converter.PeriodTypeConverter
import com.github.k1rakishou.model.converter.ReplyTypeTypeConverter
import com.github.k1rakishou.model.converter.TextTypeTypeConverter
import com.github.k1rakishou.model.converter.VideoServiceTypeConverter
import com.github.k1rakishou.model.dao.ChanBoardDao
import com.github.k1rakishou.model.dao.ChanCatalogSnapshotDao
import com.github.k1rakishou.model.dao.ChanFilterDao
import com.github.k1rakishou.model.dao.ChanFilterWatchGroupDao
import com.github.k1rakishou.model.dao.ChanPostDao
import com.github.k1rakishou.model.dao.ChanPostHideDao
import com.github.k1rakishou.model.dao.ChanPostHttpIconDao
import com.github.k1rakishou.model.dao.ChanPostImageDao
import com.github.k1rakishou.model.dao.ChanPostReplyDao
import com.github.k1rakishou.model.dao.ChanSavedReplyDao
import com.github.k1rakishou.model.dao.ChanSiteDao
import com.github.k1rakishou.model.dao.ChanTextSpanDao
import com.github.k1rakishou.model.dao.ChanThreadDao
import com.github.k1rakishou.model.dao.ChanThreadViewableInfoDao
import com.github.k1rakishou.model.dao.InlinedFileInfoDao
import com.github.k1rakishou.model.dao.MediaServiceLinkExtraContentDao
import com.github.k1rakishou.model.dao.NavHistoryDao
import com.github.k1rakishou.model.dao.SeenPostDao
import com.github.k1rakishou.model.dao.ThreadBookmarkDao
import com.github.k1rakishou.model.dao.ThreadBookmarkGroupDao
import com.github.k1rakishou.model.dao.ThreadBookmarkReplyDao
import com.github.k1rakishou.model.entity.InlinedFileInfoEntity
import com.github.k1rakishou.model.entity.MediaServiceLinkExtraContentEntity
import com.github.k1rakishou.model.entity.SeenPostEntity
import com.github.k1rakishou.model.entity.bookmark.ThreadBookmarkEntity
import com.github.k1rakishou.model.entity.bookmark.ThreadBookmarkGroupEntity
import com.github.k1rakishou.model.entity.bookmark.ThreadBookmarkGroupEntryEntity
import com.github.k1rakishou.model.entity.bookmark.ThreadBookmarkReplyEntity
import com.github.k1rakishou.model.entity.chan.board.ChanBoardEntity
import com.github.k1rakishou.model.entity.chan.board.ChanBoardIdEntity
import com.github.k1rakishou.model.entity.chan.catalog.ChanCatalogSnapshotEntity
import com.github.k1rakishou.model.entity.chan.filter.ChanFilterBoardConstraintEntity
import com.github.k1rakishou.model.entity.chan.filter.ChanFilterEntity
import com.github.k1rakishou.model.entity.chan.filter.ChanFilterWatchGroupEntity
import com.github.k1rakishou.model.entity.chan.post.ChanPostEntity
import com.github.k1rakishou.model.entity.chan.post.ChanPostHideEntity
import com.github.k1rakishou.model.entity.chan.post.ChanPostHttpIconEntity
import com.github.k1rakishou.model.entity.chan.post.ChanPostIdEntity
import com.github.k1rakishou.model.entity.chan.post.ChanPostImageEntity
import com.github.k1rakishou.model.entity.chan.post.ChanPostReplyEntity
import com.github.k1rakishou.model.entity.chan.post.ChanSavedReplyEntity
import com.github.k1rakishou.model.entity.chan.post.ChanTextSpanEntity
import com.github.k1rakishou.model.entity.chan.site.ChanSiteEntity
import com.github.k1rakishou.model.entity.chan.site.ChanSiteIdEntity
import com.github.k1rakishou.model.entity.chan.thread.ChanThreadEntity
import com.github.k1rakishou.model.entity.chan.thread.ChanThreadViewableInfoEntity
import com.github.k1rakishou.model.entity.navigation.NavHistoryElementIdEntity
import com.github.k1rakishou.model.entity.navigation.NavHistoryElementInfoEntity
import com.github.k1rakishou.model.entity.view.ChanThreadsWithPosts
import com.github.k1rakishou.model.entity.view.OldChanPostThread
import com.github.k1rakishou.model.migrations.Migration_v10_to_v11
import com.github.k1rakishou.model.migrations.Migration_v11_to_v12
import com.github.k1rakishou.model.migrations.Migration_v12_to_v13
import com.github.k1rakishou.model.migrations.Migration_v13_to_v14
import com.github.k1rakishou.model.migrations.Migration_v1_to_v2
import com.github.k1rakishou.model.migrations.Migration_v2_to_v3
import com.github.k1rakishou.model.migrations.Migration_v3_to_v4
import com.github.k1rakishou.model.migrations.Migration_v4_to_v5
import com.github.k1rakishou.model.migrations.Migration_v5_to_v6
import com.github.k1rakishou.model.migrations.Migration_v6_to_v7
import com.github.k1rakishou.model.migrations.Migration_v7_to_v8
import com.github.k1rakishou.model.migrations.Migration_v8_to_v9
import com.github.k1rakishou.model.migrations.Migration_v9_to_v10

@DoNotStrip
@Database(
  entities = [
    ChanSiteIdEntity::class,
    ChanSiteEntity::class,
    ChanBoardIdEntity::class,
    ChanBoardEntity::class,
    ChanThreadEntity::class,
    ChanPostIdEntity::class,
    ChanPostEntity::class,
    ChanPostImageEntity::class,
    ChanPostHttpIconEntity::class,
    ChanTextSpanEntity::class,
    ChanPostReplyEntity::class,
    ChanSavedReplyEntity::class,
    ChanPostHideEntity::class,
    ChanThreadViewableInfoEntity::class,
    ChanFilterEntity::class,
    ChanFilterBoardConstraintEntity::class,
    ChanFilterWatchGroupEntity::class,
    ChanCatalogSnapshotEntity::class,
    MediaServiceLinkExtraContentEntity::class,
    SeenPostEntity::class,
    InlinedFileInfoEntity::class,
    NavHistoryElementIdEntity::class,
    NavHistoryElementInfoEntity::class,
    ThreadBookmarkEntity::class,
    ThreadBookmarkReplyEntity::class,
    ThreadBookmarkGroupEntity::class,
    ThreadBookmarkGroupEntryEntity::class
  ],
  views = [
    ChanThreadsWithPosts::class,
    OldChanPostThread::class
  ],
  version = 14,
  exportSchema = true
)
@TypeConverters(
  value = [
    DateTimeTypeConverter::class,
    VideoServiceTypeConverter::class,
    PeriodTypeConverter::class,
    HttpUrlTypeConverter::class,
    ChanPostImageTypeTypeConverter::class,
    TextTypeTypeConverter::class,
    ReplyTypeTypeConverter::class,
    BitSetTypeConverter::class,
    JsonSettingsTypeConverter::class
  ]
)
abstract class KurobaDatabase : RoomDatabase() {
  abstract fun mediaServiceLinkExtraContentDao(): MediaServiceLinkExtraContentDao
  abstract fun seenPostDao(): SeenPostDao
  abstract fun inlinedFileDao(): InlinedFileInfoDao
  abstract fun chanBoardDao(): ChanBoardDao
  abstract fun chanThreadDao(): ChanThreadDao
  abstract fun chanPostDao(): ChanPostDao
  abstract fun chanPostImageDao(): ChanPostImageDao
  abstract fun chanPostHttpIconDao(): ChanPostHttpIconDao
  abstract fun chanTextSpanDao(): ChanTextSpanDao
  abstract fun chanPostReplyDao(): ChanPostReplyDao
  abstract fun navHistoryDao(): NavHistoryDao
  abstract fun threadBookmarkDao(): ThreadBookmarkDao
  abstract fun threadBookmarkReplyDao(): ThreadBookmarkReplyDao
  abstract fun threadBookmarkGroupDao(): ThreadBookmarkGroupDao
  abstract fun chanThreadViewableInfoDao(): ChanThreadViewableInfoDao
  abstract fun chanSiteDao(): ChanSiteDao
  abstract fun chanSavedReplyDao(): ChanSavedReplyDao
  abstract fun chanPostHideDao(): ChanPostHideDao
  abstract fun chanFilterDao(): ChanFilterDao
  abstract fun chanCatalogSnapshotDao(): ChanCatalogSnapshotDao
  abstract fun chanFilterWatchGroupDao(): ChanFilterWatchGroupDao

  suspend fun ensureInTransaction() {
    require(inTransaction()) { "Must be executed in a transaction!" }
  }

  companion object {
    const val DATABASE_NAME = "Kuroba.db"
    const val EMPTY_JSON = "{}"

    // SQLite will thrown an exception if you attempt to pass more than 999 values into the IN
    // operator so we need to use batching to avoid this crash. And we use 950 instead of 999
    // just to be safe.
    const val SQLITE_IN_OPERATOR_MAX_BATCH_SIZE = 950
    const val SQLITE_TRUE = 1
    const val SQLITE_FALSE = 0

    fun buildDatabase(application: Application): KurobaDatabase {
      return Room.databaseBuilder(
        application.applicationContext,
        KurobaDatabase::class.java,
        DATABASE_NAME
      )
        .addMigrations(
          Migration_v1_to_v2(),
          Migration_v2_to_v3(),
          Migration_v3_to_v4(),
          Migration_v4_to_v5(),
          Migration_v5_to_v6(),
          Migration_v6_to_v7(),
          Migration_v7_to_v8(),
          Migration_v8_to_v9(),
          Migration_v9_to_v10(),
          Migration_v10_to_v11(),
          Migration_v11_to_v12(),
          Migration_v12_to_v13(),
          Migration_v13_to_v14()
        )
        .fallbackToDestructiveMigrationOnDowngrade()
        .build()
    }

  }
}
