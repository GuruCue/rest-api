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
package com.gurucue.recommendations.rest.data.response;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.google.common.collect.ImmutableSet;
import com.gurucue.recommendations.blender.DataSet;
import com.gurucue.recommendations.blender.TvChannelData;
import com.gurucue.recommendations.blender.VideoData;
import com.gurucue.recommendations.data.AttributeCodes;
import com.gurucue.recommendations.entity.Attribute;
import com.gurucue.recommendations.entity.Language;
import com.gurucue.recommendations.entity.Partner;
import com.gurucue.recommendations.entity.ProductType;
import com.gurucue.recommendations.entity.product.PackageProduct;
import com.gurucue.recommendations.entity.product.TvProgrammeProduct;
import com.gurucue.recommendations.entity.product.VideoProduct;
import com.gurucue.recommendations.entity.value.BooleanValue;
import com.gurucue.recommendations.entity.value.MultiValue;
import com.gurucue.recommendations.entity.value.StringValue;
import com.gurucue.recommendations.entity.value.TimestampIntervalValue;
import com.gurucue.recommendations.entity.value.TranslatableValue;
import com.gurucue.recommendations.entity.value.Value;
import com.gurucue.recommendations.rest.data.RequestCache;
import com.gurucue.recommendations.rest.data.RequestLogger;
import com.gurucue.recommendations.translator.DataTranslator;
import com.gurucue.recommendations.ResponseStatus;
import com.gurucue.recommendations.translator.TranslatorAware;
import gnu.trove.map.TLongObjectMap;
import gnu.trove.map.hash.TLongObjectHashMap;

/**
 * Output abstraction class for the recommendations web service controller.
 */
public class MovieRecommendationsResponse extends RestResponse {
    private final ArrayList<TranslatorAware> data = new ArrayList<TranslatorAware>();

    public MovieRecommendationsResponse(final Integer resultCode, final String resultMessage) {
        super(resultCode, resultMessage);
    }

    public MovieRecommendationsResponse(final ResponseStatus status) {
        super(status);
    }

    public void addProduct(final String productType, final String partnerProductCode, final Map<Attribute, Value> attributeValues, final Map<String, Float> explanations) {
        data.add(new Data(productType, partnerProductCode, attributeValues, -1, explanations));
    }

    public void addProduct(final String productType, final String partnerProductCode, final Map<Attribute, Value> attributeValues, final Integer gridLine, final Map<String, Float> explanations) {
        data.add(new Data(productType, partnerProductCode, attributeValues, gridLine, explanations));
    }

    @Override
    protected void translateRest(final DataTranslator translator) throws IOException {
        translator.addKeyValue("recommendations", data);
        final StringBuilder logBuilder = new StringBuilder(data.size() * 50);
        logBuilder.append("Weighted explanations per recommendation:");
        final Iterator<TranslatorAware> it = data.iterator();
        while (it.hasNext()) {
            final Data data = (Data)it.next();
            logBuilder.append("\n  ").append(data.productType).append(" ").append(data.partnerProductCode).append(": ");
            if ((data.explanations == null) || (data.explanations.isEmpty())) logBuilder.append("none");
            else {
                final Iterator<TranslatorAware> jt = data.explanations.iterator();
                if (jt.hasNext()) {
                    Explanation e = (Explanation)jt.next();
                    logBuilder.append("[").append(e.weight).append(" \"").append(e.value).append("\"]");
                    while (jt.hasNext()) {
                        e = (Explanation)jt.next();
                        logBuilder.append(", [").append(e.weight).append(" \"").append(e.value).append("\"]");
                    }
                }
            }
        }
        RequestCache.get().getLogger().subLogger(MovieRecommendationsResponse.class.getSimpleName()).debug(logBuilder.toString());
    }

