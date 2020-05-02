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

import android.content.Context;

import com.github.adamantcheese.chan.core.database.DatabaseHelper;
import com.github.adamantcheese.chan.core.database.DatabaseManager;
import com.github.adamantcheese.chan.utils.Logger;
import com.github.k1rakishou.feather2.Provides;

import javax.inject.Singleton;

public class DatabaseModule {

    @Provides
    @Singleton
    public DatabaseHelper provideDatabaseHelper(Context applicationContext) {
        Logger.d(AppModule.DI_TAG, "Database helper");
        return new DatabaseHelper(applicationContext);
    }

    @Provides
    @Singleton
    public DatabaseManager provideDatabaseManager() {
        Logger.d(AppModule.DI_TAG, "Database manager");
        return new DatabaseManager();
    }
}
