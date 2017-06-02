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
import com.gurucue.recommendations.Transaction;
import com.gurucue.recommendations.data.DataLink;
import com.gurucue.recommendations.data.DataManager;
import com.gurucue.recommendations.entity.ConsumerEvent;
import com.gurucue.recommendations.entity.Partner;
import com.gurucue.recommendations.entity.product.TvChannelProduct;
import com.gurucue.recommendations.entity.product.TvProgrammeProduct;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Entry point for processing live-tv zaps.
 * Holds a list of active (currently broadcasting) tv-programme
 * handlers and dispatches zaps to them.
 *
 * @see com.gurucue.recommendations.rest.data.processing.zap.TvProgrammeState
 */
final class TvChannelState {
    private static final Logger log = LogManager.getLogger(TvChannelState.class);
    public static final long PROCESSING_DELAY = 5L * 60L * 1000L; // 5 minutes into the past, in milliseconds
    final ZapProcessor owner;
    private final ConcurrentHashMap<Long, TvProgrammeState> tvProgrammes = new ConcurrentHashMap<>(); // indexed by tv-programme ID
    final TvChannelProduct tvChannel;
    final Partner partner;
    private volatile boolean hasEPG = true; // log missing EPG only if it's a new happenstance, i.e. log it only once for those tv-channels that don't have EPG at all
    private final Map<String, DeviceZaps> noEPGViewership = new HashMap<>(); // zapKey -> timestamp -> ConsumerEvent, for tv-channels without EPG

    TvChannelState(final ZapProcessor owner, final TvChannelProduct tvChannel, final Partner partner) {
        this.owner = owner;
        this.tvChannel = tvChannel;
        this.partner = partner;
    }

    final void process(final ConsumerEvent zap, final String zapKey, final DeviceState deviceState) {
        final TvProgrammeProduct tvProgramme;

        final long tvSearchStart = System.nanoTime();
        try (final DataLink link = DataManager.getNewLink()) {
            try (final Transaction transaction = Transaction.newTransaction(link)) {
                tvProgramme = link.getProductManager().tvProgrammeAtTimeForTvChannelAndPartner(transaction, zap.getPartner(), tvChannel, zap.getEventTimestamp().getTime());
                transaction.commit();
            }
        }
        final long tvSearchStop = System.nanoTime();
        final long tvSearchTime = tvSearchStop - tvSearchStart;
        if (tvSearchTime > 5000000L) { // 5 milliseconds
            log.error("Finding the currently playing tv-programme took too long: " + tvSearchTime + " ns");
        }
        if (tvProgramme == null) {
            synchronized (noEPGViewership) {
                DeviceZaps deviceZaps = noEPGViewership.get(zapKey);
                if (deviceZaps == null) {
                    deviceZaps = new DeviceZaps();
                    noEPGViewership.put(zapKey, deviceZaps);
                }
                deviceZaps.store(zap);
            }
            if (hasEPG) {
                hasEPG = false;
                log.error("Cannot process zap: cannot find a TV-programme running on TV-channel " + tvChannel.partnerProductCode + " at time " + (zap.getEventTimestamp().getTime() / 1000L));
            }
            return;
        }
        else hasEPG = true;

        final TvProgrammeState state = getState(zap, tvProgramme);
        if (state != null) state.process(zap, zapKey, deviceState);
    }

    private TvProgrammeState getState(final ConsumerEvent zap, final TvProgrammeProduct tvProgramme) {
        final TvProgrammeState state;
        final TvProgrammeState existingState = tvProgrammes.get(tvProgramme.id);
        if (existingState == null) {
            final long now = Timer.currentTimeMillis();
            if (tvProgramme.endTimeMillis < (now - owner.currentBufferingDelayMillis)) {
                log.warn("Ignoring zap on TV-channel " + tvChannel.partnerProductCode + " at time " + (zap.getEventTimestamp().getTime() / 1000L) + ": TV-programme running at the time ended more than 5 minutes ago (ID: " + tvProgramme.id + ")");
                return null;
            }
            final TvProgrammeState newState = new TvProgrammeState(this, tvProgramme);
            final TvProgrammeState previousState = tvProgrammes.putIfAbsent(tvProgramme.id, newState);
            if (previousState == null) state = newState;
            else state = previousState;
        }
        else if (existingState.isGone) {
            log.warn("Ignoring zap on TV-channel " + tvChannel.partnerProductCode + " at time " + (zap.getEventTimestamp().getTime() / 1000L) + ": TV-programme running at the time ended more than 5 minutes ago (ID: " + tvProgramme.id + ")");
            return null;
        }
        else state = existingState;

        return state;
    }