    public static MovieRecommendationsResponse fromDataSet(final DataSet<VideoData> dataSet, final String responseMessage, final AttributeCodes attributeCodes, final RequestLogger logger, final Partner partner) {
        final MovieRecommendationsResponse response = new MovieRecommendationsResponse(ResponseStatus.OK.getCode(), responseMessage);
        dataSet.forEach((final VideoData videoData) -> {
            final Map<Attribute, Value> outputValues = new HashMap<>();
            final ImmutableSet<Attribute> matchedAttributes = videoData.rank == null ? null : videoData.rank.getMatchedAttributes();
            if ((matchedAttributes != null) && !matchedAttributes.isEmpty()) {
                final Value[] matches = new Value[matchedAttributes.size()];
                int i = 0;
                for (final Attribute a : matchedAttributes) {
                    matches[i++] = new StringValue(a.getIdentifier());
                }
                outputValues.put(attributeCodes.matchedAttribute, new MultiValue(matches));
            }
            if (videoData.isTvProgramme) {
                final TvProgrammeProduct tvProgramme = (TvProgrammeProduct) videoData.video;
                if (videoData.chosenTvChannels == null) {
                    logger.error("the list of chosen TV channels for tv-programme " + tvProgramme.id + " (partner=" + partner.getUsername() + ", code=" + tvProgramme.partnerProductCode + ") is null");
                    return;
                }
                if (videoData.chosenTvChannels.isEmpty()) {
                    logger.error("the list of chosen TV channels for tv-programme " + tvProgramme.id + " (partner=" + partner.getUsername() + ", code=" + tvProgramme.partnerProductCode + ") is empty");
                    return;
                }
                TvChannelData tvChannelData = videoData.chosenTvChannels.iterator().next(); // get the first TV-channel
                final Map<Attribute, Value> inputValues = tvProgramme.attributes.values;

                outputValues.put(attributeCodes.tvChannel, new StringValue(tvChannelData.tvChannel.partnerProductCode));
                outputValues.put(attributeCodes.beginTime, inputValues.get(attributeCodes.beginTime));
                outputValues.put(attributeCodes.endTime, inputValues.get(attributeCodes.endTime));
                final Value title = inputValues.get(attributeCodes.title);
                if (title != null) {
                    outputValues.put(attributeCodes.title, title);
                }
                outputValues.put(attributeCodes.isSubscribed, new BooleanValue(videoData.isSubscribed)); // this is a pseudo-attribute, thus the original product does not contain it
                if (!videoData.isSubscribed) {
                    // if the consumer is not subscribed, supply additional attributes to the client
                    final Attribute packageId = attributeCodes.packageId;
                    final List<TvChannelData> tvChannels = videoData.availableTvChannels;
                    if (tvChannels.size() == 1) {
                        // optimization for the case of a single tv-channel: simple enumeration
                        final List<PackageProduct> packages = tvChannels.get(0).productPackages;
                        if ((packages != null) && (packages.size() > 0)) {
                            final Value[] packageValues = new Value[packages.size()];
                            for (int i = packageValues.length - 1; i >= 0; i--) {
                                packageValues[i] = new StringValue(packages.get(i).partnerProductCode);
                            }
                            outputValues.put(attributeCodes.packageId, new MultiValue(packageValues));
                        }
                    } else if (tvChannels.size() > 1) {
                        // de-duplicate packages first, then enumerate
                        final TLongObjectMap<Value> packageMap = new TLongObjectHashMap<>();
                        for (final TvChannelData tvData : tvChannels) {
                            for (final PackageProduct p : tvData.productPackages) {
                                packageMap.put(p.id, new StringValue(p.partnerProductCode));
                            }
                        }
                        if (packageMap.size() > 0) {
                            outputValues.put(attributeCodes.packageId, new MultiValue(packageMap.values(new Value[packageMap.size()])));
                        }
                    }

                    final Value description = inputValues.get(attributeCodes.description);
                    if (description != null) {
                        outputValues.put(attributeCodes.description, description);
                    }
                    final Value imageUrl = inputValues.get(attributeCodes.imageUrl);
                    if (imageUrl != null) {
                        outputValues.put(attributeCodes.imageUrl, imageUrl);
                    }
                }
                outputValues.put(attributeCodes.isSeries, new BooleanValue(tvProgramme.seriesId > 0L)); // this is a pseudo-attribute, thus the original product does not contain it

                response.addProduct(ProductType.TV_PROGRAMME, tvProgramme.partnerProductCode, outputValues, videoData.gridLine, videoData.prettyExplanations);
            } else {
                final VideoProduct videoProduct = (VideoProduct) videoData.video;
                final Map<Attribute, Value> inputValues = videoProduct.attributes.values;

                outputValues.put(attributeCodes.isSubscribed, new BooleanValue(videoData.isSubscribed)); // this is a pseudo-attribute, thus the original product does not contain it
                final Value catalogueId = inputValues.get(attributeCodes.catalogueId);
                if (catalogueId != null) {
                    outputValues.put(attributeCodes.catalogueId, catalogueId);
                }
                final Value price = inputValues.get(attributeCodes.price);
                if (price != null) {
                    outputValues.put(attributeCodes.price, price);
                }
                if (!videoData.isSubscribed) { // TODO: defer package selection to Dataset.recommend()
                    // if the consumer is not subscribed, supply additional attributes to the client
                    if ((videoData.productPackages != null) && (!videoData.productPackages.isEmpty())) {
                        final Value[] packageValues = new Value[videoData.productPackages.size()];
                        for (int i = packageValues.length - 1; i >= 0; i--) {
                            packageValues[i] = new StringValue(videoData.productPackages.get(i).partnerProductCode);
                        }
                        outputValues.put(attributeCodes.packageId, new MultiValue(packageValues));
                    }
                    final Value title = inputValues.get(attributeCodes.title);
                    if (title != null) {
                        outputValues.put(attributeCodes.title, title);
                    }

                    final Value runTime = inputValues.get(attributeCodes.runTime);
                    if (runTime != null) {
                        outputValues.put(attributeCodes.runTime, runTime);
                    }
                    final Value description = inputValues.get(attributeCodes.description);
                    if (description != null) {
                        outputValues.put(attributeCodes.description, description);
                    }
                    final Value imageUrl = inputValues.get(attributeCodes.imageUrl);
                    if (imageUrl != null) {
                        outputValues.put(attributeCodes.imageUrl, imageUrl);
                    }
                }
                outputValues.put(attributeCodes.isSeries, new BooleanValue(videoProduct.seriesId > 0L)); // this is a pseudo-attribute, thus the original product does not contain it

                response.addProduct(ProductType.VIDEO, videoProduct.partnerProductCode, outputValues, videoData.gridLine, videoData.prettyExplanations);
            }
        });
        return response;
    }

