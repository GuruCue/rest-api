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
package com.gurucue.recommendations.rest.data.processing.product;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.gurucue.recommendations.ProcessingException;
import com.gurucue.recommendations.ResponseException;
import com.gurucue.recommendations.ResponseStatus;
import com.gurucue.recommendations.Timer;
import com.gurucue.recommendations.Transaction;
import com.gurucue.recommendations.TransactionCloseJob;
import com.gurucue.recommendations.data.AttributeCodes;
import com.gurucue.recommendations.data.DataLink;
import com.gurucue.recommendations.data.DataManager;
import com.gurucue.recommendations.data.ProductTypeCodes;
import com.gurucue.recommendations.entity.Attribute;
import com.gurucue.recommendations.entity.Partner;
import com.gurucue.recommendations.entity.ProductType;
import com.gurucue.recommendations.entity.product.Matcher;
import com.gurucue.recommendations.entity.product.MatcherKey;
import com.gurucue.recommendations.entity.product.Product;
import com.gurucue.recommendations.entity.product.SeriesMatch;
import com.gurucue.recommendations.entity.product.TvProgrammeProduct;
import com.gurucue.recommendations.entity.product.VideoMatch;
import com.gurucue.recommendations.entity.value.AttributeValues;
import com.gurucue.recommendations.entity.value.LongValue;
import com.gurucue.recommendations.entity.value.MultiValue;
import com.gurucue.recommendations.entity.value.NullValue;
import com.gurucue.recommendations.entity.value.StringValue;
import com.gurucue.recommendations.entity.value.TimestampIntervalValue;
import com.gurucue.recommendations.entity.value.TranslatableValue;
import com.gurucue.recommendations.entity.value.Value;
import com.gurucue.recommendations.entitymanager.ProductManager;
import com.gurucue.recommendations.rest.data.DatabaseWorkerJob;
import com.gurucue.recommendations.rest.data.DatabaseWorkerThread;
import com.gurucue.recommendations.rest.data.RequestCache;
import com.gurucue.recommendations.rest.data.RequestLogger;
import com.gurucue.recommendations.rest.data.processing.EpisodeDetectionSlovene;
import com.gurucue.recommendations.type.ValueType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Handles video and tv-programme products.
 */
