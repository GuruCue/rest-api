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

import com.gurucue.recommendations.DatabaseException;
import com.gurucue.recommendations.Timer;
import com.gurucue.recommendations.TimerListener;
import com.gurucue.recommendations.data.DataManager;
import com.gurucue.recommendations.entity.ConsumerEvent;
import com.gurucue.recommendations.entity.product.TvProgrammeProduct;
import org.apache.logging.log4j.Logger;

/**
 * Processing logic for zap events, implemented as a singleton class.
 * Proxies zaps further, depending on product type.
 * Holds a list of zap timestamps per device.
 *
 * @see com.gurucue.recommendations.rest.data.processing.zap.TvChannelState
 */
public final class ConsumerEventProcessor implements TimerListener {
    /**
     * The instance to use for dispatching zap events for processing.
     * @see #saveNewServiceEvent(com.gurucue.recommendations.entity.ConsumerEvent)
     */
    public static final ConsumerEventProcessor INSTANCE = new ConsumerEventProcessor();

    private final ZapProcessor processor;
    private long timerMillis;
    private final long idEventTypeZap;

    private ConsumerEventProcessor() {
        idEventTypeZap = DataManager.getConsumerEventTypeCodes().idForZap;
        processor = new ZapProcessor(this);
    }

    /**
     * Starts the processing threads. Should be called at a service startup.
     */
    public void start() {
        processor.start();
        synchronized (this) {
            timerMillis = Timer.currentTimeMillis() + 10000L; // 10 secs
            Timer.INSTANCE.schedule(timerMillis, this);
        }
    }

    /**
     * Stops the processing thread. Should be called at a service shutdown.
     */
    public void stop() {
        synchronized (this) {
            Timer.INSTANCE.unschedule(timerMillis, this);
        }
        processor.stop();
    }

    public final void saveNewServiceEvent(final ConsumerEvent consumerEvent){//}, final Product product) {
        try {
            DataManager.queueConsumerEvent(consumerEvent);
        }
        catch (InterruptedException e) {
            throw new DatabaseException("Interrupted while queueing an event from the event service: " + e.toString(), e);
        }

        if ((consumerEvent != null) && (consumerEvent.getEventType() != null) && (consumerEvent.getEventType().getId().longValue() == idEventTypeZap)) {
            // process a zap
            // TODO: check whether we're running
            processor.submit(consumerEvent);
        }
    }

    /**
     * Periodically logs some processing statistics.
     * @param expiryTime
     */
    @Override
    public final void onTimerExpired(final long expiryTime) {
        if (!processor.running) return;
        synchronized (this) {
            timerMillis = expiryTime + 10000L; // 10 secs
            Timer.INSTANCE.schedule(timerMillis, this);
        }
        processor.logStatistics();
    }

    static String tvProgrammeTitle(final TvProgrammeProduct tvProgramme, final Logger log) {
        return tvProgramme.title.asString();
    }
}
