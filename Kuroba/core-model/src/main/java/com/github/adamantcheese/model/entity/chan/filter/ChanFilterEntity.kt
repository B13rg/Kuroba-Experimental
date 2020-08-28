package com.github.adamantcheese.model.entity.chan.filter

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.github.adamantcheese.model.data.filter.FilterType

@Entity(tableName = ChanFilterEntity.TABLE_NAME)
data class ChanFilterEntity(
  @PrimaryKey(autoGenerate = true)
  @ColumnInfo(name = FILTER_ID_COLUMN_NAME)
  val filterId: Long = 0L,
  @ColumnInfo(name = ENABLED_COLUMN_NAME)
  val enabled: Boolean = true,
  @ColumnInfo(name = TYPE_COLUMN_NAME)
  val type: Int = FilterType.SUBJECT.flag or FilterType.COMMENT.flag,
  @ColumnInfo(name = PATTERN_COLUMN_NAME)
  val pattern: String? = null,
  @ColumnInfo(name = ACTION_COLUMN_NAME)
  val action: Int = 0,
  @ColumnInfo(name = COLOR_COLUMN_NAME)
  val color: Int = 0,
  @ColumnInfo(name = FILTER_ORDER_COLUMN_NAME)
  val filterOrder: Int = -1,
  @ColumnInfo(name = APPLY_TO_REPLIES_COLUMN_NAME)
  val applyToReplies: Boolean = false,
  @ColumnInfo(name = ONLY_ON_OP_COLUMN_NAME)
  val onlyOnOP: Boolean = false,
  @ColumnInfo(name = APPLY_TO_SAVED_COLUMN_NAME)
  val applyToSaved: Boolean = false
) {

  companion object {
    const val TABLE_NAME = "chan_filter"

    const val FILTER_ID_COLUMN_NAME = "filter_id"
    const val ENABLED_COLUMN_NAME = "enabled"
    const val TYPE_COLUMN_NAME = "type"
    const val PATTERN_COLUMN_NAME = "pattern"
    const val ACTION_COLUMN_NAME = "action"
    const val COLOR_COLUMN_NAME = "color"
    const val FILTER_ORDER_COLUMN_NAME = "filter_order"
    const val APPLY_TO_REPLIES_COLUMN_NAME = "apply_to_replies"
    const val ONLY_ON_OP_COLUMN_NAME = "only_on_op"
    const val APPLY_TO_SAVED_COLUMN_NAME = "apply_to_saved"
  }
}