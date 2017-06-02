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
 * Thrown to indicate a HTTP 400 Bad Request client error.
 * @see HttpException
 */
public class HttpBadRequestException extends HttpException {
    private static final long serialVersionUID = -6250376772486556980L;

    public HttpBadRequestException() {
        super(HttpServletResponse.SC_BAD_REQUEST, "Bad Request");
    }

    public HttpBadRequestException(final String message) {
        super(HttpServletResponse.SC_BAD_REQUEST, message);
    }

    public HttpBadRequestException(final String message, final Throwable cause) {
        super(HttpServletResponse.SC_BAD_REQUEST, message, cause);
    }

    public HttpBadRequestException(final Throwable cause) {
        super(HttpServletResponse.SC_BAD_REQUEST, "Bad Request", cause);
    }
}
