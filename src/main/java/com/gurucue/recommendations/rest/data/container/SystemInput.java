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

import com.gurucue.recommendations.parser.BooleanParser;
import com.gurucue.recommendations.parser.IntegerParser;
import com.gurucue.recommendations.parser.Rule;
import com.gurucue.recommendations.parser.StringParser;
import com.gurucue.recommendations.parser.StructuredTokenParser;
import com.gurucue.recommendations.parser.StructuredTokenParserMaker;
import com.gurucue.recommendations.ResponseException;
import com.gurucue.recommendations.ResponseStatus;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * System request's parser definition and data container.
 */
public final class SystemInput implements Serializable, StructuredTokenParser {
    private static final long serialVersionUID = -4851154799446674466L;

    static final String TAG_REFRESH_CACHE = "refreshCache";
    static final String TAG_AI_FULL_UPDATE = "aiFullUpdate";
    static final String TAG_NEW_CONSUMER_EVENT_QUEUE_SIZE = "newConsumerEventQueueSize";
    static final String TAG_NEW_CONSUMER_EVENT_THREAD_POOL_SIZE = "newConsumerEventThreadPoolSize";
    static final String TAG_DEBUG_CONSUMERS = "debugConsumers";

    static final Rule parseRule = Rule.map("request", false, new Maker(), new Rule[] {
            Rule.value(TAG_REFRESH_CACHE, true, BooleanParser.parser),
            Rule.value(TAG_AI_FULL_UPDATE, true, BooleanParser.parser),
            Rule.value(TAG_NEW_CONSUMER_EVENT_QUEUE_SIZE, true, IntegerParser.parser),
            Rule.value(TAG_NEW_CONSUMER_EVENT_THREAD_POOL_SIZE, true, IntegerParser.parser),
            Rule.list(TAG_DEBUG_CONSUMERS, true, String.class, Rule.value("debuggedConsumers", true, StringParser.parser))
    });

    private boolean refreshCache;
    private boolean aiFullUpdate;
    private Integer newConsumerEventQueueSize;
    private Integer newConsumerEventThreadPoolSize;
    private List<String> debuggedConsumers;

    public SystemInput() {
        refreshCache = false;
        aiFullUpdate = false;
        newConsumerEventQueueSize = null;
        newConsumerEventThreadPoolSize = null;
    }

    public boolean isRefreshCache() {
        return refreshCache;
    }

    public void setRefreshCache(final boolean refreshCache) {
        this.refreshCache = refreshCache;
    }

    public boolean isAiFullUpdate() {
        return aiFullUpdate;
    }

    public void setAiFullUpdate(final boolean aiFullUpdate) {
        this.aiFullUpdate = aiFullUpdate;
    }

    public Integer getNewConsumerEventQueueSize() {
        return newConsumerEventQueueSize;
    }

    public void setTagNewConsumerEventQueueSize(final Integer newConsumerEventQueueSize) {
        this.newConsumerEventQueueSize = newConsumerEventQueueSize;
    }

    public Integer getNewConsumerEventThreadPoolSize() {
        return newConsumerEventThreadPoolSize;
    }

    public void setTagNewConsumerEventThreadPoolSize(final Integer newConsumerEventThreadPoolSize) {
        this.newConsumerEventThreadPoolSize = newConsumerEventThreadPoolSize;
    }

    public List<String> getDebuggedConsumers() {
        return debuggedConsumers;
    }

    public void setDebuggedConsumers(final List<String> debuggedConsumers) {
        this.debuggedConsumers = debuggedConsumers;
    }

    // utility methods

    // StructuredTokenParser interface

    @Override
    public void begin(final String memberName, final Map<String, Object> params) {}

    @Override
    public void consume(final String memberName, final Object member) throws ResponseException {
        try {
            switch (memberName) {
                case TAG_REFRESH_CACHE:
                    if (null == member) return;
                    setRefreshCache(((Boolean) member).booleanValue());
                    break;
                case TAG_AI_FULL_UPDATE:
                    if (null == member) return;
                    setAiFullUpdate(((Boolean) member).booleanValue());
                    break;
                case TAG_NEW_CONSUMER_EVENT_QUEUE_SIZE:
                    setTagNewConsumerEventQueueSize((Integer) member);
                    break;
                case TAG_NEW_CONSUMER_EVENT_THREAD_POOL_SIZE:
                    setTagNewConsumerEventThreadPoolSize((Integer) member);
                    break;
                case TAG_DEBUG_CONSUMERS:
                    setDebuggedConsumers((List<String>) member);
                    break;
            }
        }
        catch (ClassCastException e) {
            throw new ResponseException(ResponseStatus.INTERNAL_PROCESSING_ERROR, e, "Attempted to set a value of invalid type to the member " + memberName + " of a SystemInput instance: " + member.getClass().getCanonicalName());
        }
    }

    @Override
    public SystemInput finish() throws ResponseException {
        return this;
    }

    // driver code

    public static SystemInput parse(final String format, final String input) throws ResponseException {
        final Object result = Rule.parse(format, input, parseRule, null);
        if (result instanceof SystemInput) return (SystemInput)result;
        throw new ResponseException(ResponseStatus.INTERNAL_PROCESSING_ERROR, "Internal error: parse did not result in a SystemInput instance, but instead " + result.getClass().getCanonicalName());
    }

    private static class Maker implements StructuredTokenParserMaker {
        @Override
        public StructuredTokenParser create(final Map<String, Object> params) {
            return new SystemInput();
        }
    }
}
