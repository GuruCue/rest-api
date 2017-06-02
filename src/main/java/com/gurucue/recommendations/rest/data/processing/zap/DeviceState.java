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
import com.gurucue.recommendations.data.DataManager;
import com.gurucue.recommendations.data.DataTypeCodes;
import com.gurucue.recommendations.data.ProductTypeCodes;
import com.gurucue.recommendations.entity.Consumer;
import com.gurucue.recommendations.entity.ConsumerEvent;
import com.gurucue.recommendations.entity.DataType;
import com.gurucue.recommendations.entity.ProductType;
import com.gurucue.recommendations.entity.product.Product;
import com.gurucue.recommendations.entity.product.TvProgrammeProduct;
import com.gurucue.recommendations.entity.value.AttributeValues;
import gnu.trove.function.TObjectFunction;
import gnu.trove.iterator.TLongObjectIterator;
import gnu.trove.map.TLongObjectMap;
import gnu.trove.procedure.TLongObjectProcedure;
import gnu.trove.procedure.TLongProcedure;
import gnu.trove.procedure.TObjectProcedure;
import gnu.trove.set.TLongSet;
import gnu.trove.set.hash.TLongHashSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * Contains information on current device activity, so any old left-over zaps
 * can be removed as early as possible. Any left-over zaps are considered as
 * a result of a processing bug, and this is a measure to clean up after any
 * such bug.
 */
final class DeviceState/* implements TimerListener*/ {
    private static final Logger log = LogManager.getLogger(DeviceState.class);

    static final Product NULL_PRODUCT = new Product(0L, 0L, -1L, null, null, null, null, AttributeValues.NO_VALUES, AttributeValues.NO_VALUES);
    /**
     * Contains a list of visited tv-programmes by this device, that haven't
     * ended yet. This is to correctly determine "ancient" zaps, that is
     * zaps that are too old to be relevant for anything.
     */
    private final Map<Long, TvProgrammeProduct> watchedTvProgrammes = new HashMap<>(); // product-id -> PartnerTvProgramme instance, access must be guarded with synchronized(this)

    /**
     * Maps zap timestamps to products. It uses
     * an ordered mapping, so it is possible to obtain a time-sorted list of
     * zaps for each box. Needed to compute the origin of a consumption.
     */
    final ConcurrentSkipListMap<Long, Product> events = new ConcurrentSkipListMap<>(); // timeMillis -> tv-channel / tv-programme / video

    /**
     * Maps product IDs to product zaps instances.
     * This is to correctly determine "ancient" zaps, and to manage
     * delayed bookkeeping.
     */
    private final Set<ProductZaps> parkedToExpire = new HashSet<>(); // access must be guarded with synchronized(this)

    /**
     * Contains the currently playing catch-up or video. If the user is not
     * watching/playing either (e.g. he is watching live-tv), then this is
     * null.
     */
    private ProductZaps currentlyPlaying = null; // access must be guarded with synchronized(this)

    /**
     * The consumer owning this device.
     */
    final Consumer consumer;

    /**
     * The ID of this device.
     */
    final String deviceId;

    private final ZapProcessor owner;

    DeviceState(final Consumer consumer, final String deviceId, final ZapProcessor owner) {
        this.consumer = consumer;
        this.deviceId = deviceId;
        this.owner = owner;
    }

    /**
     * Register device activity for redundant zap accounting.
     *
     * @param tvProgramme
     */
    void addTvProgramme(final TvProgrammeProduct tvProgramme) {
        synchronized (this) {
            watchedTvProgrammes.put(tvProgramme.id, tvProgramme);
        }
    }

