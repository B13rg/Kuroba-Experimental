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
package com.github.adamantcheese.json;

import com.github.adamantcheese.SettingProvider;

public class JsonSettingsProvider implements SettingProvider {
    public final JsonSettings jsonSettings;
    private Callback callback;

    public JsonSettingsProvider(JsonSettings jsonSettings, Callback callback) {
        this.jsonSettings = jsonSettings;
        this.callback = callback;
    }

    //region Integer
    @Override
    public int getInt(String key, int def) {
        JsonSetting setting = jsonSettings.settings.get(key);
        if (setting != null) {
            return ((IntegerJsonSetting) setting).value;
        } else {
            return def;
        }
    }

    @Override
    public void putInt(String key, int value) {
        JsonSetting jsonSetting = jsonSettings.settings.get(key);
        if (jsonSetting == null) {
            IntegerJsonSetting v = new IntegerJsonSetting(value);
            jsonSettings.settings.put(key, v);
        } else {
            ((IntegerJsonSetting) jsonSetting).value = value;
        }
        callback.save();
    }

    @Override
    public void putIntSync(String key, Integer value) {
        throw new UnsupportedOperationException();
    }

    //endregion
    //region Long
    @Override
    public long getLong(String key, long def) {
        JsonSetting setting = jsonSettings.settings.get(key);
        if (setting != null) {
            return ((LongJsonSetting) setting).value;
        } else {
            return def;
        }
    }

    @Override
    public void putLong(String key, long value) {
        JsonSetting jsonSetting = jsonSettings.settings.get(key);
        if (jsonSetting == null) {
            LongJsonSetting v = new LongJsonSetting(value);
            jsonSettings.settings.put(key, v);
        } else {
            ((LongJsonSetting) jsonSetting).value = value;
        }
        callback.save();
    }

    @Override
    public void putLongSync(String key, Long value) {
        throw new UnsupportedOperationException();
    }

    //endregion
    //region Boolean
    @Override
    public boolean getBoolean(String key, boolean def) {
        JsonSetting setting = jsonSettings.settings.get(key);
        if (setting != null) {
            return ((BooleanJsonSetting) setting).value;
        } else {
            return def;
        }
    }

    @Override
    public void putBoolean(String key, boolean value) {
        JsonSetting jsonSetting = jsonSettings.settings.get(key);
        if (jsonSetting == null) {
            BooleanJsonSetting v = new BooleanJsonSetting(value);
            jsonSettings.settings.put(key, v);
        } else {
            ((BooleanJsonSetting) jsonSetting).value = value;
        }
        callback.save();
    }

    @Override
    public void putBooleanSync(String key, Boolean value) {
        throw new UnsupportedOperationException();
    }

    //endregion
    //region String
    @Override
    public String getString(String key, String def) {
        JsonSetting setting = jsonSettings.settings.get(key);
        if (setting != null) {
            return ((StringJsonSetting) setting).value;
        } else {
            return def;
        }
    }

    @Override
    public void putString(String key, String value) {
        JsonSetting jsonSetting = jsonSettings.settings.get(key);
        if (jsonSetting == null) {
            StringJsonSetting v = new StringJsonSetting(value);
            jsonSettings.settings.put(key, v);
        } else {
            ((StringJsonSetting) jsonSetting).value = value;
        }
        callback.save();
    }

    @Override
    public void putStringSync(String key, String value) {
        throw new UnsupportedOperationException();
    }
    //endregion

    @Override
    public void removeSync(String key) {
        throw new UnsupportedOperationException();
    }

    public interface Callback {
        void save();
    }
}
