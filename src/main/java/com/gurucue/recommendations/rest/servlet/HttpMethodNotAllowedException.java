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
 * Thrown to indicate a 405 Method Not Allowed. Constructors require the
 * value of the <code>Allowed</code> header, which is basically a list of
 * allowed HTTP methods and must be included in the error response to the
 * client.
 */
public class HttpMethodNotAllowedException extends HttpException {
    private static final long serialVersionUID = 6817350506083667203L;

    public HttpMethodNotAllowedException(final String allowedHttpMethods) {
        super(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "Method Not Allowed", new HttpHeader[]{new HttpHeader("Allowed", allowedHttpMethods)});
    }

    public HttpMethodNotAllowedException(final String allowedHttpMethods, final String message) {
        super(HttpServletResponse.SC_METHOD_NOT_ALLOWED, message, new HttpHeader[]{new HttpHeader("Allowed", allowedHttpMethods)});
    }

    public HttpMethodNotAllowedException(final String allowedHttpMethods, final String message, final Throwable cause) {
        super(HttpServletResponse.SC_METHOD_NOT_ALLOWED, message, new HttpHeader[]{new HttpHeader("Allowed", allowedHttpMethods)}, cause);
    }

    public HttpMethodNotAllowedException(final String allowedHttpMethods, final Throwable cause) {
        super(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "Method Not Allowed", new HttpHeader[]{new HttpHeader("Allowed", allowedHttpMethods)}, cause);
    }
}
