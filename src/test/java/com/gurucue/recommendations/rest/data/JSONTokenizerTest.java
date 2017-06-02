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
import junit.framework.TestCase;
import org.junit.Test;
import com.gurucue.recommendations.tokenizer.*;

import java.util.NoSuchElementException;

public class JSONTokenizerTest extends TestCase {
    @Test
    public void testSimple() {
        final String simpleJson = "{\"string\": \"something\", \"number\":2,\"bool\":true,\"null\":null}";
        JSONTokenizer tokenizer = new JSONTokenizer(simpleJson);
        try {
            Token token = tokenizer.nextToken();
            assertTrue("The root token is not a JSONObjectToken", token instanceof JSONObjectToken);
            assertNull("The root token's name is not null", token.getName());
            JSONObjectToken rootToken = (JSONObjectToken)token;

            assertTrue("The root token indicates there is no first sub-token", rootToken.hasNext());
            token = rootToken.next();
            assertTrue("The first sub-token is not named \"string\"", "string".equals(token.getName()));
            assertTrue("The \"string\" sub-token is not a StringToken", token instanceof StringToken);
            StringToken stringToken = (StringToken)token;
            assertTrue("The \"string\" string sub-token doesn't contain the string \"something\", instead it contains \"" + stringToken.asString() + "\"", "something".equals(stringToken.asString()));

            assertTrue("The root token indicates there is no second sub-token", rootToken.hasNext());
            token = rootToken.next();
            assertTrue("The second sub-token is not named \"number\"", "number".equals(token.getName()));
            assertTrue("The \"number\" sub-token is not a LongToken", token instanceof LongToken);
            LongToken longToken = (LongToken)token;
            assertTrue("The \"number\" integer sub-token doesn't contain the number 2, instead it contains " + longToken.asString(), longToken.asLong().equals(2L));

            assertTrue("The root token indicates there is no third sub-token", rootToken.hasNext());
            token = rootToken.next();
            assertTrue("The third sub-token is not named \"bool\"", "bool".equals(token.getName()));
            assertTrue("The \"bool\" sub-token is not a BooleanToken", token instanceof BooleanToken);
            BooleanToken boolToken = (BooleanToken)token;
            assertTrue("The \"bool\" boolean sub-token doesn't contain the value true, instead it contains " + boolToken.asString(), boolToken.asBoolean().equals(Boolean.TRUE));

            assertTrue("The root token indicates there is no fourth sub-token", rootToken.hasNext());
            token = rootToken.next();
            assertTrue("The fourth sub-token is not named \"null\"", "null".equals(token.getName()));
            assertTrue("The \"null\" sub-token is not a NullToken", token instanceof NullToken);
            NullToken nullToken = (NullToken)token;
            assertTrue("The \"null\" Null sub-token is not null, instead it contains " + nullToken.asString(), nullToken.isNull());

            assertStructureEnd(rootToken);

            token = tokenizer.nextToken();
            assertNull("The tokenizer didn't finish after the root token finished, I got a token \"" + (null == token ? "null" : token.getName()) + "\" of class " + (null == token ? "null" : token.getClass().getCanonicalName()), token);
        }
        catch (ResponseException e) {
            e.printStackTrace();
            throw new RuntimeException("A ResponseException was raised: " + e.toString(), e);
        }
    }

