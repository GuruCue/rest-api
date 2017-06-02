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
import com.gurucue.recommendations.ResponseException;
import com.gurucue.recommendations.ResponseStatus;
import com.gurucue.recommendations.Transaction;
import com.gurucue.recommendations.data.DataManager;
import com.gurucue.recommendations.data.ProductTypeCodes;
import com.gurucue.recommendations.entity.Attribute;
import com.gurucue.recommendations.entity.Partner;
import com.gurucue.recommendations.entity.ProductType;
import com.gurucue.recommendations.entity.value.Value;
import com.gurucue.recommendations.rest.data.RequestCache;

import java.util.Map;
import java.util.Set;

public final class Processor {
    private static final VideoHandler videoHandler = new VideoHandler();
    private static final SimpleHandler simpleHandler = new SimpleHandler();
    private static final long tvProgrammeId;
    private static final long videoId;
    static {
        final ProductTypeCodes productTypeCodes = DataManager.getProductTypeCodes();
        tvProgrammeId = productTypeCodes.idForTvProgramme;
        videoId = productTypeCodes.idForVideo;
    }
    private static final Map<Attribute, Value> NO_VALUES = ImmutableMap.of();

    public static void add(
            final RequestCache requestData,
            final Transaction transaction,
            final Partner partner,
            final ProductType productType,
            final String partnerProductCode,
            final Map<Attribute, Value> attributeValues
    ) throws ResponseException {
        final long productTypeId = productType.getId();
        if ((productTypeId == tvProgrammeId) || (productTypeId == videoId)) {
            videoHandler.handle(requestData, Transaction.get(), ProductOperation.ADDITION, productType, partnerProductCode, NO_VALUES, attributeValues);
        }
        else {
            simpleHandler.handle(requestData, Transaction.get(), ProductOperation.ADDITION, productType, partnerProductCode, NO_VALUES, attributeValues);
        }
    }

    public static void modify(
            final RequestCache requestData,
            final Transaction transaction,
            final Partner partner,
            final ProductType productType,
            final String partnerProductCode,
            final Map<Attribute, Value> removedValues,
            final Map<Attribute, Value> addedValues
    ) throws ResponseException {
        final long productTypeId = productType.getId();
        if ((productTypeId == tvProgrammeId) || (productTypeId == videoId)) {
            videoHandler.handle(requestData, Transaction.get(), ProductOperation.MODIFICATION, productType, partnerProductCode, removedValues, addedValues);
        }
        else {
            simpleHandler.handle(requestData, Transaction.get(), ProductOperation.MODIFICATION, productType, partnerProductCode, removedValues, addedValues);
        }
    }

    static void verifyAttributes(
            final Map<Attribute, Value> attributeValues,
            final Set<Attribute> requiredAttributes,
            final Set<Attribute> allowedAttributes,
            final ProductType productType,
            final String partnerProductCode,
            final boolean isModification
    ) throws ResponseException {
        for (final Attribute required : requiredAttributes) {
            if (!attributeValues.containsKey(required)) {
                if (isModification) throw new ResponseException(ResponseStatus.NO_SUCH_PRODUCT_ID, "The " + productType.getIdentifier() + " with ID " + partnerProductCode + " does not exist, cannot modify it");
                else throw new ResponseException(ResponseStatus.ATTRIBUTE_MISSING, "The mandatory attribute \"" + required.getIdentifier() + "\" is missing");
            }
        }
        for (final Attribute presentAttribute : attributeValues.keySet()) {
            if (!allowedAttributes.contains(presentAttribute)) throw new ResponseException(ResponseStatus.REQUEST_ATTRIBUTE_ILLEGAL, "The attribute \"" + presentAttribute.getIdentifier() + "\" is not allowed for " + productType.getIdentifier() + " products");
        }
    }
}
