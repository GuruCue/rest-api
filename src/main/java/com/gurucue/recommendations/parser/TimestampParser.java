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
import com.gurucue.recommendations.ResponseStatus;
import com.gurucue.recommendations.tokenizer.PrimitiveToken;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.TimeZone;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public final class TimestampParser implements PrimitiveTokenParser {
    public static final TimestampParser parser = new TimestampParser();

    private static final Lock timestampFormatLock = new ReentrantLock();
    private static final SimpleDateFormat[] timestampFormats = new SimpleDateFormat[] {
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"),
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ssXXX"),
            new SimpleDateFormat("yyyy-MM-dd HH:mm")
    };
    private static final TimeZone utcTimezone = TimeZone.getTimeZone("UTC");

    @Override
    public Object parse(final PrimitiveToken token) throws ResponseException {
        try {
            Long l = token.asLong();
            if (l == null) return null;
            return new Timestamp(l * 1000L); // convert seconds to milliseconds
        }
        catch (ResponseException|RuntimeException e) {
            // pass
        }

        final String timestampString = token.asString();

        timestampFormatLock.lock();
        try {
            for (int i = 0; i < timestampFormats.length; i++) {
                try {
                    final SimpleDateFormat f = timestampFormats[i];
                    f.setTimeZone(utcTimezone); // any previous parsing may have left the SimpleDateFormat in a different timezone
                    return new Timestamp(f.parse(timestampString).getTime());
                }
                catch (java.text.ParseException e) {}
            }
        }
        finally {
            timestampFormatLock.unlock();
        }

        throw new ResponseException(ResponseStatus.ILLEGAL_TIMESTAMP_VALUE, "Illegal timestamp value: don't know how to parse \"" + timestampString.replace("\"", "\\\"") + "\"");
    }
}
