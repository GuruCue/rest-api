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
package com.gurucue.recommendations.rest.data.container;

import java.io.Serializable;
import java.util.Map;

import com.gurucue.recommendations.entity.ProductAttributeTranslation;
import com.gurucue.recommendations.ResponseException;
import com.gurucue.recommendations.ResponseStatus;
import com.gurucue.recommendations.entity.Language;
import com.gurucue.recommendations.parser.LanguageParser;
import com.gurucue.recommendations.parser.Rule;
import com.gurucue.recommendations.parser.StringParser;
import com.gurucue.recommendations.parser.StructuredTokenParser;
import com.gurucue.recommendations.parser.StructuredTokenParserMaker;

public final class TranslationInput implements Serializable, StructuredTokenParser {
    private static final long serialVersionUID = -6287412829597186514L;
    
    static final String TAG_LANGUAGE = "language";
    static final String TAG_VALUE = "value";
    static final StructuredTokenParserMaker maker = new Maker();
    static final Rule parseRule = Rule.map("translation", false, maker, new Rule[] {
            Rule.value(TAG_LANGUAGE, false, LanguageParser.parser),
            Rule.value(TAG_VALUE, true, StringParser.parser)
    });

    private boolean isAddition = true;
    private ProductAttributeTranslation productAttributeTranslation;

    private Language language;
    private String value;
    
    public TranslationInput() {
        language = null;
        value = null;
    }
    
    public TranslationInput(final Language language, final String value) {
        this.language = language;
        this.value = value;
    }
    
    public Language getLanguage() {
        return language;
    }
    
    public void setLanguage(Language language) {
        this.language = language;
    }
    
    public String getValue() {
        return value;
    }
    
    public void setValue(String value) {
        this.value = value;
    }

    public ProductAttributeTranslation asProductAttributeTranslation() {
        if (productAttributeTranslation == null) {
            productAttributeTranslation = new ProductAttributeTranslation(null, null, language, value);
        }
        return productAttributeTranslation;
    }

    @Override
    public void begin(final String memberName, final Map<String, Object> params) {
        if (params == null) isAddition = true;
        else {
            final Object isAdditionObj = params.get("isAddition");
            isAddition = (isAdditionObj == null) || Boolean.TRUE.equals(isAdditionObj);
        }
    }

    @Override
    public void consume(final String memberName, final Object member)
            throws ResponseException {
        try {
            if (TAG_LANGUAGE.equals(memberName)) setLanguage((Language)member);
            else if (TAG_VALUE.equals(memberName)) setValue((String)member);
            else throw new ResponseException(ResponseStatus.INTERNAL_PROCESSING_ERROR, "Attempted to set a value to an unknown member of a TranslationInput instance: " + memberName);
        }
        catch (ClassCastException e) {
            throw new ResponseException(ResponseStatus.INTERNAL_PROCESSING_ERROR, e, "Attempted to set a value of invalid type to the member " + memberName + " of a TranslationInput instance: " + member.getClass().getCanonicalName());
        }
    }

    @Override
    public TranslationInput finish() throws ResponseException {
        if (language == null) throw new ResponseException(ResponseStatus.TRANSLATION_WITHOUT_LANGUAGE, "A translation is missing translation language");
        if ((value != null) && (value.length() == 0)) value = null; // a zero-length value is the same as a null value
        if (isAddition && (value == null)) throw new ResponseException(ResponseStatus.TRANSLATION_WITHOUT_VALUE, "A translation is missing translation text");
        return this;
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj == null) return false;
        if (obj instanceof AttributeInput) {
            AttributeInput other = (AttributeInput) obj;
            // compare values
            boolean ret = (this.getValue() == other.getValue()) ||
                    ((this.getValue() != null) && this.getValue().equals(other.getValue()));
            // compare languages
            ret = ret && ((this.getLanguage() == other.getLanguage()) ||
                    ((this.getLanguage() != null) && this.getLanguage().equals(other.getLanguage())));
            return ret;
        }
        return false;
    }

    @Override
    public int hashCode() {
        // recipe taken from Effective Java, 2nd edition (ISBN 978-0-321-35668-0), page 47
        int result = 17;
        result = 31 * result + (null == getValue() ? 0 : getValue().hashCode());
        result = 31 * result + (null == getLanguage() ? 0 : getLanguage().hashCode());
        return result;
    }

    private static class Maker implements StructuredTokenParserMaker {
        @Override
        public StructuredTokenParser create(final Map<String, Object> params) {
            return new TranslationInput();
        }
    }
}
