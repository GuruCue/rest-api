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
package com.gurucue.recommendations.rest.recommender;

import com.gurucue.recommendations.Timer;
import com.gurucue.recommendations.TimerListener;
import com.gurucue.recommendations.blender.TopBlender;
import com.gurucue.recommendations.compiler.JavaEngine;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Pattern;

/**
 * The handler for run-time management of the blending system.
 * It uses a {@link JavaEngine} for run-time compiling of filter scripts, and then
 * instantiates all classes implementing the {@link TopBlender} interface and maps them
 * accordingly to their partner ownership (top-level package name), blender name,
 * and {@link com.gurucue.recommendations.entity.DataType}.
 */
public final class BlenderHandler implements TimerListener {
    private static final Logger log = LogManager.getLogger(BlenderHandler.class);
    private static final long REFRESH_CYCLE = 60L * 1000L; // one minute
    private static final Pattern nonLiterals = Pattern.compile("^[^a-zA-Z]|(?<!^)[^0-9a-zA-Z]");
    public static final BlenderHandler INSTANCE = new BlenderHandler();

    private final JavaEngine engine;
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final Lock accessLock = rwLock.readLock();
    private final Lock writeLock = rwLock.writeLock();

    private Map<TopBlenderKey, TopBlender> topBlenders;
    private long nextRefreshMillis;
    private final AtomicBoolean refreshIsRunning = new AtomicBoolean(false);
    private boolean isActive = true;

    private BlenderHandler() {
        engine = new JavaEngine("/opt/GuruCue/blenders");
        try {
            refresh();
        }
        catch (Throwable e) {
            log.error("Initial Java engine refresh failed: " + e.toString(), e);
            topBlenders = Collections.emptyMap();
        }
        nextRefreshMillis = System.currentTimeMillis() + REFRESH_CYCLE;
        Timer.INSTANCE.schedule(nextRefreshMillis, this);
    }

    public void shutdown() {
        log.info("Shutting down blenders...");
        final long t = nextRefreshMillis;
        Timer.INSTANCE.unschedule(t, this);
        isActive = false;
        refreshIsRunning.set(true);
        if (t != nextRefreshMillis) Timer.INSTANCE.unschedule(nextRefreshMillis, this);
    }

    public boolean refresh() {
        if (!refreshIsRunning.compareAndSet(false, true)) return false;
        try {
            final long startRefreshTime = System.nanoTime();
            if (!engine.refresh()) {
                // the engine was not refreshed, probably due to no file having changed
                log.debug("Not refreshing blenders: java engine found no updated sources");
                return false;
            }

            final long startProcessingTime = System.nanoTime();
            final StringBuilder logBuilder = new StringBuilder(4096);
            logBuilder.append("Refreshing top blenders:");
            final int startingSize = logBuilder.length();
            final Map<TopBlenderKey, TopBlender> newTopBlenders = new HashMap<>();
            for (final Class<? extends TopBlender> blenderClass : engine.getInstancesOf(TopBlender.class)) {
                logBuilder.append("\n  ");
                final TopBlenderKey key = TopBlenderKey.fromClass(blenderClass);
                if (key == null) {
                    final String reason = "Failed to instantiate blender " + blenderClass.getCanonicalName() + ": it cannot be mapped with a TopBlenderKey";
                    log.error(reason);
                    logBuilder.append(reason);
                    continue;
                }
                final TopBlender blender;
                try {
                    blender = blenderClass.newInstance();
                } catch (Exception e) {
                    final String reason = "Failed to instantiate top blender " + blenderClass.getCanonicalName() + ": " + e.toString();
                    log.error(reason, e);
                    logBuilder.append(reason);
                    continue;
                }
                final TopBlender previous = newTopBlenders.put(key, blender);
                logBuilder.append("partner=\"").append(key.partnerName).append("\", group=\"").append(key.blenderGroup.getPath()).append("\": ").append(blenderClass.getCanonicalName());
                if (previous != null) logBuilder.append(", replacing: ").append(previous.getClass().getCanonicalName());
            }
            if (logBuilder.length() == startingSize) {
                logBuilder.append("\n  No blenders defined");
            }
            final long startReplacingTime = System.nanoTime();
            writeLock.lock();
            try {
                topBlenders = newTopBlenders;
            } finally {
                writeLock.unlock();
            }
            final long finishTime = System.nanoTime();
            logBuilder.append("\nTimings: ").append(finishTime - startRefreshTime)
                    .append(" ns, of that compiling ").append(startProcessingTime - startRefreshTime)
                    .append(" ns, processing ").append(startReplacingTime - startProcessingTime)
                    .append(" ns, lock-and-switchover ").append(finishTime - startReplacingTime).append(" ns");
            log.info(logBuilder.toString());
            return true;
        }
        finally {
            if (isActive) refreshIsRunning.compareAndSet(true, false);
        }
    }

    @Override
    public void onTimerExpired(final long expiryTime) {
        Timer.INSTANCE.schedule(nextRefreshMillis = expiryTime + REFRESH_CYCLE, this);
        refresh();
    }

    public TopBlender getTopBlender(final String partnerUsername, final BlenderGroup blenderGroup) {
        final String partnerName = nonLiterals.matcher(partnerUsername).replaceAll("");
        accessLock.lock();
        try {
            if (topBlenders == null) return null;
            return topBlenders.get(new TopBlenderKey(partnerName, blenderGroup));
        }
        finally {
            accessLock.unlock();
        }
    }

    // ========== Helper classes ==========

    /**
     * Mapping key for top blenders, where they are keyed by the tuple (patner_name, blender_group).
     */
    private static final class TopBlenderKey {
        public final String partnerName;
        public final BlenderGroup blenderGroup;
        TopBlenderKey(final String partnerName, final BlenderGroup blenderGroup) {
            if (partnerName == null) throw new NullPointerException("Instantiating with a null partner name");
            if (blenderGroup == null) throw new NullPointerException("Instantiating with a null blender group");
            this.partnerName = partnerName;
            this.blenderGroup = blenderGroup;
        }
        @Override
        public int hashCode() {
            return 31 * partnerName.hashCode() + blenderGroup.hashCode();
        }
        @Override
        public boolean equals(final Object obj) {
            if (obj == null) return false;
            if (!(obj instanceof TopBlenderKey)) return false;
            final TopBlenderKey other = (TopBlenderKey)obj;
            if (other.blenderGroup != this.blenderGroup) return false;
            return other.partnerName.equals(this.partnerName);
        }
        public static TopBlenderKey fromClass(final Class<? extends TopBlender> clazz) {
            final String canonicalName = clazz.getCanonicalName();
            final int firstDosPos = canonicalName.indexOf(".");
            if (firstDosPos < 0) return null;
            final String partnerName = canonicalName.substring(0, firstDosPos);
            final int secondDotPos = canonicalName.indexOf(".", firstDosPos + 1);
            if (secondDotPos < 0) return null;
            final String path = canonicalName.substring(firstDosPos + 1, secondDotPos);
            final BlenderGroup bg = BlenderGroup.byPath(path);
            if (bg == null) return null;
            return new TopBlenderKey(partnerName, bg);
        }
    }
}
