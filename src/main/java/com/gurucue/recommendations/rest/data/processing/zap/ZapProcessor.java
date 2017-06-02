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
import com.gurucue.recommendations.Transaction;
import com.gurucue.recommendations.data.ConsumerEventTypeCodes;
import com.gurucue.recommendations.data.DataLink;
import com.gurucue.recommendations.data.DataManager;
import com.gurucue.recommendations.data.DataTypeCodes;
import com.gurucue.recommendations.data.ProductTypeCodes;
import com.gurucue.recommendations.entity.ConsumerEvent;
import com.gurucue.recommendations.entity.ConsumerEventType;
import com.gurucue.recommendations.entity.DataType;
import com.gurucue.recommendations.entity.Partner;
import com.gurucue.recommendations.entity.product.Product;
import com.gurucue.recommendations.entity.product.TvChannelProduct;
import com.gurucue.recommendations.entity.product.TvProgrammeProduct;
import com.gurucue.recommendations.entity.product.VideoProduct;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * The zap event processing threads. Maintains a single queue instance and
 * N threads that process the queued zap events.
 */
final class ZapProcessor implements Runnable, TimerListener {
    private static final Logger log = LogManager.getLogger(ZapProcessor.class);
    public static final long CONSUMPTION_FLUSH_INTERVAL = 60000L; // 1 minute
    public static final long ZAP_PURGE_INTERVAL = 1800000L; // 30 minutes, must be a multiple of CONSUMPTION_FLUSH_INTERVAL because there is only one timer
    public static final long VIEWERSHIP_INTERVAL = 300000L; // 5 minutes
    public static final int STAT_MINUTES_COUNT = 60; // for how many STAT_RESOLUTION_MILLIS in the past to do the statistics
    public static final int STAT_MINUTES_WINDOW = 10; // the window, in STAT_RESOLUTION_MILLIS
    public static final long STAT_RESOLUTION_MILLIS = 60000L;

    // queueing stuff
    private static final int QUEUE_LIMIT = 100000;
    private final Lock lock = new ReentrantLock();
    private final Condition elementAdded = lock.newCondition();
    private final Condition elementRemoved = lock.newCondition();
    private final LinkedList<ConsumerEvent> queue = new LinkedList<>();
    private int queueSize;
    private int submitted;
    private int consumed;
    boolean running = false;
    long lastViewershipCreation = 0L;

    // processing stuff
    private final ConsumerEventProcessor owner;
    /**
     * Maps tv-channel IDs to tv-channel state instances, for live-tv zap processing.
     */
    private final ConcurrentHashMap<Long, TvChannelState> tvChannelStates = new ConcurrentHashMap<>();
    /**
     * Maps device IDs to device states.
     */
    final ConcurrentHashMap<String, DeviceState> events = new ConcurrentHashMap<>(); // device-id/consumer-id -> deviceState
    private final long idForZap;
    private final long idForDeviceId;
    private final long idForStatus;
    private final long idForTvChannel;
    private final DataType deviceId;

    private long timerMillis;
    private final Thread[] threads = new Thread[10];

    // delay statistics stuff, use synchronized(this) to access
    int[][] delayStatistics = new int[STAT_MINUTES_WINDOW][STAT_MINUTES_COUNT];
    int currentWindowIndex;
    long nextWindowTimestamp;

    volatile long currentBufferingDelayMillis = 5L * 60L * 1000L; // initial buffering delay: 5 minutes
    volatile long currentStatDelayMillis = currentBufferingDelayMillis; // multiple of VIEWERSHIP_INTERVAL, exact or first next value from currentBufferingDelayMillis

