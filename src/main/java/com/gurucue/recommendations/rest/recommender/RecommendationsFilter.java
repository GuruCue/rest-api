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

import com.gurucue.recommendations.Utils;
import com.gurucue.recommendations.blender.DataSet;
import com.gurucue.recommendations.blender.StatefulFilter;
import com.gurucue.recommendations.blender.VideoData;
import com.gurucue.recommendations.entity.product.GeneralVideoProduct;
import com.gurucue.recommendations.recommender.RecommendProduct;
import com.gurucue.recommendations.recommender.Recommendation;
import com.gurucue.recommendations.recommender.RecommendationSettings;
import com.gurucue.recommendations.recommender.Recommendations;
import com.gurucue.recommendations.rest.data.RequestCache;
import gnu.trove.map.TLongObjectMap;
import gnu.trove.map.hash.TLongObjectHashMap;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Stateful filter wrapper around the recommender invocation.
 */
public final class RecommendationsFilter implements StatefulFilter<VideoData> {
    final RecommenderProviderImpl provider;
    final String recommenderName;
    final RecommendationSettings settings;
    final RecommenderInvoker invoker;

    public RecommendationsFilter(
            final RecommenderProviderImpl provider,
            final String recommenderName,
            final RecommendationSettings settings,
            final RecommenderInvoker invoker
    ) {
        this.provider = provider;
        this.recommenderName = recommenderName;
        this.settings = settings;
        this.invoker = invoker;
    }

    StringBuilder log = null;

