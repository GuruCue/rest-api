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

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.CharBuffer;
import java.util.*;

/**
 * Utility methods for use in servlets.
 */
public final class ServletUtils {
    /**
     * The path-info subdirectory delimiter.
     */
    private final static char PATH_DELIMITER = '/';
    /**
     * Maximum number of sub-directories in a path-info.
     */
    private final static int MAX_PATH_FRAGMENTS = 10;
    /**
     * A constant empty array for requests where a path-info is not present.
     */
    private final static String[] NO_FRAGMENTS = new String[0];
    /**
     * Defines the maximum request size for {@link #streamToString(java.io.BufferedReader)}.
     * If a request is longer than this, then a HTTP 413 Entity Too Large
     * exception is thrown.
     */
    private final static int MAX_ENTITY_SIZE = 128 * 1024; // 128 kB

    private ServletUtils() {} // not instantiable

    /**
     * <p>Splits any path info into path fragments separated by the path delimiter <code>/</code>.
     * Usually used as: <code>String[] fragments = ServletUtils.pathInfoFragments(request.getPathInfo());</code></p>
     * <p>Example:</p>
     * <ul>
     *     <li>if the servlet's url pattern is <code>"/srv/*"</code> and a client accessed it with
     *         a request having path "/srv/id/time", then this method returns {"id", "time"}.</li>
     * </ul>
     * Starting and trailing delimiters do not result in starting and trailing empty strings.
     * Any number of successive delimiters is treated as a single delimiter.
     *
     * @param pathInfo    the path-info part of the URI path, that comes after the servlet path part of the URI (as returned by {@link javax.servlet.http.HttpServletRequest#getPathInfo()})
     * @return an array of subdirectory names that comprise the path-info
     * @throws HttpRequestUriTooLongException in case the path-info is too deep (more than {@link #MAX_PATH_FRAGMENTS} subdirectories)
     */
    public static String[] pathInfoFragments(final String pathInfo) throws HttpRequestUriTooLongException {
        if (null == pathInfo) return NO_FRAGMENTS; // no additional path supplied
        final int l = pathInfo.length();
        if (l == 0) return NO_FRAGMENTS; // no additional path supplied
        int delimiterIndex = pathInfo.indexOf(PATH_DELIMITER);
        if (delimiterIndex < 0) return new String[] { pathInfo }; // no delimiter in the additional path -> this is the only fragment
        if (pathInfo.length() < 2) return NO_FRAGMENTS; // the delimiter is the only thing in the additional path
        final String[] fragments = new String[MAX_PATH_FRAGMENTS];
        int i = 0;
        int previousIndex = 0;
        while (delimiterIndex >= 0) {
            if (delimiterIndex > previousIndex) {
                if (i >= MAX_PATH_FRAGMENTS) throw new HttpRequestUriTooLongException("Request path too deep");
                fragments[i++] = pathInfo.substring(previousIndex, delimiterIndex);
            }
            previousIndex = delimiterIndex + 1; // step just after the current delimiter
            delimiterIndex = pathInfo.indexOf(PATH_DELIMITER, previousIndex); // and find the next delimiter
        }
        if (previousIndex < l) {
            if (i >= MAX_PATH_FRAGMENTS) throw new HttpRequestUriTooLongException("Request path too deep");
            fragments[i++] = pathInfo.substring(previousIndex, l);
        }
        return Arrays.copyOf(fragments, i);
    }

