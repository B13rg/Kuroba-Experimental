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
package com.github.adamantcheese.chan.core.database;

import com.github.adamantcheese.chan.core.model.orm.SiteModel;
import com.github.adamantcheese.chan.core.site.Site;
import com.j256.ormlite.stmt.DeleteBuilder;
import com.j256.ormlite.stmt.QueryBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

public class DatabaseSiteManager {
    private DatabaseHelper helper;

    public DatabaseSiteManager(DatabaseHelper databaseHelper) {
        this.helper = databaseHelper;
    }

    public Callable<SiteModel> byId(int id) {
        return () -> helper.getSiteDao().queryForId(id);
    }

    public Callable<List<SiteModel>> getAll() {
        return () -> helper.getSiteDao().queryForAll();
    }

    public Callable<Long> getCount() {
        return () -> helper.getSiteDao().countOf();
    }

    public Callable<SiteModel> add(final SiteModel site) {
        return () -> {
            helper.getSiteDao().create(site);
            return site;
        };
    }

    public Callable<SiteModel> update(final SiteModel site) {
        return () -> {
            helper.getSiteDao().update(site);
            return site;
        };
    }

    public Callable<SiteModel> updateId(final SiteModel site, final int newId) {
        return () -> {
            helper.getSiteDao().updateId(site, newId);
            return site;
        };
    }

    public Callable<Map<Integer, Integer>> getOrdering() {
        return () -> {
            QueryBuilder<SiteModel, Integer> q = helper.getSiteDao().queryBuilder();
            q.selectColumns("id", "order");
            List<SiteModel> modelsWithOrder = q.query();
            Map<Integer, Integer> ordering = new HashMap<>();
            for (SiteModel siteModel : modelsWithOrder) {
                ordering.put(siteModel.id, siteModel.order);
            }
            return ordering;
        };
    }

    public Callable<Void> updateOrdering(final List<Integer> siteIdsWithCorrectOrder) {
        return () -> {
            for (int i = 0; i < siteIdsWithCorrectOrder.size(); i++) {
                Integer id = siteIdsWithCorrectOrder.get(i);
                SiteModel m = helper.getSiteDao().queryForId(id);
                m.order = i;
                helper.getSiteDao().update(m);
            }
            return null;
        };
    }

    public Callable<Void> deleteSite(Site site) {
        return () -> {
            DeleteBuilder<SiteModel, Integer> builder = helper.getSiteDao().deleteBuilder();
            builder.where().eq("id", site.id());
            builder.delete();

            return null;
        };
    }
}