    final void processLongZap(final ConsumerEvent zap, final long previousTvProgrammeEndTime, final String zapKey, final DeviceState deviceState) {
        final TvProgrammeProduct tvProgramme;
        try (final DataLink link = DataManager.getNewLink()) {
            try (final Transaction transaction = Transaction.newTransaction(link)) {
                tvProgramme = link.getProductManager().firstTvProgrammeAfterTimeForTvChannelAndPartner(transaction, zap.getPartner(), tvChannel, previousTvProgrammeEndTime - 1L);
                transaction.commit();
            }
        }
        if (tvProgramme == null) {
            log.error("Cannot process long zap: cannot find a TV-programme running on TV-channel " + tvChannel.partnerProductCode + " at or after time " + (previousTvProgrammeEndTime / 1000L));
            return;
        }

        // TODO: what if the tvProgramme begin-time is in the future? Do a timer with re-checking if no zap occured up to then.
        final TvProgrammeState programmeState = getState(zap, tvProgramme);
        if (programmeState != null) {
            final DeviceZaps deviceZaps = programmeState.process(zap, zapKey, deviceState);
        }
    }

    final void remove(final TvProgrammeProduct tvProgramme) {
        tvProgrammes.remove(tvProgramme.id);
    }

    final Viewership viewership(final long timestampMillis, final long productTypeIdForTvChannel) {
        final Viewership result = new Viewership(timestampMillis, tvChannel, partner);
        final List<TvProgrammeState> states = new LinkedList<>(tvProgrammes.values()); // cache the list while we iterate
        for (final TvProgrammeState state : states) {
            final TvProgrammeProduct tvProgramme = state.tvProgramme;
            if ((tvProgramme.beginTimeMillis > timestampMillis) || (tvProgramme.endTimeMillis <= timestampMillis)) continue; // tv-show out-of-range
            result.tvProgramme = tvProgramme;
            state.currentViewership(result, tvChannel.id, owner.events, productTypeIdForTvChannel);
            break; // only one tv-programme possible at once
        }
        states.clear();
        final List<Map.Entry<String, DeviceZaps>> deviceZapses;
        synchronized (noEPGViewership) {
            if (noEPGViewership.isEmpty()) return result;
            deviceZapses = new LinkedList<>(noEPGViewership.entrySet());
        }
        final ConcurrentHashMap<String, DeviceState> events = owner.events;
        final long tvChannelId = tvChannel.id;
        for (final Map.Entry<String, DeviceZaps> deviceZaps : deviceZapses) {
            deviceZaps.getValue().currentViewership(result, tvChannelId, events.get(deviceZaps.getKey()), productTypeIdForTvChannel);
        }
        deviceZapses.clear();
        return result;
    }

    final void pruneNoEPGViewership(final Long timestampMillis) {
        final List<Map.Entry<String, DeviceZaps>> deviceZapses;
        synchronized (noEPGViewership) {
            deviceZapses = new LinkedList<>(noEPGViewership.entrySet());
        }
        for (final Map.Entry<String, DeviceZaps> deviceZaps : deviceZapses) deviceZaps.getValue().cutOffZaps(timestampMillis);
        synchronized (noEPGViewership) {
            for (final Map.Entry<String, DeviceZaps> deviceZaps : deviceZapses) {
                if (deviceZaps.getValue().isEmpty()) noEPGViewership.remove(deviceZaps.getKey());
            }
        }
        deviceZapses.clear();
    }
}
