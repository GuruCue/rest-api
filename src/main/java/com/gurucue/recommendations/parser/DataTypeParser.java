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
import com.gurucue.recommendations.ResponseException;
import com.gurucue.recommendations.ResponseStatus;
import com.gurucue.recommendations.tokenizer.PrimitiveToken;
import com.gurucue.recommendations.entity.DataType;


public final class DataTypeParser implements PrimitiveTokenParser {
    public static final DataTypeParser parser = new DataTypeParser();

    @Override
    public Object parse(final PrimitiveToken token) throws ResponseException {
        final DataType dataType = DataManager.getDataTypeCodes().byIdentifier(token.asString());
        if (dataType == null) {
            throw new ResponseException(ResponseStatus.INVALID_CONSUMER_EVENT_DATA_TYPE, "There is no data type " + token.asString());
        }
        return dataType;
    }

}
