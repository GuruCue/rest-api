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
package com.gurucue.recommendations.rest.data;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class RequestLogger {
    private static final ConcurrentMap<LoggerKey, RequestLogger> loggers = new ConcurrentHashMap<>();

    private final LoggerKey key;
    private final Logger logger;
    private final String logPrefix;

    private RequestLogger(final LoggerKey key) {
        if (key == null) throw new NullPointerException("The logger key is null");
        if (key.loggerName == null) throw new NullPointerException("The logger name is null");
        if (key.logPrefix == null) throw new NullPointerException("The logging prefix is null");
        this.key = key;
        this.logger = LogManager.getLogger(key.loggerName);
        this.logPrefix = key.logPrefix;
    }

    public static RequestLogger getLogger(final String name, final String logPrefix) {
        final LoggerKey key = new LoggerKey(name, logPrefix);
        RequestLogger logger = loggers.get(key);
        if (logger != null) return logger;
        logger = new RequestLogger(key);
        RequestLogger previousLogger = loggers.putIfAbsent(key, logger);
        if (previousLogger == null) return logger;
        return previousLogger;
    }

    public RequestLogger subLogger(final String subname) {
        return getLogger(key.loggerName + "." + subname, key.logPrefix);
    }

    public boolean isDebugEnabled() {
        return logger.isDebugEnabled();
    }

    public void log(final Level level, final String message, final Throwable t) {
        logger.log(level, logPrefix + message, t);
    }

    public void debug(final String message) {
        log(Level.DEBUG, message, null);
    }

    public void debug(final String message, Throwable t) {
        log(Level.DEBUG, message, t);
    }

    public void info(final String message) {
        log(Level.INFO, message, null);
    }

    public void info(final String message, Throwable t) {
        log(Level.INFO, message, t);
    }

    public void warn(final String message) {
        log(Level.WARN, message, null);
    }

    public void warn(final String message, Throwable t) {
        log(Level.WARN, message, t);
    }

    public void error(final String message) {
        log(Level.ERROR, message, null);
    }

    public void error(final String message, Throwable t) {
        log(Level.ERROR, message, t);
    }

    private static final class LoggerKey {
        final String loggerName;
        final String logPrefix;
        final int hash;

        LoggerKey(final String loggerName, final String logPrefix) {
            this.loggerName = loggerName;
            this.logPrefix = logPrefix;
            this.hash = (loggerName.hashCode() * 31) + logPrefix.hashCode();
        }

        @Override
        public boolean equals(final Object obj) {
            if (obj == null) return false;
            if (obj instanceof LoggerKey) {
                final LoggerKey other = (LoggerKey)obj;
                if (!loggerName.equals(other.loggerName)) return false;
                return logPrefix.equals(other.logPrefix);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }
}
