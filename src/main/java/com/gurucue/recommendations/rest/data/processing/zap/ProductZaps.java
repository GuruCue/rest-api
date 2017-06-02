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

import com.google.common.collect.Range;
import com.google.common.collect.RangeSet;
import com.google.common.collect.TreeRangeSet;
import com.gurucue.recommendations.DatabaseException;
import com.gurucue.recommendations.Timer;
import com.gurucue.recommendations.data.DataManager;
import com.gurucue.recommendations.data.DataTypeCodes;
import com.gurucue.recommendations.data.ProductTypeCodes;
import com.gurucue.recommendations.entity.ConsumerEvent;
import com.gurucue.recommendations.entity.DataType;
import com.gurucue.recommendations.entity.ProductType;
import com.gurucue.recommendations.entity.product.Product;
import com.gurucue.recommendations.entity.product.TvProgrammeProduct;
import com.gurucue.recommendations.entity.product.VideoProduct;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * Collects zaps that belong to a content playout, either catch-up or
 * video-on-demand.
 */
final class ProductZaps /*implements TimerListener*/ { // TODO: remove old code
    /** How much time to wait from the last (consumption ending) zap, before composing the consumption event, in ms. */
    private final static long COMPUTATION_DELAY = 5L * 60L * 1000L; // 5 minutes
    /** Ultimate deadline, after which this instance is re-examined. */
    private final static Logger log = LogManager.getLogger(ProductZaps.class);

    private final DeviceState owner;
    final TreeMap<Long, EventData> zaps = new TreeMap<>();
    final Product product;
    final String title;

    public static final ConcurrentHashMap<String, Boolean> recommenderChannels = new ConcurrentHashMap<>(10); // partner-product-codes of tv-channels with recommendations
    public static final ConcurrentHashMap<String, Boolean> recommenderZapOrigins = new ConcurrentHashMap<>(10); // values of the origin field in zaps that signify recommendations origin
    static {
        recommenderChannels.put("PRIPOROČILA", Boolean.TRUE);
        recommenderChannels.put("s_PRIPOROČILA", Boolean.TRUE);
        recommenderZapOrigins.put("recommender", Boolean.TRUE);
        recommenderZapOrigins.put("recommender\n", Boolean.TRUE);
        recommenderZapOrigins.put("recommendations", Boolean.TRUE);
    }

    private boolean inactive = false; // a sanity flag: if it's true then it's an error to use the instance any further
    private final String logPrefixId;
    private final long contentDurationMillis;

    ProductZaps(final DeviceState owner, final Product product) {
        this.owner = owner;
        this.product = product;
        this.logPrefixId = "[" + owner.deviceId + "/" + product.id + "] ";
        if (product == null) throw new NullPointerException("A product zap refers to a null product");

        final StringBuilder logBuilder = new StringBuilder(256);
        logBuilder.append("Now collecting ");
        if (product instanceof TvProgrammeProduct) {
            final TvProgrammeProduct tvProgramme = (TvProgrammeProduct)product;
            title = ConsumerEventProcessor.tvProgrammeTitle(tvProgramme, log);
            logBuilder.append("catchup");
            contentDurationMillis = tvProgramme.endTimeMillis - tvProgramme.beginTimeMillis;
        }
        else if (product instanceof VideoProduct) {
            final VideoProduct video = (VideoProduct)product;
            title = video.title.asString();
            logBuilder.append("VoD");
            contentDurationMillis = video.runTime * 60000L; // run-time in database is in minutes, convert to millis
        }
        else {
            throw new DatabaseException("A product zap does not refer to a catch-up or video-on-demand product");
        }
        logBuilder.append(" zaps for device ")
                .append(owner.deviceId)
                .append(" of consumer ")
                .append(owner.consumer.getId().longValue())
                .append(" (")
                .append(owner.consumer.getUsername())
                .append(") playing product ")
                .append(product.id)
                .append(" (")
                .append(product.partnerProductCode)
                .append(", \"")
                .append(title)
                .append("\")");
        log.debug(logBuilder.toString());
    }

