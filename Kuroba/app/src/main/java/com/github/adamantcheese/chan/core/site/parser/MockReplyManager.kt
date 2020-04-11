package com.github.adamantcheese.chan.core.site.parser

import androidx.annotation.GuardedBy
import com.github.adamantcheese.chan.utils.Logger
import com.github.adamantcheese.model.data.descriptor.BoardDescriptor
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor
import java.util.*

/**
 * This class is used to add mock replies to posts that were made by you or marked as yours. The
 * main point of this is to tests things related to replies to your posts (like notifications
 * showing up on (You)s and such). This is only should be used for development purposes.
 *
 * If you want to add a mock reply to a post that was not made by you then you should mark that
 * post as yours beforehand (in case you want to test (You) notification show up) because this class
 * DOES NOT do that automatically for you.
 *
 * Also, the replies are not persisted across application lifecycle, so once the app dies all
 * replies in the mockReplyMultiMap will be gone and you will have to add them again.
 *
 * ThreadSafe.
 * */
class MockReplyManager {
    @GuardedBy("this")
    private val mockReplyMultiMap = mutableMapOf<ChanDescriptor.ThreadDescriptor, LinkedList<Int>>()

    fun addMockReply(siteName: String, boardCode: String, opNo: Int, postNo: Int) {
        synchronized(this) {
            val threadDescriptor = ChanDescriptor.ThreadDescriptor(BoardDescriptor.create(siteName, boardCode), opNo.toLong())

            if (!mockReplyMultiMap.containsKey(threadDescriptor)) {
                mockReplyMultiMap[threadDescriptor] = LinkedList()
            }

            mockReplyMultiMap[threadDescriptor]!!.addFirst(postNo)
            Logger.d(TAG, "addMockReply() mock replies count = ${mockReplyMultiMap.size}")
        }
    }

    fun getLastMockReply(siteName: String, boardCode: String, opNo: Int): Int {
        return synchronized(this) {
            val threadDescriptor = ChanDescriptor.ThreadDescriptor(BoardDescriptor.create(siteName, boardCode), opNo.toLong())

            val repliesQueue = mockReplyMultiMap[threadDescriptor]
                    ?: return@synchronized -1

            if (repliesQueue.isEmpty()) {
                mockReplyMultiMap.remove(threadDescriptor)
                return@synchronized -1
            }

            val lastReply = repliesQueue.removeLast()
            Logger.d(TAG, "getLastMockReplyOrNull() mock replies " +
                    "count = ${mockReplyMultiMap.values.sumBy { queue -> queue.size }}")

            if (repliesQueue.isEmpty()) {
                mockReplyMultiMap.remove(threadDescriptor)
            }

            return@synchronized lastReply
        }
    }

    companion object {
        private const val TAG = "MockReplyManager"
    }
}