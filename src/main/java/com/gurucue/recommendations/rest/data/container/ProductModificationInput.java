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
import com.gurucue.recommendations.entity.ProductType;
import com.gurucue.recommendations.entity.value.MultiValue;
import com.gurucue.recommendations.entity.value.Value;
import com.gurucue.recommendations.parser.ProductTypeParser;
import com.gurucue.recommendations.parser.Rule;
import com.gurucue.recommendations.parser.StringParser;
import com.gurucue.recommendations.parser.StructuredTokenParser;
import com.gurucue.recommendations.parser.StructuredTokenParserMaker;
import com.gurucue.recommendations.ResponseException;
import com.gurucue.recommendations.ResponseStatus;

import java.io.Serializable;
import java.util.*;

/**
 * Object representation and parser of a product POST request.
 */
public final class ProductModificationInput implements Serializable, StructuredTokenParser {
    private static final long serialVersionUID = 4199719457150537755L;

    static final String TAG_TYPE = "type";
    static final String TAG_ID = "id";
    static final String TAG_ATTRIBUTES_SET = "attributes-set";
    static final String TAG_ATTRIBUTES_CLEAR = "attributes-clear";
    static final StructuredTokenParserMaker maker = new Maker();
    static final Rule parseRule = Rule.map("request", false, maker, new Rule[] {
            Rule.value(TAG_TYPE, false, ProductTypeParser.parser),
            Rule.value(TAG_ID, false, StringParser.parser),
            Rule.list(TAG_ATTRIBUTES_SET, true, AttributeInput.class, AttributeInput.parseRule),
            Rule.list(TAG_ATTRIBUTES_CLEAR, true, AttributeInput.class, AttributeInput.parseRule)
    });

    private ProductType productType;
    private String productCode;
    private List<AttributeInput> attributesSet;
    private List<AttributeInput> attributesClear;

    public ProductModificationInput() {
        productType = null;
        productCode = null;
        attributesSet = Collections.emptyList();
        attributesClear = Collections.emptyList();
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

    public List<AttributeInput> getAttributesSet() {
        return attributesSet;
    }

    public void setAttributesSet(final List<AttributeInput> attributesSet) {
        this.attributesSet = attributesSet;
    }

    public List<AttributeInput> getAttributesClear() {
        return attributesClear;
    }

    public void setAttributesClear(final List<AttributeInput> attributesClear) {
        this.attributesClear = attributesClear;
    }

    public ImmutableMap<Attribute, Value> getAttributeValuesSet() throws ResponseException {
        return assembleAttributeValues(attributesSet);
    }

    public ImmutableMap<Attribute, Value> getAttributeValuesClear() throws ResponseException {
        return assembleAttributeValues(attributesClear);
    }

    private ImmutableMap<Attribute, Value> assembleAttributeValues(final List<AttributeInput> inputs) throws ResponseException {
        final Map<Attribute, Object> mapping = new HashMap<>(inputs.size());
        for (final AttributeInput input : inputs) {
            final Attribute a = input.getAttribute();
            if (a.getIsMultivalue()) {
                final Object existingObject = mapping.get(a);
                if (existingObject == null) {
                    final List<Value> valueList = new ArrayList<>();
                    valueList.add(input.asValue());
                    mapping.put(a, valueList);
                }
                else {
                    ((List<Value>)existingObject).add(input.asValue());
                }
            }
            else mapping.put(a, input.asValue());
        }
        final ImmutableMap.Builder<Attribute, Value> builder = ImmutableMap.builder();
        for (final Map.Entry<Attribute, Object> entry : mapping.entrySet()) {
            final Attribute a = entry.getKey();
            if (a.getIsMultivalue()) {
                final List<Value> l = (List<Value>)entry.getValue();
                builder.put(a, new MultiValue(l.toArray(new Value[l.size()])));
            }
            else {
                builder.put(a, (Value)entry.getValue());
            }
        }
        return builder.build();
    }

    // StructuredTokenParser interface

    @Override
    public void begin(final String memberName, final Map<String, Object> params) {
        switch (memberName) {
            case TAG_ATTRIBUTES_SET:
                params.put("isDeletion", Boolean.FALSE); // attributes don't allow empty values/languages
                break;
            default:
                params.put("isDeletion", Boolean.TRUE); // attributes allow empty values/languages
                break;
        }
    }

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
                case TAG_ATTRIBUTES_SET:
                    setAttributesSet((List<AttributeInput>) member);
                    break;
                case TAG_ATTRIBUTES_CLEAR:
                    setAttributesClear((List<AttributeInput>) member);
                    break;
                default:
                    throw new ResponseException(ResponseStatus.INTERNAL_PROCESSING_ERROR, "Attempted to set a value to an unknown member of a ProductModificationInput instance: " + memberName);
            }
        }
        catch (ClassCastException e) {
            throw new ResponseException(ResponseStatus.INTERNAL_PROCESSING_ERROR, e, "Attempted to set a value of invalid type to the member " + memberName + " of a ProductModificationInput instance: " + member.getClass().getCanonicalName());
        }
    }

    @Override
    public ProductModificationInput finish() throws ResponseException {
        if (productType == null) throw new ResponseException(ResponseStatus.PRODUCT_TYPE_MISSING);
        if ((productCode == null) || (productCode.length() == 0)) throw new ResponseException(ResponseStatus.PRODUCT_ID_MISSING);
        if (attributesSet == null) attributesSet = Collections.emptyList();
        else if (attributesSet.size() > 1) {
            // construct a new list, filtered
            final List<AttributeInput> newList = new ArrayList<>();
            for (final AttributeInput a : attributesSet) a.addToList(newList);
            attributesSet = newList;
        }
        if (attributesClear == null) attributesClear = Collections.emptyList();
        else if (attributesClear.size() > 1) {
            // construct a new list, filtered
            final List<AttributeInput> newList = new ArrayList<>();
            for (final AttributeInput a : attributesClear) a.addToList(newList);
            attributesClear = newList;
        }
        return this;
    }

    // driver code

    public static ProductModificationInput parse(final String format, final String input) throws ResponseException {
        final Map<String, Object> parseParams = new HashMap<>();
        parseParams.put("isDeletion", Boolean.FALSE);
        parseParams.put("isAddition", Boolean.FALSE); // translations allow deletions (= empty values)
        final Object result = Rule.parse(format, input, parseRule, parseParams);
        if (result instanceof ProductModificationInput) return (ProductModificationInput)result;
        throw new ResponseException(ResponseStatus.INTERNAL_PROCESSING_ERROR, "Internal error: parse did not result in a ProductModificationInput instance, but instead " + result.getClass().getCanonicalName());
    }

    private static class Maker implements StructuredTokenParserMaker {
        @Override
        public StructuredTokenParser create(final Map<String, Object> params) {
            return new ProductModificationInput();
        }
    }
}