    void store(final Long timeMillis, final PlayingState state, final double speed, final long watchOffsetMillis, final ConsumerEvent event) {
        synchronized (this) {
            if (inactive) {
                log.error(new StringBuilder(256)
                        .append("Using an inactive ProductZaps instance to store an event! device=")
                        .append(owner.deviceId)
                        .append(", product=")
                        .append(product.id)
                        .append(" (")
                        .append(title)
                        .append("), time=")
                        .append(timeMillis.longValue())
                        .append(", state=")
                        .append(state)
                        .toString()
                );
            }
            zaps.put(timeMillis, new EventData(state, timeMillis, speed, watchOffsetMillis, event));
        }
    }

    // must be guarded with synchronized(this)
    private void convert(final StringBuilder logBuilder) {
        if (inactive) {
            log.error(new StringBuilder(256)
                            .append("Using an inactive ProductZaps instance to construct a consumption event! device=")
                            .append(owner.deviceId)
                            .append(", product=")
                            .append(product.id)
                            .append(" (")
                            .append(title)
                            .append(")")
                            .toString()
            );
        }
        final Iterator<Map.Entry<Long, EventData>> zapIterator = zaps.entrySet().iterator();
        if (!zapIterator.hasNext()) return; // no zaps to process
        final ConcurrentSkipListMap<Long, Product> deviceZapTimes = owner.events;

        final long cutoffTimestamp = Timer.currentTimeMillis() - COMPUTATION_DELAY;

        // find the first playing/winding zap
        Long previousTime = null;
        EventData previousData = null;

        // start with the first event
        Map.Entry<Long, EventData> entry = zapIterator.next();
        EventData data = entry.getValue();
        if (data.timeMillis > cutoffTimestamp) {
            // no event in range
            return;
        }
        previousTime = entry.getKey();
        previousData = data;
        ConsumptionGenerator generator = new ConsumptionGenerator(data);

        Long ceilingTimestamp = null; // the timestamp of the first interesting event - to clear any events before it

        // compute consumption properties
        while (zapIterator.hasNext()) {
            entry = zapIterator.next();
            data = entry.getValue();
            if (data.timeMillis > cutoffTimestamp) {
                generator = null; // consumption not yet finished: the end event not in range
                break;
            }
            final long eventTime = entry.getKey().longValue();
            final Long zapTime = deviceZapTimes.higherKey(previousTime);

            if ((zapTime != null) && (zapTime.longValue() < eventTime)) {
                // end of current consumption
                if (previousData.state == PlayingState.PLAY) {
                    // something was going on - stop it
                    generator.addEvent(new EventData(PlayingState.INTERNAL_STOP, zapTime, 1.0, 0L, null));
                }
                storeEvent(generator.constructConsumption(deviceZapTimes, product, title, logBuilder));
                ceilingTimestamp = entry.getKey();
                generator = new ConsumptionGenerator(data);
            } else {
                generator.addEvent(data);
            }

            previousData = data;
            previousTime = entry.getKey();
        }

        // close the last consumption after we ran out of zaps, if there's some event in the future that's incompatible
        if (generator != null) {
            // check if this is the last event and it either arrived more than contentDurationMillis+5minutes ago, or it is a STOP and it arrived 5 or more minutes ago
            if (!zapIterator.hasNext() && ((previousData.timeMillis <= (cutoffTimestamp - contentDurationMillis)) || ((previousData.state == PlayingState.STOP) && (previousData.timeMillis <= cutoffTimestamp)))) {
                final int historySize = zaps.size();
                zaps.clear(); // all zaps "spent", so clear all of them
                storeEvent(generator.constructConsumption(deviceZapTimes, product, title, logBuilder));
                logHistory(historySize, 0, 2);
                return;
            }

            // check if there's a zap in future, that doesn't belong to this consumption
            final Map.Entry<Long, Product> nextZapEntry = deviceZapTimes.higherEntry(previousTime);
            if (nextZapEntry != null) {
                final Product partnerProduct = nextZapEntry.getValue();
                if ((nextZapEntry.getKey().longValue() < cutoffTimestamp) && ((partnerProduct == null) || (partnerProduct.id < 0L) || (partnerProduct.id != product.id))) {
                    final int historySize = zaps.size();
                    zaps.clear(); // no zap is viable, so clear all of them
                    if (previousData.state == PlayingState.PLAY) {
                        // something was going on - stop it
                        generator.addEvent(new EventData(PlayingState.INTERNAL_STOP, nextZapEntry.getKey(), 1.0, 0L, null));
                    }
                    storeEvent(generator.constructConsumption(deviceZapTimes, product, title, logBuilder));
                    logHistory(historySize, 0, 3);
                    return;
                }
            }
        }

        if (ceilingTimestamp != null) {
            final SortedMap<Long, EventData> history = zaps.headMap(ceilingTimestamp);
            final int historySize = history.size();
            logHistory(historySize, zaps.size(), 4);
        }
    }