    @Override
    public DataSet<VideoData> transform(final DataSet<VideoData> source) {
        final StringBuilder logger = new StringBuilder(512);
        // process any tags
        final Map<String, Integer> limits = source.getLimitTags();
        if ((limits != null) && !limits.isEmpty()) {
            final Map<String, String> tags = new HashMap<>(limits.size() + 2);
            final StringBuilder registeredTags = new StringBuilder(30 * limits.size()); // guesstimate
            final Iterator<Map.Entry<String, Integer>> limitsIterator = limits.entrySet().iterator();
            if (limitsIterator.hasNext()) {
                final Map.Entry<String, Integer> firstEntry = limitsIterator.next();
                final String firstKey = firstEntry.getKey();
                final String firstValue = firstEntry.getValue().toString();
                tags.put("MAX_ITEMS_" + firstKey, firstValue);
                registeredTags.append(firstKey);
                logger.append("Setting tags for recommender:\n    MAX_ITEMS_").append(firstKey).append("=").append(firstValue);
                while (limitsIterator.hasNext()) {
                    final Map.Entry<String, Integer> entry = limitsIterator.next();
                    final String key = entry.getKey();
                    final String value = entry.getValue().toString();
                    tags.put("MAX_ITEMS_" + key, value);
                    registeredTags.append(";").append(key);
                    logger.append("\n    MAX_ITEMS_").append(key).append("=").append(value);
                }
                tags.put("PRODUCT_TAGS", registeredTags.toString());
                logger.append("\n    PRODUCT_TAGS=").append(registeredTags).append("\n");
                settings.setTags(tags);
            }
            else {
                logger.append("No tags set for recommender.\n");
            }
        }
        else {
            logger.append("No tags set for recommender.\n");
        }

        // create a lookup map, to use with product IDs returned by recommender
        final RecommendProduct[] availableProducts = new RecommendProduct[source.size()];
        final TLongObjectMap<VideoData> dataLookup = new TLongObjectHashMap<>(source.size());
        final IntegerCounter indexer = new IntegerCounter(0);
        final long nanoStart = System.nanoTime();
        source.forEach(videoData -> {
            final long productId = videoData.video.id;
            availableProducts[indexer.count++] = new RecommendProduct(productId, videoData.tags);
            dataLookup.put(productId, videoData);
        });

        Recommendations recommendationsResult = null;
        final long nanoPrepare = System.nanoTime();
        try {
            recommendationsResult = provider.invoke(recommenderName, availableProducts, invoker);
        }
        catch (RuntimeException e) {
            logger.append("ERROR: Recommender failed with an exception, making random selection: ").append(e.toString());
            Utils.formatStackTrace(logger, e.getStackTrace()); // formatStackTrace uses prefix newline, not suffix like we do
            logger.append("\n");
        }
        final long nanoRecommendation = System.nanoTime();
        if (recommendationsResult == null) {
            // TODO: determine a TV-grid recommender more nicely, not with the constant
            try {
                final long nanoRandomStart = System.nanoTime();
                if ("tv-grid".equals(recommenderName))
                    recommendationsResult = RandomGridSelection.INSTANCE.recommendations(RequestCache.get().getLogger(), settings, source.toArray(new VideoData[source.size()]));
                else
                    recommendationsResult = RandomSelection.INSTANCE.recommendations(RequestCache.get().getLogger(), settings, source.toArray(new VideoData[source.size()]));
                final long nanoRandomEnd = System.nanoTime();
                logger.append("Random selection successful, generated ").append(recommendationsResult.recommendations.length).append(" products, timing: ").append(nanoRandomEnd - nanoRandomStart).append(" ns\n");
            }
            catch (RuntimeException e) {
                logger.append("ERROR: Random selection failed with an exception, returning 0 results: ").append(e.toString());
                Utils.formatStackTrace(logger, e.getStackTrace()); // formatStackTrace uses prefix newline, not suffix like we do
                logger.append("\n");
                recommendationsResult = new Recommendations(new Recommendation[0]); // we return nothing
            }
        }

        final DataSet.Builder<VideoData> builder = new DataSet.Builder<>(source.getDuplicateResolver(), source);
        final Recommendation[] recommendations = recommendationsResult.recommendations;
        final StringBuilder detailsBuilder = new StringBuilder(recommendations.length * 300);

        for (int i = 0; i < recommendations.length; i++) {
            final Recommendation rating = recommendations[i];
            if (rating == null) {
                logger.append("ERROR: Recommender returned null at index ").append(i).append(" of the recommendations array of length ").append(recommendations.length).append("\n");
                continue;
            }
            final long productId;
            try {
                productId = rating.productId;
            }
            catch (RuntimeException e) {
                logger.append("ERROR: Internal error while fetching product ID from the ProductRating instance at index ").append(i).append(" of the recommendations array of length ").append(recommendations.length).append(": ").append(e.toString());
                Utils.formatStackTrace(logger, e.getStackTrace()); // formatStackTrace uses prefix newline, not suffix like we do
                logger.append("\n");
                continue;
            }
            final VideoData data = dataLookup.get(productId);
            if (data == null) {
                logger.append("ERROR: Recommender returned product recommendation with ID ").append(productId).append(" which is not among submitted choices").append("\n");
                continue;
            }

            final String virtualChannel = rating.tags.get("virtual_channel");
            if (virtualChannel != null) {
                // we have a grid recommender
                try {
                    data.gridLine = Integer.parseInt(virtualChannel) + 1;
                }
                catch (NumberFormatException e) {
                    logger.append("ERROR: Virtual channel \"").append(virtualChannel).append("\" is not an integer for product ").append(data.video.id).append(": ").append(e.toString());
                    Utils.formatStackTrace(logger, e.getStackTrace()); // formatStackTrace uses prefix newline, not suffix like we do
                    logger.append("\n");
                }
            }

            data.explanation = rating.explanation;

            data.prediction = rating.prediction;

            data.prettyExplanations = rating.prettyExplanations;

            builder.add(data);

            final GeneralVideoProduct video = data.video;
            if (video == null) detailsBuilder.append("    ERROR: product not present, cannot log details");
            else {
                detailsBuilder.append("    product ").append(video.id);
                if (data.isTvProgramme) detailsBuilder.append(" (tv-programme ");
                else detailsBuilder.append(" (video ");
                detailsBuilder.append(video.partnerProductCode).append(") ");
                if (video.title == null) detailsBuilder.append("NULL title");
                else detailsBuilder.append("\"").append(video.title.asString().replace("\"", "\\\"")).append("\"");
            }
            detailsBuilder.append("\n        prediction: ").append(data.prediction).append("\n        explanation: ");
            if (data.explanation == null) detailsBuilder.append("null");
            else detailsBuilder.append("\"").append(data.explanation.replace("\"", "\\\"")).append("\"");
            detailsBuilder.append("\n        pretty explanations:");
            if (data.prettyExplanations == null) detailsBuilder.append(" null");
            else if (data.prettyExplanations.isEmpty()) detailsBuilder.append(" empty list");
            else {
                data.prettyExplanations.forEach((final String explanation, final Float weight) -> detailsBuilder.append("\n            weight: ").append(weight).append(", explanation: \"").append(explanation.replace("\"", "\\\"")).append("\""));
            }
            detailsBuilder.append("\n");
        }
        final long nanoProcessing = System.nanoTime();

        logger.append("Returning ").append(builder.size()).append(" recommendations, timings: AI input preparation: ").append(nanoPrepare - nanoStart).append(" ns, obtaining AI result: ").append(nanoRecommendation - nanoPrepare).append(" ns, converting AI result into internal dataset: ").append(nanoProcessing - nanoRecommendation).append(" ns\n").append(detailsBuilder);
        log = logger;

        return builder.build();
    }

    @Override
    public void writeLog(final StringBuilder output) {
        if (log != null) output.append(log);
    }
}