    /**
     * Signal a tv-programme eviction, so the redundant zap accounting logic
     * removes any left-over zaps.
     *
     * @param tvProgramme
     */
    void evictTvProgramme(final TvProgrammeProduct tvProgramme) {
        final TvProgrammeProduct[] currentlyWatchedTvProgrammes;
        final ProductZaps[] currentlyPlayedContent;
        synchronized (this) {
            watchedTvProgrammes.remove(tvProgramme.id);

            // take a snapshot of watched tv-programmes and played content, so they can be processed without holding the lock, to avoid deadlocks
            final Collection<TvProgrammeProduct> watchedTvProgrammesCollection = watchedTvProgrammes.values();
            currentlyWatchedTvProgrammes = watchedTvProgrammesCollection.toArray(new TvProgrammeProduct[watchedTvProgrammesCollection.size()]);
            currentlyPlayedContent = parkedToExpire.toArray(new ProductZaps[parkedToExpire.size()]);
        }
        final Long lastEventTime = events.lowerKey(lowestOccupiedZap(tvProgramme.endTimeMillis, currentlyWatchedTvProgrammes, currentlyPlayedContent)); // leave the last event, so during next consumption assembly the origin can be determined
        if (lastEventTime != null) {
            events.headMap(lastEventTime).clear(); // clear past event times; this will also clear a long zap, but that doesn't matter for our calculations
        }
    }

    private long lowestOccupiedZap(long ceiling, final TvProgrammeProduct[] currentlyWatchedTvProgrammes, final ProductZaps[] currentlyPlayedContent) {
        for (int i = currentlyWatchedTvProgrammes.length - 1; i >= 0; i--) {
            final TvProgrammeProduct leftoverTvProgramme = currentlyWatchedTvProgrammes[i];
            if (leftoverTvProgramme.beginTimeMillis < ceiling)
                ceiling = leftoverTvProgramme.beginTimeMillis; // a tv-programme still exists that began before the evicted tv-programme ended
        }
        for (int i = currentlyPlayedContent.length - 1; i >= 0; i--) {
            final ProductZaps playZaps = currentlyPlayedContent[i];
            final Long lowestTime = playZaps.firstZapTime();
            if ((lowestTime != null) && (lowestTime.longValue() < ceiling)) ceiling = lowestTime.longValue();
        }
        return ceiling;
    }

    /**
     * Tells whether the instance is not being used, so it can be removed
     * from bookkeeping.
     *
     * @return whether the device is idle
     */
    boolean isIdle() {
        if (!events.isEmpty()) return false;
        synchronized (this) {
            return parkedToExpire.isEmpty();
        }
    }

    /**
     * Registers a zap and the referenced product. The list of events is
     * needed to correctly determine live-tv consumptions.
     *
     * @param event
     * @param product
     */
    void zap(final ConsumerEvent event, final Product product) {
        final Long zapTimeMillis = event.getEventTimestamp().getTime();
        if (product == null) {
            // if the product is null, it still may be reconstructible from the event data _err-product-type and _err-product-id
            // under the assumption that the product information was submitted with the event request (i.e. the product
            // data was not missing, only the product was not found in the database)
            Map<DataType, String> data = event.getData();
            if (data == null) data = Collections.emptyMap();
            final DataTypeCodes codes = DataManager.getDataTypeCodes();
            String productCode = data.get(codes._errProductId);
            ProductType productType = null;
            if (productCode != null) {
                final String productTypeIdentifier = data.get(codes._errProductType);
                if (productTypeIdentifier != null) {
                    productType = DataManager.getProductTypeCodes().byIdentifier(productTypeIdentifier);
                    if (productType == null) productType = new ProductType(null, productTypeIdentifier);
                }
            }
            if (productType != null) {
                events.put(zapTimeMillis, Product.create(0L, productType.getId() == null ? 0L : productType.getId(), event.getPartner().getId(), productCode, null, null, null, AttributeValues.NO_VALUES, AttributeValues.NO_VALUES, DataManager.getProvider()));
            }
            else {
                events.put(zapTimeMillis, NULL_PRODUCT);
            }
        }
        else {
            events.put(zapTimeMillis, product);
        }
    }

    int clearAncientZaps(final Long limitMillis) {
        final NavigableMap<Long, Product> submap = events.headMap(limitMillis);
        final int count = submap.size();
        submap.clear();
        return count;
    }

    private static final int INITIAL_LOG_CAPACITY = 128;