public final class VideoHandler implements ProductHandler {
    private static final Logger log = LogManager.getLogger(VideoHandler.class);
    private static final Set<Attribute> requiredVideoAttributes;
    private static final Set<Attribute> allowedVideoAttributes;
    private static final Set<Attribute> requiredTvProgrammeAttributes;
    private static final Set<Attribute> allowedTvProgrammeAttributes;
    static {
        final AttributeCodes attributeCodes = DataManager.getAttributeCodes();

        final ImmutableSet.Builder<Attribute> videoRequiredBuilder = ImmutableSet.builder();
        videoRequiredBuilder.add(attributeCodes.title);
        videoRequiredBuilder.add(attributeCodes.productionYear);
        requiredVideoAttributes = videoRequiredBuilder.build();

        final ImmutableSet.Builder<Attribute> videoAllowedBuilder = ImmutableSet.builder();
        videoAllowedBuilder.add(attributeCodes.actor);
        videoAllowedBuilder.add(attributeCodes.airDate);
        videoAllowedBuilder.add(attributeCodes.catalogueId);
        videoAllowedBuilder.add(attributeCodes.cataloguePrice); // TODO: deprecated, replaced with price
        videoAllowedBuilder.add(attributeCodes.country);
        videoAllowedBuilder.add(attributeCodes.description);
        videoAllowedBuilder.add(attributeCodes.director);
        videoAllowedBuilder.add(attributeCodes.episodeNumber);
        videoAllowedBuilder.add(attributeCodes.genre);
        videoAllowedBuilder.add(attributeCodes.imageUrl);
        videoAllowedBuilder.add(attributeCodes.imdbLink);
        videoAllowedBuilder.add(attributeCodes.imdbRating);
        videoAllowedBuilder.add(attributeCodes.isAdult);
        videoAllowedBuilder.add(attributeCodes.parentalRating);
        videoAllowedBuilder.add(attributeCodes.price);
        videoAllowedBuilder.add(attributeCodes.productionYear);
        videoAllowedBuilder.add(attributeCodes.runTime);
        videoAllowedBuilder.add(attributeCodes.seasonNumber);
        videoAllowedBuilder.add(attributeCodes.spokenLanguage);
        videoAllowedBuilder.add(attributeCodes.subtitleLanguage);
        videoAllowedBuilder.add(attributeCodes.title);
        videoAllowedBuilder.add(attributeCodes.title2);
        videoAllowedBuilder.add(attributeCodes.validity);
        videoAllowedBuilder.add(attributeCodes.videoCategory);
        videoAllowedBuilder.add(attributeCodes.videoFormat);
        videoAllowedBuilder.add(attributeCodes.vodCategory);
        allowedVideoAttributes = videoAllowedBuilder.build();

        final ImmutableSet.Builder<Attribute> tvProgrammeRequiredBuilder = ImmutableSet.builder();
        tvProgrammeRequiredBuilder.add(attributeCodes.beginTime);
        tvProgrammeRequiredBuilder.add(attributeCodes.endTime);
        tvProgrammeRequiredBuilder.add(attributeCodes.title);
        tvProgrammeRequiredBuilder.add(attributeCodes.tvChannel);
        requiredTvProgrammeAttributes = tvProgrammeRequiredBuilder.build();

        final ImmutableSet.Builder<Attribute> tvProgrammeAllowedBuilder = ImmutableSet.builder();
        tvProgrammeAllowedBuilder.add(attributeCodes.actor);
        tvProgrammeAllowedBuilder.add(attributeCodes.airDate);
        tvProgrammeAllowedBuilder.add(attributeCodes.beginTime);
        tvProgrammeAllowedBuilder.add(attributeCodes.country);
        tvProgrammeAllowedBuilder.add(attributeCodes.description);
        tvProgrammeAllowedBuilder.add(attributeCodes.director);
        tvProgrammeAllowedBuilder.add(attributeCodes.endTime);
        tvProgrammeAllowedBuilder.add(attributeCodes.episodeNumber);
        tvProgrammeAllowedBuilder.add(attributeCodes.genre);
        tvProgrammeAllowedBuilder.add(attributeCodes.imageUrl);
        tvProgrammeAllowedBuilder.add(attributeCodes.imdbLink);
        tvProgrammeAllowedBuilder.add(attributeCodes.imdbRating);
        tvProgrammeAllowedBuilder.add(attributeCodes.isAdult);
        tvProgrammeAllowedBuilder.add(attributeCodes.parentalRating);
        tvProgrammeAllowedBuilder.add(attributeCodes.productionYear);
        tvProgrammeAllowedBuilder.add(attributeCodes.runTime);
        tvProgrammeAllowedBuilder.add(attributeCodes.seasonNumber);
        tvProgrammeAllowedBuilder.add(attributeCodes.spokenLanguage);
        tvProgrammeAllowedBuilder.add(attributeCodes.subtitleLanguage);
        tvProgrammeAllowedBuilder.add(attributeCodes.title);
        tvProgrammeAllowedBuilder.add(attributeCodes.title2);
        tvProgrammeAllowedBuilder.add(attributeCodes.tvChannel);
        tvProgrammeAllowedBuilder.add(attributeCodes.videoCategory);
        tvProgrammeAllowedBuilder.add(attributeCodes.videoFormat);
        allowedTvProgrammeAttributes = tvProgrammeAllowedBuilder.build();
    }

