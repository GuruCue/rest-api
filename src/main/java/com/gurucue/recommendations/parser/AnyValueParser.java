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
package com.gurucue.recommendations.parser;

import com.gurucue.recommendations.ResponseException;
import com.gurucue.recommendations.tokenizer.Iterator;
import com.gurucue.recommendations.tokenizer.ListToken;
import com.gurucue.recommendations.tokenizer.MapToken;
import com.gurucue.recommendations.tokenizer.PrimitiveToken;
import com.gurucue.recommendations.tokenizer.Token;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Parses anything, and returns it either as a PrimitiveToken or AnyCollection.
 * An AnyCollection can contain instances of PrimitiveToken or AnyCollection.
 */
public final class AnyValueParser extends Rule {

    public AnyValueParser(final String name, final boolean optional) {
        super(name, optional);
    }

    @Override
    public Token parse(final Token token, final Map<String, Object> params) throws ResponseException {
        if (token.isNull()) return null;
        if (token instanceof PrimitiveToken) return token;
        final List<Token> result = new LinkedList<>();
        final Iterator it = (Iterator)token;
        while (it.hasNext()) {
            result.add(parse(it.next(), params));
        }
        return new AnyCollection(token.getName(), result, token instanceof ListToken, token instanceof MapToken);
    }
}
