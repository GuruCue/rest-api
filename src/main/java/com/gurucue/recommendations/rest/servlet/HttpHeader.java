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

import java.io.Serializable;

/**
 * Describes a HTTP header: it contains the header name and its value.
 */
public class HttpHeader implements Serializable {
    private static final long serialVersionUID = -5008827048113017199L;
    public static final HttpHeader[] NO_HEADERS = new HttpHeader[0];

    public final String name;
    public final String value;

    public HttpHeader(final String name, final String value) {
        this.name = name;
        this.value = value;
    }
}