    @Override
    public void handle(
            final RequestCache requestData,
            final Transaction transaction,
            final ProductOperation modificationType,
            final ProductType productType,
            final String partnerProductCode,
            final Map<Attribute, Value> removedValues,
            final Map<Attribute, Value> addedValues
    ) throws ResponseException {
        final Partner partner = requestData.getPartner();
        final DataLink link = transaction.getLink();
        final ProductManager pm = link.getProductManager();
        final RequestLogger logger = requestData.getLogger();
        final StringBuilder logBuilder = new StringBuilder(4096);

        if (modificationType == ProductOperation.DELETION) {
            pm.deleteByPartnerAndTypeAndCode(transaction, partner, productType, partnerProductCode);
            logBuilder.append("Deleted ").append(productType.getIdentifier()).append(" product with partner code ").append(partnerProductCode).append(" for partner ").append(partner.getUsername());
            logger.debug(logBuilder.toString());
            return; // and we're done
        }

        try {
            final AttributeCodes attributeCodes = DataManager.getAttributeCodes();
            final ProductTypeCodes productTypeCodes = DataManager.getProductTypeCodes();

            logBuilder.append("Performing a ").append(productType.getIdentifier());
            if (modificationType == ProductOperation.ADDITION) logBuilder.append(" addition");
            else if (modificationType == ProductOperation.MODIFICATION) logBuilder.append(" modification");
            else
                throw new ProcessingException(ResponseStatus.UNKNOWN_ERROR, "VideoHandler was invoked to do unknown type of processing: " + modificationType.toString());
            logBuilder.append(" for partner ").append(partner.getUsername()).append(" on product with partner code ").append(partnerProductCode);

            // ---------- basic processing ----------

            final Product existingProduct = pm.getProductByPartnerAndTypeAndCode(transaction, partner, productType, partnerProductCode, true);

            if (existingProduct == null) logBuilder.append("\n  No existing product found");
            else {
                logBuilder.append("\n  Found an existing product with ID ").append(existingProduct.id).append(" and added=");
                if (existingProduct.added == null) logBuilder.append("(null)");
                else logBuilder.append(existingProduct.added.toString());
                logBuilder.append(", deleted=");
                if (existingProduct.deleted == null) logBuilder.append("(null)");
                else logBuilder.append(existingProduct.deleted.toString());
                logBuilder.append(", related=");
                existingProduct.related.toJson(logBuilder);
                logBuilder.append(", attributes=");
                existingProduct.attributes.toJson(logBuilder);
            }

            AttributeValues newValues = (existingProduct == null) || (modificationType == ProductOperation.ADDITION) ? new AttributeValues(addedValues) : existingProduct.attributes.modify(addedValues, removedValues);
            if (productType.getId().longValue() == productTypeCodes.idForTvProgramme) {
                Processor.verifyAttributes(newValues.values, requiredTvProgrammeAttributes, allowedTvProgrammeAttributes, productType, partnerProductCode, (existingProduct == null) && (modificationType == ProductOperation.MODIFICATION));
            } else if (productType.getId().longValue() == productTypeCodes.idForVideo) {
                Processor.verifyAttributes(newValues.values, requiredVideoAttributes, allowedVideoAttributes, productType, partnerProductCode, (existingProduct == null) && (modificationType == ProductOperation.MODIFICATION));
            } else {
                throw new ProcessingException(ResponseStatus.UNKNOWN_ERROR, "VideoHandler was invoked to process a product of type " + productType.getIdentifier());
            }

            // ---------- attributes sanitizing ----------

            final Map<Attribute, Value> removeUnsanitizedValues = new HashMap<>();
            final Map<Attribute, Value> addSanitizedValues = new HashMap<>();

            // sanitize title for matching purposes, use title2 instead if appropriate
            Value aValue = addedValues.get(attributeCodes.title);
            if ((aValue == null) && (existingProduct != null))
                aValue = existingProduct.attributes.get(attributeCodes.title);
            Value aValue2 = addedValues.get(attributeCodes.title2);
            if ((aValue2 == null) && (existingProduct != null))
                aValue2 = existingProduct.attributes.get(attributeCodes.title2);
            final TranslatableValue rawTitle;
            try {
                rawTitle = (TranslatableValue) aValue;
            } catch (ClassCastException e) {
                throw new ProcessingException(ResponseStatus.UNKNOWN_ERROR, "The title is not a TranslatableValue but a " + aValue.getClass().getCanonicalName() + ": " + e.toString(), e);
            }
            final TranslatableValue rawTitle2;
            try {
                rawTitle2 = (TranslatableValue) aValue2;
            } catch (ClassCastException e) {
                throw new ProcessingException(ResponseStatus.UNKNOWN_ERROR, "The title2 is not a TranslatableValue but a " + aValue.getClass().getCanonicalName() + ": " + e.toString(), e);
            }
            TitleSanitizer titleSanitizer = TitleSanitizer.sanitize(rawTitle, rawTitle2);
            TranslatableValue sanitizedTitle = titleSanitizer.sanitizedValue; // use the sanitized title for video and series matchers
            boolean isHD = titleSanitizer.isHD;
            logBuilder.append("\n  original title:  ");
            rawTitle.toString(logBuilder);
            if (rawTitle2 != null) {
                logBuilder.append("\n  original title2: ");
                rawTitle2.toString(logBuilder);
            }
            logBuilder.append("\n  sanitized title: ");
            sanitizedTitle.toString(logBuilder);

            EpisodeDetectionSlovene seriesDetection = EpisodeDetectionSlovene.detect(sanitizedTitle, newValues);
            if (seriesDetection != null) {
                logBuilder.append("\n  detected series parameters from the sanitized title:  ");
                seriesDetection.toString(logBuilder);
            }

            if (isHD) {
                // sanitizer recognized HD from the title
                final String existingFormat = newValues.getAsString(attributeCodes.videoFormat);
                if ((existingFormat == null) || !"hd".equalsIgnoreCase(existingFormat)) {
                    addSanitizedValues.put(attributeCodes.videoFormat, new StringValue("hd"));
                    logBuilder.append("\n  injecting attribute value: video-format=\"hd\"");
                }
            }

            // remap catalogue-price to price
            final Value cataloguePrice = addedValues.get(attributeCodes.cataloguePrice);
            if (cataloguePrice != null) {
                // replace catalogue-price with price
                removeUnsanitizedValues.put(attributeCodes.cataloguePrice, cataloguePrice);
                addSanitizedValues.put(attributeCodes.price, cataloguePrice);
                logBuilder.append("\n  renaming attribute \"catalogue-price\" to \"price\"");
            }

            // merge overlapping validities
            final TimestampIntervalValue[] newValidities;
            final TimestampIntervalValue[] oldValidities;
            TimestampIntervalValue[] processedValidities;
            final Value validityValue = newValues.get(attributeCodes.validity);
            if ((validityValue != null) && validityValue.isArray && (validityValue.valueType == ValueType.TIMESTAMP_INTERVAL)) {
                newValidities = validityValue.asTimestampIntervals();
            } else newValidities = null;
            if ((existingProduct != null) && (modificationType == ProductOperation.ADDITION)) {
                // retain previous validities even in a replace operation, but cut them off at the now(); this is needed to correctly perform analyses on past recommendations
                final Value oldValidityValue = existingProduct.attributes.get(attributeCodes.validity);
                if ((oldValidityValue != null) && oldValidityValue.isArray && (oldValidityValue.valueType == ValueType.TIMESTAMP_INTERVAL)) {
                    final TimestampIntervalValue[] vals = validityValue.asTimestampIntervals();
                    final ArrayList<TimestampIntervalValue> a = new ArrayList<>(vals.length);
                    final long now = Timer.currentTimeMillis();
                    for (int i = 0; i < vals.length; i++) {
                        final TimestampIntervalValue val = vals[i];
                        if (val.beginMillis >= now) continue; // don't retain a validity that begins in the future
                        if (val.endMillis <= now) a.add(val); // retain the validity that began and ended in the past
                        else
                            a.add(TimestampIntervalValue.fromMillis(val.beginMillis, now)); // cut off the validity that began in the past, but ends in the future
                    }
                    if (a.isEmpty()) oldValidities = null;
                    else {
                        oldValidities = a.toArray(new TimestampIntervalValue[a.size()]);
                        logBuilder.append("\n  retaining (some of) the previous validities: ");
                        final Iterator<TimestampIntervalValue> iterator = a.iterator();
                        if (iterator.hasNext()) {
                            iterator.next().toString(logBuilder);
                            while (iterator.hasNext()) {
                                logBuilder.append(", ");
                                iterator.next().toString(logBuilder);
                            }
                        }
                    }
                } else oldValidities = null;
            } else oldValidities = null;
            if (oldValidities == null) {
                if ((newValidities != null) && (newValidities.length > 1)) {
                    processedValidities = TimestampIntervalValue.cleanupIntervals(newValidities);
                    if (processedValidities != newValidities) {
                        logBuilder.append("\n  original validities:   ");
                        newValidities[0].toString(logBuilder);
                        for (int i = 1; i < newValidities.length; i++) newValidities[i].toString(logBuilder);
                        logBuilder.append("\n  cleaned-up validities: ");
                        if ((processedValidities != null) && (processedValidities.length > 0)) {
                            processedValidities[0].toString(logBuilder);
                            for (int i = 1; i < processedValidities.length; i++)
                                processedValidities[i].toString(logBuilder);
                        } else logBuilder.append("(none)");
                    }
                } else processedValidities = newValidities;
            } else {
                processedValidities = TimestampIntervalValue.mergeIntervals(newValidities, oldValidities);
                logBuilder.append("\n  original validities: ");
                if ((newValidities != null) && (newValidities.length > 0)) {
                    newValidities[0].toString(logBuilder);
                    for (int i = 1; i < newValidities.length; i++) newValidities[i].toString(logBuilder);
                } else logBuilder.append("(none)");
                logBuilder.append("\n  merged validities:   ");
                if ((processedValidities != null) && (processedValidities.length > 0)) {
                    processedValidities[0].toString(logBuilder);
                    for (int i = 1; i < processedValidities.length; i++) processedValidities[i].toString(logBuilder);
                } else logBuilder.append("(none)");
            }

            if (processedValidities != newValidities) {
                // some merging has occurred
                removeUnsanitizedValues.put(attributeCodes.validity, NullValue.INSTANCE); // remove all validities, so the fresh ones can fully replace them
                if ((processedValidities != null) && (processedValidities.length > 0)) {
                    addSanitizedValues.put(attributeCodes.validity, new MultiValue(processedValidities));
                }
            }

            // apply any fixes
            if (!removeUnsanitizedValues.isEmpty() || !addSanitizedValues.isEmpty()) {
                newValues = newValues.modify(addSanitizedValues, removeUnsanitizedValues);
            }

            // ---------- video-id and series-id detection ----------

            final ImmutableMap.Builder<Attribute, Value> relatedRemoveBuilder = ImmutableMap.builder();
            final ImmutableMap.Builder<Attribute, Value> relatedAddBuilder = ImmutableMap.builder();

            final ImmutableMap.Builder<Attribute, Value> videoMatchAttributesBuilder = ImmutableMap.builder();
            final AttributeValues videoMatchRelated;

            videoMatchAttributesBuilder.put(attributeCodes.title, sanitizedTitle);
            final Value productionYearValue = newValues.get(attributeCodes.productionYear);
            if (productionYearValue != null) videoMatchAttributesBuilder.put(attributeCodes.productionYear, productionYearValue);

            // first detect series
            if (seriesDetection != null) {
                // get/create the SeriesMatch first
                SeriesMatch seriesMatch = SeriesMatch.create(attributeCodes, productTypeCodes.idForSeries, seriesDetection.seriesTitle);
                // retrieve matcher from db layer: if it doesn't exist: for update; if it exists: read-only
                //   -> getOrCreate()
                seriesMatch = (SeriesMatch) getMatcher(attributeCodes.seriesId, transaction, attributeCodes, pm, seriesMatch, logBuilder);

                videoMatchRelated = new AttributeValues(ImmutableMap.<Attribute, Value>of(attributeCodes.seriesId, new LongValue(seriesMatch.id, false)));
                relatedAddBuilder.put(attributeCodes.seriesId, new LongValue(seriesMatch.id, false));
                logBuilder.append("\n  setting related series-id=").append(seriesMatch.id);

                final Map<Attribute, Value> valuesToAdd = new HashMap<>(5);
                final Map<Attribute, Value> valuesToRemove = new HashMap<>(5);

                fixLongValue(logBuilder, false, attributeCodes.episodeNumber, newValues.get(attributeCodes.episodeNumber), seriesDetection.episodeNumber, valuesToAdd, valuesToRemove, videoMatchAttributesBuilder);
                fixLongValue(logBuilder, false, attributeCodes.seasonNumber, newValues.get(attributeCodes.seasonNumber), seriesDetection.seasonNumber, valuesToAdd, valuesToRemove, videoMatchAttributesBuilder);
                fixLongValue(logBuilder, true, attributeCodes.airDate, newValues.get(attributeCodes.airDate), seriesDetection.airDate, valuesToAdd, valuesToRemove, videoMatchAttributesBuilder);

                // add the new series attribute-values
                if (!valuesToAdd.isEmpty() || !valuesToRemove.isEmpty()) {
                    newValues = newValues.modify(valuesToAdd, valuesToRemove);
                }
            } else {
                videoMatchRelated = AttributeValues.NO_VALUES;
                relatedRemoveBuilder.put(attributeCodes.seriesId, NullValue.INSTANCE);
            }

            final VideoMatch videoMatch = (VideoMatch) getMatcher(attributeCodes.videoId, transaction, attributeCodes, pm, new VideoMatch(attributeCodes, 0L, productTypeCodes.idForVideo, Partner.PARTNER_ZERO_ID, null, null, null, null, new AttributeValues(videoMatchAttributesBuilder.build()), videoMatchRelated), logBuilder);
            relatedAddBuilder.put(attributeCodes.videoId, new LongValue(videoMatch.id, false));
            logBuilder.append("\n  setting related video-id=").append(videoMatch.id);

            final AttributeValues newRelated = existingProduct == null ? new AttributeValues(relatedAddBuilder.build()) : existingProduct.related.modify(relatedAddBuilder.build(), relatedRemoveBuilder.build());

            // ---------- saving to database ----------

            if ((existingProduct == null) || !(newValues.equals(existingProduct.attributes) && newRelated.equals(existingProduct.related))) {
                // do the modification only if there's actually something to modify
                final Product newProduct;
                if (existingProduct == null)
                    newProduct = Product.create(0L, productType.getId(), partner.getId(), partnerProductCode, null, null, null, newValues, newRelated, link.getProvider());
                else
                    newProduct = Product.create(existingProduct.id, productType.getId(), partner.getId(), partnerProductCode, existingProduct.added, existingProduct.modified, existingProduct.deleted, newValues, newRelated, link.getProvider());
                final Product savedProduct = pm.save(transaction, newProduct);

                if (existingProduct == null)
                    logBuilder.append("\n  saved a new Product to the database with ID ").append(savedProduct.id);
                else
                    logBuilder.append("\n  modified an existing Product in the database with ID ").append(savedProduct.id);

                if (productType.getId().longValue() == productTypeCodes.idForTvProgramme) {
                    try {
                        final TvProgrammeProduct newTvProgramme = (TvProgrammeProduct) savedProduct;
                        if (existingProduct == null) {
                            DatabaseWorkerThread.INSTANCE.addJob(new OverlapRemovalJob(logger, partner, newTvProgramme));
                            logBuilder.append("\n  scheduling overlapped tv-programmes removal because this is a new tv-programme");
                        } else {
                            final TvProgrammeProduct existingTvProgramme = (TvProgrammeProduct) existingProduct;
                            if ((existingTvProgramme.beginTimeMillis != newTvProgramme.beginTimeMillis) || (existingTvProgramme.endTimeMillis != newTvProgramme.endTimeMillis)) {
                                DatabaseWorkerThread.INSTANCE.addJob(new OverlapRemovalJob(logger, partner, newTvProgramme));
                                logBuilder.append("\n  scheduling overlapped tv-programmes removal because begin-time and/or end-time changed");
                            }
                        }
                    } catch (ClassCastException e) {
                        logger.error("Failed to cast a tv-programme to a TvProgrammeProduct, no overlapping tv-programmes removed: " + e.toString(), e);
                    }
                }
            } else {
                logBuilder.append("\n  no change in values detected, no operation performed in the database");
            }
            logger.debug(logBuilder.toString());
        }
        catch (ResponseException|RuntimeException e) {
            logger.error("Exception occurred while processing a product, below are processing descriptions: " + e.toString() + "\n" + logBuilder.toString(), e);
            throw e;
        }
    }

