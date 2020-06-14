package com.github.adamantcheese.model.converter

import androidx.room.TypeConverter
import com.github.adamantcheese.model.data.video_service.MediaServiceType

class VideoServiceTypeConverter {

  @TypeConverter
  fun toVideoServiceType(value: Int): MediaServiceType {
    return MediaServiceType.fromTypeValue(value)
  }

  @TypeConverter
  fun fromVideoServiceType(mediaServiceType: MediaServiceType): Int {
    return mediaServiceType.typeValue
  }

}