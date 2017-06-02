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
import com.gurucue.recommendations.ResponseException;
import com.gurucue.recommendations.ResponseStatus;
import com.gurucue.recommendations.blender.BlendParameters;
import com.gurucue.recommendations.entity.DataType;
import com.gurucue.recommendations.entity.value.AttributeValues;
import com.gurucue.recommendations.parser.IntegerParser;
import com.gurucue.recommendations.parser.Rule;
import com.gurucue.recommendations.parser.StringParser;
import com.gurucue.recommendations.parser.StructuredTokenParser;
import com.gurucue.recommendations.parser.StructuredTokenParserMaker;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Parses the search request.
 */
public final class SearchInput implements Serializable, StructuredTokenParser {

    static final String TAG_TYPE = "type";
    static final String TAG_USER_ID = "user-id";
    static final String TAG_QUERY = "query";
    static final String TAG_MAX_ITEMS = "maxItems";
    static final String TAG_ATTRIBUTES = "attributes";
    static final String TAG_DATA = "data";
    static final StructuredTokenParserMaker maker = new Maker();

    static final Rule parseRule = Rule.map("request", false, maker, new Rule[]{
            Rule.value(TAG_TYPE, true, StringParser.parser),
            Rule.value(TAG_USER_ID, false, StringParser.parser),
            Rule.value(TAG_QUERY, false, StringParser.parser),
            Rule.value(TAG_MAX_ITEMS, true, IntegerParser.parser),
            Rule.list(TAG_ATTRIBUTES, true, AttributeInput.class, AttributeInput.parseRule),
            Rule.list(TAG_DATA, true, ConsumerEventDataInput.class, ConsumerEventDataInput.parseRule)
    });

    private String type;
    private String userId;
    private String query;
    private Integer maxResults;
    private List<AttributeInput> attributes;
    private List<ConsumerEventDataInput> data;

    public SearchInput() {
        this.type = null;
        this.userId = null;
        this.maxResults = null;
        this.attributes = Collections.emptyList();
        this.data = Collections.emptyList();
    }

    public String getType() {
        return type;
    }

    public void setType(final String type) {
        this.type = type;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(final String username) {
        this.userId = username;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(final String query) {
        this.query = query;
    }

    public Integer getMaxResults() {
        return maxResults;
    }

    public void setMaxResults(final Integer maxResults) {
        this.maxResults = maxResults;
    }

    public List<AttributeInput> getAttributes() {
        return attributes;
    }

    public void setAttributes(final List<AttributeInput> attributes) {
        this.attributes = attributes;
    }

    public List<ConsumerEventDataInput> getData() {
        return data;
    }

    public void setData(final List<ConsumerEventDataInput> data) {
        this.data = data;
    }

    public BlendParameters asBlendParameters() throws ResponseException {
        final ImmutableMap.Builder<DataType, String> dataBuilder = ImmutableMap.builder();
        data.forEach((final ConsumerEventDataInput input) -> dataBuilder.put(input.getType(), input.getValue()));
        final BlendParameters result = new BlendParameters(type, userId, dataBuilder.build());
        if (maxResults != null) {
            result.addInput("maxItems", maxResults);
        }
        result.addInput("query", query);
        if ((attributes != null) && (attributes.size() > 0)) {
            result.addInput("attributes", new AttributeValues(AttributeInput.asAttributeValues(attributes)));
        }
        return result;
    }

    // utility methods

    // StructuredTokenParser interface

    @Override
    public void begin(final String memberName, final Map<String, Object> params) {}

    @SuppressWarnings("unchecked")
    @Override
    public void consume(final String memberName, final Object member) throws ResponseException {
        try {
            switch (memberName) {
                case TAG_TYPE:
                    setType((String) member);
                    break;
                case TAG_USER_ID:
                    setUserId((String) member);
                    break;
                case TAG_QUERY:
                    setQuery((String) member);
                    break;
                case TAG_MAX_ITEMS:
                    setMaxResults((Integer) member);
                    break;
                case TAG_ATTRIBUTES:
                    setAttributes((List<AttributeInput>) member);
                    break;
                case TAG_DATA:
                    setData((List<ConsumerEventDataInput>) member);
                    break;
                default:
                    throw new ResponseException(ResponseStatus.INTERNAL_PROCESSING_ERROR, "Attempted to set a value to an unknown member of a SearchInput instance: " + memberName);
            }
        }
        catch (ClassCastException e) {
            throw new ResponseException(ResponseStatus.INTERNAL_PROCESSING_ERROR, e, "Attempted to set a value of invalid type to the member " + memberName + " of a SearchInput instance: " + member.getClass().getCanonicalName());
        }
    }

    @Override
    public SearchInput finish() throws ResponseException {
        return this;
    }

    // driver code

    public static SearchInput parse(final String format, final String input) throws ResponseException {
        final Object result = Rule.parse(format, input, parseRule, null);
        if (result instanceof SearchInput) return (SearchInput)result;
        throw new ResponseException(ResponseStatus.INTERNAL_PROCESSING_ERROR, "Internal error: parse did not result in a SearchInput instance, but instead " + result.getClass().getCanonicalName());
    }

    private static class Maker implements StructuredTokenParserMaker {
        @Override
        public StructuredTokenParser create(final Map<String, Object> params) {
            return new SearchInput();
        }
    }
}
