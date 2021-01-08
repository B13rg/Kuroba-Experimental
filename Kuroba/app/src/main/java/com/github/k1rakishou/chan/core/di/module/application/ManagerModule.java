/*
 * KurobaEx - *chan browser https://github.com/K1rakishou/Kuroba-Experimental/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.k1rakishou.chan.core.di.module.application;

import android.content.Context;

import com.github.k1rakishou.ChanSettings;
import com.github.k1rakishou.chan.core.base.okhttp.ProxiedOkHttpClient;
import com.github.k1rakishou.chan.core.base.okhttp.RealProxiedOkHttpClient;
import com.github.k1rakishou.chan.core.helper.DialogFactory;
import com.github.k1rakishou.chan.core.helper.FilterEngine;
import com.github.k1rakishou.chan.core.helper.LastPageNotificationsHelper;
import com.github.k1rakishou.chan.core.helper.LastViewedPostNoInfoHolder;
import com.github.k1rakishou.chan.core.helper.PostHideHelper;
import com.github.k1rakishou.chan.core.helper.ReplyNotificationsHelper;
import com.github.k1rakishou.chan.core.image.ImageLoaderV2;
import com.github.k1rakishou.chan.core.loader.OnDemandContentLoader;
import com.github.k1rakishou.chan.core.loader.impl.Chan4CloudFlareImagePreloader;
import com.github.k1rakishou.chan.core.loader.impl.InlinedFileInfoLoader;
import com.github.k1rakishou.chan.core.loader.impl.PostExtraContentLoader;
import com.github.k1rakishou.chan.core.loader.impl.PrefetchLoader;
import com.github.k1rakishou.chan.core.manager.ApplicationVisibilityManager;
import com.github.k1rakishou.chan.core.manager.ArchivesManager;
import com.github.k1rakishou.chan.core.manager.BoardManager;
import com.github.k1rakishou.chan.core.manager.BookmarksManager;
import com.github.k1rakishou.chan.core.manager.BottomNavBarVisibilityStateManager;
import com.github.k1rakishou.chan.core.manager.Chan4CloudFlareImagePreloaderManager;
import com.github.k1rakishou.chan.core.manager.ChanFilterManager;
import com.github.k1rakishou.chan.core.manager.ChanThreadManager;
import com.github.k1rakishou.chan.core.manager.ChanThreadViewableInfoManager;
import com.github.k1rakishou.chan.core.manager.ControllerNavigationManager;
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager;
import com.github.k1rakishou.chan.core.manager.HistoryNavigationManager;
import com.github.k1rakishou.chan.core.manager.LocalSearchManager;
import com.github.k1rakishou.chan.core.manager.OnDemandContentLoaderManager;
import com.github.k1rakishou.chan.core.manager.PageRequestManager;
import com.github.k1rakishou.chan.core.manager.PostFilterManager;
import com.github.k1rakishou.chan.core.manager.PostHideManager;
import com.github.k1rakishou.chan.core.manager.PostingLimitationsInfoManager;
import com.github.k1rakishou.chan.core.manager.PrefetchImageDownloadIndicatorManager;
import com.github.k1rakishou.chan.core.manager.ReplyManager;
import com.github.k1rakishou.chan.core.manager.ReportManager;
import com.github.k1rakishou.chan.core.manager.SavedReplyManager;
import com.github.k1rakishou.chan.core.manager.SeenPostsManager;
import com.github.k1rakishou.chan.core.manager.SettingsNotificationManager;
import com.github.k1rakishou.chan.core.manager.SiteManager;
import com.github.k1rakishou.chan.core.manager.ThreadBookmarkGroupManager;
import com.github.k1rakishou.chan.core.manager.ThreadFollowHistoryManager;
import com.github.k1rakishou.chan.core.site.ParserRepository;
import com.github.k1rakishou.chan.core.site.SiteRegistry;
import com.github.k1rakishou.chan.core.site.loader.ChanThreadLoaderCoordinator;
import com.github.k1rakishou.chan.core.site.parser.MockReplyManager;
import com.github.k1rakishou.chan.core.site.parser.ReplyParser;
import com.github.k1rakishou.chan.core.site.parser.search.SimpleCommentParser;
import com.github.k1rakishou.chan.core.usecase.FetchThreadBookmarkInfoUseCase;
import com.github.k1rakishou.chan.core.usecase.ParsePostRepliesUseCase;
import com.github.k1rakishou.chan.features.bookmarks.watcher.BookmarkForegroundWatcher;
import com.github.k1rakishou.chan.features.bookmarks.watcher.BookmarkWatcherCoordinator;
import com.github.k1rakishou.chan.features.bookmarks.watcher.BookmarkWatcherDelegate;
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils;
import com.github.k1rakishou.common.AppConstants;
import com.github.k1rakishou.core_themes.ThemeEngine;
import com.github.k1rakishou.model.repository.BoardRepository;
import com.github.k1rakishou.model.repository.BookmarksRepository;
import com.github.k1rakishou.model.repository.ChanFilterRepository;
import com.github.k1rakishou.model.repository.ChanPostHideRepository;
import com.github.k1rakishou.model.repository.ChanPostRepository;
import com.github.k1rakishou.model.repository.ChanSavedReplyRepository;
import com.github.k1rakishou.model.repository.ChanThreadViewableInfoRepository;
import com.github.k1rakishou.model.repository.HistoryNavigationRepository;
import com.github.k1rakishou.model.repository.SeenPostRepository;
import com.github.k1rakishou.model.repository.SiteRepository;
import com.github.k1rakishou.model.repository.ThreadBookmarkGroupRepository;
import com.github.k1rakishou.model.source.cache.thread.ChanThreadsCache;
import com.google.gson.Gson;

import java.io.File;
import java.util.HashSet;
import java.util.concurrent.Executor;

import javax.inject.Named;
import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import io.reactivex.schedulers.Schedulers;
import kotlinx.coroutines.CoroutineScope;

import static com.github.k1rakishou.chan.core.di.module.application.AppModule.getCacheDir;
import static com.github.k1rakishou.common.AndroidUtils.getNotificationManager;
import static com.github.k1rakishou.common.AndroidUtils.getNotificationManagerCompat;

@Module
public class ManagerModule {
    private static final String CRASH_LOGS_DIR_NAME = "crashlogs";

    @Provides
    @Singleton
    public SiteManager provideSiteManager(
            CoroutineScope appScope,
            SiteRepository siteRepository
    ) {
        return new SiteManager(
                appScope,
                AppModuleAndroidUtils.isDevBuild(),
                ChanSettings.verboseLogs.get(),
                siteRepository,
                SiteRegistry.INSTANCE
        );
    }

    @Provides
    @Singleton
    public BoardManager provideBoardManager(
            CoroutineScope appScope,
            SiteRepository siteRepository,
            BoardRepository boardRepository
    ) {
        return new BoardManager(
                appScope,
                AppModuleAndroidUtils.isDevBuild(),
                siteRepository,
                boardRepository
        );
    }

    @Provides
    @Singleton
    public FilterEngine provideFilterEngine(ChanFilterManager chanFilterManager) {
        return new FilterEngine(chanFilterManager);
    }

    @Provides
    @Singleton
    public ReplyManager provideReplyManager(
            AppConstants appConstants,
            Gson gson
    ) {
        return new ReplyManager(appConstants, gson);
    }

    @Provides
    @Singleton
    public PageRequestManager providePageRequestManager(
            SiteManager siteManager,
            BoardManager boardManager
    ) {
        return new PageRequestManager(
                siteManager,
                boardManager
        );
    }

    @Provides
    @Singleton
    public ArchivesManager provideArchivesManager(
            Context appContext,
            Gson gson,
            AppConstants appConstants,
            CoroutineScope appScope
    ) {
        return new ArchivesManager(
                gson,
                appContext,
                appScope,
                appConstants,
                ChanSettings.verboseLogs.get()
        );
    }

    @Provides
    @Singleton
    public MockReplyManager provideMockReplyManager() {
        return new MockReplyManager();
    }

    @Provides
    @Singleton
    public ReportManager provideReportManager(
            ProxiedOkHttpClient okHttpClient,
            Gson gson,
            SettingsNotificationManager settingsNotificationManager
    ) {
        File cacheDir = getCacheDir();

        return new ReportManager(
                okHttpClient.okHttpClient(),
                settingsNotificationManager,
                gson,
                new File(cacheDir, CRASH_LOGS_DIR_NAME)
        );
    }

    @Provides
    @Singleton
    public SettingsNotificationManager provideSettingsNotificationManager() {
        return new SettingsNotificationManager();
    }

    @Provides
    @Singleton
    public OnDemandContentLoaderManager provideOnDemandContentLoader(
            PrefetchLoader prefetchLoader,
            PostExtraContentLoader postExtraContentLoader,
            InlinedFileInfoLoader inlinedFileInfoLoader,
            Chan4CloudFlareImagePreloader chan4CloudFlareImagePreloader,
            @Named(ExecutorsModule.onDemandContentLoaderExecutorName) Executor onDemandContentLoaderExecutor
    ) {
        HashSet<OnDemandContentLoader> loaders = new HashSet<>();
        loaders.add(chan4CloudFlareImagePreloader);
        loaders.add(prefetchLoader);
        loaders.add(postExtraContentLoader);
        loaders.add(inlinedFileInfoLoader);

        return new OnDemandContentLoaderManager(
                Schedulers.from(onDemandContentLoaderExecutor),
                loaders
        );
    }

    @Provides
    @Singleton
    public SeenPostsManager provideSeenPostsManager(
            CoroutineScope appScope,
            SeenPostRepository seenPostRepository
    ) {
        return new SeenPostsManager(
                appScope,
                ChanSettings.verboseLogs.get(),
                seenPostRepository
        );
    }

    @Provides
    @Singleton
    public PrefetchImageDownloadIndicatorManager providePrefetchIndicatorAnimationManager() {
        return new PrefetchImageDownloadIndicatorManager();
    }

    @Provides
    @Singleton
    public GlobalWindowInsetsManager provideGlobalWindowInsetsManager() {
        return new GlobalWindowInsetsManager();
    }

    @Provides
    @Singleton
    public ApplicationVisibilityManager provideApplicationVisibilityManager() {
        return new ApplicationVisibilityManager();
    }

    @Provides
    @Singleton
    public HistoryNavigationManager provideHistoryNavigationManager(
            CoroutineScope appScope,
            HistoryNavigationRepository historyNavigationRepository,
            ApplicationVisibilityManager applicationVisibilityManager
    ) {
        return new HistoryNavigationManager(
                appScope,
                historyNavigationRepository,
                applicationVisibilityManager
        );
    }

    @Provides
    @Singleton
    public ControllerNavigationManager provideControllerNavigationManager() {
        return new ControllerNavigationManager();
    }

    @Provides
    @Singleton
    public PostFilterManager providePostFilterManager() {
        return new PostFilterManager();
    }

    @Provides
    @Singleton
    public BottomNavBarVisibilityStateManager provideReplyViewStateManager() {
        return new BottomNavBarVisibilityStateManager();
    }

    @Provides
    @Singleton
    public BookmarksManager provideBookmarksManager(
            CoroutineScope appScope,
            ApplicationVisibilityManager applicationVisibilityManager,
            ArchivesManager archivesManager,
            BookmarksRepository bookmarksRepository
    ) {
        return new BookmarksManager(
                AppModuleAndroidUtils.isDevBuild(),
                ChanSettings.verboseLogs.get(),
                appScope,
                applicationVisibilityManager,
                archivesManager,
                bookmarksRepository,
                SiteRegistry.INSTANCE
        );
    }

    @Provides
    @Singleton
    public ReplyParser provideReplyParser(
            SiteManager siteManager,
            ParserRepository parserRepository
    ) {
        return new ReplyParser(
                siteManager,
                parserRepository
        );
    }

    @Provides
    @Singleton
    public BookmarkWatcherDelegate provideBookmarkWatcherDelegate(
            BookmarksManager bookmarksManager,
            ArchivesManager archivesManager,
            SiteManager siteManager,
            LastViewedPostNoInfoHolder lastViewedPostNoInfoHolder,
            FetchThreadBookmarkInfoUseCase fetchThreadBookmarkInfoUseCase,
            ParsePostRepliesUseCase parsePostRepliesUseCase,
            ReplyNotificationsHelper replyNotificationsHelper,
            LastPageNotificationsHelper lastPageNotificationsHelper
    ) {
        return new BookmarkWatcherDelegate(
                AppModuleAndroidUtils.isDevBuild(),
                ChanSettings.verboseLogs.get(),
                bookmarksManager,
                archivesManager,
                siteManager,
                lastViewedPostNoInfoHolder,
                fetchThreadBookmarkInfoUseCase,
                parsePostRepliesUseCase,
                replyNotificationsHelper,
                lastPageNotificationsHelper
        );
    }

    @Provides
    @Singleton
    public BookmarkForegroundWatcher provideBookmarkForegroundWatcher(
            CoroutineScope appScope,
            Context appContext,
            AppConstants appConstants,
            BookmarksManager bookmarksManager,
            ArchivesManager archivesManager,
            BookmarkWatcherDelegate bookmarkWatcherDelegate,
            ApplicationVisibilityManager applicationVisibilityManager
    ) {
        return new BookmarkForegroundWatcher(
                ChanSettings.verboseLogs.get(),
                appScope,
                appContext,
                appConstants,
                bookmarksManager,
                archivesManager,
                bookmarkWatcherDelegate,
                applicationVisibilityManager
        );
    }

    @Provides
    @Singleton
    public BookmarkWatcherCoordinator provideBookmarkWatcherController(
            Context appContext,
            CoroutineScope appScope,
            AppConstants appConstants,
            BookmarksManager bookmarksManager,
            BookmarkForegroundWatcher bookmarkForegroundWatcher
    ) {
        return new BookmarkWatcherCoordinator(
                ChanSettings.verboseLogs.get(),
                appContext,
                appScope,
                appConstants,
                bookmarksManager,
                bookmarkForegroundWatcher
        );
    }

    @Provides
    @Singleton
    public LastViewedPostNoInfoHolder provideLastViewedPostNoInfoHolder() {
        return new LastViewedPostNoInfoHolder();
    }

    @Provides
    @Singleton
    public ReplyNotificationsHelper provideReplyNotificationsHelper(
            Context appContext,
            CoroutineScope appScope,
            BookmarksManager bookmarksManager,
            ChanPostRepository chanPostRepository,
            ImageLoaderV2 imageLoaderV2,
            ThemeEngine themeEngine,
            SimpleCommentParser simpleCommentParser
    ) {
        return new ReplyNotificationsHelper(
                AppModuleAndroidUtils.isDevBuild(),
                ChanSettings.verboseLogs.get(),
                appContext,
                appScope,
                getNotificationManagerCompat(),
                getNotificationManager(),
                bookmarksManager,
                chanPostRepository,
                imageLoaderV2,
                themeEngine,
                simpleCommentParser
        );
    }

    @Provides
    @Singleton
    public LastPageNotificationsHelper provideLastPageNotificationsHelper(
            Context appContext,
            PageRequestManager pageRequestManager,
            BookmarksManager bookmarksManager,
            ThemeEngine themeEngine
    ) {
        return new LastPageNotificationsHelper(
                AppModuleAndroidUtils.isDevBuild(),
                appContext,
                getNotificationManagerCompat(),
                pageRequestManager,
                bookmarksManager,
                themeEngine
        );
    }

    @Provides
    @Singleton
    public ChanThreadViewableInfoManager provideChanThreadViewableInfoManager(
            ChanThreadViewableInfoRepository chanThreadViewableInfoRepository,
            CoroutineScope appScope
    ) {
        return new ChanThreadViewableInfoManager(
                ChanSettings.verboseLogs.get(),
                appScope,
                chanThreadViewableInfoRepository
        );
    }

    @Provides
    @Singleton
    public SavedReplyManager provideSavedReplyManager(
            ChanSavedReplyRepository chanSavedReplyRepository
    ) {
        return new SavedReplyManager(
                ChanSettings.verboseLogs.get(),
                chanSavedReplyRepository
        );
    }

    @Provides
    @Singleton
    public PostHideManager providePostHideManager(
        ChanPostHideRepository chanPostHideRepository,
        CoroutineScope appScope
    ) {
        return new PostHideManager(
                ChanSettings.verboseLogs.get(),
                appScope,
                chanPostHideRepository
        );
    }

    @Provides
    @Singleton
    public ChanFilterManager provideChanFilterManager(
            ChanFilterRepository chanFilterRepository,
            ChanPostRepository chanPostRepository,
            CoroutineScope appScope,
            PostFilterManager postFilterManager
    ) {
        return new ChanFilterManager(
                appScope,
                chanFilterRepository,
                chanPostRepository,
                postFilterManager
        );
    }

    @Provides
    @Singleton
    public LocalSearchManager provideLocalSearchManager() {
        return new LocalSearchManager();
    }

    @Singleton
    @Provides
    public PostHideHelper providePostHideHelper(
            PostHideManager postHideManager,
            PostFilterManager postFilterManager
    ) {
        return new PostHideHelper(
                postHideManager,
                postFilterManager
        );
    }

    @Singleton
    @Provides
    public DialogFactory provideDialogFactory(
            ApplicationVisibilityManager applicationVisibilityManager,
            ThemeEngine themeEngine
    ) {
        return new DialogFactory(
                applicationVisibilityManager,
                themeEngine
        );
    }

    @Singleton
    @Provides
    public ThreadBookmarkGroupManager provideThreadBookmarkGroupEntryManager(
            CoroutineScope appScope,
            ThreadBookmarkGroupRepository threadBookmarkGroupEntryRepository,
            BookmarksManager bookmarksManager
    ) {
        return new ThreadBookmarkGroupManager(
                appScope,
                ChanSettings.verboseLogs.get(),
                threadBookmarkGroupEntryRepository,
                bookmarksManager
        );
    }

    @Singleton
    @Provides
    public Chan4CloudFlareImagePreloaderManager provideChan4CloudFlareImagePreloaderManager(
            CoroutineScope appScope,
            RealProxiedOkHttpClient realProxiedOkHttpClient,
            ChanThreadManager chanThreadManager
    ) {
        return new Chan4CloudFlareImagePreloaderManager(
                appScope,
                ChanSettings.verboseLogs.get(),
                realProxiedOkHttpClient,
                chanThreadManager
        );
    }

    @Singleton
    @Provides
    public ChanThreadManager provideChanThreadManager(
            SiteManager siteManager,
            BookmarksManager bookmarksManager,
            PostFilterManager postFilterManager,
            SavedReplyManager savedReplyManager,
            ChanThreadsCache chanThreadsCache,
            ChanPostRepository chanPostRepository,
            ChanThreadLoaderCoordinator chanThreadLoaderCoordinator
    ) {
        return new ChanThreadManager(
                siteManager,
                bookmarksManager,
                postFilterManager,
                savedReplyManager,
                chanThreadsCache,
                chanPostRepository,
                chanThreadLoaderCoordinator
        );
    }

    @Singleton
    @Provides
    public ThreadFollowHistoryManager provideThreadFollowHistoryManager() {
        return new ThreadFollowHistoryManager();
    }

    @Singleton
    @Provides
    public PostingLimitationsInfoManager providePostingLimitationsInfoManager(
            SiteManager siteManager
    ) {
        return new PostingLimitationsInfoManager(siteManager);
    }

}
