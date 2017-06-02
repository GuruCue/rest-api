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
package com.gurucue.recommendations.rest;

import com.gurucue.recommendations.Timer;
import com.gurucue.recommendations.TimerListener;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Every minute logs the memory consumption, for diagnostic purposes.
 */
public class PeriodicStatusLogger implements TimerListener {
    private static final Logger log = LogManager.getLogger(PeriodicStatusLogger.class);

    @Override
    public void onTimerExpired(final long expiryTime) {
        Timer.INSTANCE.schedule(expiryTime + 60000L, this); // re-schedule in a minute
        final Runtime runtime = Runtime.getRuntime();
        log.info(String.format("Memory free: %.3f MB, total: %.3f MB, max: %.3f MB", runtime.freeMemory() / 1048576.0, runtime.totalMemory() / 1048576.0, runtime.maxMemory() / 1048576.0));
    }
}
