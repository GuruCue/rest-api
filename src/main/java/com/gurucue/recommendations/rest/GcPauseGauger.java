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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runs in the background and tries to check every 50ms if it was more than
 * 50ms since the last check, thereby indicating that probably a garbage
 * collector took a significant portion of time to do its work. It logs
 * such occurrences for diagnostics purposes.
 */
public class GcPauseGauger implements Runnable {
    private static final Logger log = LogManager.getLogger(GcPauseGauger.class);
    public static final GcPauseGauger INSTANCE = new GcPauseGauger();

    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread thread = null;

    private GcPauseGauger() {}

    public synchronized void start() {
        if (thread != null) throw new IllegalStateException("Thread already active");
        thread = new Thread(this, "GC Pause Gauging");
        thread.setDaemon(true);
        running.set(true);
        thread.start();
    }

    public synchronized void stop() {
        if (thread == null) return;
        running.set(false);
        thread.interrupt();
        try {
            thread.join();
        } catch (InterruptedException e) {
            log.error("Interrupted while waiting for the worker thread to exit: " + e.toString(), e);
        }
    }

    @Override
    public void run() {
        log.info("Pause gauger thread started");
        try {
            long beforeStamp = System.nanoTime();
            while (running.get()) {
                try {
                    Thread.sleep(50L);
                    final long afterStamp = System.nanoTime();
                    final long delta = (afterStamp - beforeStamp) / 1000000L;
                    if (delta > 60L) {
                        log.warn("Detected possible GC pause of length between " + delta + " and " + (delta - 50L) + " ms");
                    }
                    beforeStamp = afterStamp;
                }
                catch (Exception e) {
                    log.warn("Caught an exception while in the worker loop: " + e.toString(), e);
                }
            }
        }
        finally {
            log.info("Pause gauger thread exiting");
        }
    }
}