    static final class Data implements TranslatorAware {
        final String productType;
        final String partnerProductCode;
        final Integer gridLine;
        final ArrayList<TranslatorAware> attributes = new ArrayList<>();
        final List<TranslatorAware> explanations;

        Data (final String productType, final String partnerProductCode/*, final Double estimatedRating*/, final Map<Attribute, Value> attributeValues, final Integer gridLine, final Map<String, Float> explanations) {
            this.productType = productType;
            this.partnerProductCode = partnerProductCode;
            this.gridLine = gridLine;
            if (attributeValues != null) {
                for (final Map.Entry<Attribute, Value> entry : attributeValues.entrySet()) {
                    final Attribute a = entry.getKey();
                    final Value v = entry.getValue();
                    if (v.isArray) {
                        final MultiValue m = (MultiValue)v;
                        for (final Value vv : m.values) attributes.add(new AttributeValue(a, vv));
                    }
                    else attributes.add(new AttributeValue(a, v));
                }
            }
            if ((explanations == null) || explanations.isEmpty()) this.explanations = null;
            else { // TODO: optimize the sorting below
                final List<Explanation> list = new ArrayList<>(explanations.size());
                for (final Map.Entry<String, Float> entry : explanations.entrySet()) list.add(new Explanation(entry.getKey(), entry.getValue()));
                Collections.sort(list, ExplanationComparator.INSTANCE);
                this.explanations = new ArrayList<>(list.size());
                for (final Explanation e : list) this.explanations.add(e);
            }
        }

