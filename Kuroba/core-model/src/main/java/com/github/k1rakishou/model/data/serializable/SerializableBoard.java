package com.github.k1rakishou.model.data.serializable;

import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

public class SerializableBoard {
    @SerializedName("id")
    public int id;
    @SerializedName("site_id")
    public int siteId;
    @SerializedName("serializable_site")
    public SerializableSite site;
    @SerializedName("saved")
    public boolean saved;
    @SerializedName("order")
    public int order;
    @SerializedName("name")
    public String name;
    @SerializedName("code")
    public String code;

    public SerializableBoard(
            int id, int siteId, SerializableSite site, boolean saved, int order, String name, String code
    ) {
        this.id = id;
        this.siteId = siteId;
        this.site = site;
        this.saved = saved;
        this.order = order;
        this.name = name;
        this.code = code;
    }

    @Override
    public int hashCode() {
        return 31 * siteId + 31 * name.hashCode() + 31 * code.hashCode();
    }

    @Override
    public boolean equals(@Nullable Object other) {
        if (other == null) {
            return false;
        }

        if (other == this) {
            return true;
        }

        if (this.getClass() != other.getClass()) {
            return false;
        }

        SerializableBoard otherBoard = (SerializableBoard) other;
        return this.siteId == otherBoard.siteId && this.name.equals(otherBoard.name)
                && this.code.equals(otherBoard.code);
    }
}
