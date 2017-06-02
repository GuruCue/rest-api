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

import com.gurucue.recommendations.parser.DataTypeParser;
import com.gurucue.recommendations.parser.Rule;
import com.gurucue.recommendations.parser.StringParser;
import com.gurucue.recommendations.parser.StructuredTokenParser;
import com.gurucue.recommendations.parser.StructuredTokenParserMaker;
import com.gurucue.recommendations.rest.data.RequestCache;
import com.gurucue.recommendations.ResponseException;
import com.gurucue.recommendations.ResponseStatus;
import com.gurucue.recommendations.entity.DataType;

import java.io.Serializable;
import java.util.Map;

/**
 * Represents a data instance of a consumer event. Used in input data
 * processing for web services.
 */
public final class ConsumerEventDataInput implements Serializable, StructuredTokenParser {
    private static final long serialVersionUID = -7929063392531922174L;

    static final String TAG_IDENTIFIER = "identifier";
    static final String TAG_VALUE = "value";
    static final StructuredTokenParserMaker maker = new Maker();
    static final Rule parseRule = Rule.map("item", true, maker, new Rule[] {
            Rule.value(TAG_IDENTIFIER, false, DataTypeParser.parser),
            Rule.value(TAG_VALUE, false, StringParser.parser)
    });

    private DataType type;
    private String value;

    private long timingCreate = System.nanoTime();
    private long lastTiming = timingCreate;
    private long timingIdentifier = 0L;
    private long timingValue = 0L;
    private long timingFirstParse = 0L;
    private long timingPreIdentifier;
    private long timingPreValue;

    public ConsumerEventDataInput() {}
    
    public ConsumerEventDataInput(final DataType type, final String value) {
        this.type = type;
        this.value = value;
    }
    
    public final DataType getType() {
        return type;
    }

    public final void setType(final DataType type) {
        this.type = type;
    }

    public final String getValue() {
        return value;
    }

    public final void setValue(final String value) {
        this.value = value;
    }

    @Override
    public final void begin(final String memberName, final Map<String, Object> params) {
        final long now = System.nanoTime();
        if (timingFirstParse == 0L) timingFirstParse = now - lastTiming;
        switch (memberName) {
            case TAG_IDENTIFIER:
                timingPreIdentifier = now - lastTiming;
                break;
            case TAG_VALUE:
                timingPreValue = now - lastTiming;
                break;
        }
        lastTiming = now;
    }

    @Override
    public final void consume(final String memberName, final Object member)
            throws ResponseException {
        try {
            switch (memberName) {
                case TAG_IDENTIFIER:
                    setType((DataType) member);
                    timingIdentifier = System.nanoTime() - lastTiming;
                    break;
                case TAG_VALUE:
                    setValue((String) member);
                    timingValue = System.nanoTime() - lastTiming;
                    break;
                default:
                    throw new ResponseException(ResponseStatus.INTERNAL_PROCESSING_ERROR, "Attempted to set a value to an unknown member of a ConsumerEventDataInput instance: " + memberName);
            }
        }
        catch (ClassCastException e) {
            throw new ResponseException(ResponseStatus.INTERNAL_PROCESSING_ERROR, e, "Attempted to set a value of invalid type to the member " + memberName + " of a ConsumerEventDataInput instance: " + member.getClass().getCanonicalName());
        }
    }

    @Override
    public final ConsumerEventDataInput finish() throws ResponseException {
        final long now = System.nanoTime();
        final long everythingNano = now - timingCreate;
        final long timingAfterLast = now - lastTiming;
        if (everythingNano > 1000000L) { // more than 1ms -> not cool
            RequestCache.get().getLogger().debug("ConsumerEventDataInput timings: all: " + everythingNano + " ns, before the first component: " + timingFirstParse + " ns, before identifier: " + timingPreIdentifier + " ns, identifier: " + timingIdentifier + " ns, before value: " + timingPreValue + " ns, value: " + timingValue + " ns, after the last component: " + timingAfterLast + " ns");
        }
        return this;
    }

    private static final class Maker implements StructuredTokenParserMaker {
        @Override
        public StructuredTokenParser create(final Map<String, Object> params) {
            return new ConsumerEventDataInput();
        }
    }
}
