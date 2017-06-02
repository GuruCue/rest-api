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

import com.google.common.collect.ImmutableMap;
import com.gurucue.recommendations.blender.VideoData;
import com.gurucue.recommendations.data.AttributeCodes;
import com.gurucue.recommendations.data.DataManager;
import com.gurucue.recommendations.entity.product.TvProgrammeProduct;
import com.gurucue.recommendations.recommender.Recommendation;
import com.gurucue.recommendations.recommender.RecommendationSettings;
import com.gurucue.recommendations.recommender.Recommendations;
import com.gurucue.recommendations.rest.data.RequestLogger;
import gnu.trove.list.TIntList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.list.linked.TIntLinkedList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Returns a random choice of given products, to be displayed in a grid.
 * To be used in place of a real grid recommender.
 */
public final class RandomGridSelection {
    public static final RandomGridSelection INSTANCE = new RandomGridSelection(5);
    private static final String EXPLANATION_RANDOM = "Random";

    private final int lines;

    private RandomGridSelection(final int lines) {
        this.lines = lines;
    }

    public Recommendations recommendations(final RequestLogger logger, final RecommendationSettings settings, final VideoData[] candidateProducts) {
        final RequestLogger myLogger = logger.subLogger(getClass().getSimpleName());
        final AttributeCodes attributeCodes = DataManager.getAttributeCodes();
        final long beginTimeId = attributeCodes.idForBeginTime;
        final long endTimeId = attributeCodes.idForEndTime;
        final List<IntervalList> intervalLists = new ArrayList<>(lines);
        int i;
        for (i = lines; i > 0; i--) intervalLists.add(new IntervalList());
        final int maxProductCount = settings.maxResults;
        final int productsPerLine = maxProductCount / lines;
        final int productListSize = candidateProducts.length;
        if (maxProductCount >= productListSize) {
            // insufficient pool of products, copy the whole list
            for (int k = 0; k < productListSize; k++) addTvEntry(new TvEntry(candidateProducts[k], beginTimeId, endTimeId), intervalLists, productsPerLine);
        }
        else {
            // choose lines*n tv-programmes
            final Random random = new Random();
            final TIntList freeIndices = new TIntLinkedList(productListSize);
            for (i = 0; i < productListSize; i++) freeIndices.add(i); // fill the set of available indices
            int count = 0;
            while ((count < maxProductCount) && (freeIndices.size() > 0)) {
                final int pointerIndex = random.nextInt(freeIndices.size());
                final int chosenIndex = freeIndices.removeAt(pointerIndex);
                if (addTvEntry(new TvEntry(candidateProducts[chosenIndex], beginTimeId, endTimeId), intervalLists, productsPerLine)) count++;
            }
            myLogger.debug("Assembled a tv-grid pool of " + count + " non-overlapping tv-programmes to choose from");
        }
        // reshuffle the result into a flat list
        final Recommendation[] recs = new Recommendation[maxProductCount];
        final TIntList intervalIndices = new TIntArrayList(lines);
        for (i = intervalLists.size(); i > 0; i--) intervalIndices.add(0);
        int productCounter = 0;
        for (;;) {
            TvEntry entry = null;
            i = 0;
            int entryIndex = 0;
            // fetch the first entry available from iterators
            while (i < lines) {
                entryIndex = intervalIndices.get(i);
                final List<TvEntry> entries = intervalLists.get(i).entryList;
                if (entryIndex < entries.size()) {
                    entry = entries.get(entryIndex);
                    break;
                }
                i++;
            }
            if (entry == null) break; // no more entries
            // now compare with the rest of iterators and choose the minimum
            int gridLine = i;
            if (entry.beginTime >= 0) {
                // check if there is an entry on remaining lines that begins before this one
                i++;
                while (i < lines) {
                    final int otherEntryIndex = intervalIndices.get(i);
                    final List<TvEntry> entries = intervalLists.get(i).entryList;
                    final int otherN = entries.size();
                    TvEntry otherEntry = null;
                    int j = otherEntryIndex;
                    while (j < otherN) {
                        otherEntry = entries.get(j);
                        if (otherEntry.beginTime < 0L) {
                            // there is a non-TV-show, add and skip it immediately
                            recs[productCounter] = new Recommendation(otherEntry.data.video.id, ImmutableMap.of("virtual_channel", Integer.toString(i + 1, 10)), 0.0, EXPLANATION_RANDOM, null); // grid lines begin with 1
                            productCounter++;
                            j++;
                        }
                        else break;
                    }
                    if (j < otherN) {
                        if (otherEntry.beginTime < entry.beginTime) {
                            entry = otherEntry;
                            entryIndex = j;
                            gridLine = i;
                        }
                    }
                    if (j != otherEntryIndex) intervalIndices.set(i, j); // update the counter, skipping any non-TV-shows
                    i++;
                }
            }
            // increase the index counter on the chosen list
            intervalIndices.set(gridLine, entryIndex + 1);
            // add the product to the result
            recs[productCounter] = new Recommendation(entry.data.video.id, ImmutableMap.of("virtual_channel", Integer.toString(gridLine + 1, 10)), 0.0, EXPLANATION_RANDOM, null); // grid lines begin with 1
            productCounter++;
        }
        myLogger.info("recommendLiveTv(): requested for " + maxProductCount + " products, that is " + lines + " lines, each " + productsPerLine + " recommendations, returning: " + productCounter + " random recommendations");
        return new Recommendations(recs.length > productCounter ? recs : Arrays.copyOf(recs, productCounter));
    }

