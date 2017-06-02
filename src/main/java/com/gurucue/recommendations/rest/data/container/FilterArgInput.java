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
import com.gurucue.recommendations.parser.AnyValueParser;
import com.gurucue.recommendations.parser.Rule;
import com.gurucue.recommendations.parser.StringParser;
import com.gurucue.recommendations.parser.StructuredTokenParser;
import com.gurucue.recommendations.parser.StructuredTokenParserMaker;
import com.gurucue.recommendations.tokenizer.Token;

import java.io.Serializable;
import java.util.Map;

/**
 * Parses parameters of a filter, as an argument for a blender.
 */
public final class FilterArgInput implements Serializable, StructuredTokenParser {
    private static final long serialVersionUID = -7683358497198685184L;

    public static final String TAG_IDENTIFIER = "identifier";
    public static final String TAG_PARAMETERS = "parameters";
    static final StructuredTokenParserMaker maker = new Maker();

    static final Rule parseRule = Rule.map("filter", true, maker, new Rule[]{
            Rule.value(TAG_IDENTIFIER, false, StringParser.parser),
            new AnyValueParser(TAG_PARAMETERS, true)
    });

    private String identifier;
    private Token parameters;

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public Token getParameters() {
        return parameters;
    }

    public void setParameters(Token parameters) {
        this.parameters = parameters;
    }

    // StructuredTokenParser interface

    @Override
    public void begin(final String memberName, final Map<String, Object> params) throws ResponseException {
        // nothing to be initialized
    }

    @SuppressWarnings("unchecked")
    @Override
    public void consume(final String memberName, final Object member) throws ResponseException {
        try {
            switch (memberName) {
                case TAG_IDENTIFIER:
                    setIdentifier((String) member);
                    break;

                case TAG_PARAMETERS:
                    if ((member == null) || (member instanceof Token)) setParameters((Token) member);
                    else throw new ResponseException(ResponseStatus.INTERNAL_PROCESSING_ERROR, "Attempted to set as parameters an instance that is not a Token: " + member.getClass().getCanonicalName());
                    break;

                default:
                    throw new ResponseException(ResponseStatus.INTERNAL_PROCESSING_ERROR, "Attempted to set a value to an unknown member of a FilterArgInput instance: " + memberName);
            }
        }
        catch (ClassCastException e) {
            throw new ResponseException(ResponseStatus.INTERNAL_PROCESSING_ERROR, e, "Attempted to set a value of invalid type to the member " + memberName + " of a FilterArgInput instance: " + member.getClass().getCanonicalName());
        }
    }

    @Override
    public Object finish() throws ResponseException {
        return this;
    }

    // driver code

    public static FilterArgInput parse(final String format, final String input) throws ResponseException {
        final Object result = Rule.parse(format, input, parseRule, null);
        if (result instanceof FilterArgInput) return (FilterArgInput)result;
        throw new ResponseException(ResponseStatus.INTERNAL_PROCESSING_ERROR, "Internal error: parse did not result in a FilterArgInput instance, but instead " + result.getClass().getCanonicalName());
    }

    private static final class Maker implements StructuredTokenParserMaker {
        @Override
        public StructuredTokenParser create(final Map<String, Object> params) {
            return new FilterArgInput();
        }
    }
}