    /**
     * Chooses the requested response format among the optional
     * <code>format</code> parameter given in the URL, the optional
     * <code>Accept</code> headers, and the <code>Content-Type</code> header
     * if given. The <code>format</code> parameter takes precedense and
     * if not given, then the <code>Accept</code> headers are consulted,
     * which is the conventional way of specifying the format of a response.
     * If neither of those is present, then we fallback to the
     * <code>Content-Type</code>. If none of the parameters is present, or
     * if the first present parameter (in the order: format, <code>Accept</code>
     * headers, <code>Content-Type</code> header) is not of supported type,
     * then the HTTP error 406 Not Acceptable is thrown. Therefore it is
     * guaranteed this method either returns a non-null {@link MimeType},
     * or throws a {@link HttpNotAcceptableException} or a
     * {@link HttpBadRequestException}.
     *
     * @param formatParameter       the <code>format</code> parameter of the request (as returned by the {@link javax.servlet.http.HttpServletRequest#getParameter(String)})
     * @param acceptHeaders         the <code>Accept</code> headers of the request (as returned by the {@link javax.servlet.http.HttpServletRequest#getHeaders(String)})
     * @param requestContentType    the already processed <code>Content-Type</code> header (as returned by the {@link RestServlet#processRequestFormat(String)} method)
     * @return the {@link MimeType} value representing the requested response format
     * @throws HttpNotAcceptableException in case the requested response format is not supported, or was not specified
     * @throws HttpBadRequestException in case the <code>Accept</code> headers contain a syntax error
     */
    public static MimeType chooseResponseFormat(final String formatParameter, final Enumeration<String> acceptHeaders, final MimeType requestContentType) throws HttpNotAcceptableException, HttpBadRequestException {
        if (null != formatParameter) {
            // format parameter overrides everything
            MimeType mimeType = MimeType.fromMimeTypeName(formatParameter);
            if (null == mimeType) {
                // perhaps it was given in a non-standard way
                if ("xml".equalsIgnoreCase(formatParameter)) {
                    mimeType = MimeType.APPLICATION_XML;
                }
                else if ("json".equalsIgnoreCase(formatParameter)) {
                    mimeType = MimeType.APPLICATION_JSON;
                }
                else throw new HttpNotAcceptableException("Requested response in an unknown format: " + formatParameter);
            }
            return mimeType;
        }

        if ((null != acceptHeaders) && (acceptHeaders.hasMoreElements())) {
            // if Accept header is specified, then it defines the response format
            MimeType mimeType = parseAcceptHeaders(acceptHeaders);
            if (null == mimeType) throw new HttpNotAcceptableException("Accept header(s) contain no usable media range: only XML and JSON are acceptable formats");
            return mimeType;
        }

        // if none of the above is set, then default to the Content-Type header, if given
        if (null != requestContentType) {
            return requestContentType;
        }

        return MimeType.APPLICATION_JSON; // default MIME type
    }

    public static MimeType parseAcceptHeaders(final Enumeration<String> acceptHeaders) {
        final SortedMap<Double, Set<String>> mediaRanges = new TreeMap<Double, Set<String>>(new ReverseDoubleOrdering());
        while (acceptHeaders.hasMoreElements()) {
            final String header = acceptHeaders.nextElement();
            final String[] ranges = header.split(",");
            for (int i = 0; i < ranges.length; i++) {
                final String[] rangeAndParams = ranges[i].trim().split(";");
                final String mediaRange = rangeAndParams[0].trim();
                final String qvalString = rangeAndParams.length > 1 ? rangeAndParams[1].trim() : "q=1";
                final String[] qvalFragments = qvalString.split("=");
                if (qvalFragments.length != 2) throw new HttpBadRequestException("Illegal q value in an Accept header (syntax error): " + qvalString);
                final Double qval;
                try {
                    qval = Double.valueOf(qvalFragments[1]);
                }
                catch (NumberFormatException e) {
                    throw new HttpBadRequestException("Illegal q value in an Accept header (number format error): " + qvalString, e);
                }
                Set<String> mimeSet = mediaRanges.get(qval);
                if (null == mimeSet) {
                    mimeSet = new HashSet<String>();
                    mediaRanges.put(qval, mimeSet);
                }
                mimeSet.add(mediaRange);
            }
        }

        for (Set<String> mediaRangeSet : mediaRanges.values()) {
            // this iterates according to ascending order of the corresponding keys, i.e. by descending qval in this case
            for (String mediaRange: mediaRangeSet) {
                final MimeType fmt = MimeType.fromMediaRange(mediaRange);
                if (null != fmt) return fmt;
            }
        }

        return null;
    }

    /**
     * Stores the content of the input stream represented by the specified
     * {@link BufferedReader} into a {@link String} and returns it.
     *
     * @param reader    a {@link BufferedReader} instance (as returned by the {@link javax.servlet.http.HttpServletRequest#getReader()})
     * @return a {@link String} with the content of the specified reader
     * @throws IOException if there was an error reading the input
     * @throws HttpRequestEntityTooLargeException if the size of the input stream is bigger than the maximum acceptable entity size
     */
    public static String streamToString(final BufferedReader reader) throws IOException, HttpRequestEntityTooLargeException {
        final CharBuffer buffer = CharBuffer.allocate(2048); // TODO: optimization step: experimentally define a buffer size that can wholly encompass a request in most cases
        final StringBuilder sb = new StringBuilder();
        while (reader.read(buffer) > 0) {
            buffer.flip();
            sb.append(buffer.toString());
            buffer.clear();
            if (sb.length() > MAX_ENTITY_SIZE) throw new HttpRequestEntityTooLargeException();
        }
        return sb.toString();
    }

    private static class ReverseDoubleOrdering implements Comparator<Double> {

        @Override
        public int compare(final Double d1, final Double d2) {
            if (d1 < d2) return 1;
            if (d1 > d2) return -1;
            return 0;
        }

        @Override
        public boolean equals(final Object obj) {
            return (null != obj) && (obj instanceof ReverseDoubleOrdering);
        }
    }
}
