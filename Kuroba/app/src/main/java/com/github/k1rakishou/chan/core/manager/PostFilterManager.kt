package com.github.k1rakishou.chan.core.manager

import com.github.k1rakishou.chan.core.model.PostFilter
import com.github.k1rakishou.common.DoNotStrip
import com.github.k1rakishou.common.hashSetWithCap
import com.github.k1rakishou.common.mutableMapWithCap
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

@DoNotStrip
class PostFilterManager {
  private val lock = ReentrantReadWriteLock()
  private val filterStorage = mutableMapWithCap<PostDescriptor, PostFilter>(512)

  fun insert(postDescriptor: PostDescriptor, postFilter: PostFilter) {
    lock.write { filterStorage[postDescriptor] = postFilter }
  }

  fun contains(postDescriptor: PostDescriptor): Boolean {
    return lock.read { filterStorage.containsKey(postDescriptor) }
  }

  fun remove(postDescriptor: PostDescriptor) {
    lock.write { filterStorage.remove(postDescriptor) }
  }

  fun removeMany(postDescriptorList: Collection<PostDescriptor>) {
    lock.write {
      postDescriptorList.forEach { postDescriptor ->
        filterStorage.remove(postDescriptor)
      }
    }
  }

  fun removeAllForDescriptor(chanDescriptor: ChanDescriptor) {
    lock.write {
      val toDelete = hashSetWithCap<PostDescriptor>(128)

      filterStorage.keys.forEach { postDescriptor ->
        if (postDescriptor.descriptor == chanDescriptor) {
          toDelete += postDescriptor
        }
      }

      toDelete.forEach { postDescriptor ->
        filterStorage.remove(postDescriptor)
      }
    }
  }

  fun update(postDescriptor: PostDescriptor, updateFunc: (PostFilter) -> Unit) {
    lock.write {
      val postFilter = filterStorage[postDescriptor] ?: PostFilter()
      updateFunc(postFilter)
      filterStorage[postDescriptor] = postFilter
    }
  }

  fun clear() {
    lock.write { filterStorage.clear() }
  }

  fun isEnabled(postDescriptor: PostDescriptor): Boolean {
    return lock.read { filterStorage[postDescriptor]?.enabled ?: false }
  }

  fun getFilterHash(postDescriptor: PostDescriptor): Int {
    return lock.read { filterStorage[postDescriptor]?.hashCode() ?: 0 }
  }

  fun getFilterHighlightedColor(postDescriptor: PostDescriptor): Int {
    return lock.read {
      val enabled = filterStorage[postDescriptor]?.enabled ?: false
      if (!enabled) {
        return@read 0
      }

      return@read filterStorage[postDescriptor]?.filterHighlightedColor ?: 0
    }
  }

  fun getFilterStub(postDescriptor: PostDescriptor): Boolean {
    return lock.read {
      val enabled = filterStorage[postDescriptor]?.enabled ?: false
      if (!enabled) {
        return@read false
      }

      return@read filterStorage[postDescriptor]?.filterStub ?: false
    }
  }

  fun getFilterRemove(postDescriptor: PostDescriptor): Boolean {
    return lock.read {
      val enabled = filterStorage[postDescriptor]?.enabled ?: false
      if (!enabled) {
        return@read false
      }

      return@read filterStorage[postDescriptor]?.filterRemove ?: false
    }
  }

  fun getFilterWatch(postDescriptor: PostDescriptor): Boolean {
    return lock.read {
      val enabled = filterStorage[postDescriptor]?.enabled ?: false
      if (!enabled) {
        return@read false
      }

      return@read filterStorage[postDescriptor]?.filterWatch ?: false
    }
  }

  fun getFilterReplies(postDescriptor: PostDescriptor): Boolean {
    return lock.read {
      val enabled = filterStorage[postDescriptor]?.enabled ?: false
      if (!enabled) {
        return@read false
      }

      return@read filterStorage[postDescriptor]?.filterReplies ?: false
    }
  }

  fun getFilterOnlyOP(postDescriptor: PostDescriptor): Boolean {
    return lock.read {
      val enabled = filterStorage[postDescriptor]?.enabled ?: false
      if (!enabled) {
        return@read false
      }

      return@read filterStorage[postDescriptor]?.filterOnlyOP ?: false
    }
  }

  fun getFilterSaved(postDescriptor: PostDescriptor): Boolean {
    return lock.read {
      val enabled = filterStorage[postDescriptor]?.enabled ?: false
      if (!enabled) {
        return@read false
      }

      return@read filterStorage[postDescriptor]?.filterSaved ?: false
    }
  }

  fun hasFilterParameters(postDescriptor: PostDescriptor): Boolean {
    return lock.read {
      return@read filterStorage[postDescriptor]?.filterSaved ?: false
        || filterStorage[postDescriptor]?.filterHighlightedColor != 0
        || filterStorage[postDescriptor]?.filterReplies ?: false
        || filterStorage[postDescriptor]?.filterStub ?: false
    }
  }

}