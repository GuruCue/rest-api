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
package com.gurucue.recommendations.rest.servlet;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Enumerates all acceptable mime-types for REST requests and responses.
 */
public enum MimeType {
    APPLICATION_XML(ContentFormat.XML, "application/xml"),
    APPLICATION_JSON(ContentFormat.JSON, "application/json"),
    TEXT_XML(ContentFormat.XML, "text/xml"),
    TEXT_JSON(ContentFormat.JSON, "text/json");

    private static final Map<String, MimeType> allTypes;
    private final static Map<String, MimeType> mediaRanges;
    static {
        final Map<String, MimeType> types = new HashMap<String, MimeType>();
        for (MimeType t : values()) types.put(t.TYPE_NAME, t);
        allTypes = Collections.unmodifiableMap(types);
        // copy all mime types to media ranges
        final Map<String, MimeType> ranges = new HashMap<String, MimeType>(types);
        // and prefer XML
        ranges.put("application/*", APPLICATION_XML);
        ranges.put("text/*", TEXT_XML);
        ranges.put("*/*", APPLICATION_XML);
        mediaRanges = Collections.unmodifiableMap(ranges);
    }

    public final ContentFormat CONTENT_FORMAT;
    public final String TYPE_NAME;

    MimeType(final ContentFormat contentFormat, final String typeName) {
        this.CONTENT_FORMAT = contentFormat;
        this.TYPE_NAME = typeName;
    }

    public static MimeType fromMimeTypeName(final String typeName) {
        return allTypes.get(typeName);
    }

    public static MimeType fromMediaRange(final String mediaRange) {
        return mediaRanges.get(mediaRange);
    }

    public static MimeType fromContentType(final String contentType) {
        if (null == contentType) return null;
        return fromMimeTypeName(contentType.split(";")[0].trim());
    }
}
