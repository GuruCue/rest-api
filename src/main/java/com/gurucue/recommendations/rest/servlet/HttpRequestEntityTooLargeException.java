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

import javax.servlet.http.HttpServletResponse;

/**
 * Thrown to indicate a 413 Request Entity Too Large.
 */
public class HttpRequestEntityTooLargeException extends HttpException {
    private static final long serialVersionUID = 3402195984621706472L;

    public HttpRequestEntityTooLargeException() {
        super(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "Request Entity Too Large");
    }

    public HttpRequestEntityTooLargeException(final String message) {
        super(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, message);
    }

    public HttpRequestEntityTooLargeException(final String message, final Throwable cause) {
        super(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, message, cause);
    }

    public HttpRequestEntityTooLargeException(final Throwable cause) {
        super(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "Request Entity Too Large", cause);
    }
}
