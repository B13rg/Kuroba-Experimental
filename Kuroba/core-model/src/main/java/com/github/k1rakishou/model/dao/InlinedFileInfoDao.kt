package com.github.k1rakishou.model.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.github.k1rakishou.model.entity.InlinedFileInfoEntity
import org.joda.time.DateTime

@Dao
abstract class InlinedFileInfoDao {

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  abstract suspend fun insert(inlinedFileInfoEntity: InlinedFileInfoEntity)

  @Query("""
        SELECT * 
        FROM ${InlinedFileInfoEntity.TABLE_NAME} 
        WHERE 
            ${InlinedFileInfoEntity.FILE_URL_COLUMN_NAME} = :fileUrl
    """)
  abstract suspend fun selectByFileUrl(fileUrl: String): InlinedFileInfoEntity?

  @Query("""
        DELETE 
        FROM ${InlinedFileInfoEntity.TABLE_NAME}
        WHERE ${InlinedFileInfoEntity.INSERTED_AT_COLUMN_NAME} < :dateTime
    """)
  abstract suspend fun deleteOlderThan(dateTime: DateTime): Int

  @Query("DELETE FROM ${InlinedFileInfoEntity.TABLE_NAME}")
  abstract suspend fun deleteAll(): Int

  @Query("SELECT COUNT(*) FROM ${InlinedFileInfoEntity.TABLE_NAME}")
  abstract suspend fun count(): Int

  /**
   * For tests only!
   * */
  @Query("SELECT *FROM ${InlinedFileInfoEntity.TABLE_NAME}")
  abstract suspend fun testGetAll(): List<InlinedFileInfoEntity>

}