        @Override
        public void translate(final DataTranslator translator) throws IOException {
            translator.beginObject("recommendation");
            translator.addKeyValue("type", productType);
            translator.addKeyValue("id", partnerProductCode);
            if (gridLine >= 0) {
                translator.addKeyValue("gridLine", gridLine);
            }
            if (attributes.size() > 0) {
                translator.addKeyValue("attributes", attributes);
            }
            if ((explanations != null) && (!explanations.isEmpty())) {
                translator.addKeyValue("explanations", explanations);
            }
            translator.endObject();
        }
    }

    static final class AttributeValue implements TranslatorAware {
        final Attribute attribute;
        final Value value;

        AttributeValue(final Attribute attribute, final Value value) {
            this.attribute = attribute;
            this.value = value;
        }

        @Override
        public void translate(final DataTranslator translator) throws IOException {
            translator.beginObject("attribute");
            translator.addKeyValue("identifier", attribute.getIdentifier());
            if (attribute.getIsTranslatable()) {
                final TranslatableValue v = value.asTranslatable();
                translator.addKeyValue("value", v.value);
                if ((v.language != null) && (!Language.UNKNOWN.equals(v.language.getIso639_2t()))) {
                    translator.addKeyValue("language", v.language.getIso639_1());
                }
                final List<TranslatorAware> trs = productAttributeTranslationsToTranslatorAwareList(v.language, v.translations);
                if (trs.size() > 0) {
                    translator.addKeyValue("translations", trs);
                }
            }
            else switch (attribute.getValueType()) {
                case BOOLEAN:
                    translator.addKeyValue("value", value.asBoolean());
                    break;
                case TIMESTAMP:
                    translator.addKeyValue("value", value.asInteger() / 1000L);
                    break;
                case INTEGER:
                    translator.addKeyValue("value", value.asInteger());
                    break;
                case FLOAT:
                    translator.addKeyValue("value", value.asFloat());
                    break;
                case TIMESTAMP_INTERVAL:
                    final TimestampIntervalValue t = value.asTimestampInterval();
                    translator.addKeyValue("value", new StringBuilder(30).append(t.beginMillis / 1000L).append(" ").append(t.endMillis / 1000L).toString());
                    break;
                default:
                    translator.addKeyValue("value", value.asString());
                    break;
            }
            translator.endObject();
        }
    }

    public static List<TranslatorAware> productAttributeTranslationsToTranslatorAwareList(final Language originalLanguage, final Map<Language, String> translations) {
        if (translations.size() == 0) return Collections.emptyList();
        final long originalLanguageId = originalLanguage == null ? -1L : originalLanguage.getId().longValue();
        final List<TranslatorAware> result = new ArrayList<>();
        for (final Map.Entry<Language, String> translation : translations.entrySet()) {
            final Language language = translation.getKey();
            if (language.getId().longValue() == originalLanguageId) continue;
            result.add(new Translation(language, translation.getValue()));
        }
        return result;
    }

    static final class Translation implements TranslatorAware {
        final Language language;
        final String value;

        Translation(final Language language, final String value) {
            this.language = language;
            this.value = value;
        }

        @Override
        public void translate(final DataTranslator translator) throws IOException {
            translator.beginObject("translation");
            translator.addKeyValue("language", language.getIso639_1());
            translator.addKeyValue("value", value);
            translator.endObject();
        }
    }

    static final class Explanation implements TranslatorAware {
        final String value;
        final float weight;

        Explanation(final String value, final float weight) {
            this.value = value;
            this.weight = weight;
        }

        @Override
        public void translate(final DataTranslator translator) throws IOException {
            translator.beginObject("explanation");
            translator.addKeyValue("value", value);
            translator.endObject();
        }
    }

    static final class ExplanationComparator implements Comparator<Explanation> {
        static final ExplanationComparator INSTANCE = new ExplanationComparator();
        @Override
        public int compare(final Explanation o1, final Explanation o2) {
            if (o1.weight > o2.weight) return -1;
            if (o1.weight < o2.weight) return 1;
            return 0;
        }
    }
}
