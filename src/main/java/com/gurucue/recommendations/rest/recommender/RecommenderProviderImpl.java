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

import com.gurucue.recommendations.DatabaseException;
import com.gurucue.recommendations.blender.StatefulFilter;
import com.gurucue.recommendations.blender.VideoData;
import com.gurucue.recommendations.data.DataManager;
import com.gurucue.recommendations.data.RecommenderProvider;
import com.gurucue.recommendations.data.jdbc.JdbcDataLink;
import com.gurucue.recommendations.recommender.RecommendProduct;
import com.gurucue.recommendations.recommender.RecommendationSettings;
import com.gurucue.recommendations.recommender.Recommendations;
import com.gurucue.recommendations.rest.data.RequestCache;
import com.gurucue.recommendations.rest.data.RequestLogger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Implements the recommender provider.
 * @see RecommenderProvider
 */
public final class RecommenderProviderImpl implements RecommenderProvider {
    public static final RecommenderProviderImpl INSTANCE = new RecommenderProviderImpl();
    private static final Logger log = LogManager.getLogger(RecommenderProviderImpl.class);

    private final ConcurrentMap<String, RmiRecommender> recommenders = new ConcurrentHashMap<>();

    private RecommenderProviderImpl() {} // this is a singleton class, we don't permit instantiation elsewhere

    @Override
    public StatefulFilter<VideoData> recommendationsFilter(
            final String recommenderName,
            final long consumerId,
            final RecommendationSettings settings
    ) {
        return new RecommendationsFilter(this, recommenderName, settings, (recommender, candidateProducts) -> recommender.recommendations(RequestCache.get().getLogger(), consumerId, settings, candidateProducts));
    }

    @Override
    public StatefulFilter<VideoData> similarFilter(
            final String recommenderName,
            final long[] productIdsForSimilar,
            final RecommendationSettings settings
    ) {
        return new RecommendationsFilter(this, recommenderName, settings, (recommender, candidateProducts) -> recommender.similar(RequestCache.get().getLogger(), productIdsForSimilar, settings, candidateProducts));
    }

    public void shutdown() {
        // shutdown any recommenders
        log.info("Shutting down recommenders...");
        final List<RmiRecommender> buffer = new ArrayList<>(recommenders.values());
        recommenders.clear(); // first make them unavailable
        for (final RmiRecommender recommender : buffer) recommender.shutdown();
    }

    public void refreshRecommenders() {
        log.info("refreshing recommender provider");

        final String noaiSetting = System.getenv("RECSRV_NOAI");
        if ((noaiSetting != null) && ("yes".equalsIgnoreCase(noaiSetting) || "true".equalsIgnoreCase(noaiSetting) || "1".equals(noaiSetting))) {
            log.warn("AI library initialization disabled with RECSRV_NOAI environment variable, using built-in random recommender for any recommendation requests");
            // shutdown any recommenders
            final List<RmiRecommender> buffer = new ArrayList<>(recommenders.values());
            recommenders.clear(); // first make them unavailable
            for (final RmiRecommender recommender : buffer) recommender.shutdown();
        }
        else {
            // TODO: define entity manager API so we don't have to run SQL
            final Set<String> remaining = new HashSet<>(recommenders.keySet());
            final JdbcDataLink link = (JdbcDataLink) DataManager.getNewLink();
            try {
                final Statement statement = link.createStatement();
                try {
                    final ResultSet rs = statement.executeQuery("select id, name, hostname from recommender where id >= 0 and id < 1000"); // only select valid IDs: non-negative (internal) and less than 1000 (testing)
                    try {
                        while (rs.next()) {
                            final Long recommenderId = rs.getLong(1);
                            final String recommenderName = rs.getString(2);
                            final String recommenderLocation = rs.getString(3);

                            if (remaining.remove(recommenderName)) {
                                // an existing recommender
                                log.info("Reconfiguring recommender " + recommenderName + " (" + recommenderId + ") living on host " + (recommenderLocation == null ? "(null)" : "\"" + recommenderLocation + "\""));
                                recommenders.get(recommenderName).reconfigure(recommenderName, recommenderLocation);
                            }
                            else {
                                // a new recommender
                                log.info("Initializing recommender " + recommenderName + " (" + recommenderId + ") living on host " + (recommenderLocation == null ? "(null)" : "\"" + recommenderLocation + "\""));
                                recommenders.put(recommenderName, new RmiRecommender(recommenderName, recommenderId, recommenderLocation));
                            }
                        }
                    } finally {
                        rs.close();
                    }
                } finally {
                    statement.close();
                }
                link.commit();
            } catch (SQLException | RuntimeException e) {
                try {
                    link.rollback();
                } catch (DatabaseException e1) {
                    log.error("Failed to rollback transaction: " + e1.toString(), e1);
                }
                log.error("failed to read recommenders from the database: " + e.toString(), e);
                throw new DatabaseException("Failed to read recommenders from the database: " + e.toString(), e);
            } finally {
                link.close();
            }

            for (final String recommenderName : remaining) {
                final RmiRecommender recommender = recommenders.remove(recommenderName);
                if (recommender != null) {
                    log.info("Shutting down recommender " + recommender.getName() + "(" + recommender.getId() + ") living on host \"" + recommender.getRecommenderHostname() + "\"");
                    recommender.shutdown();
                }
            }

            if (recommenders.size() == 0) log.error("No recommenders are defined: the table RECOMMENDER is empty");
        }
    }

    Recommendations recommendations(final String recommenderName, final long consumerId, final RecommendationSettings settings, final RecommendProduct[] candidateProducts) {
        final RequestLogger logger = RequestCache.get().getLogger();
        Recommendations result = null;
        final RmiRecommender r = recommenders.get(recommenderName);
        if (r != null) {
            result = r.recommendations(logger, consumerId, settings, candidateProducts);
        }
        return result;
    }

    Recommendations similar(final String recommenderName, final long[] productIdsForSimilar, final RecommendationSettings settings, final RecommendProduct[] candidateProducts) {
        final RequestLogger logger = RequestCache.get().getLogger();
        Recommendations result = null;
        final RmiRecommender r = recommenders.get(recommenderName);
        if (r != null) {
            result = r.similar(logger, productIdsForSimilar, settings, candidateProducts);
        }
        return result;
    }

    Recommendations invoke(final String recommenderName, final RecommendProduct[] candidateProducts, final RecommenderInvoker invoker) {
        final RmiRecommender r = recommenders.get(recommenderName);
        if (r != null) {
            return invoker.invoke(r, candidateProducts);
        }
        return null;
    }
}
