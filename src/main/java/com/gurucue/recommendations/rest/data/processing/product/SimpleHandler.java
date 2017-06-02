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
import com.gurucue.recommendations.ResponseException;
import com.gurucue.recommendations.Transaction;
import com.gurucue.recommendations.data.AttributeCodes;
import com.gurucue.recommendations.data.DataLink;
import com.gurucue.recommendations.data.DataManager;
import com.gurucue.recommendations.data.ProductTypeCodes;
import com.gurucue.recommendations.entity.Attribute;
import com.gurucue.recommendations.entity.Partner;
import com.gurucue.recommendations.entity.ProductType;
import com.gurucue.recommendations.entity.product.Product;
import com.gurucue.recommendations.entity.value.AttributeValues;
import com.gurucue.recommendations.entity.value.Value;
import com.gurucue.recommendations.entitymanager.ProductManager;
import com.gurucue.recommendations.rest.data.RequestCache;
import com.gurucue.recommendations.rest.data.RequestLogger;

import java.util.Map;
import java.util.Set;

public final class SimpleHandler implements ProductHandler {
    private static final Set<Attribute> requiredPackageAttributes;
    private static final Set<Attribute> allowedPackageAttributes;
    private static final Set<Attribute> requiredTvChannelAttributes;
    private static final Set<Attribute> allowedTvChannelAttributes;
    private static final Set<Attribute> requiredVodAttributes;
    private static final Set<Attribute> allowedVodAttributes;
    private static final Set<Attribute> requiredInteractiveAttributes;
    private static final Set<Attribute> allowedInteractiveAttributes;
    private static final Map<ProductType, RequiredAllowedTuple> attributeRules;
    static {
        final AttributeCodes attributeCodes = DataManager.getAttributeCodes();

        final ImmutableSet.Builder<Attribute> packageRequiredBuilder = ImmutableSet.builder();
        packageRequiredBuilder.add(attributeCodes.packageType);
        packageRequiredBuilder.add(attributeCodes.title);
        requiredPackageAttributes = packageRequiredBuilder.build();

        final ImmutableSet.Builder<Attribute> packageAllowedBuilder = ImmutableSet.builder();
        packageAllowedBuilder.add(attributeCodes.interactiveId);
        packageAllowedBuilder.add(attributeCodes.packageType);
        packageAllowedBuilder.add(attributeCodes.svodId);
        packageAllowedBuilder.add(attributeCodes.title);
        packageAllowedBuilder.add(attributeCodes.tvChannelId);
        allowedPackageAttributes = packageAllowedBuilder.build();

        final ImmutableSet.Builder<Attribute> tvChannelRequiredBuilder = ImmutableSet.builder();
        tvChannelRequiredBuilder.add(attributeCodes.title);
        requiredTvChannelAttributes = tvChannelRequiredBuilder.build();

        final ImmutableSet.Builder<Attribute> tvChannelAllowedBuilder = ImmutableSet.builder();
        tvChannelAllowedBuilder.add(attributeCodes.catchupHours);
        tvChannelAllowedBuilder.add(attributeCodes.isAdult);
        tvChannelAllowedBuilder.add(attributeCodes.spokenLanguage);
        tvChannelAllowedBuilder.add(attributeCodes.subtitleLanguage);
        tvChannelAllowedBuilder.add(attributeCodes.videoFormat);
        tvChannelAllowedBuilder.add(attributeCodes.title);
        allowedTvChannelAttributes = tvChannelAllowedBuilder.build();

        final ImmutableSet.Builder<Attribute> vodRequiredBuilder = ImmutableSet.builder();
        vodRequiredBuilder.add(attributeCodes.catalogueId);
        requiredVodAttributes = vodRequiredBuilder.build();

        final ImmutableSet.Builder<Attribute> vodAllowedBuilder = ImmutableSet.builder();
        vodAllowedBuilder.add(attributeCodes.catalogueId);
        vodAllowedBuilder.add(attributeCodes.isAdult);
        vodAllowedBuilder.add(attributeCodes.title);
        allowedVodAttributes = vodAllowedBuilder.build();

        final ImmutableSet.Builder<Attribute> interactiveRequiredBuilder = ImmutableSet.builder();
        requiredInteractiveAttributes = interactiveRequiredBuilder.build();

        final ImmutableSet.Builder<Attribute> interactiveAllowedBuilder = ImmutableSet.builder();
        interactiveAllowedBuilder.add(attributeCodes.title);
        allowedInteractiveAttributes = interactiveAllowedBuilder.build();

        final ProductTypeCodes productTypeCodes = DataManager.getProductTypeCodes();
        final ImmutableMap.Builder<ProductType, RequiredAllowedTuple> ruleBuilder = ImmutableMap.builder();
        ruleBuilder.put(productTypeCodes.package_, new RequiredAllowedTuple(requiredPackageAttributes, allowedPackageAttributes));
        ruleBuilder.put(productTypeCodes.tvChannel, new RequiredAllowedTuple(requiredTvChannelAttributes, allowedTvChannelAttributes));
        ruleBuilder.put(productTypeCodes.tvod, new RequiredAllowedTuple(requiredVodAttributes, allowedVodAttributes));
        ruleBuilder.put(productTypeCodes.svod, new RequiredAllowedTuple(requiredVodAttributes, allowedVodAttributes));
        ruleBuilder.put(productTypeCodes.interactive, new RequiredAllowedTuple(requiredInteractiveAttributes, allowedInteractiveAttributes));
        attributeRules = ruleBuilder.build();
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
        final StringBuilder logBuilder = new StringBuilder(1024);

        if (modificationType == ProductOperation.DELETION) {
            pm.deleteByPartnerAndTypeAndCode(transaction, partner, productType, partnerProductCode);
            logBuilder.append("Deleted ").append(productType.getIdentifier()).append(" product with partner code ").append(partnerProductCode).append(" for partner ").append(partner.getUsername());
            logger.debug(logBuilder.toString());
            return; // and we're done
        }

        final Product existingProduct = pm.getProductByPartnerAndTypeAndCode(transaction, partner, productType, partnerProductCode, true);
        final AttributeValues newValues = (existingProduct == null) || (modificationType == ProductOperation.ADDITION) ? new AttributeValues(addedValues) : existingProduct.attributes.modify(addedValues, removedValues);
        final AttributeValues newRelated = (existingProduct == null) ? AttributeValues.NO_VALUES : existingProduct.related;

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

        final RequiredAllowedTuple rules = attributeRules.get(productType);
        if (rules != null) Processor.verifyAttributes(newValues.values, rules.requiredAttributes, rules.allowedAttributes, productType, partnerProductCode, (existingProduct == null) && (modificationType == ProductOperation.MODIFICATION));

        if ((existingProduct == null) || !newValues.equals(existingProduct.attributes)) {
            // do the modification only if there's actually something to modify
            final Product newProduct;
            if (existingProduct == null) newProduct = Product.create(0L, productType.getId(), partner.getId(), partnerProductCode, null, null, null, newValues, newRelated, link.getProvider());
            else newProduct = Product.create(existingProduct.id, productType.getId(), partner.getId(), partnerProductCode, existingProduct.added, existingProduct.modified, existingProduct.deleted, newValues, newRelated, link.getProvider());
            final Product savedProduct = pm.save(transaction, newProduct);

            if (existingProduct == null) logBuilder.append("\n  saved a new Product to the database with ID ").append(savedProduct.id);
            else logBuilder.append("\n  modified an existing Product in the database with ID ").append(savedProduct.id);
        }
        else {
            logBuilder.append("\n  no change in values detected, no operation performed in the database");
        }

        logger.debug(logBuilder.toString());
    }

    static class RequiredAllowedTuple {
        final Set<Attribute> requiredAttributes;
        final Set<Attribute> allowedAttributes;

        RequiredAllowedTuple(final Set<Attribute> requiredAttributes, final Set<Attribute> allowedAttributes) {
            this.requiredAttributes = requiredAttributes;
            this.allowedAttributes = allowedAttributes;
        }
    }
}
