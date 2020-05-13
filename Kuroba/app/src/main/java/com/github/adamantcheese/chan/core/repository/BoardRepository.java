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
package com.github.adamantcheese.chan.core.repository;

import android.util.Pair;

import com.github.adamantcheese.chan.core.database.DatabaseBoardManager;
import com.github.adamantcheese.chan.core.database.DatabaseManager;
import com.github.adamantcheese.chan.core.model.orm.Board;
import com.github.adamantcheese.chan.core.site.Site;
import com.github.adamantcheese.chan.utils.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Observable;
import java.util.Observer;

import javax.inject.Inject;

public class BoardRepository implements Observer {
    private static final String TAG = "BoardRepository";

    private final DatabaseManager databaseManager;
    private final DatabaseBoardManager databaseBoardManager;

    private final SiteRepository.Sites allSites;

    private SitesBoards allBoards = new SitesBoards();
    private SitesBoards savedBoards = new SitesBoards();

    @Inject
    public BoardRepository(DatabaseManager databaseManager, SiteRepository siteRepository) {
        this.databaseManager = databaseManager;
        this.databaseBoardManager = databaseManager.getDatabaseBoardManager();

        allSites = siteRepository.all();
    }

    public void initialize() {
        updateObservablesSync();

        allSites.addObserver(this);
    }

    @Override
    public void update(Observable o, Object arg) {
        if (o == allSites) {
            updateObservablesAsync();
        }
    }

    public void updateAvailableBoardsForSite(Site site, List<Board> availableBoards) {
        databaseManager.runTaskAsync(() -> {
            boolean changed = databaseBoardManager.createAll(site, availableBoards).call();
            Logger.d(TAG, "updateAvailableBoardsForSite changed = " + changed);

            if (changed) {
                updateObservablesAsync();
            }

            return null;
        });
    }

    public Board getFromCode(Site site, String code) {
        for (SiteBoards siteBoards : allBoards.get()) {
            if (siteBoards.site.id() == site.id()) {
                for (Board board : siteBoards.boards) {
                    if (board.code.equals(code)) {
                        return board;
                    }
                }
                return null;
            }
        }

        return null;
    }

    public SitesBoards getAll() {
        return allBoards;
    }

    public SitesBoards getSaved() {
        return savedBoards;
    }

    public List<Board> getSiteBoards(Site site) {
        for (SiteBoards item : allBoards.siteBoards) {
            if (item.site.id() == site.id()) {
                return item.boards;
            }
        }
        return new ArrayList<>();
    }

    public List<Board> getSiteSavedBoards(Site site) {
        for (SiteBoards item : savedBoards.siteBoards) {
            if (item.site.id() == site.id()) {
                return item.boards;
            }
        }
        return new ArrayList<>();
    }

    public void updateBoardOrders(List<Board> boards) {
        databaseManager.runTaskAsync(
                databaseBoardManager.updateOrders(boards),
                (e) -> updateObservablesAsync()
        );
    }

    public void setSaved(Board board, boolean saved) {
        board.saved = saved;

        databaseManager.runTaskAsync(
                databaseBoardManager.updateIncludingUserFields(board),
                (e) -> updateObservablesAsync()
        );
    }

    public void setAllSaved(List<Board> boards, boolean saved) {
        for (Board board : boards) {
            board.saved = saved;
        }

        databaseManager.runTaskAsync
                (databaseBoardManager.updateIncludingUserFields(boards),
                (e) -> updateObservablesAsync()
        );
    }

    private void updateObservablesSync() {
        List<Pair<Site, List<Board>>> result = databaseManager.runTask(
                databaseBoardManager.getBoardsForAllSitesOrdered(allSites.getAll())
        );

        updateWith(result);
    }

    private void updateObservablesAsync() {
        databaseManager.runTaskAsync(
                databaseBoardManager.getBoardsForAllSitesOrdered(allSites.getAll()),
                this::updateWith
        );
    }

    private void updateWith(List<Pair<Site, List<Board>>> databaseData) {
        List<SiteBoards> all = new ArrayList<>();
        List<SiteBoards> saved = new ArrayList<>();
        for (Pair<Site, List<Board>> item : databaseData) {
            all.add(new SiteBoards(item.first, item.second));

            List<Board> savedBoards = new ArrayList<>();
            for (Board board : item.second) {
                if (board.saved) savedBoards.add(board);
            }
            saved.add(new SiteBoards(item.first, savedBoards));
        }

        allBoards.set(all);
        savedBoards.set(saved);

        allBoards.notifyObservers();
        savedBoards.notifyObservers();
    }

    public static class SitesBoards extends Observable {
        private List<SiteBoards> siteBoards = new ArrayList<>();

        public void set(List<SiteBoards> siteBoards) {
            this.siteBoards = siteBoards;
            setChanged();
        }

        public List<SiteBoards> get() {
            return siteBoards;
        }
    }

    public static class SiteBoards {
        public final Site site;
        public final List<Board> boards;

        public SiteBoards(Site site, List<Board> boards) {
            this.site = site;
            this.boards = boards;
        }
    }
}