    ZapProcessor(final ConsumerEventProcessor owner) {
        this.owner = owner;
        final DataTypeCodes dataTypeCodes = DataManager.getDataTypeCodes();
        idForDeviceId = dataTypeCodes.idForDeviceId;
        idForStatus = dataTypeCodes.idForStatus;
        deviceId = dataTypeCodes.deviceId;
        final ConsumerEventTypeCodes consumerEventTypeCodes = DataManager.getConsumerEventTypeCodes();
        idForZap = consumerEventTypeCodes.idForZap;
        final ProductTypeCodes productTypeCodes = DataManager.getProductTypeCodes();
        idForTvChannel = productTypeCodes.idForTvChannel;
    }

    /**
     * Creates and starts N processing threads.
     */
    final void start() {
        lock.lock();
        try {
            if (running) throw new IllegalStateException("Zap conversion thread is already running");
            queue.clear();
            queueSize = 0;
            submitted = 0;
            consumed = 0;
            running = true;

            // initialize the tv-channel mapping for live-tv (needed for computing viewership for all TV-channels from the start)
            try (final DataLink link = DataManager.getNewLink()) {
                try (final Transaction transaction = Transaction.newTransaction(link)) {
                    for (final Partner partner : link.getPartnerManager().list()) {
                        if (partner.getId() <= 0L) continue;
                        for (TvChannelProduct tvChannel : link.getProductManager().getTvChannelsForPartner(transaction, partner)) {
                            tvChannelStates.put(tvChannel.id, new TvChannelState(this, tvChannel, partner));
                        }
                    }
                }
            }

            final long now = Timer.currentTimeMillis();

            // initialize event delay statistics
            currentWindowIndex = 0;
            nextWindowTimestamp = ((now + STAT_RESOLUTION_MILLIS) / STAT_RESOLUTION_MILLIS) * STAT_RESOLUTION_MILLIS; // 1 minute resolution

            for (int i = threads.length - 1; i >= 0; i--) threads[i] = new Thread(this, "Consumption accounting #" + i);
            for (int i = threads.length - 1; i >= 0; i--) threads[i].start();

            timerMillis = now + CONSUMPTION_FLUSH_INTERVAL;
            lastViewershipCreation = (now / VIEWERSHIP_INTERVAL) * VIEWERSHIP_INTERVAL; // round down to an offset from a whole hour, which is a multiple of 5 minutes
            Timer.INSTANCE.schedule(timerMillis, this);
        }
        finally {
            lock.unlock();
        }
    }

    /**
     * Stops all processing threads.
     */
    final void stop() {
        lock.lock();
        try {
            Timer.INSTANCE.unschedule(timerMillis, this);
            if (!running) return;
            running = false;
            elementAdded.signalAll();
            elementRemoved.signalAll();
        }
        finally {
            lock.unlock();
        }

        for (int i = threads.length - 1; i >= 0; i--) {
            try {
                threads[i].join();
                threads[i] = null;
            } catch (InterruptedException e) {
                log.warn("Interrupted while waiting to join the zap conversion thread: " + e.toString(), e);
            } catch (RuntimeException e) {
                log.error("Error while stopping a thread: " + e.toString(), e);
            }
        }
    }

    /**
     * Queues for processing an event and the product referenced from the
     * event.
     *
     * @param event the event to queue
     */
    final void submit(final ConsumerEvent event){
        // basic sanity checks
        if (event.getEventTimestamp() == null) return;
        if (event.getRequestTimestamp() == null) return;
        statEventDelay(event.getRequestTimestamp().getTime(), event.getEventTimestamp().getTime()); // compute event delay stats
        if (event.getEventType().getId().longValue() != idForZap) return;
        if (event.getConsumer() == null) return;
        if (event.getPartner() == null) return;

        lock.lock();
        try {
            if (!running) throw new IllegalStateException("Cannot queue a zap: the conversion thread is not running");
            while (queueSize >= QUEUE_LIMIT) {
                try {
                    elementRemoved.await();
                }
                catch (InterruptedException e) {
                    log.warn("Interrupted while awaiting a removed-element signal in order to queue a zap");
                }
                if (!running) throw new IllegalStateException("Cannot queue a zap: the conversion thread has stopped while waiting to queue");
            }
            queue.add(event);
            queueSize++;
            submitted++;
            elementAdded.signal();
        }
        finally {
            lock.unlock();
        }
    }

