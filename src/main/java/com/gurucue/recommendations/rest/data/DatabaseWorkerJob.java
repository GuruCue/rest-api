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

import com.gurucue.recommendations.Transaction;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Defines a unit of work for the {@link DatabaseWorkerThread}.
 */
public abstract class DatabaseWorkerJob {
    private static final Logger log = LogManager.getLogger(DatabaseWorkerThread.class);

    /**
     * Invoked by {@link DatabaseWorkerThread} to perform database operations
     * on the given connection.
     *
     * @param transaction The transaction inside which to operate
     */
    public abstract void execute(Transaction transaction);

    /**
     * Handles a failure to execute the job.
     * This is primarily needed if the invocation of {@link #execute(com.gurucue.recommendations.Transaction)}
     * fails with no hope of remedy, and there needs to be left behind some
     * kind of a trail, e.g. entry in a log file.
     */
    public void onFail() {
        // default: just log the error
        log.error("Error executing a job of type " + getClass().getCanonicalName());
    }
}
