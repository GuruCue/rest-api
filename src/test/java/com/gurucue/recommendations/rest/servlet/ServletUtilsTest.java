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

import junit.framework.TestCase;
import org.junit.Test;

import java.util.Vector;

/**
 * Test case for the {@link com.gurucue.recommendations.rest.servlet.ServletUtils}.
 */
public class ServletUtilsTest extends TestCase {
    @Test
    public void testPathInfoFragmentsPrefixedAndSuffixedDelimiter() {
        try {
            String[] fragments = ServletUtils.pathInfoFragments("/one/two/");
            assertEquals("There should be exactly 2 fragments", 2, fragments.length);
            assertEquals("The first fragment should be \"one\"", "one", fragments[0]);
            assertEquals("The second fragment should be \"two\"", "two", fragments[1]);
        }
        catch (HttpException e) {
            e.printStackTrace();
            throw new RuntimeException("A ResponseException was raised: " + e.toString(), e);
        }
    }

    @Test
    public void testPathInfoFragmentsPrefixedDelimiter() {
        try {
            String[] fragments = ServletUtils.pathInfoFragments("/one/two");
            assertEquals("There should be exactly 2 fragments", 2, fragments.length);
            assertEquals("The first fragment should be \"one\"", "one", fragments[0]);
            assertEquals("The second fragment should be \"two\"", "two", fragments[1]);
        }
        catch (HttpException e) {
            e.printStackTrace();
            throw new RuntimeException("A ResponseException was raised: " + e.toString(), e);
        }
    }

    @Test
    public void testPathInfoFragmentsSuffixedDelimiter() {
        try {
            String[] fragments = ServletUtils.pathInfoFragments("one/two/");
            assertEquals("There should be exactly 2 fragments", 2, fragments.length);
            assertEquals("The first fragment should be \"one\"", "one", fragments[0]);
            assertEquals("The second fragment should be \"two\"", "two", fragments[1]);
        }
        catch (HttpException e) {
            e.printStackTrace();
            throw new RuntimeException("A ResponseException was raised: " + e.toString(), e);
        }
    }

    @Test
    public void testPathInfoFragmentsNoPrefixedAndSuffixedDelimiter() {
        try {
            String[] fragments = ServletUtils.pathInfoFragments("one/two");
            assertEquals("There should be exactly 2 fragments", 2, fragments.length);
            assertEquals("The first fragment should be \"one\"", "one", fragments[0]);
            assertEquals("The second fragment should be \"two\"", "two", fragments[1]);
        }
        catch (HttpException e) {
            e.printStackTrace();
            throw new RuntimeException("A ResponseException was raised: " + e.toString(), e);
        }
    }

    @Test
    public void testPathInfoFragmentsNullPathInfo() {
        try {
            String[] fragments = ServletUtils.pathInfoFragments(null);
            assertEquals("There should be exactly 0 fragments", 0, fragments.length);
        }
        catch (HttpException e) {
            e.printStackTrace();
            throw new RuntimeException("A ResponseException was raised: " + e.toString(), e);
        }
    }

    @Test
    public void testPathInfoFragmentsEmptyPathInfo() {
        try {
            String[] fragments = ServletUtils.pathInfoFragments("");
            assertEquals("There should be exactly 0 fragments", 0, fragments.length);
        }
        catch (HttpException e) {
            e.printStackTrace();
            throw new RuntimeException("A ResponseException was raised: " + e.toString(), e);
        }
    }

    @Test
    public void testPathInfoFragmentsOnlySingleDelimiter() {
        try {
            String[] fragments = ServletUtils.pathInfoFragments("/");
            assertEquals("There should be exactly 0 fragments", 0, fragments.length);
        }
        catch (HttpException e) {
            e.printStackTrace();
            throw new RuntimeException("A ResponseException was raised: " + e.toString(), e);
        }
    }

    @Test
    public void testPathInfoFragmentsOnlyFiveSuccessiveDelimiters() {
        try {
            String[] fragments = ServletUtils.pathInfoFragments("/////");
            assertEquals("There should be exactly 0 fragments", 0, fragments.length);
        }
        catch (HttpException e) {
            e.printStackTrace();
            throw new RuntimeException("A ResponseException was raised: " + e.toString(), e);
        }
    }

    @Test
    public void testPathInfoFragmentsTwoFragmentsSeparatedByFiveSuccessiveDelimiters() {
        try {
            String[] fragments = ServletUtils.pathInfoFragments("one/////two");
            assertEquals("There should be exactly 2 fragments", 2, fragments.length);
            assertEquals("The first fragment should be \"one\"", "one", fragments[0]);
            assertEquals("The second fragment should be \"two\"", "two", fragments[1]);
        }
        catch (HttpException e) {
            e.printStackTrace();
            throw new RuntimeException("A ResponseException was raised: " + e.toString(), e);
        }
    }

    @Test
    public void testPathInfoFragmentsPrefixedByFiveSuccessiveDelimiters() {
        try {
            String[] fragments = ServletUtils.pathInfoFragments("/////one/two");
            assertEquals("There should be exactly 2 fragments", 2, fragments.length);
            assertEquals("The first fragment should be \"one\"", "one", fragments[0]);
            assertEquals("The second fragment should be \"two\"", "two", fragments[1]);
        }
        catch (HttpException e) {
            e.printStackTrace();
            throw new RuntimeException("A ResponseException was raised: " + e.toString(), e);
        }
    }

    @Test
    public void testPathInfoFragmentsSuffixedByFiveSuccessiveDelimiters() {
        try {
            String[] fragments = ServletUtils.pathInfoFragments("one/two/////");
            assertEquals("There should be exactly 2 fragments", 2, fragments.length);
            assertEquals("The first fragment should be \"one\"", "one", fragments[0]);
            assertEquals("The second fragment should be \"two\"", "two", fragments[1]);
        }
        catch (HttpException e) {
            e.printStackTrace();
            throw new RuntimeException("A ResponseException was raised: " + e.toString(), e);
        }
    }

    @Test
    public void testParseAcceptHeadersExactChoice() {
        // build an enumeration
        Vector<String> headers = new Vector<String>();
        headers.add("audio/*, application/xml; q=0.2, application/json; q=0.4");
        headers.add("application/binary; q=0.9, text/xml; q=0.7, application/zip");
        // test
        MimeType mimeType = ServletUtils.parseAcceptHeaders(headers.elements());
        assertEquals(MimeType.TEXT_XML, mimeType);
    }

    @Test
    public void testParseAcceptHeadersMediaRangeParticularChoice() {
        // build an enumeration
        Vector<String> headers = new Vector<String>();
        headers.add("audio/*, application/xml; q=0.2, application/json; q=0.4");
        headers.add("application/binary; q=0.9, text/*; q=0.7, application/zip");
        // test
        MimeType mimeType = ServletUtils.parseAcceptHeaders(headers.elements());
        assertEquals(MimeType.TEXT_XML, mimeType);
    }

    @Test
    public void testParseAcceptHeadersMediaRangeGeneralChoice() {
        // build an enumeration
        Vector<String> headers = new Vector<String>();
        headers.add("audio/*, text/xml; q=0.2, application/json; q=0.4");
        headers.add("application/binary; q=0.9, */*");
        // test
        MimeType mimeType = ServletUtils.parseAcceptHeaders(headers.elements());
        assertEquals(MimeType.APPLICATION_XML, mimeType);
    }
}