    /**
     * Attempts to return a unique matcher for the candidate matcher. Only
     * a complete match, where all keys match, is considered a (successful) match.
     * The method obtains a list of matchers for each matcher key. If there is
     * a list with only one matcher, then the matcher is considered as unique
     * and returned. Otherwise if there is an empty list, then the method
     * saves (creates) the given candidate matcher and returns the new
     * (cloned, having a real ID) matcher. In theory, if only this algorithm
     * is employed, no other outcome is possible, but we nonetheless detect
     * the situation where there are obtained lists of matchers where each
     * list contains more than one element. In such a case an error is logged
     * and the first matcher from a list with least entries is returned
     * (maximum specificity).
     *
     * @param transaction
     * @param attributeCodes
     * @param pm
     * @param templateMatcher
     * @param logBuilder
     * @return
     */
    protected Matcher getMatcher(final Attribute relatedAttribute, final Transaction transaction, final AttributeCodes attributeCodes, final ProductManager pm, final Matcher templateMatcher, final StringBuilder logBuilder) {
        final Set<Matcher> duplicates = new HashSet<>();
        Matcher candidateMatcher = templateMatcher;
        Matcher originalMatcher = null;
        for (final MatcherKey key : templateMatcher.getKeys()) {
            final Set<Matcher> otherMatchers = pm.getMatchers(transaction, key);
            if ((otherMatchers != null) && !otherMatchers.isEmpty()) {
                for (final Matcher otherMatcher : otherMatchers) {
                    final Matcher mergeResult = candidateMatcher.merge(otherMatcher, attributeCodes);
                    if (mergeResult == null) continue; // merge not possible
                    if (mergeResult.id == otherMatcher.id) {
                        if (candidateMatcher.id != 0L) duplicates.add(candidateMatcher); // candidateMatcher could be a new instance, but otherMatcher cannot be, because it was obtained from the database
                        originalMatcher = otherMatcher;
                    }
                    else if (mergeResult.id == candidateMatcher.id) {
                        duplicates.add(otherMatcher);
                        // original matcher stays the same
                    }
                    else {
                        if (candidateMatcher.id != 0L) duplicates.add(candidateMatcher);
                        duplicates.add(otherMatcher);
                        originalMatcher = null;
                    }
                    candidateMatcher = mergeResult;
                }
            }
        }

        // save any modifications
        if (candidateMatcher != originalMatcher) {
            candidateMatcher = (Matcher)pm.save(transaction, candidateMatcher);
            if (originalMatcher == null) {
                logBuilder.append("\n    creating a new matcher for ").append(relatedAttribute.getIdentifier()).append("=").append(candidateMatcher.id).append(" with related=");
                candidateMatcher.related.toJson(logBuilder);
                logBuilder.append(", attributes=");
                candidateMatcher.attributes.toJson(logBuilder);
            }
            else {
                logBuilder.append("\n    updating the existing matcher for ").append(relatedAttribute.getIdentifier()).append("=").append(candidateMatcher.id);
                logBuilder.append("\n      previous related=");
                originalMatcher.related.toJson(logBuilder);
                logBuilder.append(", attributes=");
                originalMatcher.attributes.toJson(logBuilder);
                logBuilder.append("\n      updated related=");
                candidateMatcher.related.toJson(logBuilder);
                logBuilder.append(", attributes=");
                candidateMatcher.attributes.toJson(logBuilder);
            }
        }

        // remove duplicates, and relink all dependencies to the new ID
        if (!duplicates.isEmpty()) {
            final long targetRelatedId = candidateMatcher.id;
            final Iterator<Matcher> it = duplicates.iterator();
            final long firstMatcherRelatedId = it.next().id;
            if ((firstMatcherRelatedId != targetRelatedId) || it.hasNext()) {
                final List<RelatedIdModifierJob> modifierJobs = new ArrayList<>(duplicates.size());
                final LongValue targetValue = new LongValue(targetRelatedId, false);
                logBuilder.append("\n    enqueueing change of ")
                        .append(relatedAttribute.getIdentifier())
                        .append("=")
                        .append(targetRelatedId)
                        .append(" for entities having ")
                        .append(relatedAttribute.getIdentifier())
                        .append("s of merged matchers:");
                if (firstMatcherRelatedId != targetRelatedId) {
                    logBuilder.append(" ").append(firstMatcherRelatedId);
                    modifierJobs.add(new RelatedIdModifierJob(relatedAttribute, firstMatcherRelatedId, targetValue));
                    pm.delete(transaction, firstMatcherRelatedId);
                }
                while (it.hasNext()) {
                    final long relatedId = it.next().id;
                    if (relatedId != targetRelatedId) {
                        logBuilder.append(" ").append(relatedId);
                        modifierJobs.add(new RelatedIdModifierJob(relatedAttribute, relatedId, targetValue));
                        pm.delete(transaction, relatedId);
                    }
                }
                transaction.onTransactionClose(new TransactionCloseJob() {
                    @Override
                    public void commit() {
                        for (RelatedIdModifierJob job : modifierJobs) {
                            DatabaseWorkerThread.INSTANCE.addJob(job);
                        }
                    }

                    @Override
                    public void rollback() {
                        // no-op
                    }
                });
            }
        }

        return candidateMatcher;
    }