    /**
     * The body of a processing thread. Dequeues and processes events in a
     * loop.
     */
    @Override
    public final void run() {
        final long threadId = Thread.currentThread().getId();
        final String logPrefix = "[" + threadId + "] ";
        log.info(logPrefix + "Zap conversion thread started");
        try {
            loop:
            for (; ; ) {
                final ConsumerEvent event;
                try {
                    // dequeue an entry
                    lock.lock();
                    try {
                        while (queueSize == 0) {
                            if (!running) break loop;
                            try {
                                elementAdded.await();
                            } catch (InterruptedException e) {
                                log.warn(logPrefix + "Interrupted while waiting for an element in queue: " + e.toString(), e);
                            }
                        }
                        event = queue.poll();
                        queueSize--;
                        consumed++;
                        elementRemoved.signal();
                    } finally {
                        lock.unlock();
                    }

                    final Product product = event.getProduct();

                    // first register the zap regardless of whether it happened on a tv-channel or not

                    // extract the zap key, this is a unique key describing an entity that generates events
                    String zapKey = null;
                    // if the event contains device-id, then this is the zap key, otherwise zap key is the consumer's username
                    Map<DataType, String> eventData = event.getData();
                    if (eventData == null) eventData = Collections.emptyMap();
                    zapKey = eventData.get(deviceId);
                    if (zapKey == null) {
                        zapKey = event.getConsumer().getUsername();
                    }

                    final long registerZapStart = System.nanoTime();
                    final DeviceState deviceState = registerZap(event, zapKey, product); // store to the zap registry
                    final long registerZapStop = System.nanoTime();
                    final long registerZapTime = registerZapStop - registerZapStart;
                    Product playingZapProduct = null; // by default: if a content was playing, then it should stop now

                    long tvChannelProcessingTime = 0L;

                    // decide on what processing to perform
                    if (product == null) {
                        // box status changes, and invalid events (having an unknown product)
                    }
                    else if (product instanceof TvChannelProduct) {
                        // live-tv zap
                        final TvChannelProduct tvChannel = (TvChannelProduct)product;

                        TvChannelState tvChannelState = tvChannelStates.get(tvChannel.id);
                        if (tvChannelState == null) {
                            tvChannelState = new TvChannelState(this, tvChannel, event.getPartner());
                            TvChannelState previousTvChannelState = tvChannelStates.putIfAbsent(tvChannel.id, tvChannelState);
                            if (previousTvChannelState != null) tvChannelState = previousTvChannelState;
                        }
                        final long tvChannelProcessingStart = System.nanoTime();
                        tvChannelState.process(event, zapKey, deviceState);
                        final long tvChannelProcessingStop = System.nanoTime();
                        tvChannelProcessingTime = tvChannelProcessingStop - tvChannelProcessingStart;
                    }
                    else if (product instanceof TvProgrammeProduct) {
                        // catch-up zap
                        playingZapProduct = product;
                    }
                    else if (product instanceof VideoProduct) {
                        // VOD zap
                        playingZapProduct = product;
                    }

                    final long deviceStateProcessingStart = System.nanoTime();
                    deviceState.updatePlayingZap(event, playingZapProduct);
                    final long deviceStateProcessingStop = System.nanoTime();
                    final long deviceStateProcessingTime = deviceStateProcessingStop - deviceStateProcessingStart;

                    if ((tvChannelProcessingTime + deviceStateProcessingTime + registerZapTime) > 5000000L) { // 5 milliseconds
                        log.warn(logPrefix + "Processing took too long: registering zap: " + registerZapTime + " ns, tv-channel processing: " + tvChannelProcessingTime + " ns, device state processing: " + deviceStateProcessingTime + " ns, product: " + (product == null ? "(null)" : product.id));
                    }

                } catch (Throwable e) {
                    log.error(logPrefix + "Failed to process an event: " + e.toString(), e);
                }
            }
        }
        finally {
            log.info(logPrefix + "Zap conversion thread exiting");
        }
    }

