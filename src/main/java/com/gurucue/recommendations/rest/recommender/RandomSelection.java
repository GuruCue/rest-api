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

import com.gurucue.recommendations.blender.VideoData;
import com.gurucue.recommendations.recommender.Recommendation;
import com.gurucue.recommendations.recommender.RecommendationSettings;
import com.gurucue.recommendations.recommender.Recommendations;
import com.gurucue.recommendations.rest.data.RequestLogger;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Returns a random choice of given products. To be used in place of a real recommender.
 */
public final class RandomSelection {
    public static final RandomSelection INSTANCE = new RandomSelection();
    private static final String EXPLANATION_RANDOM = "Random";

    private RandomSelection() {} // it's a singleton, therefore no outside instantiation possible

    public Recommendations recommendations(final RequestLogger logger, final RecommendationSettings settings, final VideoData[] candidateProducts) {
        final RequestLogger myLogger = logger.subLogger(getClass().getSimpleName());
        final Map<String, String> emptyRecommenderTags = Collections.emptyMap();
        final int productListSize = candidateProducts.length;
        final int n = settings.maxResults;
        if (n >= productListSize) {
            myLogger.info("recommend(): requested " + n + " recommendations, returning all " + productListSize);
            final Recommendation[] recs = new Recommendation[candidateProducts.length];
            for (int i = candidateProducts.length - 1; i >= 0; i--) {
                recs[i] = new Recommendation(candidateProducts[i].video.id, emptyRecommenderTags, 0.0, EXPLANATION_RANDOM, null);
            }
            myLogger.info("recommend(): requested " + n + " recommendations, randomly chosen " + productListSize + " from " + productListSize);
            return new Recommendations(recs);
        }

        final Map<String, IntegerCounter> constraints = new HashMap<>();
        final Set<String> blockers = new HashSet<>();
        final String productTags = settings.getTags().get("PRODUCT_TAGS");
        if (productTags != null) {
            final String[] tags = productTags.split(";");
            for (int i = tags.length - 1; i >= 0; i--) {
                final String tag = tags[i];
                final String constraint = settings.getTags().get("MAX_ITEMS_" + tag);
                if (constraint == null) {
                    myLogger.warn("Ignoring tag " + tag + ": no setting found: MAX_ITEMS_" + tag);
                    continue;
                }
                try {
                    constraints.put(tag, new IntegerCounter(Integer.valueOf(constraint, 10)));
                }
                catch (NumberFormatException e) {
                    myLogger.warn("Ignoring tag " + tag + ": constraint MAX_ITEMS_" + tag + " not an integer: " + e.toString(), e);
                    continue;
                }
            }
        }

        final Recommendation[] recs = new Recommendation[n];

        final int[] availableIndices = new int[productListSize];
        for (int i = productListSize - 1; i >= 0; i--) {
            availableIndices[i] = i;
        }
        final int[] rejectedIndices = new int[productListSize];
        int rejectedCount = 0;
        int selectedCount = 0;
        final Random random = new Random();
        selectionLoop:
        while (selectedCount < n) {
            final int availableCount = productListSize - selectedCount - rejectedCount;
            if (availableCount <= 0) break;
            final int index = random.nextInt(availableCount);
            int c = 0;
            for (int i = 0; i < productListSize; i++) {
                final int j = availableIndices[i];
                if (c < index) {
                    if (j >= 0) c++;
                }
                else {
                    if (j >= 0) {
                        final VideoData p = candidateProducts[j];
                        availableIndices[i] = -1; // remove it from future choices
                        // check for blockers
                        for (final String blocker : blockers) {
                            if (p.tags.contains(blocker)) { // blocked
                                rejectedIndices[rejectedCount] = j;
                                rejectedCount++;
                                continue selectionLoop;
                            }
                        }
                        // adjust counters
                        for (final String tag : p.tags) {
                            final IntegerCounter counter = constraints.get(tag);
                            if (counter == null) continue;
                            if (counter.dec() == 0) {
                                constraints.remove(tag);
                                blockers.add(tag);
                            }
                        }
                        // add to result
                        recs[selectedCount] = new Recommendation(p.video.id, emptyRecommenderTags, 0.0, EXPLANATION_RANDOM, null);
                        selectedCount++;
                        break;
                    }
                }
            }
        }

        final int remainder = n - selectedCount;
        if (remainder > 0) {
            myLogger.warn("Cannot satisfy constraints, will choose " + remainder + " products that are in violation");
            for (int i = remainder - 1; i >= 0; i--) {
                final VideoData p = candidateProducts[rejectedIndices[i]];
                recs[selectedCount] = new Recommendation(p.video.id, emptyRecommenderTags, 0.0, EXPLANATION_RANDOM, null);
                selectedCount++;
            }
        }

        myLogger.info("recommend(): requested " + n + " recommendations, randomly chosen " + selectedCount + " from " + productListSize);
        return new Recommendations(recs.length > selectedCount ? recs : Arrays.copyOf(recs, selectedCount));
    }
}