    private static void fixLongValue(final StringBuilder logBuilder, final boolean isTimestamp, final Attribute attribute, final Value provided, final LongValue detected, final Map<Attribute, Value> toAdd, final Map<Attribute, Value> toRemove, final ImmutableMap.Builder<Attribute, Value> matchBuilder) {
        if (provided != null) {
            if (provided instanceof LongValue) {
                matchBuilder.put(attribute, provided);
                logBuilder.append("\n  using provided attribute value: ").append(attribute.getIdentifier()).append("=").append(provided.asInteger());
            }
            else {
                final long i = provided.asInteger();
                if (i <= 0L) {
                    logBuilder.append("\n  the provided ").append(attribute.getIdentifier()).append(" is not an integer, throwing it away: ").append(provided.asString());
                    if (detected != null) {
                        matchBuilder.put(attribute, detected);
                        toAdd.put(attribute, detected);
                        logBuilder.append("\n  injecting attribute value: ").append(attribute.getIdentifier()).append("=").append(detected.asInteger());
                    }
                    else {
                        toRemove.put(attribute, NullValue.INSTANCE);
                    }
                }
                else {
                    final LongValue newValue = new LongValue(i, isTimestamp);
                    matchBuilder.put(attribute, newValue);
                    toAdd.put(attribute, newValue);
                    logBuilder.append("\n  using provided attribute value (converted to integer): ").append(attribute.getIdentifier()).append("=").append(newValue.asInteger());
                }
            }
        }
        else if (detected != null) {
            matchBuilder.put(attribute, detected);
            toAdd.put(attribute, detected);
            logBuilder.append("\n  injecting attribute value: ").append(attribute.getIdentifier()).append("=").append(detected.asInteger());
        }
    }