    /**
     * Queues the given zap into the internal zap list, for later consumption conversion.
     *
     * @param event the zap
     * @param zapKey the ID of the device on which the zap happened
     * @param product the TV-channel/TV-programme/video that was zapped to, or null in case this is a device status change
     * @return the DeviceState referenced by zapKey
     */
    private final DeviceState registerZap(final ConsumerEvent event, final String zapKey, final Product product) {
        final DeviceState existingDeviceState = events.get(zapKey);
        if (existingDeviceState == null) {
            final DeviceState newDeviceState = new DeviceState(event.getConsumer(), zapKey, this);
            final DeviceState previousDeviceState = events.putIfAbsent(zapKey, newDeviceState);
            if (previousDeviceState == null) {
                newDeviceState.zap(event, product);
                return newDeviceState;
            }
            previousDeviceState.zap(event, product);
            return previousDeviceState;
        }
        existingDeviceState.zap(event, product);
        return existingDeviceState;
    }

    void logStatistics() {
        final int size;
        final int c;
        final int s;
        lock.lock();
        try {
            size = queueSize;
            c = consumed;
            s = submitted;
            consumed = 0;
            submitted = 0;
        }
        finally {
            lock.unlock();
        }
        final StringBuilder logBuilder = new StringBuilder(80);
        logBuilder.append("Zap queue size: ");
        logBuilder.append(size);
        logBuilder.append(", submitted since last time: ");
        logBuilder.append(s);
        logBuilder.append(", processed: ");
        logBuilder.append(c);
        log.info(logBuilder.toString());
    }

    long lastPurgeTimestamp = 0L; // must be accessed inside synchronized()

