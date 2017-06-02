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

import com.gurucue.recommendations.entity.RelationConsumerProduct;
import com.gurucue.recommendations.parser.BooleanParser;
import com.gurucue.recommendations.parser.Rule;
import com.gurucue.recommendations.parser.StringParser;
import com.gurucue.recommendations.parser.StructuredTokenParser;
import com.gurucue.recommendations.parser.StructuredTokenParserMaker;
import com.gurucue.recommendations.rest.data.RequestCache;
import com.gurucue.recommendations.ResponseException;
import com.gurucue.recommendations.ResponseStatus;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class ConsumerInput implements Serializable, StructuredTokenParser {
    private static final long serialVersionUID = 6669598215174058916L;

    static final String TAG_USER_ID = "user-id";
    static final String TAG_PRODUCT_RELATIONS = "product-relations";
    static final String TAG_DELETE_HISTORY = "delete-history";
    static final StructuredTokenParserMaker maker = new Maker();

    static final Rule parseRule = Rule.map("request", false, maker, new Rule[]{
            Rule.value(TAG_USER_ID, false, StringParser.parser),
            Rule.list(TAG_PRODUCT_RELATIONS, true, RelationConsumerProduct.class, RelationConsumerProductInput.parseRule),
            Rule.value(TAG_DELETE_HISTORY, true, BooleanParser.parser)
    });

    private String userId;
    private List<RelationConsumerProduct> productRelations;
    private boolean deleteHistory = false;

    public ConsumerInput() {
        this.userId = null;
        this.productRelations = Collections.emptyList();
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(final String userId) {
        this.userId = userId;
    }

    public List<RelationConsumerProduct> getProductRelations() {
        return productRelations;
    }

    public void setProductRelations(final List<RelationConsumerProduct> productRelations) {
        this.productRelations = productRelations;
    }

    public boolean getDeleteHistory() {
        return deleteHistory;
    }

    public void setDeleteHistory(final boolean deleteHistory) {
        this.deleteHistory = deleteHistory;
    }

    // StructuredTokenParser interface

    @Override
    public void begin(final String memberName, final Map<String, Object> params) {}

    @SuppressWarnings("unchecked")
    @Override
    public void consume(final String memberName, final Object member) throws ResponseException {
        try {
            switch (memberName) {
                case TAG_USER_ID:
                    setUserId((String) member);
                    break;
                case TAG_PRODUCT_RELATIONS:
                    setProductRelations((List<RelationConsumerProduct>) member);
                    break;
                case TAG_DELETE_HISTORY:
                    final Boolean b = (Boolean) member;
                    setDeleteHistory(b == null ? false : b.booleanValue());
                    break;
                default:
                    throw new ResponseException(ResponseStatus.INTERNAL_PROCESSING_ERROR, "Attempted to set a value to an unknown member of a ConsumerInput instance: " + memberName);
            }
        }
        catch (ClassCastException e) {
            throw new ResponseException(ResponseStatus.INTERNAL_PROCESSING_ERROR, e, "Attempted to set a value of invalid type to the member " + memberName + " of a ConsumerInput instance: " + member.getClass().getCanonicalName());
        }
    }

    @Override
    public ConsumerInput finish() throws ResponseException {
        return this;
    }

    // driver code

    public static ConsumerInput parse(final String format, final String input, final RequestCache data) throws ResponseException {
        final Object result = Rule.parse(format, input, parseRule, null);
        if (result instanceof ConsumerInput) return (ConsumerInput)result;
        throw new ResponseException(ResponseStatus.INTERNAL_PROCESSING_ERROR, "Internal error: parse did not result in a ConsumerInput instance, but instead " + result.getClass().getCanonicalName());
    }

    private static class Maker implements StructuredTokenParserMaker {
        @Override
        public StructuredTokenParser create(final Map<String, Object> params) {
            return new ConsumerInput();
        }
    }
}
