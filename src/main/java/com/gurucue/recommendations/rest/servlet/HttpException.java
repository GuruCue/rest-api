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
import java.io.IOException;

/**
 * Thrown to indicate a HTTP error, mainly used for 4xx class of errors.
 * The 4xx class of status code is intended for cases in which the client
 * seems to have erred. See RFC2616 chapter 10.4 for details.
 */
public class HttpException extends IllegalArgumentException {
    private static final long serialVersionUID = 911269560918613345L;
    private final int statusCode;
    private final HttpHeader[] headers;

    /**
     * Constructs a new exception with the specified status code and message.
     *
     * @param statusCode    the 4xx class of status code (which is saved for later retrieval by {@link #getStatusCode()})
     * @param message       the message accompanying the status code (which is saved for later retrieval by {@link Throwable#getMessage()})
     */
    public HttpException(final int statusCode, final String message) {
        super(message);
        this.statusCode = statusCode;
        headers = HttpHeader.NO_HEADERS;
    }

    /**
     * Constructs a new exception with the specified status code, message, and additional HTTP headers to send to the client with this error.
     *
     * @param statusCode    the 4xx class of status code (which is saved for later retrieval by {@link #getStatusCode()})
     * @param message       the message accompanying the status code (which is saved for later retrieval by {@link Throwable#getMessage()})
     * @param headers       any additional headers that should accompany the error when it is returned to the client (which are saved for later retrieval by {@link #getHeaders()})
     */
    public HttpException(final int statusCode, final String message, final HttpHeader[] headers) {
        super(message);
        this.statusCode = statusCode;
        this.headers = headers;
    }

    /**
     * Constructs a new exception with the specified status code, message, and cause.
     *
     * @param statusCode    the 4xx class of status code (which is saved for later retrieval by {@link #getStatusCode()})
     * @param message       the message accompanying the status code (which is saved for later retrieval by {@link Throwable#getMessage()})
     * @param cause         the cause (which is saved for later retrieval by {@link Throwable#getCause()})
     */
    public HttpException(final int statusCode, final String message, final Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
        headers = HttpHeader.NO_HEADERS;
    }

    /**
     * Constructs a new sception with the specified status code, message, additional headers to send to the client with this error, and cause.
     *
     * @param statusCode    the 4xx class of status code (which is saved for later retrieval by {@link #getStatusCode()})
     * @param message       the message accompanying the status code (which is saved for later retrieval by {@link Throwable#getMessage()})
     * @param headers       any additional headers that should accompany the error when it is returned to the client (which are saved for later retrieval by {@link #getHeaders()})
     * @param cause         the cause (which is saved for later retrieval by {@link Throwable#getCause()})
     */
    public HttpException(final int statusCode, final String message, final HttpHeader[] headers, final Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
        this.headers = headers;
    }

    /**
     * Returns the HTTP status code underlying this exception.
     *
     * @return HTTP status code, which should be used with {@link javax.servlet.http.HttpServletResponse#sendError(int)} or {@link javax.servlet.http.HttpServletResponse#sendError(int, String)}
     */
    public int getStatusCode() {
        return statusCode;
    }

    /**
     * Returns additional HTTP headers to send with this error.
     * @return the array containing additional HTTP headers to return to client with this error
     */
    public HttpHeader[] getHeaders() {
        return headers;
    }

    public void sendError(final HttpServletResponse response) throws IOException {
        for (int i = 0; i < headers.length; i++) {
            final HttpHeader header = headers[i];
            response.setHeader(header.name, header.value);
        }
        response.sendError(statusCode, getMessage());
    }
}
