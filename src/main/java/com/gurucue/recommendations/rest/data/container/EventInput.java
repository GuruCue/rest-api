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

import com.gurucue.recommendations.Timer;
import com.gurucue.recommendations.entity.ConsumerEventType;
import com.gurucue.recommendations.entity.ProductType;
import com.gurucue.recommendations.parser.ConsumerEventTypeParser;
import com.gurucue.recommendations.parser.ProductTypeParser;
import com.gurucue.recommendations.parser.Rule;
import com.gurucue.recommendations.parser.StringParser;
import com.gurucue.recommendations.parser.StructuredTokenParser;
import com.gurucue.recommendations.parser.StructuredTokenParserMaker;
import com.gurucue.recommendations.parser.TimestampParser;
import com.gurucue.recommendations.ResponseException;
import com.gurucue.recommendations.ResponseStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.Serializable;
import java.sql.Timestamp;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class EventInput implements Serializable, StructuredTokenParser {
    private static final long serialVersionUID = -7591887371198878994L;
    private static final Logger log = LogManager.getLogger(EventInput.class);

    static final String TAG_TYPE = "type";
    static final String TAG_USER_ID = "user-id";
    static final String TAG_PRODUCT_TYPE = "product-type";
    static final String TAG_PRODUCT_ID = "product-id";
    static final String TAG_TIMESTAMP = "timestamp";
    static final String TAG_DATA = "data";
    static final StructuredTokenParserMaker maker = new Maker();

    static final Rule parseRule = Rule.map("request", false, maker, new Rule[] {
            Rule.value(TAG_TYPE, true, ConsumerEventTypeParser.parser),
            Rule.value(TAG_USER_ID, true, StringParser.parser),
            Rule.value(TAG_PRODUCT_TYPE, true, ProductTypeParser.parser),
            Rule.value(TAG_PRODUCT_ID, true, StringParser.parser),
            Rule.value(TAG_TIMESTAMP, true, TimestampParser.parser),
            Rule.list(TAG_DATA, true, ConsumerEventDataInput.class, ConsumerEventDataInput.parseRule)
    });

    private ConsumerEventType eventType;
    private String userId;
    private ProductType productType;
    private String productId;
    private Timestamp timestamp;
    private List<ConsumerEventDataInput> data;

    private long timingCreate = System.nanoTime();
    private long lastTiming = timingCreate;
    private long timingEventType = 0L;
    private long timingUserId = 0L;
    private long timingProductType = 0L;
    private long timingProductId = 0L;
    private long timingTimestamp = 0L;
    private long timingData = 0L;

    public EventInput() {
        this.eventType = null;
        this.userId = null;
        this.productType = null;
        this.productId = null;
        this.timestamp = null;
        this.data = Collections.emptyList();
    }

    public ConsumerEventType getEventType() {
        return eventType;
    }

    public void setEventType(final ConsumerEventType eventType) {
        this.eventType = eventType;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(final String userId) {
        this.userId = userId;
    }

    public ProductType getProductType() {
        return productType;
    }

    public void setProductType(final ProductType productType) {
        this.productType = productType;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(final String productId) {
        this.productId = productId;
    }

    public Timestamp getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(final Timestamp timestamp) {
        this.timestamp = timestamp;
    }

    public List<ConsumerEventDataInput> getData() {
        return data;
    }

    public void setData(final List<ConsumerEventDataInput> data) {
        this.data = data;
    }
    
    // StructuredTokenParser interface

    @Override
    public final void begin(final String memberName, final Map<String, Object> params) {
        lastTiming = System.nanoTime();
    }

    @SuppressWarnings("unchecked")
    @Override
    public final void consume(final String memberName, final Object member) throws ResponseException {
        try {
            switch (memberName) {
                case TAG_TYPE:
                    setEventType((ConsumerEventType) member);
                    timingEventType = System.nanoTime() - lastTiming;
                    break;
                case TAG_USER_ID:
                    setUserId((String) member);
                    timingUserId = System.nanoTime() - lastTiming;
                    break;
                case TAG_PRODUCT_TYPE:
                    setProductType((ProductType) member);
                    timingProductType = System.nanoTime() - lastTiming;
                    break;
                case TAG_PRODUCT_ID:
                    setProductId((String) member);
                    timingProductId = System.nanoTime() - lastTiming;
                    break;
                case TAG_TIMESTAMP:
                    setTimestamp((Timestamp) member);
                    timingTimestamp = System.nanoTime() - lastTiming;
                    break;
                case TAG_DATA:
                    setData((List<ConsumerEventDataInput>) member);
                    timingData = System.nanoTime() - lastTiming;
                    break;
                default:
                    throw new ResponseException(ResponseStatus.INTERNAL_PROCESSING_ERROR, "Attempted to set a value to an unknown member of a EventInput instance: " + memberName);
            }
        }
        catch (ClassCastException e) {
            throw new ResponseException(ResponseStatus.INTERNAL_PROCESSING_ERROR, e, "Attempted to set a value of invalid type to the member " + memberName + " of a EventInput instance: " + member.getClass().getCanonicalName());
        }
    }

    @Override
    public final EventInput finish() throws ResponseException {
        // TODO: verify that all required data entries are present, as marked in consumer_event_type_data
        final long beginNano = System.nanoTime();
        // verify there are no duplicate event data types
        final Set<String> events = new HashSet<String>();
        for (final ConsumerEventDataInput item : data) {
            final String identifier = item.getType().getIdentifier();
            if (events.contains(identifier)) throw new ResponseException(ResponseStatus.DUPLICATE_CONSUMER_EVENT_DATA, "There is more than one instance of consumer event data with identifier " + identifier);
            events.add(identifier);
        }
        if (eventType == null) throw new ResponseException(ResponseStatus.EVENT_TYPE_MISSING);
        if (userId == null) throw new ResponseException(ResponseStatus.USERNAME_MISSING);
        if (timestamp == null) timestamp = new Timestamp(Timer.currentTimeMillis());
        final long endNano = System.nanoTime();
        final long everythingNano = endNano - timingCreate;
        if (everythingNano > 1000000L) { // more than 1ms -> not cool
            log.debug("Timings: all: " + everythingNano + " ns, eventType: " + timingEventType + " ns, userId: " + timingUserId + " ns, productType: " + timingProductType + " ns, productId: " + timingProductId + " ns, timestamp: " + timingTimestamp + " ns, data: " + timingData + " ns, finish: " + (endNano - beginNano) + " ns");
        }
        return this;
    }

    // driver code

    public static EventInput parse(final String format, final String input) throws ResponseException {
        final Object result = Rule.parse(format, input, parseRule, null);
        if (result instanceof EventInput) return (EventInput)result;
        throw new ResponseException(ResponseStatus.INTERNAL_PROCESSING_ERROR, "Internal error: parse did not result in a EventInput instance, but instead " + result.getClass().getCanonicalName());
    }

    private static final class Maker implements StructuredTokenParserMaker {
        @Override
        public StructuredTokenParser create(final Map<String, Object> params) {
            return new EventInput();
        }
    }
}
