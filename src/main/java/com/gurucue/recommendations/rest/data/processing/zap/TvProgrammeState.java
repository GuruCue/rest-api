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

import com.gurucue.recommendations.Timer;
import com.gurucue.recommendations.TimerListener;
import com.gurucue.recommendations.entity.ConsumerEvent;
import com.gurucue.recommendations.entity.product.TvProgrammeProduct;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Processes live-tv zaps by collecting zaps for each device separately,
 * for a specific tv-programme.
 * Holds a list of device zap handlers to achieve per-device zap collection.
 * Installs a timer to fire 5 minutes after the tv-programme ends, to trigger
 * each device zap handler's consumption generation. After that the instance
 * is no longer processing any zaps that may arrive.
 *
 * @see com.gurucue.recommendations.rest.data.processing.zap.DeviceZaps
 */
final class TvProgrammeState implements TimerListener {
    private static final Logger log = LogManager.getLogger(TvProgrammeState.class);
    final ConcurrentHashMap<String, DeviceZaps> devices = new ConcurrentHashMap<>(); // indexed by zap key
    volatile boolean isGone = false;

    final TvChannelState owner;
    final TvProgrammeProduct tvProgramme;

    TvProgrammeState(final TvChannelState owner, final TvProgrammeProduct tvProgramme) {
        this.owner = owner;
        this.tvProgramme = tvProgramme;
        Timer.INSTANCE.schedule(tvProgramme.endTimeMillis + TvChannelState.PROCESSING_DELAY, this); // when to generate, flush and evict consumptions for this tv-programme
        final StringBuilder logBuilder = new StringBuilder(256);
        logBuilder.append("Now collecting zaps for tv-programme ");
        logBuilder.append(tvProgramme.id);
        logBuilder.append(" [");
        logBuilder.append(tvProgramme.partnerProductCode);
        logBuilder.append("] \"");
        logBuilder.append(tvProgramme.title.asString());
        logBuilder.append("\" on tv-channel ");
        logBuilder.append(owner.tvChannel.id);
        logBuilder.append(" [");
        logBuilder.append(owner.tvChannel.partnerProductCode);
        logBuilder.append("], begin=");
        logBuilder.append(tvProgramme.beginTimeMillis / 1000L);
        logBuilder.append(", end=");
        logBuilder.append(tvProgramme.endTimeMillis / 1000L);
        log.info(logBuilder.toString());
    }

    final DeviceZaps process(final ConsumerEvent zap, final String zapKey, final DeviceState deviceState) {
        if (isGone) {
            return null;
        }
        deviceState.addTvProgramme(tvProgramme);
        final DeviceZaps existingStatus = devices.get(zapKey);
        if (existingStatus == null) {
            final DeviceZaps newStatus = new DeviceZaps();
            final DeviceZaps previousStatus = devices.putIfAbsent(zapKey, newStatus);
            if (previousStatus != null) {
                previousStatus.store(zap);
                return previousStatus;
            }
            else {
                newStatus.store(zap);
                return newStatus;
            }
        }
        else {
            existingStatus.store(zap);
            return existingStatus;
        }
    }

    @Override
    final public void onTimerExpired(final long expiryTime) {
        if (isGone) {
            // evict phase
            log.debug("Evicting tv-programme " + tvProgramme.id);
            owner.remove(tvProgramme);
            devices.clear(); // expunge, so GC has easier work
            return;
        }

        // verify that we can generate consumption
        final long generationMillis = tvProgramme.endTimeMillis + owner.owner.currentBufferingDelayMillis;
        if (expiryTime < generationMillis) {
            // reschedule
            Timer.INSTANCE.schedule(generationMillis, this);
            return;
        }

        // generate consumption
        isGone = true; // mark for eviction
        // cache the oft-used heap variables locally
        final ConcurrentHashMap<String, DeviceState> events = owner.owner.events;
        final TvChannelState owner = this.owner;
        final TvProgrammeProduct tvProgramme = this.tvProgramme;
        // Evict with a delay, so anything parallel to this processing won't store something for nothing,
        // and so we don't get duplicate tv-programmes because of micro-timing issues between requests,
        // because we're using lock-free algorithms.
        // And not to interfere with viewership computations!
        final long evictionMillis = tvProgramme.endTimeMillis + owner.owner.currentStatDelayMillis + ZapProcessor.CONSUMPTION_FLUSH_INTERVAL + 30000L;
        Timer.INSTANCE.schedule(evictionMillis, this); // give it 30 seconds, which should be more than plenty
        final long tvChannelId = owner.tvChannel.id;
        // consumption generation phase
        final StringBuilder logBuilder = new StringBuilder(131072);
        logBuilder.append("Generating livetv-consumptions for tv-programme ");
        logBuilder.append(tvProgramme.id);
        logBuilder.append(" [");
        logBuilder.append(tvProgramme.partnerProductCode);
        logBuilder.append("] \"");
        logBuilder.append(tvProgramme.title.asString());
        logBuilder.append("\" on tv-channel ");
        logBuilder.append(owner.tvChannel.id);
        logBuilder.append(" [");
        logBuilder.append(owner.tvChannel.partnerProductCode);
        logBuilder.append("], begin=");
        logBuilder.append(tvProgramme.beginTimeMillis / 1000L);
        logBuilder.append(", end=");
        logBuilder.append(tvProgramme.endTimeMillis / 1000L);
        logBuilder.append(", devices=");
        logBuilder.append(devices.size());
        try {
            for (final Map.Entry<String, DeviceZaps> stateEntry : devices.entrySet()) {
                final String zapKey = stateEntry.getKey();
                final DeviceState deviceState = events.get(zapKey);
                if (deviceState == null) {
                    // at least the zap that generated this device state must be present, otherwise something is terribly wrong
                    log.error("Cannot generate livetv-consumption: there are no zaps for device key " + zapKey);
                    continue;
                }
                final DeviceZaps deviceZaps = stateEntry.getValue();
                try {
                    logBuilder.append("\n    ");
                    deviceZaps.convert(zapKey, tvProgramme, owner, tvChannelId, deviceState, logBuilder);
                } catch (Throwable e) {
                    log.error("Failed while attempting livetv-consumption conversion: " + e.toString(), e);
                    logBuilder.append(e.toString());
                }
                deviceZaps.clear(); // expunge events, so GC has easier work
                deviceState.evictTvProgramme(tvProgramme);
            }
        }
        finally {
            log.debug(logBuilder.toString());
        }
    }

    final void currentViewership(final Viewership viewership, final long tvChannelId, final ConcurrentHashMap<String, DeviceState> events, final long idForTvChannel) {
        final List<Map.Entry<String, DeviceZaps>> deviceZapses = new LinkedList<>(devices.entrySet()); // cache for iteration
        for (final Map.Entry<String, DeviceZaps> entry : deviceZapses) {
            entry.getValue().currentViewership(viewership, tvChannelId, events.get(entry.getKey()), idForTvChannel);
        }
    }
}
