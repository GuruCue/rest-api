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

import com.gurucue.recommendations.data.DataManager;
import com.gurucue.recommendations.entity.Language;
import com.gurucue.recommendations.ResponseException;
import com.gurucue.recommendations.ResponseStatus;
import com.gurucue.recommendations.tokenizer.PrimitiveToken;

public class LanguageParser implements PrimitiveTokenParser {
    public static final LanguageParser parser = new LanguageParser();

    @Override
    public Object parse(final PrimitiveToken token) throws ResponseException {
        String languageCode = token.asString();
        if ((languageCode == null) || (languageCode.length() == 0)) {
            languageCode = Language.UNKNOWN;
        }
        final Language language = DataManager.getCurrentLink().getLanguageManager().getByIdentifier(languageCode);
        if (language == null) {
            throw new ResponseException(ResponseStatus.UNKNOWN_ERROR, "There is no language \"" + token.asString() + "\"");
        }
        return language;
    }
}
