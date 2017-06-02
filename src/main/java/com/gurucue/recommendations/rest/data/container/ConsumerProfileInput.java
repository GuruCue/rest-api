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

import com.gurucue.recommendations.ResponseException;
import com.gurucue.recommendations.ResponseStatus;
import com.gurucue.recommendations.parser.Rule;
import com.gurucue.recommendations.parser.StringParser;
import com.gurucue.recommendations.parser.StructuredTokenParser;
import com.gurucue.recommendations.parser.StructuredTokenParserMaker;
import com.gurucue.recommendations.rest.data.RequestCache;

import java.io.Serializable;
import java.util.Map;

public final class ConsumerProfileInput implements Serializable, StructuredTokenParser {
    private static final long serialVersionUID = -5108981839033048096L;

    static final String TAG_USER_ID = "user-id";
    static final String TAG_PROFILE_ID = "user-profile-id";
    static final StructuredTokenParserMaker maker = new Maker();

    static final Rule parseRule = Rule.map("request", false, maker, new Rule[]{
            Rule.value(TAG_USER_ID, false, StringParser.parser),
            Rule.value(TAG_PROFILE_ID, false, StringParser.parser)
    });

    private String userId;
    private String userProfileId;

    public String getUserId() {
        return userId;
    }

    public void setUserId(final String userId) {
        this.userId = userId;
    }

    public String getUserProfileId() {
        return userProfileId;
    }

    public void setUserProfileId(final String userProfileId) {
        this.userProfileId = userProfileId;
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
                case TAG_PROFILE_ID:
                    setUserProfileId((String) member);
                    break;
                default:
                    throw new ResponseException(ResponseStatus.INTERNAL_PROCESSING_ERROR, "Attempted to set a value to an unknown member of a ConsumerInput instance: " + memberName);
            }
        }
        catch (ClassCastException e) {
            throw new ResponseException(ResponseStatus.INTERNAL_PROCESSING_ERROR, e, "Attempted to set a value of invalid type to the member " + memberName + " of a ConsumerProfileInput instance: " + member.getClass().getCanonicalName());
        }
    }

    @Override
    public Object finish() {
        return this;
    }

    // driver code

    public static ConsumerProfileInput parse(final String format, final String input, final RequestCache data) throws ResponseException {
        final Object result = Rule.parse(format, input, parseRule, null);
        if (result instanceof ConsumerProfileInput) return (ConsumerProfileInput)result;
        throw new ResponseException(ResponseStatus.INTERNAL_PROCESSING_ERROR, "Internal error: parse did not result in a ConsumerProfileInput instance, but instead " + result.getClass().getCanonicalName());
    }

    private static class Maker implements StructuredTokenParserMaker {
        @Override
        public StructuredTokenParser create(final Map<String, Object> params) {
            return new ConsumerProfileInput();
        }
    }
}
