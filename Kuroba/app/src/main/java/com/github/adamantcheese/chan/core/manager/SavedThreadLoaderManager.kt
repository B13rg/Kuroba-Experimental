package com.github.adamantcheese.chan.core.manager

import com.github.adamantcheese.chan.core.mapper.ThreadMapper
import com.github.adamantcheese.chan.core.model.ChanThread
import com.github.adamantcheese.chan.core.model.orm.Loadable
import com.github.adamantcheese.chan.core.repository.SavedThreadLoaderRepository
import com.github.adamantcheese.chan.ui.settings.base_directory.LocalThreadsBaseDirectory
import com.github.adamantcheese.chan.ui.theme.Theme
import com.github.adamantcheese.chan.utils.BackgroundUtils
import com.github.adamantcheese.chan.utils.Logger
import com.github.k1rakishou.fsaf.FileManager
import com.github.k1rakishou.fsaf.file.DirectorySegment
import com.github.k1rakishou.fsaf.file.FileSegment
import com.google.gson.Gson
import java.io.IOException
import javax.inject.Inject

class SavedThreadLoaderManager @Inject constructor(
        private val gson: Gson,
        private val savedThreadLoaderRepository: SavedThreadLoaderRepository,
        private val fileManager: FileManager,
        private val postFilterManager: PostFilterManager
) {

    fun loadSavedThread(loadable: Loadable, currentTheme: Theme): ChanThread? {
        BackgroundUtils.ensureBackgroundThread()

        val baseDir = fileManager.newBaseDirectoryFile<LocalThreadsBaseDirectory>()
        if (baseDir == null) {
            Logger.e(TAG, "loadSavedThread() fileManager.newLocalThreadFile() returned null")
            return null
        }

        val threadSaveDir = baseDir.clone(ThreadSaveManager.getThreadSubDir(loadable))

        val threadSaveDirExists = fileManager.exists(threadSaveDir)
        val threadSaveDirIsDirectory = fileManager.isDirectory(threadSaveDir)

        if (!threadSaveDirExists || !threadSaveDirIsDirectory) {
            Logger.e(TAG, "threadSaveDir does not exist or is not a directory: "
                    + "(path = " + threadSaveDir.getFullPath()
                    + ", exists = " + threadSaveDirExists
                    + ", isDir = " + threadSaveDirIsDirectory + ")")
            return null
        }

        val threadFile = threadSaveDir.clone(FileSegment(SavedThreadLoaderRepository.THREAD_FILE_NAME))

        val threadFileExists = fileManager.exists(threadFile)
        val threadFileIsFile = fileManager.isFile(threadFile)
        val threadFileCanRead = fileManager.canRead(threadFile)

        if (!threadFileExists || !threadFileIsFile || !threadFileCanRead) {
            Logger.e(TAG, "threadFile does not exist or not a file or cannot be read: " +
                    "(path = " + threadFile.getFullPath()
                    + ", exists = " + threadFileExists
                    + ", isFile = " + threadFileIsFile
                    + ", canRead = " + threadFileCanRead + ")")
            return null
        }

        val threadSaveDirImages = threadSaveDir.clone(DirectorySegment("images"))

        val imagesDirExists = fileManager.exists(threadSaveDirImages)
        val imagesDirIsDir = fileManager.isDirectory(threadSaveDirImages)

        if (!imagesDirExists || !imagesDirIsDir) {
            Logger.e(TAG, "threadSaveDirImages does not exist or is not a directory: "
                    + "(path = " + threadSaveDirImages.getFullPath()
                    + ", exists = " + imagesDirExists
                    + ", isDir = " + imagesDirIsDir + ")")
            return null
        }

        try {
            val serializableThread = savedThreadLoaderRepository.loadOldThreadFromJsonFile(threadSaveDir)
            if (serializableThread == null) {
                Logger.e(TAG, "Could not load thread from json")
                return null
            }

            return ThreadMapper.fromSerializedThread(
              gson,
              loadable,
              serializableThread,
              postFilterManager,
              currentTheme
            )
        } catch (e: IOException) {
            Logger.e(TAG, "Could not load saved thread", e)
            return null
        }
    }

    companion object {
        private const val TAG = "SavedThreadLoaderManager"
    }

}
