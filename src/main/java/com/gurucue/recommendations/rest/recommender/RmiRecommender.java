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

import com.gurucue.recommendations.ProcessingException;
import com.gurucue.recommendations.recommender.BasicRecommender;
import com.gurucue.recommendations.recommender.RecommendProduct;
import com.gurucue.recommendations.recommender.RecommendationSettings;
import com.gurucue.recommendations.recommender.Recommendations;
import com.gurucue.recommendations.recommender.RecommenderNotReadyException;
import com.gurucue.recommendations.rest.data.RequestCache;
import com.gurucue.recommendations.rest.data.RequestLogger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Invokes the real recommender via RMI.
 */
public final class RmiRecommender {
    private static final Logger log = LogManager.getLogger(RmiRecommender.class);

    private final long recommenderId;
    private String recommenderHostname;
    private String name;
    private BasicRecommender recommender;
    private final ExecutorService asyncRecommenderInvoker;

    public RmiRecommender(final String name, final long recommenderId, final String recommenderHostname) {
        this.recommenderId = recommenderId;
        this.recommenderHostname = recommenderHostname == null ? "127.0.0.1" : recommenderHostname;
        this.name = name;
        this.recommender = null;
        this.asyncRecommenderInvoker = Executors.newFixedThreadPool(3, runnable -> new Thread(runnable, "Async recommender invoker: " + name + "@" + recommenderHostname + " [" + recommenderId + "]"));
        log.info("Recommender " + this.name + " (" + this.recommenderId + "@" + this.recommenderHostname + ") instantiated");
    }

    private BasicRecommender getRecommender() throws RecommenderNotReadyException {
        try {
            synchronized (this) {
                if (recommender != null) return recommender;
                log.debug("[REC " + recommenderId + "] Looking up RMI proxy AI" + recommenderId + " at " + recommenderHostname);
                final Registry registry = LocateRegistry.getRegistry(recommenderHostname);
                return recommender = (BasicRecommender) registry.lookup("AI" + recommenderId);
            }
        }
        catch (Exception e) {
            final String message = "Failed to obtain a RMI proxy for the recommender " + recommenderId + " located at " + recommenderHostname + ": " + e.toString();
            log.error(message, e);
            throw new RecommenderNotReadyException(message, e);
        }
    }

    public long getId() {
        return recommenderId;
    }

    public String getName() {
        return name;
    }

    public String getRecommenderHostname() {
        return recommenderHostname;
    }

    public void reconfigure(final String newName, final String newRecommenderHostname) {
        final String realRecommenderHostname = newRecommenderHostname == null ? "127.0.0.1" : newRecommenderHostname;
        synchronized (this) {
            if (!recommenderHostname.equals(realRecommenderHostname)) {
                recommenderHostname = realRecommenderHostname;
                recommender = null;
            }
            name = newName;
        }
        log.info("Recommender " + this.recommenderId + " reconfigured with name " + newName + " and residing at server " + realRecommenderHostname);
    }

    private static final long TIMEOUT_NS = 2L * 1000L * 1000000L; // 2 seconds in ns

    private Recommendations asyncInvoke(final RequestLogger logger, final Callable<Recommendations> job) {
        if (asyncRecommenderInvoker.isShutdown()) throw new ProcessingException("The recommender " + recommenderId + " (" + this.recommenderId + "@" + this.recommenderHostname + ") has been shut down");
        long now = System.nanoTime();
        final long limit = now + TIMEOUT_NS;
        int i = 0;
        while ((now < limit) && (i < 3)) { // wait until 2 seconds have passed, but no more than 3 iterations
            final Future<Recommendations> task;
            try {
                task = asyncRecommenderInvoker.submit(job);
            } catch (RejectedExecutionException e) {
                RequestCache.get().getLogger().subLogger(getClass().getSimpleName()).error("Failed to async invoke a recommender: " + e.toString(), e);
                return null;
            }

            try {
                return task.get(limit - now, TimeUnit.NANOSECONDS);
            } catch (CancellationException e) {
                logger.subLogger(getClass().getSimpleName()).error("[REC " + recommenderId + "] Recommender async invocation cancelled: " + e.toString(), e);
            } catch (ExecutionException e) {
                logger.subLogger(getClass().getSimpleName()).error("[REC " + recommenderId + "] Recommender async execution failed: " + e.toString(), e);
                synchronized (this) {
                    recommender = null; // reset the recommender, so we get a new instance on next getRecommender()
                }
            } catch (InterruptedException e) {
                logger.subLogger(getClass().getSimpleName()).error("[REC " + recommenderId + "] Recommender async execution interrupted: " + e.toString(), e);
            } catch (TimeoutException e) {
                logger.subLogger(getClass().getSimpleName()).error("[REC " + recommenderId + "] Recommender async execution timed out: " + e.toString(), e);
            }

            task.cancel(true);
            now = System.nanoTime();
            i++;
        }
        return null;
    }

    public Recommendations recommendations(final RequestLogger logger, final long consumerId, final RecommendationSettings settings, final RecommendProduct[] candidateProducts) {
        return asyncInvoke(logger, () -> getRecommender().recommendations(consumerId, candidateProducts, settings));
    }

    public Recommendations similar(final RequestLogger logger, final long[] productIdsForSimilar, final RecommendationSettings settings, final RecommendProduct[] candidateProducts) {
        return asyncInvoke(logger, () -> getRecommender().similar(productIdsForSimilar, candidateProducts, settings));
    }

    public void shutdown() {
        asyncRecommenderInvoker.shutdown();
    }
}