    private boolean addTvEntry(final TvEntry entry, final List<IntervalList> intervalLists, final int lineLimit) {
        for (int i = 0; i < intervalLists.size(); i++) {
            final IntervalList l = intervalLists.get(i);
            if (l.entryList.size() >= lineLimit) continue;
            if (l.insertTvEntry(entry)) return true;
        }
        return false;
    }

    private static class TvEntry {
        final VideoData data;
        final long beginTime;
        final long endTime;

        public TvEntry(final VideoData data, final long beginTimeId, final long endTimeId) {
            this.data = data;
            if (data.isTvProgramme) {
                final TvProgrammeProduct tvProgramme = (TvProgrammeProduct)data.video;
                beginTime = tvProgramme.endTimeMillis < 0L ? -1L : tvProgramme.beginTimeMillis; // to make sure that beginTime is negative if either one time is negative, this makes tests easy
                endTime = tvProgramme.endTimeMillis;
            }
            else {
                beginTime = -1L;
                endTime = -1L;
            }
        }
    }

    private static class IntervalList {
        final List<TvEntry> entryList = new ArrayList<>();

        public boolean insertTvEntry(final TvEntry tvEntry) {
            final int endIndex = entryList.size() - 1;
            int i = endIndex;
            if (tvEntry.beginTime >= 0L) {
                // the time interval is set, do an ordered insertion
                TvEntry otherTvEntry = null;
                while (i >= 0) {
                    otherTvEntry = entryList.get(i);
                    if (otherTvEntry.beginTime <= tvEntry.beginTime) break; // find the latest show that start before or at the same time as this show
                    i--;
                }
                if (i >= 0) { // if there is a previous show (this means it starts at the latest as this show):
                    if (tvEntry.beginTime < otherTvEntry.endTime) {
                        return false; // the show begins before the previous show ends, this means an overlap
                    }
                }
                if (i < endIndex) { // if there is a next show
                    for (int j = i + 1; j < endIndex; j++) {
                        // seek the next entry, that is a TV-show
                        otherTvEntry = entryList.get(j);
                        if (otherTvEntry.beginTime >= 0) {
                            if (tvEntry.endTime > otherTvEntry.beginTime) {
                                return false; // the show ends after the next show begins, this means an overlap
                            }
                            break; // no more searching necessary
                        }
                    }
                }
            }
            // no overlap, add the entry
            if (i == endIndex) {
                entryList.add(tvEntry);
            }
            else {
                entryList.add(i+1, tvEntry);
            }
            return true;
        }
    }
}
