package com.github.k1rakishou.model.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.github.k1rakishou.model.entity.navigation.NavHistoryElementIdEntity
import com.github.k1rakishou.model.entity.navigation.NavHistoryElementInfoEntity
import com.github.k1rakishou.model.entity.navigation.NavHistoryFullDto

@Dao
abstract class NavHistoryDao {

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  abstract suspend fun insertManyIdsOrReplace(
    navHistoryElementIdEntityList: List<NavHistoryElementIdEntity>
  ): List<Long>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  abstract suspend fun insertManyInfoOrReplace(
    navHistoryElementInfoEntityList: List<NavHistoryElementInfoEntity>
  ): List<Long>

  @Query("""
    SELECT * 
    FROM ${NavHistoryElementIdEntity.TABLE_NAME} nav_ids
    INNER JOIN ${NavHistoryElementInfoEntity.TABLE_NAME} nav_infos
        ON nav_ids.${NavHistoryElementIdEntity.ID_COLUMN_NAME} = nav_infos.${NavHistoryElementInfoEntity.OWNER_NAV_HISTORY_ID_COLUMN_NAME}
    ORDER BY nav_infos.${NavHistoryElementInfoEntity.ELEMENT_ORDER_COLUMN_NAME} ASC
    LIMIT :maxCount
  """)
  abstract suspend fun selectAll(maxCount: Int): List<NavHistoryFullDto>

  @Query("""
    DELETE 
    FROM ${NavHistoryElementIdEntity.TABLE_NAME}
    WHERE ${NavHistoryElementIdEntity.ID_COLUMN_NAME} NOT IN (:excludedIds)
  """)
  abstract suspend fun deleteAllExcept(excludedIds: List<Long>)

}