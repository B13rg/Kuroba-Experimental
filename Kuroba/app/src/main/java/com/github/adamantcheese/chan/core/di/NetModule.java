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
package com.github.adamantcheese.chan.core.di;

import android.net.ConnectivityManager;

import com.github.adamantcheese.chan.BuildConfig;
import com.github.adamantcheese.chan.Chan;
import com.github.adamantcheese.chan.core.cache.CacheHandler;
import com.github.adamantcheese.chan.core.cache.FileCacheV2;
import com.github.adamantcheese.chan.core.cache.stream.WebmStreamingSource;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.core.site.SiteResolver;
import com.github.adamantcheese.chan.core.site.http.HttpCallManager;
import com.github.adamantcheese.chan.utils.Logger;
import com.github.k1rakishou.feather2.Provides;
import com.github.k1rakishou.fsaf.FileManager;
import com.github.k1rakishou.fsaf.file.RawFile;

import java.io.File;

import javax.inject.Named;
import javax.inject.Singleton;

import okhttp3.Dns;
import okhttp3.OkHttpClient;

import static com.github.adamantcheese.chan.core.di.AppModule.getCacheDir;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getApplicationLabel;
import static java.util.concurrent.TimeUnit.SECONDS;

public class NetModule {
    public static final String USER_AGENT = getApplicationLabel() + "/" + BuildConfig.VERSION_NAME;
    public static final String DOWNLOADER_OKHTTP_CLIENT_NAME = "downloader_okhttp_client";
    private static final String FILE_CACHE_DIR = "filecache";
    private static final String FILE_CHUNKS_CACHE_DIR = "file_chunks_cache";

    @Provides
    @Singleton
    public CacheHandler provideCacheHandler(
            FileManager fileManager
    ) {
        Logger.d(AppModule.DI_TAG, "Cache handler");

        File cacheDir = getCacheDir();
        RawFile cacheDirFile = fileManager.fromRawFile(new File(cacheDir, FILE_CACHE_DIR));
        RawFile chunksCacheDirFile = fileManager.fromRawFile(new File(cacheDir, FILE_CHUNKS_CACHE_DIR));

        return new CacheHandler(fileManager, cacheDirFile, chunksCacheDirFile, ChanSettings.autoLoadThreadImages.get());
    }

    @Provides
    @Singleton
    public FileCacheV2 provideFileCacheV2(
            ConnectivityManager connectivityManager,
            FileManager fileManager,
            CacheHandler cacheHandler,
            SiteResolver siteResolver,
            @Named(DOWNLOADER_OKHTTP_CLIENT_NAME) OkHttpClient okHttpClient
    ) {
        Logger.d(AppModule.DI_TAG, "File cache V2");
        return new FileCacheV2(fileManager, cacheHandler, siteResolver, okHttpClient, connectivityManager);
    }

    @Provides
    @Singleton
    public WebmStreamingSource provideWebmStreamingSource(
            FileManager fileManager, FileCacheV2 fileCacheV2, CacheHandler cacheHandler
    ) {
        Logger.d(AppModule.DI_TAG, "WebmStreamingSource");
        return new WebmStreamingSource(fileManager, fileCacheV2, cacheHandler);
    }

    @Provides
    @Singleton
    public HttpCallManager provideHttpCallManager(ProxiedOkHttpClient okHttpClient) {
        Logger.d(AppModule.DI_TAG, "Http call manager");
        return new HttpCallManager(okHttpClient);
    }

    /**
     * This okHttpClient is for posting.
     */
    // TODO(FileCacheV2): make this @Named as well instead of using hacks
    @Provides
    @Singleton
    public ProxiedOkHttpClient provideProxiedOkHttpClient(Dns okHttpDns, Chan.OkHttpProtocols okHttpProtocols) {
        Logger.d(AppModule.DI_TAG, "ProxiedOkHTTP client");
        return new ProxiedOkHttpClient(okHttpDns, okHttpProtocols);
    }

    /**
     * This okHttpClient is for images/file/apk updates/ downloading, prefetching, etc.
     */
    @Provides
    @Singleton
    @Named(DOWNLOADER_OKHTTP_CLIENT_NAME)
    public OkHttpClient provideOkHttpClient(Dns okHttpDns, Chan.OkHttpProtocols okHttpProtocols) {
        Logger.d(AppModule.DI_TAG, "DownloaderOkHttp client");

        return new OkHttpClient.Builder()
                .readTimeout(5, SECONDS)
                .writeTimeout(5, SECONDS)
                .protocols(okHttpProtocols.protocols)
                .dns(okHttpDns)
                .build();
    }

    //this is basically the same as OkHttpClient, but with a singleton for a proxy instance
    public static class ProxiedOkHttpClient extends OkHttpClient {
        private OkHttpClient proxiedClient;
        private Dns okHttpDns;
        private Chan.OkHttpProtocols okHttpProtocols;

        public ProxiedOkHttpClient(Dns okHttpDns, Chan.OkHttpProtocols okHttpProtocols) {
            this.okHttpDns = okHttpDns;
            this.okHttpProtocols = okHttpProtocols;
        }

        public OkHttpClient getProxiedClient() {
            if (proxiedClient == null) {
                // Proxies are usually slow, so they have increased timeouts
                proxiedClient = newBuilder()
                        .proxy(ChanSettings.getProxy())
                        .connectTimeout(30, SECONDS)
                        .readTimeout(30, SECONDS)
                        .writeTimeout(30, SECONDS)
                        .protocols(okHttpProtocols.protocols)
                        .dns(okHttpDns)
                        .build();
            }

            return proxiedClient;
        }
    }
}