    private void logHistory(final int historyRemoved, final int historyRemained, final int codepath) {
        final StringBuilder logBuilder = new StringBuilder(256);
        logBuilder.append("(path #").append(codepath);
        if (historyRemoved == 0) {
            if (historyRemained == 0) logBuilder.append(") Empty history for device ");
            else logBuilder.append(") No history removed, ").append(historyRemained).append(" zaps remained for device ");
        }
        else if (historyRemained == 0) {
            logBuilder.append(") Cleared history (").append(historyRemoved).append(" zaps removed) for device ");
        }
        else {
            logBuilder.append(") Removed ").append(historyRemoved).append(" zaps, ").append(historyRemained).append(" zaps remaining in history for device ");
        }
        logBuilder.append(owner.deviceId)
                .append(" of consumer ")
                .append(owner.consumer.getId().longValue())
                .append(" (")
                .append(owner.consumer.getUsername())
                .append(") playing product ")
                .append(product.id)
                .append(" (")
                .append(product.partnerProductCode)
                .append(", \"")
                .append(title)
                .append("\")");
        if ((historyRemoved == 0) && (historyRemained > 0)) {
            log.warn(logBuilder.toString());
        }
        else {
            log.debug(logBuilder.toString());
        }
    }

    private void storeEvent(final ConsumerEvent consumption) {
        if (consumption != null) {
            try {
                DataManager.queueConsumerEvent(consumption);//DataManager.queueConsumerEvent(null, consumption);
            } catch (InterruptedException e) {
                log.error(logPrefixId + "Interrupted while waiting to queue a consumption event: " + e.toString(), e);
            }
        }
    }

    Long firstZapTime() {
        synchronized (this) {
            if (zaps.isEmpty()) {
                // this should never occur, but we account for it anyway
                return null;
            }
            return zaps.firstKey();
        }
    }

    public boolean flush(final StringBuilder logBuilder) {
        synchronized (this) {
            try {
                convert(logBuilder);
            }
            catch (Exception e) {
                log.error(logPrefixId + "Exception while assembling catchup/VoD consumption: " + e.toString(), e);
            }
            final boolean noZaps = zaps.isEmpty();
            if (noZaps) inactive = true;
            return noZaps;
        }
    }

    static final class EventData {
        final PlayingState state;
        final long timeMillis;
        final double speed;
        final long watchOffsetMillis;
        final ConsumerEvent event;

        EventData(final PlayingState state, final long timeMillis, final double speed, final long watchOffsetMillis, final ConsumerEvent event) {
            this.state = state;
            this.timeMillis = timeMillis;
            this.speed = speed;
            this.watchOffsetMillis = watchOffsetMillis;
            this.event = event;
        }
    }

    static class ConsumptionGenerator {
        final EventData firstEvent;
        final RangeSet<Long> consumedIntervals = TreeRangeSet.create();
        long played = 0L;
        final RangeSet<Long> rewoundIntervals = TreeRangeSet.create();
        long rewound = 0L;
        final RangeSet<Long> fastForwardIntervals = TreeRangeSet.create();
        long fastForwarded = 0L;
        int playCount = 0;
        int fastForwardCount = 0;
        int rewindCount = 0;
        int stopCount = 0;
        int pauseCount = 0;
        EventData previousData;
        final StringBuilder eventIdLog = new StringBuilder(256);

        ConsumptionGenerator(final EventData data) {
            firstEvent = previousData = data;
            countEvent(data);
        }

        private void countEvent(final EventData data) {
            if (data.state == PlayingState.PLAY) {
                if (data.speed == 1) playCount++;
                else if (data.speed < 0) rewindCount++;
                else if (data.speed > 0) fastForwardCount++;
            }
            else if (data.state == PlayingState.PAUSE) pauseCount++;
            else if (data.state == PlayingState.STOP) stopCount++;
            if (data.event == null) eventIdLog.append(", (dummy STOP)");
            else if (data.event.getId() == null) eventIdLog.append(", (null ID)");
            else if (data.state == PlayingState.INTERNAL_STOP) eventIdLog.append(", ").append(data.event.getId().longValue()).append(" (handled as STOP)");
            else eventIdLog.append(", ").append(data.event.getId().longValue());
        }