    // TODO: do not remove Product instances that overlap, instead just remove the tv-channel code from their attributes,
    // TODO: so we gain the proper multi-tv-channel support for tv-programmes
    public static class OverlapRemovalJob extends DatabaseWorkerJob {
        private final RequestLogger logger;
        private final Partner partner;
        private final TvProgrammeProduct tvProgramme;

        OverlapRemovalJob(final RequestLogger logger, final Partner partner, final TvProgrammeProduct tvProgramme) {
            this.logger = logger;
            this.partner = partner;
            this.tvProgramme = tvProgramme;
        }

        @Override
        public void execute(final Transaction transaction) {
            final List<Product> removed = transaction.getLink().getProductManager().removeTvProgrammesOverlappingInterval(transaction, tvProgramme);
            if (logger.isDebugEnabled()) {
                final StringBuilder sb = new StringBuilder();
                sb.append("[Database Worker Thread] Removed ");
                sb.append(removed.size());
                sb.append(" overlapping products for TV-programme ");
                sb.append(tvProgramme.id);
                sb.append(" on TV-channel(s) ");
                int i = tvProgramme.tvChannelCodes.length - 1;
                if (i < 0) sb.append("[no channels defined]");
                else {
                    sb.append(tvProgramme.tvChannelCodes[i]);
                    i--;
                    while (i >= 0) {
                        sb.append(", ");
                        sb.append(tvProgramme.tvChannelCodes[i]);
                        i--;
                    }
                }
                sb.append(" between ");
                sb.append(tvProgramme.beginTimeMillis / 1000L);
                sb.append(" and ");
                sb.append(tvProgramme.endTimeMillis / 1000L);
                if (removed.size() > 0) {
                    sb.append(":");
                    final Iterator<Product> productIterator = removed.iterator();
                    while (productIterator.hasNext()) {
                        sb.append("\n    ");
                        sb.append(productIterator.next().toString());
                    }
                }
                logger.debug(sb.toString());
            }
        }
    }

    public static class RelatedIdModifierJob extends DatabaseWorkerJob {
        private final Attribute attribute;
        private final long oldId;
        private final LongValue newValue;

        RelatedIdModifierJob(final Attribute attribute, final long oldId, final LongValue newValue) {
            this.attribute = attribute;
            this.oldId = oldId;
            this.newValue = newValue;
        }

        @Override
        public void execute(final Transaction transaction) {
            log.debug("modifying products' related field " + attribute.getIdentifier() + ": " + oldId + " -> " + newValue.value);
            final ProductManager pm = transaction.getLink().getProductManager();
            pm.setRelatedFieldForAll(transaction, attribute, new LongValue(oldId, false), newValue);
        }
    }
}
