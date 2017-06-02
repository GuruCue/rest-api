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

import com.gurucue.recommendations.tokenizer.Token;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class AnyCollection implements Token {
    public final String name;
    public final List<Token> collection;
    public final boolean isList;
    public final boolean isMap;

    public AnyCollection(
            final String name,
            final List<Token> collection,
            final boolean isList,
            final boolean isMap
    ) {
        this.name = name;
        this.collection = collection;
        this.isList = isList;
        this.isMap = isMap;
    }

    public List<Token> toList() {
        return collection;
    }

    public Map<String, Token> toMap() {
        final Map<String, Token> result = new HashMap<>(collection.size());
        collection.forEach((final Token t) -> result.put(t.getName(), t));
        return result;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean isNull() {
        return collection == null;
    }
}