        void addEvent(final EventData data) {
            countEvent(data);

            if (previousData.state == PlayingState.PLAY) {
                // update timings
                final long timeDelta = data.timeMillis - previousData.timeMillis;
                final long offset = previousData.watchOffsetMillis;
                if (previousData.speed == 1) {
                    // play
                    consumedIntervals.add(Range.closed(offset, offset + timeDelta));
                    played += timeDelta;
                }
                else if (previousData.speed < 0) {
                    // rewind
                    rewoundIntervals.add(Range.closed(offset + (long)(timeDelta * previousData.speed), offset));
                    rewound += timeDelta;
                }
                else {
                    // fast forward (in theory also possible: slow motion)
                    fastForwardIntervals.add(Range.closed(offset, offset + (long)(timeDelta * previousData.speed)));
                    fastForwarded += timeDelta;
                }
            }

            previousData = data; // remember for the next time
        }

        ConsumerEvent constructConsumption(final ConcurrentSkipListMap<Long, Product> deviceZapTimes, final Product product, final String title, final StringBuilder logBuilder) {

            final DataTypeCodes typeCodes = DataManager.getDataTypeCodes();
            final ConsumerEvent template = firstEvent.event;
            final Map<DataType, String> eventData = new HashMap<>();
            final ConsumerEvent consumption = new ConsumerEvent(null, new Timestamp(previousData.timeMillis), template.getPartner(), template.getProduct(), template.getConsumer(), DataManager.getConsumerEventTypeCodes().consumption, eventData, template.getUserProfileId());
            consumption.setRequestTimestamp(new Timestamp(System.currentTimeMillis()));

            if (product instanceof TvProgrammeProduct) {
                logBuilder.append("\nCatch-up consumption of tv-programme ");
            }
            else if (product instanceof VideoProduct) {
                logBuilder.append("\nVOD consumption of video ");
            }
            else {
                logBuilder.append("\nConsumption of unknown type of product with ID ");
            }
            logBuilder.append(product.id)
                    .append(" (partner ")
                    .append(template.getPartner().getId().longValue())
                    .append(", product code ")
                    .append(product.partnerProductCode)
                    .append(", \"")
                    .append(title)
                    .append("\") at ")
                    .append(previousData.timeMillis)
                    .append(", consumer ")
                    .append(template.getConsumer().getId().longValue())
                    .append(" (username: ")
                    .append(template.getConsumer().getUsername())
                    .append(")");

            // copy device data
            final Map<DataType, String> templateData = template.getData();
            if (templateData != null) {
                final String deviceId = templateData.get(typeCodes.deviceId);
                final String deviceType = templateData.get(typeCodes.deviceType);
                final String tvChannelId = templateData.get(typeCodes.tvChannelId);
                if (deviceId != null) {
                    if (deviceType == null) logBuilder.append(", device ").append(deviceId).append(" of unknown type");
                    else logBuilder.append(", ").append(deviceType).append(" ").append(deviceId);
                    eventData.put(typeCodes.deviceId, deviceId);
                }
                if (deviceType != null) {
                    eventData.put(typeCodes.deviceType, deviceType);
                }
                if (tvChannelId != null) {
                    eventData.put(typeCodes.tvChannelId, tvChannelId);
                }
            }

            // set origin
            Map.Entry<Long, Product> originEntry = deviceZapTimes.lowerEntry(firstEvent.timeMillis);
            final long productId = product.id;
            final ProductTypeCodes productTypeCodes = DataManager.getProductTypeCodes();
            String dataOrigin = null;

            while (originEntry != null) {
                final Product p = originEntry.getValue();
                final String originWait = Long.toString((firstEvent.timeMillis - originEntry.getKey()) / 1000L);
                if ((p == null) || (p == DeviceState.NULL_PRODUCT)) {
                    dataOrigin = "other";
                    eventData.put(typeCodes.origin, dataOrigin);
                    eventData.put(typeCodes.originWait, originWait); // in seconds
                    logBuilder.append(", origin other, origin ID unknown, origin wait ").append(originWait).append(" s");
                    break;
                }
                else if ((p.id < 0L) || (p.id != productId)) {
                    // p.id < 0L means an invalid product, the corresponding event specified a product type and code of a product that does not exist
                    final long productTypeId = p.productTypeId;
                    final ProductType productType = productTypeCodes.byId(p.productTypeId);
                    final String partnerProductCode = p.partnerProductCode;
                    dataOrigin = (productTypeId != productTypeCodes.idForTvProgramme) && (productTypeId != productTypeCodes.idForVideo) && recommenderChannels.containsKey(partnerProductCode) ? "recommendations" : "other";
                    final String originCode; // = (productType == null ? "(null)" : productType.getIdentifier()) + " " + (partnerProductCode == null ? "(null)" : partnerProductCode);
                    if (productType == null) {
                        if (partnerProductCode == null) originCode = null;
                        else originCode = "(null) " + partnerProductCode;
                    }
                    else if (partnerProductCode == null) originCode = productType.getIdentifier() + " (null)";
                    else originCode = productType.getIdentifier() + " " + partnerProductCode;
                    final String originId = p.id < 0L ? null : Long.toString(p.id, 10);

                    eventData.put(typeCodes.origin, dataOrigin);
                    logBuilder.append(", origin ").append(dataOrigin);
                    if (originId != null) {
                        eventData.put(typeCodes.originId, originId);
                        logBuilder.append(", origin ID ").append(originId);
                    }
                    else logBuilder.append(", origin ID missing");
                    if (originCode != null) {
                        eventData.put(typeCodes.originCode, originCode);
                        logBuilder.append(" (").append(originCode).append(")");
                    }
                    eventData.put(typeCodes.originWait, originWait); // in seconds
                    logBuilder.append(", origin wait ").append(originWait).append(" s");
                    break;
                }

                originEntry = deviceZapTimes.lowerEntry(originEntry.getKey());
            }

            // extract origin reported by the first event
            final String firstEventOrigin = (firstEvent.event == null) || (firstEvent.event.getData() == null) ? null : firstEvent.event.getData().get(typeCodes.origin);
            final boolean firstEventIsFromRecommender = (firstEventOrigin != null) && recommenderZapOrigins.containsKey(firstEventOrigin);
            if (firstEventOrigin != null) {
                logBuilder.append(", first event supplied origin \"").append(firstEventOrigin.replace("\n", "\\n")).append("\"");
            }

            if (dataOrigin == null) {
                // copy over the origin from the first zap
                if (firstEventOrigin != null) {
                    final String origin = firstEventIsFromRecommender ? "recommendations" : "other";
                    eventData.put(typeCodes.origin, origin);
                    logBuilder.append(", using origin from the first event: ").append(origin);
                }
            }
            else if ("other".equals(dataOrigin)) {
                if (firstEventIsFromRecommender) {
                    eventData.put(typeCodes.origin, "recommendations");
                    logBuilder.append(", overriding origin from the first zap: recommendations");
                }
            }

            // set content duration
            long contentDuration = 0L; // seconds
            if (product instanceof TvProgrammeProduct) {
                final TvProgrammeProduct tvProgramme = (TvProgrammeProduct)product;
                contentDuration = (tvProgramme.endTimeMillis - tvProgramme.beginTimeMillis) / 1000L;
            }
            else if (product instanceof VideoProduct) {
                final VideoProduct video = (VideoProduct)product;
                contentDuration = video.runTime;
            }
            if (contentDuration > 0L) {
                eventData.put(typeCodes.contentDuration, Long.toString(contentDuration, 10));
                logBuilder.append(", content duration: ").append(contentDuration).append(" s");
            }

            // set timings
            logBuilder.append("\n  play duration: ").append(played / 1000L).append(" s, intervals: ");
            final long sumPlayed = intervalSum(consumedIntervals, logBuilder);
            logBuilder.append(", interval sum: ").append(sumPlayed).append(" s");
            if (sumPlayed > 0L) {
                eventData.put(typeCodes.contentWatched, Long.toString(sumPlayed, 10));
                eventData.put(typeCodes.watchPercentage, sumPlayed > contentDuration ? "100" : Long.toString(sumPlayed * 100L / contentDuration, 10));
            }
            if (played >= 1000L) eventData.put(typeCodes.watchDuration, Long.toString(played / 1000L, 10));

            logBuilder.append("\n  fast-forward duration: ").append(fastForwarded / 1000L).append(" s, intervals: ");
            final long sumFastForwarded = intervalSum(fastForwardIntervals, logBuilder);
            final String sumStringFastForwarded = Long.toString(sumFastForwarded, 10);
            logBuilder.append(", interval sum: ").append(sumStringFastForwarded).append(" s");
            if (sumFastForwarded > 0L) eventData.put(typeCodes.contentFastForwarded, sumStringFastForwarded);
            if (fastForwarded >= 1000L) eventData.put(typeCodes.fastForwardDuration, Long.toString(fastForwarded / 1000L, 10));

            logBuilder.append("\n  rewind duration: ").append(rewound / 1000L).append(" s, intervals: ");
            final long sumRewound = intervalSum(rewoundIntervals, logBuilder);
            final String sumStringRewound = Long.toString(sumRewound, 10);
            logBuilder.append(", interval sum: ").append(sumStringRewound).append(" s");
            if (sumRewound > 0L) eventData.put(typeCodes.contentRewound, sumStringRewound);
            if (rewound >= 1000L) eventData.put(typeCodes.rewindDuration, Long.toString(rewound / 1000L, 10));

            // counters
            logBuilder.append("\n  play count: ").append(playCount);
            if (playCount > 0) eventData.put(typeCodes.playCount, Integer.toString(playCount, 10));
            logBuilder.append("\n  fast-forward count: ").append(fastForwardCount);
            if (fastForwardCount > 0) eventData.put(typeCodes.fastForwardCount, Integer.toString(fastForwardCount, 10));
            logBuilder.append("\n  rewind count: ").append(rewindCount);
            if (rewindCount > 0) eventData.put(typeCodes.rewindCount, Integer.toString(rewindCount, 10));
            logBuilder.append("\n  pause count: ").append(pauseCount);
            if (pauseCount > 0) eventData.put(typeCodes.pauseCount, Integer.toString(pauseCount, 10));
            logBuilder.append("\n  stop count: ").append(stopCount);
            if (stopCount > 0) eventData.put(typeCodes.stopCount, Integer.toString(stopCount, 10));

            // alternative consumption -- delta time
            final long consumptionDuration = (previousData.timeMillis - firstEvent.timeMillis) / 1000L;
            final long consumptionPercentage = consumptionDuration > contentDuration ? 100L : (consumptionDuration * 100L) / contentDuration;
            logBuilder.append("\n  consumption duration: ").append(consumptionDuration).append("\n  consumption percentage: ").append(consumptionPercentage);
            if (consumptionDuration > 0L) eventData.put(typeCodes.consumptionDuration, Long.toString(consumptionDuration, 10));
            if (consumptionPercentage > 0L)
                eventData.put(typeCodes.consumptionPercentage, Long.toString(consumptionPercentage, 10));

            // referenced zaps
            logBuilder.append("\n  zap IDs: ");
            if (eventIdLog.length() > 2) logBuilder.append(eventIdLog, 2, eventIdLog.length());
            else logBuilder.append("none");

            return consumption;
        }

        /**
         * Computes the sum of intervals in the given set of intervals
         * (provided as a RangeSet), and returns it, converted into
         * seconds.
         *
         * @param intervals
         * @param logBuilder
         * @return
         */
        private static long intervalSum(final RangeSet<Long> intervals, final StringBuilder logBuilder) {
            final Iterator<Range<Long>> intervalIterator = intervals.asRanges().iterator();
            if (!intervalIterator.hasNext()) {
                logBuilder.append("none");
                return 0L;
            }
            final Range<Long> firstInterval = intervalIterator.next();
            final long firstLower = firstInterval.lowerEndpoint() / 1000L;
            final long firstUpper = firstInterval.upperEndpoint() / 1000L;
            long intervalSum = firstUpper - firstLower;
            logBuilder.append("[").append(firstLower).append(", ").append(firstUpper).append("]");
            while (intervalIterator.hasNext()) {
                final Range<Long> interval = intervalIterator.next();
                final long lower = interval.lowerEndpoint() / 1000L;
                final long upper = interval.upperEndpoint() / 1000L;
                intervalSum += upper - lower;
                logBuilder.append(", [").append(lower).append(", ").append(upper).append("]");
            }
            return intervalSum;
        }
    }
}
