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
package com.gurucue.recommendations.rest.data;

import com.gurucue.recommendations.ResponseException;
import com.gurucue.recommendations.tokenizer.*;
import org.junit.Test;

import junit.framework.TestCase;

import java.util.NoSuchElementException;

public class XMLTokenizerTest extends TestCase {
    @Test
    public void testSimple() {
        final String simpleXML = "<root><part1>something</part1><part2>other thing</part2></root>";
        XMLTokenizer tokenizer = new XMLTokenizer(simpleXML);
        try {
            Token token = tokenizer.nextToken();
            assertTrue("The root token is not an XMLTreeToken", token instanceof XMLTreeToken);
            assertTrue("The root token is not named \"root\"", "root".equals(token.getName()));
            XMLTreeToken rootToken = (XMLTreeToken)token;

            assertTrue("The root token indicates there is no first sub-token", rootToken.hasNext());
            token = rootToken.next();
            assertTrue("The first sub-token is not named \"part1\"", "part1".equals(token.getName()));
            assertTrue("The \"part1\" sub-token is not a StringToken", token instanceof StringToken);
            StringToken stringToken = (StringToken)token;
            assertTrue("The \"part1\" string sub-token doesn't contain the string \"something\", instead it contains \"" + (null == stringToken.asString() ? "null" : stringToken.asString()) + "\"", "something".equals(stringToken.asString()));

            assertTrue("The root token indicates there is no second sub-token", rootToken.hasNext());
            token = rootToken.next();
            assertTrue("The second sub-token is not named \"part2\"", "part2".equals(token.getName()));
            assertTrue("The \"part2\" sub-token is not a StringToken", token instanceof StringToken);
            stringToken = (StringToken)token;
            assertTrue("The \"part2\" string sub-token doesn't contain the string \"other thing\", instead it contains \"" + (null == stringToken.asString() ? "null" : stringToken.asString()) + "\"", "other thing".equals(stringToken.asString()));

            assertStructureEnd(rootToken);

            token = tokenizer.nextToken();
            assertNull("The tokenizer didn't finish after the root token finished, I got a token \"" + (null == token ? "null" : token.getName()) + "\" of class " + (null == token ? "null" : token.getClass().getCanonicalName()), token);
        }
        catch (ResponseException e) {
            e.printStackTrace();
            fail("A ResponseException was raised: " + e.toString());
        }
    }
    
    @Test
    public void testSimpleWithXMLDeclaration() {
        final String simpleXML = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><root><part1>something</part1><part2>other thing</part2></root>";
        XMLTokenizer tokenizer = new XMLTokenizer(simpleXML);
        try {
            Token token = tokenizer.nextToken();
            assertTrue("The root token is not an XMLTreeToken", token instanceof XMLTreeToken);
            assertTrue("The root token is not named \"root\"", "root".equals(token.getName()));
            XMLTreeToken rootToken = (XMLTreeToken)token;

            assertTrue("The root token indicates there is no first sub-token", rootToken.hasNext());
            token = rootToken.next();
            assertTrue("The first sub-token is not named \"part1\"", "part1".equals(token.getName()));
            assertTrue("The \"part1\" sub-token is not a StringToken", token instanceof StringToken);
            StringToken stringToken = (StringToken)token;
            assertTrue("The \"part1\" string sub-token doesn't contain the string \"something\", instead it contains \"" + (null == stringToken.asString() ? "null" : stringToken.asString()) + "\"", "something".equals(stringToken.asString()));

            assertTrue("The root token indicates there is no second sub-token", rootToken.hasNext());
            token = rootToken.next();
            assertTrue("The second sub-token is not named \"part2\"", "part2".equals(token.getName()));
            assertTrue("The \"part2\" sub-token is not a StringToken", token instanceof StringToken);
            stringToken = (StringToken)token;
            assertTrue("The \"part2\" string sub-token doesn't contain the string \"other thing\", instead it contains \"" + (null == stringToken.asString() ? "null" : stringToken.asString()) + "\"", "other thing".equals(stringToken.asString()));

            assertStructureEnd(rootToken);

            token = tokenizer.nextToken();
            assertNull("The tokenizer didn't finish after the root token finished, I got a token \"" + (null == token ? "null" : token.getName()) + "\" of class " + (null == token ? "null" : token.getClass().getCanonicalName()), token);
        }
        catch (ResponseException e) {
            e.printStackTrace();
            fail("A ResponseException was raised: " + e.toString());
        }
    }
    