    /**
     * Takes care of playing (RTSP) events accounting. Takes an event and a
     * product. Every event must be reported, but a product must be null
     * if the event is not a playing event, and non-null otherwise. It is
     * assumed that if the product is not null, then the event in question
     * is a playing event with the product as a catch-up or VOD content
     * being played. If the product is null, then the method makes sure
     * that any outstanding playout for the box/consumer is stopped.
     *
     * @param event the event that occured
     * @param product in case of a playing event the product from the event, null otherwise
     */
    void updatePlayingZap(final ConsumerEvent event, final Product product) {
        final long now = Timer.currentTimeMillis();
        if (event.getEventTimestamp().getTime() < (now - owner.currentBufferingDelayMillis)) {
            final StringBuilder errBuilder = new StringBuilder(256);
            errBuilder.append("Ignoring catchup/VOD zap at time ")
                    .append(event.getEventTimestamp().getTime() / 1000L)
                    .append(": it is more than 5 minutes in the past, for consumer ")
                    .append(event.getConsumer().getId().longValue())
                    .append(" (username: ").append(event.getConsumer().getUsername()).append(") ");
            if (product == null) errBuilder.append("and no product");
            else {
                final ProductType productType = DataManager.getProductTypeCodes().byId(product.productTypeId);
                errBuilder.append("and product ")
                        .append(productType == null ? "(null product type)" : productType.getIdentifier())
                        .append(" ")
                        .append(product.id)
                        .append(" (partner code: ").append(product.partnerProductCode)
                        .append(")");
            }
            log.warn(errBuilder.toString());
            return;
        }

        PlayingState state = null;
        double speed = 1;
        boolean speedSet = false; // does event data contain "speed"?
        long watchOffsetMillis = 0;
        boolean watchOffsetSet = false; // does event data contain "watch-offset"?
        StringBuilder errors = null;
        final Long newProductId;
        if (product == null) {
            newProductId = null;
            state = PlayingState.INTERNAL_STOP;
        }
        else {
            // extract the playing state (one of: play, pause, stop), playing speed, watch offset
            newProductId = product.id;
            final DataTypeCodes codes = DataManager.getDataTypeCodes();
            final Map<DataType, String> data = event.getData();
            final String actionString = data.get(codes.action);
            if (actionString != null) {
                state = PlayingState.fromString(actionString);
                if (state == null) {
                    state = PlayingState.STOP;
                    if (errors == null) errors = new StringBuilder(INITIAL_LOG_CAPACITY);
                    errors.append("\n  unknown action: ").append(actionString);
                }
            }
            final String speedString = data.get(codes.speed);
            if (speedString != null) {
                try {
                    speed = Double.parseDouble(speedString);
                    speedSet = true;
                }
                catch (NumberFormatException e) {
                    speedSet = false;
                    if (errors == null) errors = new StringBuilder(INITIAL_LOG_CAPACITY);
                    errors.append("\n  speed is not a number: ").append(speedString);
                }
            }
            final String watchOffsetString = data.get(codes.watchOffset);
            if (watchOffsetString != null) {
                try {
                    watchOffsetMillis = Long.parseLong(watchOffsetString, 10) * 1000L;
                    watchOffsetSet = true;
                }
                catch (NumberFormatException e) {
                    watchOffsetSet = false;
                    if (errors == null) errors = new StringBuilder(INITIAL_LOG_CAPACITY);
                    errors.append("\n  watch offset is not an integer: ").append(watchOffsetString);
                }
            }
            if (state == null) {
                // no playing state defined, choose one instead
                if (errors == null) errors = new StringBuilder(INITIAL_LOG_CAPACITY);
                if (speedSet || watchOffsetSet) {
                    errors.append("\n  no state was provided, but ");
                    if (speedSet) errors.append("speed");
                    else errors.append("watch-offset");
                    errors.append(" was set, therefore assuming play");
                    state = PlayingState.PLAY;
                }
                else {
                    errors.append("\n  no state was provided, assuming stop");
                    state = PlayingState.STOP;
                }
            }
        }

        final ProductZaps zaps;
        synchronized (this) {
            if (newProductId == null) {
                zaps = null;
                // the event is not a content playing event
                if (currentlyPlaying != null) {
                    // cancel active playing
                    currentlyPlaying = null;
                }
            }
            // this is a content playing event, store it accordingly
            else if (currentlyPlaying == null) {
                zaps = new ProductZaps(this, product);
                currentlyPlaying = zaps;
                parkedToExpire.add(zaps);
            }
            else if (currentlyPlaying.product.id == newProductId.longValue()) {
                zaps = currentlyPlaying;
            }
            else {
                zaps = new ProductZaps(this, product);
                currentlyPlaying = zaps;
                parkedToExpire.add(zaps);
            }

            if (zaps != null) {
                zaps.store(event.getEventTimestamp().getTime(), state, speed, watchOffsetMillis, event);
            }
        }

        if (errors != null) {
            final StringBuilder logEntry = new StringBuilder(errors.length() + 128);
            logEntry.append("Errors encountered while processing zap event at time ")
                    .append(event.getEventTimestamp().getTime() / 1000L)
                    .append(" for consumer ").append(event.getConsumer().getId().longValue())
                    .append(" (username: ").append(event.getConsumer().getUsername()).append(") ");
            if (product == null) logEntry.append("and no product");
            else {
                final ProductType productType = DataManager.getProductTypeCodes().byId(product.productTypeId);
                logEntry.append("and product ")
                        .append(productType == null ? "(null product type)" : productType.getIdentifier())
                        .append(" ")
                        .append(product.id)
                        .append(" (partner code: ").append(product.partnerProductCode)
                        .append(")");
            }
            logEntry.append(":").append(errors);
            log.error(logEntry.toString());
        }
    }