    @Override
    public final void onTimerExpired(long expiryTime) {
        final boolean purgeZaps;
        final boolean generateViewership;
        final long targetViewershipMillis;
        final long startViewershipMillis;
        lock.lock();
        try {
            if (!running) return;
            // rearm the timer
            timerMillis = expiryTime + CONSUMPTION_FLUSH_INTERVAL;
            Timer.INSTANCE.schedule(timerMillis, this);
            purgeZaps = (expiryTime - ZAP_PURGE_INTERVAL) >= lastPurgeTimestamp;
            if (purgeZaps) lastPurgeTimestamp = expiryTime;
            final long nextViewershipMillis = ((expiryTime / VIEWERSHIP_INTERVAL) * VIEWERSHIP_INTERVAL) - currentStatDelayMillis;
            generateViewership = nextViewershipMillis > lastViewershipCreation;
            if (generateViewership) {
                startViewershipMillis = lastViewershipCreation + VIEWERSHIP_INTERVAL;
                targetViewershipMillis = lastViewershipCreation = nextViewershipMillis;
            }
            else {
                startViewershipMillis = 0L;
                targetViewershipMillis = 0L;
            }
        }
        finally {
            lock.unlock();
        }

        // flush consumptions per every DeviceState
        final StringBuilder flushLog = new StringBuilder(32768);
        flushLog.append("Performing a consumption flush cycle:");
        final int startLen = flushLog.length();
        for (final DeviceState state : events.values()) {
            state.sweep(flushLog);
        }
        if (startLen == flushLog.length()) {
            flushLog.append("  No consumptions to flush");
        }
        log.info(flushLog.toString());

        // generate viewership
        try {
            if (generateViewership) {
                final long startNano = System.nanoTime();
                final ConsumerEventType viewershipType = DataManager.getConsumerEventTypeCodes().viewership;
                final long productTypeIdForTvChannel = DataManager.getProductTypeCodes().idForTvChannel;
                final List<TvChannelState> tvChannelStates = new ArrayList<>(this.tvChannelStates.values());
                final List<Viewership> viewerships = new ArrayList<>(tvChannelStates.size());
                final StringBuilder logBuilder = new StringBuilder(16384);
                for (long millis = startViewershipMillis; millis <= targetViewershipMillis; millis += VIEWERSHIP_INTERVAL) {
                    int totalCount = 0;
                    for (final TvChannelState tvChannelState : tvChannelStates) {
                        final Viewership v = tvChannelState.viewership(targetViewershipMillis, productTypeIdForTvChannel);
                        viewerships.add(v);
                        totalCount += v.zaps.size();
                    }
                    final DataTypeCodes dataTypeCodes = DataManager.getDataTypeCodes();
                    if (millis > startViewershipMillis) logBuilder.append("\n");
                    logBuilder.append("Generated viewerships for ")
                            .append(viewerships.size()).append(" TV-channels and ")
                            .append(totalCount).append(" viewers at ")
                            .append(new Date(millis).toString());
                    for (final Viewership v : viewerships) {
                        DataManager.queueConsumerEvent(v.toEvent(viewershipType, dataTypeCodes));
                        v.log(logBuilder);
                    }
                    viewerships.clear();
                }
                tvChannelStates.clear();
                final long endNano = System.nanoTime();
                logBuilder.append("\nTiming: ").append(endNano - startNano).append(" ns");
                log.info(logBuilder.toString());
            }
        }
        catch (Exception e) {
            log.error("Error while generating viewership: " + e.toString(), e);
        }

        // adapt to the new zap delays
        final StringBuilder logBuilder = new StringBuilder(STAT_MINUTES_COUNT * 50);
        logBuilder.append("Current zap delivery delay distribution, rounded down to minutes of delay:");
        final long startDelayNano = System.nanoTime();
        final int[] delays = currentDelays(); // one counter per each interval of STAT_RESOLUTION_MILLIS width
        final long processDelayNano = System.nanoTime();
        final int delayIntervals = delays.length;
        final int[] cumulativeCounts = new int[delayIntervals];
        int totalCount = 0;
        for (int i = 0; i < delayIntervals; i++) {
            final int n = delays[i];
            totalCount += n;
            cumulativeCounts[i] = totalCount;
            logBuilder.append("\n  ").append(i).append(": ").append(n);
        }
        final int cutoffCount = (totalCount * 90) / 100; // find the first bracket (interval) in cumulativeCounts containing 90% of totalCount
        int i = 0;
        while (i < delayIntervals) {
            if (cumulativeCounts[i] >= cutoffCount) break;
            i++;
        }
        if (i < 3) i = 4; // minimum: 5 minutes of buffering
        else if (i > 28) i = 29; // maximum: 30 minutes of buffering
        else i++; // correct for the fact that i is zero-based
        i++; // add a minute, just in case
        final long newDelay = i * STAT_RESOLUTION_MILLIS; // set the new buffering delay
        if (newDelay != currentBufferingDelayMillis) {
            currentBufferingDelayMillis = newDelay;
            logBuilder.append("\nUsing the new buffering delay: ").append(i).append(" minutes");
            long newStatDelay = (newDelay / VIEWERSHIP_INTERVAL) * VIEWERSHIP_INTERVAL;
            if (newStatDelay < newDelay) newStatDelay += VIEWERSHIP_INTERVAL;
            if (newStatDelay != currentStatDelayMillis) {
                currentStatDelayMillis = newStatDelay;
                logBuilder.append(", viewership statistics delay changed to ");
            }
            else {
                logBuilder.append(", viewership statistics delay remains at ");
            }
            logBuilder.append(newStatDelay / VIEWERSHIP_INTERVAL)
                    .append(" integral interval(s) (")
                    .append(VIEWERSHIP_INTERVAL).append(" ns)");
        }
        else {
            logBuilder.append("\nThe buffering delay remains at ").append(i).append(" minutes");
        }
        final long startPurgeNano = System.nanoTime();
        logBuilder.append("\nTimings: retrieving cumulative statistics of the current window: ")
                .append(processDelayNano - startDelayNano)
                .append(" ns, post-processing and delay computation: ")
                .append(startPurgeNano - processDelayNano).append(" ns");
        log.debug(logBuilder.toString());

        // purge zaps per device, and any viewership zaps per tv-channel
        if (purgeZaps) {
            final Long limitMillis = expiryTime - 86400000L; // length of one day
            int count = 0;
            for (final Map.Entry<String, DeviceState> entry : events.entrySet()) {
                final DeviceState state = entry.getValue();
                if (state == null) continue;
                count += state.clearAncientZaps(limitMillis);
            }
            log.debug(count + " zaps removed with timestamps less than " + (limitMillis / 1000L)/* + ", " + idleCount + " idle device entries removed"*/);
            final Long cutoffMillis = expiryTime - ZAP_PURGE_INTERVAL;
            final List<TvChannelState> tvChannelStates = new LinkedList<>(this.tvChannelStates.values());
            for (final TvChannelState tvChannelState : tvChannelStates) {
                tvChannelState.pruneNoEPGViewership(cutoffMillis);
            }
            tvChannelStates.clear();
        }
    }