    @Test
    public void testComplex() {
        final String json = "{\"string\": \"something\", \"number\":2,\"emptyObject\":{ },\"emptyList\":[],\"object\":{\"double\":1.2},\"array\":[{}, {\"name\":\"blah\"}, false],\"bool\":true,\"null\":null}";
        JSONTokenizer tokenizer = new JSONTokenizer(json);
        try {
            Token token = tokenizer.nextToken();
            assertTrue("The root token is not a JSONObjectToken", token instanceof JSONObjectToken);
            assertNull("The root token's name is not null", token.getName());
            JSONObjectToken rootToken = (JSONObjectToken)token;

            assertTrue("The root token indicates there is no first sub-token", rootToken.hasNext());
            token = rootToken.next();
            assertTrue("The first sub-token is not named \"string\"", "string".equals(token.getName()));
            assertTrue("The \"string\" sub-token is not a StringToken", token instanceof StringToken);
            StringToken stringToken = (StringToken)token;
            assertTrue("The \"string\" string sub-token doesn't contain the string \"something\", instead it contains \"" + stringToken.asString() + "\"", "something".equals(stringToken.asString()));

            assertTrue("The root token indicates there is no second sub-token", rootToken.hasNext());
            token = rootToken.next();
            assertTrue("The second sub-token is not named \"number\"", "number".equals(token.getName()));
            assertTrue("The \"number\" sub-token is not a LongToken", token instanceof LongToken);
            LongToken longToken = (LongToken)token;
            assertTrue("The \"number\" integer sub-token doesn't contain the number 2, instead it contains " + longToken.asString(), longToken.asLong().equals(2L));

            assertTrue("The root token indicates there is no third sub-token", rootToken.hasNext());
            token = rootToken.next();
            assertTrue("The third sub-token is not named \"emptyObject\"", "emptyObject".equals(token.getName()));
            assertTrue("The \"emptyObject\" sub-token is not a JSONObjectToken", token instanceof JSONObjectToken);
            JSONObjectToken objectToken = (JSONObjectToken)token;

            assertStructureEnd(objectToken);

            assertTrue("The root token indicates there is no fourth sub-token", rootToken.hasNext());
            token = rootToken.next();
            assertTrue("The fourth sub-token is not named \"emptyList\"", "emptyList".equals(token.getName()));
            assertTrue("The \"emptyList\" sub-token is not a JSONListToken", token instanceof JSONListToken);
            JSONListToken listToken = (JSONListToken)token;

            assertStructureEnd(listToken);

            assertTrue("The root token indicates there is no fifth sub-token", rootToken.hasNext());
            token = rootToken.next();
            assertTrue("The fifth sub-token is not named \"object\"", "object".equals(token.getName()));
            assertTrue("The \"object\" sub-token is not a JSONObjectToken", token instanceof JSONObjectToken);
            objectToken = (JSONObjectToken)token;

            assertTrue("The \"object\" sub-token indicates there is no first sub-token", objectToken.hasNext());
            token = objectToken.next();
            assertTrue("The first \"object\" sub-token is not named \"double\"", "double".equals(token.getName()));
            assertTrue("The \"double\" sub-token is not a LongToken", token instanceof DoubleToken);
            DoubleToken doubleToken = (DoubleToken)token;
            assertTrue("The \"double\" integer sub-token doesn't contain the number 1.2, instead it contains " + doubleToken.asString(), doubleToken.asDouble().equals(1.2));

            assertStructureEnd(objectToken);

            assertTrue("The root token indicates there is no sixth sub-token", rootToken.hasNext());
            token = rootToken.next();
            assertTrue("The sixth sub-token is not named \"array\"", "array".equals(token.getName()));
            assertTrue("The \"array\" sub-token is not a JSONListToken", token instanceof JSONListToken);
            listToken = (JSONListToken)token;

            assertTrue("The \"array\" sub-token indicates there is no first sub-token", listToken.hasNext());
            token = listToken.next();
            assertNull("The first sub-token's name in the list is not null", token.getName());
            assertTrue("The first sub-token from the list is not a JSONObjectToken", token instanceof JSONObjectToken);
            objectToken = (JSONObjectToken)token;
            assertStructureEnd(objectToken);

            assertTrue("The \"array\" sub-token indicates there is no second sub-token", listToken.hasNext());
            token = listToken.next();
            assertNull("The second sub-token's name in the list is not null", token.getName());
            assertTrue("The second sub-token from the list is not a JSONObjectToken", token instanceof JSONObjectToken);
            objectToken = (JSONObjectToken)token;
            assertTrue("The second sub-token indicates there is no first sub-token", objectToken.hasNext());
            token = objectToken.next();
            assertTrue("The first property of the second sub-token in the list is not named \"name\"", "name".equals(token.getName()));
            assertTrue("The \"name\" property of the second sub-token in the list is not a StringToken", token instanceof StringToken);
            stringToken = (StringToken)token;
            assertTrue("The \"name\" property of the second sub-token in the list doesn't contain the string \"blah\", instead it contains \"" + stringToken.asString() + "\"", "blah".equals(stringToken.asString()));
            assertStructureEnd(objectToken);

            assertTrue("The \"array\" sub-token indicates there is no third sub-token", listToken.hasNext());
            token = listToken.next();
            assertNull("The third sub-token's name in the list is not null", token.getName());
            assertTrue("The third sub-token's name in the list is not a BooleanToken", token instanceof BooleanToken);
            BooleanToken boolToken = (BooleanToken)token;
            assertTrue("The third sub-token's name in the list doesn't contain the value false, instead it contains " + boolToken.asString(), boolToken.asBoolean().equals(Boolean.FALSE));

            assertStructureEnd(listToken);


            assertTrue("The root token indicates there is no seventh sub-token", rootToken.hasNext());
            token = rootToken.next();
            assertTrue("The seventh sub-token is not named \"bool\"", "bool".equals(token.getName()));
            assertTrue("The \"bool\" sub-token is not a BooleanToken", token instanceof BooleanToken);
            boolToken = (BooleanToken)token;
            assertTrue("The \"bool\" boolean sub-token doesn't contain the value true, instead it contains " + boolToken.asString(), boolToken.asBoolean().equals(Boolean.TRUE));

            assertTrue("The root token indicates there is no eighth sub-token", rootToken.hasNext());
            token = rootToken.next();
            assertTrue("The eighth sub-token is not named \"null\"", "null".equals(token.getName()));
            assertTrue("The \"null\" sub-token is not a NullToken", token instanceof NullToken);
            NullToken nullToken = (NullToken)token;
            assertTrue("The \"null\" Null sub-token is not null, instead it contains " + nullToken.asString(), nullToken.isNull());

            assertStructureEnd(rootToken);

            token = tokenizer.nextToken();
            assertNull("The tokenizer didn't finish after the root token finished, I got a token \"" + (null == token ? "null" : token.getName()) + "\" of class " + (null == token ? "null" : token.getClass().getCanonicalName()), token);
        }
        catch (ResponseException e) {
            e.printStackTrace();
            throw new RuntimeException("A ResponseException was raised: " + e.toString(), e);
        }
    }