    /**
     * Cleans up the instance: computes consumptions that are ready for
     * "reaping", removes zaps that are not needed anymore. Must be invoked
     * in regular intervals.
     */
    public void sweep(final StringBuilder logBuilder) {
        final ProductZaps[] productZapses;
        final int n;
        synchronized (this) {
            n = parkedToExpire.size();
            productZapses = n > 0 ? parkedToExpire.toArray(new ProductZaps[n]) : null; // optimization: don't allocate if there's no need for it
            if (productZapses != null) {
                boolean parkedToExpireChanged = false;
                for (int i = n - 1; i >= 0; i--) {
                    final ProductZaps productZaps = productZapses[i];
                    if (productZaps.flush(logBuilder)) {
                        parkedToExpire.remove(productZaps);
                        parkedToExpireChanged = true;
                        logBuilder.append("\nEvicted consumption collector of ")
                                .append(productZaps.product.id)
                                .append(" (")
                                .append(productZaps.product.partnerProductCode)
                                .append(", \"")
                                .append(productZaps.title).append("\") for device ")
                                .append(deviceId)
                                .append(" of consumer ")
                                .append(consumer.getId().longValue())
                                .append(" (")
                                .append(consumer.getUsername())
                                .append(")");
                        if (currentlyPlaying == productZaps) {
                            currentlyPlaying = null;
                            logBuilder.append(", also removed as currently playing");
                        }
                    }
                }

                if (parkedToExpireChanged) {
                    final long time;
                    final TvProgrammeProduct[] currentlyWatchedTvProgrammes;
                    final ProductZaps[] currentlyPlayedContent;
                    time = Timer.currentTimeMillis();

                    // take a snapshot of watched tv-programmes and played content, so they can be processed without holding the lock, to avoid deadlocks
                    final Collection<TvProgrammeProduct> watchedTvProgrammesCollection = watchedTvProgrammes.values();
                    currentlyWatchedTvProgrammes = watchedTvProgrammesCollection.toArray(new TvProgrammeProduct[watchedTvProgrammesCollection.size()]);
                    currentlyPlayedContent = parkedToExpire.toArray(new ProductZaps[parkedToExpire.size()]);

                    final Long lastEventTime = events.lowerKey(lowestOccupiedZap(time, currentlyWatchedTvProgrammes, currentlyPlayedContent)); // leave the last event, so during next consumption assembly the origin can be determined
                    if (lastEventTime != null) {
                        events.headMap(lastEventTime).clear(); // clear past event times; this will also clear a long zap, but that doesn't matter for our calculations
                    }
                }
            }
        }
    }