    // counters

    synchronized void statEventDelay(final long deliveryTimestamp, final long eventTimestamp) {
        final long nextWindowOffset = nextWindowTimestamp - deliveryTimestamp;
        final long windowIndexOffset = nextWindowOffset / STAT_RESOLUTION_MILLIS; // relative to currentWindowIndex
        final int[] statWindow;
        if (nextWindowOffset < 0L) {
            // move the window
            nextWindowTimestamp = nextWindowTimestamp + ((-windowIndexOffset+1) * STAT_RESOLUTION_MILLIS);
            final int nextWindowIndex = currentWindowIndex + (int)(-windowIndexOffset+1);
            for (int i = currentWindowIndex + 1; i <= nextWindowIndex; i++) {
                final int[] midStatWindow = delayStatistics[i % STAT_MINUTES_WINDOW];
                for (int j = midStatWindow.length - 1; j >= 0; j--) midStatWindow[j] = 0; // initialize the new window
            }
            currentWindowIndex = nextWindowIndex % STAT_MINUTES_WINDOW;
            statWindow = delayStatistics[currentWindowIndex];
        }
        else {
            final int statWindowIndex;
            // shouldn't account for this window (using the windowIndexOffset), it is way too old, but account for it anyway
            if (windowIndexOffset >= STAT_MINUTES_WINDOW) statWindowIndex = currentWindowIndex - (int)(STAT_MINUTES_WINDOW - 1);
            else statWindowIndex = currentWindowIndex - (int)windowIndexOffset;
            statWindow = delayStatistics[statWindowIndex < 0 ? STAT_MINUTES_WINDOW + statWindowIndex : statWindowIndex];
        }
        int delayMillis = (int)((deliveryTimestamp - eventTimestamp) / STAT_RESOLUTION_MILLIS);
        if (delayMillis < 0) delayMillis = 0;
        else if (delayMillis >= STAT_MINUTES_COUNT) delayMillis = STAT_MINUTES_COUNT - 1;
        statWindow[delayMillis]++;
    }

    synchronized int[] currentDelays() {
        final int[] result = new int[STAT_MINUTES_COUNT];
        for (int i = delayStatistics.length - 1; i >= 0; i--) {
            final int[] statWindow = delayStatistics[i];
            for (int j = STAT_MINUTES_COUNT - 1; j >= 0; j--) {
                result[j] += statWindow[j];
            }
        }
        return result;
    }
}
