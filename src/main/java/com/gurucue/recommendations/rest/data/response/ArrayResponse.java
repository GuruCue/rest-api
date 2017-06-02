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
package com.gurucue.recommendations.rest.data.response;

import com.gurucue.recommendations.translator.DataTranslator;
import com.gurucue.recommendations.translator.TranslatorAware;

import java.io.IOException;
import java.util.List;

public class ArrayResponse implements TranslatorAware {
    private final String toplevelName;
    private final List<? extends TranslatorAware> elements;

    public ArrayResponse(final String toplevelName, final List<? extends TranslatorAware> elements) {
        this.toplevelName = toplevelName;
        this.elements = elements;
    }

    @Override
    public void translate(final DataTranslator translator) throws IOException {
        translator.beginArray(toplevelName);
        translator.addSequence(elements);
        translator.endArray();
    }
}
