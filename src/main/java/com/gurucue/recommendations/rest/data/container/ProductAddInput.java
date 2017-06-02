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
package com.gurucue.recommendations.rest.data.container;

import com.google.common.collect.ImmutableMap;
import com.gurucue.recommendations.entity.Attribute;
import com.gurucue.recommendations.ResponseException;
import com.gurucue.recommendations.ResponseStatus;
import com.gurucue.recommendations.entity.ProductType;
import com.gurucue.recommendations.entity.value.Value;
import com.gurucue.recommendations.parser.ProductTypeParser;
import com.gurucue.recommendations.parser.Rule;
import com.gurucue.recommendations.parser.StringParser;
import com.gurucue.recommendations.parser.StructuredTokenParser;
import com.gurucue.recommendations.parser.StructuredTokenParserMaker;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Object representation and parser of a product PUT request.
 */
public final class ProductAddInput implements Serializable, StructuredTokenParser, AttributeInputSelectable {
    private static final long serialVersionUID = 1624617591746286137L;

    static final String TAG_TYPE = "type";
    static final String TAG_ID = "id";
    static final String TAG_ATTRIBUTES = "attributes";
    static final StructuredTokenParserMaker maker = new Maker();
    static final Rule parseRule = Rule.map("request", false, maker, new Rule[] {
            Rule.value(TAG_TYPE, false, ProductTypeParser.parser),
            Rule.value(TAG_ID, false, StringParser.parser),
            Rule.list(TAG_ATTRIBUTES, true, AttributeInput.class, AttributeInput.parseRule)
    });

    private final Map<Long, List<AttributeInput>> valueListPerAttribute = new HashMap<>(); // for the getValuesOfAttribute()
    private final List<AttributeInput> publicValues = new ArrayList<>(); // TODO: remove the distinction between public and private attributes
    private final List<AttributeInput> privateValues = new ArrayList<>();

    // these are the actual properties (the content) of a request
    private ProductType productType;
    private String productCode;
    private List<AttributeInput> attributes;

    public ProductAddInput() {
        productType = null;
        productCode = null;
        attributes = Collections.emptyList();
    }

    public ProductType getProductType() {
        return productType;
    }

    public void setProductType(final ProductType productType) {
        this.productType = productType;
    }

    public String getProductCode() {
        return productCode;
    }

    public void setProductCode(final String productCode) {
        this.productCode = productCode;
    }

    public List<AttributeInput> getAttributes() {
        return attributes;
    }

    public void setAttributes(final List<AttributeInput> attributes) {
        this.attributes = attributes;
    }

    @Override
    public List<AttributeInput> getValuesOfAttribute(final Attribute attribute) {
        final List<AttributeInput> inputs = valueListPerAttribute.get(attribute.getId());
        if (inputs == null) return Collections.emptyList();
        return inputs;
    }

    @Override
    public List<AttributeInput> getAllValues() {
        return attributes;
    }

    @Override
    public List<AttributeInput> getPublicValues() {
        return publicValues;
    }

    @Override
    public List<AttributeInput> getPrivateValues() {
        return privateValues;
    }

    public ImmutableMap<Attribute, Value> getAttributeValues() throws ResponseException {
        final List<AttributeInput> theList = new ArrayList<>(publicValues);
        theList.addAll(privateValues);
        return AttributeInput.asAttributeValues(theList);
    }

    // StructuredTokenParser interface

    @Override
    public void begin(final String memberName, final Map<String, Object> params) {}

    @Override
    public void consume(final String memberName, final Object member) throws ResponseException {
        try {
            switch (memberName) {
                case TAG_TYPE:
                    setProductType((ProductType) member);
                    break;
                case TAG_ID:
                    setProductCode((String) member);
                    break;
                case TAG_ATTRIBUTES:
                    setAttributes((List<AttributeInput>) member);
                    break;
                default:
                    throw new ResponseException(ResponseStatus.INTERNAL_PROCESSING_ERROR, "Attempted to set a value to an unknown member of a ProductAddInput instance: " + memberName);
            }
        }
        catch (ClassCastException e) {
            throw new ResponseException(ResponseStatus.INTERNAL_PROCESSING_ERROR, e, "Attempted to set a value of invalid type to the member " + memberName + " of a ProductAddInput instance: " + member.getClass().getCanonicalName());
        }
    }

    @Override
    public ProductAddInput finish() throws ResponseException {
        if (productType == null) throw new ResponseException(ResponseStatus.PRODUCT_TYPE_MISSING);
        if ((productCode == null) || (productCode.length() == 0)) throw new ResponseException(ResponseStatus.PRODUCT_ID_MISSING);
        if (attributes == null) attributes = Collections.emptyList();
        else {
            // construct a new list, filtered
            final List<AttributeInput> newList = new ArrayList<>();
            for (final AttributeInput a : attributes) a.addToList(newList);
            attributes = newList;
            // fill in the lists and mappings
            for (final AttributeInput a : attributes) {
                final Attribute attr = a.getAttribute();
                if (attr.getIsPrivate()) privateValues.add(a);
                else publicValues.add(a);
                List<AttributeInput> l = valueListPerAttribute.get(attr.getId());
                if (l == null) {
                    l = new ArrayList<>();
                    valueListPerAttribute.put(attr.getId(), l);
                }
                l.add(a);
            }
        }
        return this;
    }

    // driver code

    public static ProductAddInput parse(final String format, final String input) throws ResponseException {
        final Map<String, Object> parseParams = new HashMap<>();
        parseParams.put("isDeletion", Boolean.FALSE); // attributes don't allow empty values/languages
        parseParams.put("isAddition", Boolean.TRUE); // translations don't allow deletions (= empty values)
        final Object result = Rule.parse(format, input, parseRule, parseParams);
        if (result instanceof ProductAddInput) return (ProductAddInput)result;
        throw new ResponseException(ResponseStatus.INTERNAL_PROCESSING_ERROR, "Internal error: parse did not result in a ProductAddInput instance, but instead " + result.getClass().getCanonicalName());
    }

    private static class Maker implements StructuredTokenParserMaker {
        @Override
        public StructuredTokenParser create(final Map<String, Object> params) {
            return new ProductAddInput();
        }
    }
}
