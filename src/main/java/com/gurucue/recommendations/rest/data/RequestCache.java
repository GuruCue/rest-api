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

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.gurucue.recommendations.ResponseException;
import com.gurucue.recommendations.ResponseStatus;
import com.gurucue.recommendations.data.DataManager;

import com.gurucue.recommendations.entity.*;

/**
 * Caches various things that are local to and current only for the duration of processing of a request.
 *
 */
public final class RequestCache {
    private static final Queue<RequestCache> cachePool = new ConcurrentLinkedQueue<RequestCache>();

    public static final ThreadLocal<RequestCache> instance = new ThreadLocal<RequestCache>();

    /**
     * Returns a RequestCache instance from the pool for use in
     * processing a client request.
     *
     * @param serviceName the name of the service that is using the instance (i.e. servlet name)
     * @return a RequestCache instance
     */
    public static RequestCache getCache(final String serviceName) {
        RequestCache cache = cachePool.poll();
        if (null == cache) cache = new RequestCache();
        cache.setupInstance(serviceName);
        return cache;
    }

    /**
     * Returns the current RequestCache instance.
     *
     * @return a RequestCache instance
     */
    public static RequestCache get() {
        return instance.get();
    }

    // instance stuff
    private Partner partner;
    public final List<String> trace = new ArrayList<>();
    private RequestLogger logger;

    private RequestCache() {
        // not instantiate-able from outside
    }
    
    private void setupInstance(final String serviceName) {
        String logPrefix = "[" + Thread.currentThread().getId() + "] ";
        logger = RequestLogger.getLogger("service." + serviceName, logPrefix);
        instance.set(this);
    }

    /**
     * Looks up the partner with the given username, and internally assigns
     * the corresponding DTO instance, which can be obtained with getPartner().
     *
     * @param partnerUsername the Partner's username, representing the client performing the request for which the RequestCache instance is being obtained
     * @throws ResponseException if there's no Partner in the database with the given username
     */
    public void setPartner(final String partnerUsername) throws ResponseException {
        if ((null == partnerUsername) || (0 == partnerUsername.length())) {
            throw new ResponseException(ResponseStatus.NO_PARTNER_ID);
        }
        try {
            partner = DataManager.getCurrentLink().getPartnerManager().getByUsername(partnerUsername);
            if (null == partner) throw new ResponseException(ResponseStatus.INVALID_PARTNER, "The partner is not configured: " + partnerUsername);
        }
        catch (Exception e) {
            if (e instanceof ResponseException) throw (ResponseException) e;
            throw new ResponseException(ResponseStatus.UNKNOWN_ERROR, e, "Unexpected error: " + e.toString());
        }
    }

    /**
     * Flushes and clears the Hibernate session and private cache,
     * and returns this instance to the pool of RequestCache. Must be
     * called after the current request has finished processing.
     */
    public void close(final boolean commit) {
        // if there is a trace available, then log it
        if (trace.size() > 0) {
            final StringBuilder sb = new StringBuilder();
            sb.append("A trace is available:");
            for (int i = trace.size() - 1; i >= 0; i--) {
                sb.append("\n");
                sb.append(trace.get(i));
            }
            getLogger().error(sb.toString());
            trace.clear();
        }
        // purge per-request caches, and clear other per-request things
        partner = null;
        instance.remove();

        // make the instance available for new requests
        logger = null;
        cachePool.add(this);
    }

    /**
     * Returns the Partner object representing the client that is
     * performing the current request.
     *
     * @return the Partner object representing this request's client
     */
    public Partner getPartner() {
        return partner;
    }

    /**
     * Adds a trace line, used for diagnosing errors.
     * Ideally at every stack position of an exception trace
     * a trace line should be added, so the one debugging the
     * exception gets a clearer picture of the state the
     * program was in.
     *
     * @param line a trace line
     */
    public void addTrace(final String line) {
        trace.add(line);
    }

    public RequestLogger getLogger() {
        final RequestLogger l = logger;
        if (l == null) return RequestLogger.getLogger("service.unknown", "[" + Thread.currentThread().getId() + "] ");
        return l;
    }
}
