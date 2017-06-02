/*
 * This file is part of Guru Cue Search & Recommendation Engine.
 * Copyright (C) 2017 Guru Cue Ltd.
 *
 * Guru Cue Search & Recommendation Engine is free software: you can
 * redistribute it and/or modify it under the terms of the GNU General
 * Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version.
 *
 * Guru Cue Search & Recommendation Engine is distributed in the hope
 * that it will be useful, but WITHOUT ANY WARRANTY; without even the
 * implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Guru Cue Search & Recommendation Engine. If not, see
 * <http://www.gnu.org/licenses/>.
 */
package com.gurucue.recommendations.rest.data.processing.zap;

import com.gurucue.recommendations.data.DataTypeCodes;
import com.gurucue.recommendations.entity.ConsumerEvent;
import com.gurucue.recommendations.entity.ConsumerEventType;
import com.gurucue.recommendations.entity.DataType;
import com.gurucue.recommendations.entity.Partner;
import com.gurucue.recommendations.entity.product.TvChannelProduct;
import com.gurucue.recommendations.entity.product.TvProgrammeProduct;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Contains data about a TV-channel viewership at a specific timestamp.
 */
public final class Viewership {
    public final Long timestampMillis;
    public final TvChannelProduct tvChannel;
    public final Partner partner;
    public final List<ConsumerEvent> zaps = new LinkedList<>();
    public TvProgrammeProduct tvProgramme = null;

    public Viewership(final Long timestampMillis, final TvChannelProduct tvChannel, final Partner partner) {
        this.timestampMillis = timestampMillis;
        this.tvChannel = tvChannel;
        this.partner = partner;
    }

    public void addZap(final ConsumerEvent zap) {
        zaps.add(zap);
    }

    public ConsumerEvent toEvent(final ConsumerEventType viewership, final DataTypeCodes dataTypeCodes) {
        final Map<DataType, String> data = new HashMap<>();
        final int n = zaps.size();
        data.put(dataTypeCodes.viewerCount, Integer.toString(n));
        if (tvProgramme != null) data.put(dataTypeCodes.tvProgrammeId, Long.toString(tvProgramme.id));
        final StringBuilder consumerIdBuilder = new StringBuilder(n * 124);
        final StringBuilder zapIdBuilder = new StringBuilder(n * 14);
        final Iterator<ConsumerEvent> zapIterator = zaps.iterator();
        for (final ConsumerEvent event : zaps) {
            if ((event.getConsumer() != null) && (event.getConsumer().getId() != null)) {
                if (consumerIdBuilder.length() > 0) consumerIdBuilder.append(",");
                consumerIdBuilder.append(event.getConsumer().getId().longValue());
            }
            if (event.getId() != null) {
                if (zapIdBuilder.length() > 0) zapIdBuilder.append(",");
                zapIdBuilder.append(event.getId().longValue());
            }
        }
        if (consumerIdBuilder.length() > 0) data.put(dataTypeCodes.consumerIdList, consumerIdBuilder.toString());
        if (zapIdBuilder.length() > 0) data.put(dataTypeCodes.zapIdList, zapIdBuilder.toString());
        final Timestamp ts = new Timestamp(timestampMillis);
        final ConsumerEvent result = new ConsumerEvent(null, ts, partner, tvChannel, null, viewership, data, null);
        result.setRequestTimestamp(ts);
        return result;
    }

    public void log(final StringBuilder output) {
        output.append("\n  ").append(tvChannel.title.asString()).append(": ").append(zaps.size()).append(" viewers");
    }
}