    final String literalString = "Test\n'blah' and \"blah\"\r\n\bš\t ";
    final String escapedString = "Test\\n'blah' and \\\"blah\\\"\\r\\n\\b\u0161\\t ";

    @Test
    public void testStringSpecialCharacters() {
        final String json = "{\"name\":\"" + escapedString + "\"}";
        JSONTokenizer tokenizer = new JSONTokenizer(json);
        try {
            Token token = tokenizer.nextToken();
            assertTrue("The root token is not a JSONObjectToken", token instanceof JSONObjectToken);
            assertNull("The root token's name is not null", token.getName());
            JSONObjectToken rootToken = (JSONObjectToken)token;

            assertTrue("The root token indicates there is no sub-token", rootToken.hasNext());
            token = rootToken.next();
            assertTrue("The sub-token is not named \"name\"", "name".equals(token.getName()));
            assertTrue("The \"name\" sub-token is not a StringToken", token instanceof StringToken);
            StringToken stringToken = (StringToken)token;
            assertTrue("The \"name\" string sub-token doesn't contain the string \"" + escapedString + "\", instead it contains \"" + stringToken.asString() + "\"", literalString.equals(stringToken.asString()));

            assertStructureEnd(rootToken);

            token = tokenizer.nextToken();
            assertNull("The tokenizer didn't finish after the root token finished, I got a token \"" + (null == token ? "null" : token.getName()) + "\" of class " + (null == token ? "null" : token.getClass().getCanonicalName()), token);
        }
        catch (ResponseException e) {
            e.printStackTrace();
            throw new RuntimeException("A ResponseException was raised: " + e.toString(), e);
        }
    }

    @Test
    public void testMixedStringQuotes() {
        final String json = "{\"name\":'something', 'something':\"name\"}";
        JSONTokenizer tokenizer = new JSONTokenizer(json);
        try {
            Token token = tokenizer.nextToken();
            assertTrue("The root token is not a JSONObjectToken", token instanceof JSONObjectToken);
            assertNull("The root token's name is not null", token.getName());
            JSONObjectToken rootToken = (JSONObjectToken)token;

            assertTrue("The root token indicates there is no first sub-token", rootToken.hasNext());
            token = rootToken.next();
            assertTrue("The first sub-token is not named \"name\"", "name".equals(token.getName()));
            assertTrue("The \"name\" sub-token is not a StringToken", token instanceof StringToken);
            StringToken stringToken = (StringToken)token;
            assertTrue("The \"name\" string sub-token doesn't contain the string \"something\", instead it contains \"" + stringToken.asString() + "\"", "something".equals(stringToken.asString()));

            assertTrue("The root token indicates there is no second sub-token", rootToken.hasNext());
            token = rootToken.next();
            assertTrue("The second sub-token is not named \"something\"", "something".equals(token.getName()));
            assertTrue("The \"something\" sub-token is not a StringToken", token instanceof StringToken);
            stringToken = (StringToken)token;
            assertTrue("The \"something\" string sub-token doesn't contain the string \"name\", instead it contains \"" + stringToken.asString() + "\"", "name".equals(stringToken.asString()));

            assertStructureEnd(rootToken);

            token = tokenizer.nextToken();
            assertNull("The tokenizer didn't finish after the root token finished, I got a token \"" + (null == token ? "null" : token.getName()) + "\" of class " + (null == token ? "null" : token.getClass().getCanonicalName()), token);
        }
        catch (ResponseException e) {
            e.printStackTrace();
            throw new RuntimeException("A ResponseException was raised: " + e.toString(), e);
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