    @Test
    public void testComplex() {
        final String complexXML = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                "<complex>\n" +
                "<id>123abc</id>\n" +
                "<content>\n" +
                "<name>guru</name>" +
                "<empty/>" +
                "<nothing>  </nothing>" +
                "</content>\n" +
                "<tags>" +
                "<tag>12</tag>" +
                "</tags>" +
                "</complex>";
        XMLTokenizer tokenizer = new XMLTokenizer(complexXML);
        try {
            Token token = tokenizer.nextToken();
            assertTokenNameAndType(token, XMLTreeToken.class, "complex");
            XMLTreeToken rootToken = (XMLTreeToken)token;
            assertTokenNameAndTypeAndContent(rootToken.next(), StringToken.class, "id", "123abc");
            token = rootToken.next();
            assertTokenNameAndType(token, XMLTreeToken.class, "content");
            XMLTreeToken subtree = (XMLTreeToken)token;
            assertTokenNameAndTypeAndContent(subtree.next(), StringToken.class, "name", "guru");
            assertTokenNameAndType(subtree.next(), NullToken.class, "empty");
            assertTokenNameAndType(subtree.next(), NullToken.class, "nothing");
            assertStructureEnd(subtree);
            token = rootToken.next();
            assertTokenNameAndType(token, XMLTreeToken.class, "tags");
            subtree = (XMLTreeToken)token;
            assertTokenNameAndTypeAndContent(subtree.next(), StringToken.class, "tag", "12");
            assertStructureEnd(subtree);
            assertStructureEnd(rootToken);
        }
        catch (ResponseException e) {
            e.printStackTrace();
            fail("A ResponseException was raised: " + e.toString());
        }
    }

    private void assertTokenNameAndType(final Token token, final Class<? extends Token> tokenClass, final String tokenName) {
        assertTrue("The \"" + tokenName + "\" token is not named \"" + tokenName + "\": \"" + (null == token.getName() ? "null" : token.getName()) + "\"", tokenName.equals(token.getName()));
        assertTrue("The \"" + tokenName + "\" token is not a " + tokenClass.getName() + ", but instead " + token.getClass().getName(), tokenClass.isInstance(token));
    }
    
    private void assertTokenNameAndTypeAndContent(final Token token, final Class<? extends Token> tokenClass, final String tokenName, final String content) {
        assertTokenNameAndType(token, tokenClass, tokenName);
        if ((null == content) || (content.length() == 0)) assertTrue(token instanceof NullToken);
        else {
            try {
                assertTrue((token instanceof PrimitiveToken) && content.equals(((PrimitiveToken)token).asString()));
            }
            catch (ResponseException e) {
                e.printStackTrace();
                fail("ResponseException while trying to obtain string content of the \"" + tokenName + "\" token");
            }
        }
    }
    
    private void assertStructureEnd(final StructuredToken structuredToken) {
        try {
            assertFalse("The \"" + structuredToken.getName() + "\" token doesn't finish correctly, the hasNext() indicates there exists a next element, while it shouldn't", structuredToken.hasNext());
        } catch (ResponseException e) {
            e.printStackTrace();
            fail("ResponseException while checking hasNext() for end of tree-token");
            return;
        }

        Token token;
        try {
            token = structuredToken.next();
        }
        catch (ResponseException e) {
            e.printStackTrace();
            fail("ResponseException while checking next() for end of tree-token");
            return;
        }
        catch (NoSuchElementException e) {
            return; // this is correct when we expect no more elements
        }
        assertNotNull("The \"" + structuredToken.getName() + "\" token doesn't finish correctly, it returns null instead of throwing a NoSuchElementException", token);
        fail("The \"" + structuredToken.getName() + "\" token doesn't finish correctly, I got a token \"" + token.getName() + "\" of class " + token.getClass().getCanonicalName() + " when there should be none more");
    }
}
