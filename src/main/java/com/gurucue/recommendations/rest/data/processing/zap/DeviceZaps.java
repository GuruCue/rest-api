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

import com.gurucue.recommendations.Transaction;
import com.gurucue.recommendations.data.DataLink;
import com.gurucue.recommendations.data.DataManager;
import com.gurucue.recommendations.data.DataTypeCodes;
import com.gurucue.recommendations.data.ProductTypeCodes;
import com.gurucue.recommendations.entity.ConsumerEvent;
import com.gurucue.recommendations.entity.DataType;
import com.gurucue.recommendations.entity.ProductType;
import com.gurucue.recommendations.entity.product.Product;
import com.gurucue.recommendations.entity.product.TvProgrammeProduct;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * Holds zaps that belong to a specific device and a specific tv-programme,
 * to convert them to a live-tv consumption event upon invocation of the
 * {@link #convert(String, com.gurucue.recommendations.entity.product.TvProgrammeProduct, TvChannelState, long, DeviceState, StringBuilder)}
 * method.
 *
 * @see com.gurucue.recommendations.rest.data.processing.zap.TvProgrammeState
 * @see com.gurucue.recommendations.rest.data.processing.zap.TvChannelState
 */
final class DeviceZaps {
    private static final Logger log = LogManager.getLogger(DeviceZaps.class);
    private final TreeMap<Long, ConsumerEvent> zaps = new TreeMap<>(); // timeMillis -> zap

    final synchronized void store(final ConsumerEvent event) {
        zaps.put(event.getEventTimestamp().getTime(), event);
    }

    final synchronized void convert(final String zapKey, final TvProgrammeProduct tvProgramme, final TvChannelState tvChannelState, final long tvChannelId, final DeviceState deviceState, final StringBuilder logBuilder) {
        final Iterator<Map.Entry<Long, ConsumerEvent>> zapIterator = zaps.entrySet().iterator();
        if (!zapIterator.hasNext()) return;
        final ConcurrentSkipListMap<Long, Product> deviceZapTimes = deviceState.events;
        final long beginTime = tvProgramme.beginTimeMillis;
        final long endTime = tvProgramme.endTimeMillis;
        final ProductTypeCodes productTypeCodes = DataManager.getProductTypeCodes();
        final long idForTvChannel = productTypeCodes.idForTvChannel;

        Map.Entry<Long, ConsumerEvent> zapEntry = zapIterator.next();
        ConsumerEvent firstZapEvent = zapEntry.getValue();
        // compose info about what we're processing, for logging purposes
        final String itemInfo = "tv-channel " + tvChannelState.tvChannel.partnerProductCode + ", device " + zapKey + " (consumer " + firstZapEvent.getConsumer().getId() + "), tv-programme " + tvProgramme.id + " (end-time " + (tvProgramme.endTimeMillis / 1000L) + ")";
        long firstZapTime = firstZapEvent.getEventTimestamp().getTime(); // when a switch on the tv-programme occurred, it could be caused by a long-zap in which case the first zap has the timestamp before beginTime
        ConsumerEvent lastZapEvent = firstZapEvent;
        long lastZapTime = firstZapTime;
        int zapCount = firstZapTime < beginTime ? 0 : 1;
        Long lastZapoutTime = lowestZapoutTime(firstZapTime < beginTime ? beginTime : firstZapTime, tvChannelId, deviceZapTimes, idForTvChannel); // when a switch off the tv-programme occurred
        long watchDuration = ((lastZapoutTime == null) || (lastZapoutTime.longValue() > endTime) ? endTime : lastZapoutTime.longValue()) - (firstZapTime < beginTime ? beginTime : firstZapTime);

        // watch interval accounting for logging
        logBuilder.append("device ");
        logBuilder.append(zapKey);
        logBuilder.append(" (consumer ");
        logBuilder.append(firstZapEvent.getConsumer().getId());
        logBuilder.append(" [");
        logBuilder.append(firstZapEvent.getConsumer().getUsername());
        logBuilder.append("]) watch intervals: ");
        if (firstZapTime < beginTime) {
            logBuilder.append("[LONG ZAP] 0-");
        }
        else {
            logBuilder.append((firstZapTime - beginTime) / 1000L);
            logBuilder.append("-");
        }
        if (lastZapoutTime == null) {
            logBuilder.append((endTime - beginTime) / 1000L);
            logBuilder.append(" [NO ZAPOUT]");
        }
        else if (lastZapoutTime.longValue() > endTime) {
            logBuilder.append((endTime - beginTime) / 1000L);
            logBuilder.append(" [ZAPOUT IN FUTURE]");
        }
        else {
            logBuilder.append((lastZapoutTime.longValue() - beginTime) / 1000L);
        }

        // if there was no zapout, then we watched the tv-programme to its end, no sense in looping over any leftover zaps
        while ((lastZapoutTime != null) && zapIterator.hasNext()) {
            zapEntry = zapIterator.next();
            final Long currentZapTime = zapEntry.getKey();
            lastZapTime = currentZapTime.longValue();
            lastZapEvent = zapEntry.getValue(); // remember the last zap for making a long zap
            if (lastZapTime < lastZapoutTime.longValue()) continue; // the next lowest zapout is later from this zap, so this zap must be bogus, skip it
            // the iteration over a SortedMap is ordered, so we are assured that currentZapTime is the last zap up to now
            lastZapoutTime = lowestZapoutTime(currentZapTime, tvChannelId, deviceZapTimes, idForTvChannel);

            logBuilder.append(", ");
            if (lastZapTime < beginTime) {
                logBuilder.append("[ERROR] 0-");
            }
            else {
                logBuilder.append((lastZapTime - beginTime) / 1000L);
                logBuilder.append("-");
            }
            if (lastZapoutTime == null) {
                logBuilder.append((endTime - beginTime) / 1000L);
                logBuilder.append(" [NO ZAPOUT]");
            }
            else if (lastZapoutTime.longValue() > endTime) {
                logBuilder.append((endTime - beginTime) / 1000L);
                logBuilder.append(" [ZAPOUT IN FUTURE: ");
                logBuilder.append((lastZapoutTime.longValue() - beginTime) / 1000L);
                logBuilder.append("]");
            }
            else {
                logBuilder.append((lastZapoutTime.longValue() - beginTime) / 1000L);
            }

            zapCount++;
            watchDuration += ((lastZapoutTime == null) || (lastZapoutTime.longValue() > endTime) ? endTime : lastZapoutTime.longValue()) - (lastZapTime < beginTime ? beginTime : lastZapTime);
        }

        final long lastActivity = lastZapoutTime == null ? lastZapTime : lastZapoutTime.longValue();
        if ((endTime - lastActivity) >= 21600000L) {
            logBuilder.append(" [IGNORED: last activity more than 6 hours ago: ");
            logBuilder.append((endTime - lastActivity) / 1000L);
            logBuilder.append(" secs before end-time]");
            return;
        }

        long consumptionTimeMillis = endTime;
        try {
            if ((lastZapoutTime == null) || (lastZapoutTime.longValue() >= endTime)) {
                // user is still watching the same tv-channel after this tv-programme ended
                consumptionTimeMillis = endTime;
                logBuilder.append(" [LONGZAP]");
                try {
                    tvChannelState.processLongZap(lastZapEvent, endTime, zapKey, deviceState);
                } catch (Exception e) {
                    log.error("Failed to process a long zap, " + itemInfo + ": " + e.toString(), e);
                }
            } else consumptionTimeMillis = lastZapoutTime.longValue();
        }
        catch (Exception e) {
            log.error("Failed to compute the event timestamp, " + itemInfo + ": " + e.toString(), e);
        }

        final DataTypeCodes dataTypeCodes = DataManager.getDataTypeCodes();
        final Map<DataType, String> data = new HashMap<>();
        final ConsumerEvent consumption = new ConsumerEvent(null, new Timestamp(consumptionTimeMillis), firstZapEvent.getPartner(), tvProgramme, firstZapEvent.getConsumer(), DataManager.getConsumerEventTypeCodes().liveTvConsumption, data, firstZapEvent.getUserProfileId());
        consumption.setRequestTimestamp(new Timestamp(System.currentTimeMillis()));
        final Map<DataType, String> existingData = lastZapEvent.getData();
        if (existingData != null) {
            final String deviceId = existingData.get(dataTypeCodes.deviceId);
            final String deviceType = existingData.get(dataTypeCodes.deviceType);
            if (deviceId != null) data.put(dataTypeCodes.deviceId, deviceId);
            if (deviceType != null) data.put(dataTypeCodes.deviceType, deviceType);
        }
        logBuilder.append("; ZAPCNT=");
        String s = Integer.toString(zapCount, 10);
        logBuilder.append(s);
        data.put(dataTypeCodes.zapCount, s);
        if (firstZapTime < beginTime) {
            s = Long.toString((beginTime - firstZapTime) / 1000L, 10);
            logBuilder.append(", ZAPOFF=");
            logBuilder.append(s);
            data.put(dataTypeCodes.zapOffset, s); // in seconds
            logBuilder.append(", WCHOFF=0");
            data.put(dataTypeCodes.watchOffset, "0");
        }
        else {
            logBuilder.append(", ZAPOFF=0");
            data.put(dataTypeCodes.zapOffset, "0");
            s = Long.toString((firstZapTime - beginTime) / 1000L, 10);
            logBuilder.append(", WCHOFF=");
            logBuilder.append(s);
            data.put(dataTypeCodes.watchOffset, s); // in seconds
        }
        s = Long.toString(watchDuration / 1000L, 10);
        logBuilder.append(", WCHDUR=");
        logBuilder.append(s);
        data.put(dataTypeCodes.watchDuration, s); // in seconds
        s = endTime > beginTime ? Long.toString((watchDuration * 100L) / (endTime - beginTime)) : "0";
        logBuilder.append(", WCHPRC=");
        logBuilder.append(s);
        data.put(dataTypeCodes.watchPercentage, s);
        // set origin
        if (firstZapTime < beginTime) {
            // user didn't switch on to the tv-channel while the tv-programme was playing, but was watching it from the very start, therefore the origin is the previous tv-programme
            final TvProgrammeProduct previousTvProgramme;
            try (final DataLink link = DataManager.getNewLink()) {
                try (final Transaction transaction = Transaction.newTransaction(link)) {
                    previousTvProgramme = link.getProductManager().tvProgrammeAtTimeForTvChannelAndPartner(transaction, firstZapEvent.getPartner(), tvChannelState.tvChannel, beginTime - 50000L); // 50 seconds before
                    transaction.commit();
                }
            }
            data.put(dataTypeCodes.origin, "other");
            logBuilder.append(", origin other");
            if (previousTvProgramme == null) {
                logBuilder.append(", origin ID unknown");
            }
            else {
                if (previousTvProgramme.id >= 0L) {
                    final String originId = Long.toString(previousTvProgramme.id, 10);
                    data.put(dataTypeCodes.originId, originId);
                    logBuilder.append(", origin ID ").append(originId);
                }
                else logBuilder.append(", origin ID missing");

                final String originCode;
                final ProductType productType = productTypeCodes.byId(previousTvProgramme.productTypeId);
                if (productType == null) {
                    if (previousTvProgramme.partnerProductCode == null) originCode = null;
                    else originCode = "(null product type) " + previousTvProgramme.partnerProductCode;
                }
                else if (previousTvProgramme.partnerProductCode == null) originCode = productType.getIdentifier() + " (null partner product code)";
                else originCode = productType.getIdentifier() + " " + previousTvProgramme.partnerProductCode;
                if (originCode != null) {
                    data.put(dataTypeCodes.originCode, originCode);
                    logBuilder.append(" (").append(originCode).append(") ");
                }
            }
            data.put(dataTypeCodes.originWait, "0"); // in seconds
            logBuilder.append(", origin wait 0 s (consumer stayed on the tv-channel)");
        }
        else {
            // discover the origin "normally": find the first previous zap
            deviceState.setOrigin(logBuilder, dataTypeCodes, firstZapEvent, firstZapTime, tvChannelId, data);
        }

        try {
            DataManager.queueConsumerEvent(consumption);
        } catch (InterruptedException e) {
            log.error("Interrupted while waiting to queue a livetv-consumption event: " + e.toString(), e);
        }
    }

    final synchronized void clear() {
        zaps.clear();
    }

    /**
     * Returns the lowest zap-out timestamp from the given zapTime on
     * TV-channel with the given tvChannelId, going through the timestamp
     * mapping of given device zap times. If no zap with a different
     * TV-channel or something else than a TV-channel after the given
     * zapTime exists, then a null is returned.
     *
     * @param zapTime the timestamp where to begin searching for a zap-out
     * @param tvChannelId a zap-out is a zap that doesn't belong to this TV-channel
     * @param deviceZapTimes the ordered timestamp - product mapping of zaps to search
     * @param productTypeIdForTvChannel the product-type ID for TV-channels, given as a parameter to speed up processing
     * @return the timestamp od the next lowest zap after zapTime, that doesn't belong to the given tvChannelId
     */
    private static Long lowestZapoutTime(Long zapTime, final long tvChannelId, final ConcurrentSkipListMap<Long, Product> deviceZapTimes, final long productTypeIdForTvChannel) {
        for (;;) {
            Map.Entry<Long, Product> lastZapout = deviceZapTimes.higherEntry(zapTime); // when a switch off the tv-programme occurred: timeMillis -> tvChannelId
            if (lastZapout == null) return null; // no more zaps after zapTime
            final Product partnerProduct = lastZapout.getValue();
            if ((partnerProduct == null) || (partnerProduct.id < 0L)) return lastZapout.getKey(); // either zapped to an unknown thing, or a box status change (power-off), either way this qualifies as a zapout
            final Product product = lastZapout.getValue();
            if (((product.id != tvChannelId) || (product.productTypeId != productTypeIdForTvChannel))) return lastZapout.getKey(); // zapped to a different tv-channel, or gone watching something else than live-tv, so this qualifies as a zapout
            zapTime = lastZapout.getKey(); // still on the same tv-channel, so retry with the next higher zapTime
        }
    }

    final void currentViewership(final Viewership viewership, final long tvChannelId, final DeviceState deviceState, final long productTypeIdForTvChannel) {
        final Long timestampMillis = viewership.timestampMillis;
        final Map.Entry<Long, ConsumerEvent> floorZap;
        synchronized (this) {
            floorZap = zaps.floorEntry(timestampMillis);
        }
        if (floorZap == null) return;
        final Long zapoutTime = lowestZapoutTime(floorZap.getKey(), tvChannelId, deviceState.events, productTypeIdForTvChannel);
        if ((zapoutTime != null) && (zapoutTime < timestampMillis)) return;
        viewership.addZap(floorZap.getValue());
    }

    /**
     * Remove zaps below the given timestamp.
     *
     * @param timestampMillis the timestamp below which to remove all the zaps
     */
    final synchronized void cutOffZaps(final Long timestampMillis) {
        final NavigableMap<Long, ConsumerEvent> submap = zaps.headMap(timestampMillis, false);
        if (submap != null) submap.clear();
    }

    final synchronized boolean isEmpty() {
        return zaps.isEmpty();
    }
}