    void setOrigin(final StringBuilder logBuilder, final DataTypeCodes typeCodes, final ConsumerEvent firstEvent, final long firstEventTimeMillis, final long productId, final Map<DataType, String> eventData) {
        Map.Entry<Long, Product> originEntry = events.lowerEntry(firstEventTimeMillis);
        final ProductTypeCodes productTypeCodes = DataManager.getProductTypeCodes();
        String dataOrigin = null;

        while (originEntry != null) {
            final Product p = originEntry.getValue();
            final String originWait = Long.toString((firstEventTimeMillis - originEntry.getKey()) / 1000L);
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
                dataOrigin = (productTypeId != productTypeCodes.idForTvProgramme) && (productTypeId != productTypeCodes.idForVideo) && ProductZaps.recommenderChannels.containsKey(partnerProductCode) ? "recommendations" : "other";
                final String originCode; // = (productType == null ? "(null)" : productType.getIdentifier()) + " " + (partnerProductCode == null ? "(null)" : partnerProductCode);
                if (productType == null) {
                    if (partnerProductCode == null) originCode = null;
                    else originCode = "(null product type) " + partnerProductCode;
                }
                else if (partnerProductCode == null) originCode = productType.getIdentifier() + " (null partner product code)";
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
                    logBuilder.append(" (").append(originCode).append(") ");
                }
                eventData.put(typeCodes.originWait, originWait); // in seconds
                logBuilder.append(", origin wait ").append(originWait).append(" s");
                break;
            }

            originEntry = events.lowerEntry(originEntry.getKey());
        }

        // extract origin reported by the first event
        final String firstEventOrigin = (firstEvent == null) || (firstEvent.getData() == null) ? null : firstEvent.getData().get(typeCodes.origin);
        final boolean firstEventIsFromRecommender = (firstEventOrigin != null) && ProductZaps.recommenderZapOrigins.containsKey(firstEventOrigin);
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

    }

    private static final class EmptyLongObjectMap<V> implements TLongObjectMap<V> {

        @Override
        public long getNoEntryKey() {
            return 0L;
        }

        @Override
        public int size() {
            return 0;
        }

        @Override
        public boolean isEmpty() {
            return true;
        }

        @Override
        public boolean containsKey(final long l) {
            return false;
        }

        @Override
        public boolean containsValue(final Object o) {
            return false;
        }

        @Override
        public V get(final long l) {
            return null;
        }

        @Override
        public V put(final long l, final V v) {
            throw new UnsupportedOperationException("EmptyLongObjectMap does not support put()");
        }

        @Override
        public V putIfAbsent(final long l, final V v) {
            throw new UnsupportedOperationException("EmptyLongObjectMap does not support putIfAbsent()");
        }

        @Override
        public V remove(final long l) {
            return null;
        }

        @Override
        public void putAll(Map<? extends Long, ? extends V> map) {
            throw new UnsupportedOperationException("EmptyLongObjectMap does not support putAll()");
        }

        @Override
        public void putAll(TLongObjectMap<? extends V> tLongObjectMap) {
            throw new UnsupportedOperationException("EmptyLongObjectMap does not support putAll()");
        }

        @Override
        public void clear() {

        }

        @Override
        public TLongSet keySet() {
            return new TLongHashSet();
        }

        @Override
        public long[] keys() {
            return new long[0];
        }

        @Override
        public long[] keys(final long[] longs) {
            if (longs.length == 0) return longs;
            return new long[0];
        }

        @Override
        public Collection<V> valueCollection() {
            return Collections.emptyList();
        }

        @Override
        public Object[] values() {
            return new Object[0];
        }

        @Override
        public V[] values(final V[] vs) {
            if (vs.length == 0) return vs;
            return (V[]) new Object[0];
        }

        @Override
        public TLongObjectIterator<V> iterator() {
            return new TLongObjectIterator<V>() {
                @Override
                public long key() {
                    return 0;
                }

                @Override
                public V value() {
                    return null;
                }

                @Override
                public V setValue(V v) {
                    return null;
                }

                @Override
                public void advance() {

                }

                @Override
                public boolean hasNext() {
                    return false;
                }

                @Override
                public void remove() {

                }
            };
        }

        @Override
        public boolean forEachKey(final TLongProcedure tLongProcedure) {
            return true;
        }

        @Override
        public boolean forEachValue(final TObjectProcedure<? super V> tObjectProcedure) {
            return true;
        }

        @Override
        public boolean forEachEntry(final TLongObjectProcedure<? super V> tLongObjectProcedure) {
            return true;
        }

        @Override
        public void transformValues(final TObjectFunction<V, V> tObjectFunction) {

        }

        @Override
        public boolean retainEntries(final TLongObjectProcedure<? super V> tLongObjectProcedure) {
            return false;
        }
    }
}
