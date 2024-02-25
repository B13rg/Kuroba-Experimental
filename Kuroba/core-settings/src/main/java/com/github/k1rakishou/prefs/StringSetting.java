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
package com.github.k1rakishou.prefs;

import com.github.k1rakishou.Setting;
import com.github.k1rakishou.SettingProvider;

import io.reactivex.Flowable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.processors.BehaviorProcessor;

public class StringSetting extends Setting<String> {
    private BehaviorProcessor<String> settingState = BehaviorProcessor.create();
    private volatile boolean hasCached = false;
    private String cached;

    public StringSetting(SettingProvider settingProvider, String key, String def) {
        super(settingProvider, key, def);
    }

    @Override
    public String get() {
        if (!hasCached) {
            cached = settingProvider.getString(key, def);
            hasCached = true;
        }
        return cached;
    }

    @Override
    public void set(String value) {
        if (!value.equals(get())) {
            settingProvider.putString(key, value);
            cached = value;
            settingState.onNext(value);
        }
    }

    public void setSync(String value) {
        if (!value.equals(get())) {
            settingProvider.putStringSync(key, value);
            cached = value;
            settingState.onNext(value);
        }
    }

    public void setSyncNoCheck(String value) {
        settingProvider.putStringSync(key, value);
        cached = value;
        settingState.onNext(value);
    }

    public void remove() {
        settingProvider.removeSync(key);
        hasCached = false;
        cached = def;
        settingState.onNext(def);
    }

    public Flowable<String> listenForChanges() {
        return settingState
                .onBackpressureLatest()
                .hide()
                .observeOn(AndroidSchedulers.mainThread());
    }

}
