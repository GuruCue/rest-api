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
package com.gurucue.recommendations.rest.data.response;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.gurucue.recommendations.entity.Attribute;
import com.gurucue.recommendations.entity.Language;
import com.gurucue.recommendations.entity.product.Product;
import com.gurucue.recommendations.entity.value.MultiValue;
import com.gurucue.recommendations.entity.value.TimestampIntervalValue;
import com.gurucue.recommendations.entity.value.TranslatableValue;
import com.gurucue.recommendations.entity.value.Value;
import com.gurucue.recommendations.translator.DataTranslator;
import com.gurucue.recommendations.ResponseStatus;
import com.gurucue.recommendations.translator.TranslatorAware;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ProductResponse extends RestResponse {
    private static final Logger log = LogManager.getLogger(ProductResponse.class);

    private final String productTypeIdentifier;
    private final String id;
    private final List<TranslatorAware> attributes = new ArrayList<>();
    private final boolean useShortLanguageCodes;

    public ProductResponse(final Product product, final String productTypeIdentifier, final boolean useShortLanguageCodes) {
        super(ResponseStatus.OK); // no other status is sensible; for errors just a RestResponse should be returned
        this.productTypeIdentifier = productTypeIdentifier;
        this.id = product.partnerProductCode;
        this.useShortLanguageCodes = useShortLanguageCodes;
        for (final Map.Entry<Attribute, Value> entry : product.attributes) {
            final Attribute attribute = entry.getKey();
            final Value value = entry.getValue();
            if (value.isArray) {
                final Value[] values = ((MultiValue)value).values;
                for (int i = 0; i < values.length; i++) {
                    addAttribute(attribute, values[i]);
                }
            }
            else {
                addAttribute(attribute, value);
            }
        }
    }

    public AttributeValue addAttribute(final Attribute attribute, final Value value) {
        final AttributeValue attributeValue;
        switch (value.valueType) {
            case INTEGER:
                attributeValue = new AttributeValueInteger(attribute.getIdentifier(), value);
                break;
            case STRING:
                if (attribute.getIsTranslatable()) attributeValue = new AttributeValueTranslatable(attribute.getIdentifier(), value, useShortLanguageCodes);
                else attributeValue = new AttributeValueString(attribute.getIdentifier(), value);
                break;
            case TIMESTAMP:
                attributeValue = new AttributeValueTimestamp(attribute.getIdentifier(), value);
                break;
            case TIMESTAMP_INTERVAL:
                attributeValue = new AttributeValueTimestampInterval(attribute.getIdentifier(), value);
                break;
            case BOOLEAN:
                attributeValue = new AttributeValueBoolean(attribute.getIdentifier(), value);
                break;
            case FLOAT:
                attributeValue = new AttributeValueFloat(attribute.getIdentifier(), value);
                break;
            default:
                // This must not be a fatal error! Instead a value should be missing
                //throw new UnsupportedOperationException("Value of an unknown type for attribute " + attribute.getIdentifier() + ": " + value.toString());
                log.error("Product " + productTypeIdentifier + " " + id + ": unsupported value type for attribute " + attribute.getIdentifier() + ": " + value.toString());
                attributeValue = null;
        }
        if (attributeValue != null) {
            attributes.add(attributeValue);
        }
        return attributeValue;
    }

    @Override
    protected void translateRest(final DataTranslator translator) throws IOException {
        translator.addKeyValue("type", productTypeIdentifier);
        translator.addKeyValue("id", id);
        translator.addKeyValue("attributes", attributes);
    }

    public static abstract class AttributeValue implements TranslatorAware {
        public void addTranslation(final String translationLanguage, final String translationValue) {
            throw new UnsupportedOperationException("Translations are not supported with " + getClass().getSimpleName());
        }
    }

    public static final class AttributeValueInteger extends AttributeValue {
        private final String identifier;
        private final Long value;

        AttributeValueInteger(final String identifier, final Value value) {
            this.identifier = identifier;
            this.value = value.asInteger();
        }

        @Override
        public void translate(final DataTranslator translator) throws IOException {
            translator.beginObject("attribute");
            translator.addKeyValue("identifier", identifier);
            translator.addKeyValue("value", value);
            translator.endObject();
        }
    }

    public static final class AttributeValueString extends AttributeValue {
        private final String identifier;
        private final String value;

        AttributeValueString(final String identifier, final Value value) {
            this.identifier = identifier;
            this.value = value.asString();
        }

        @Override
        public void translate(final DataTranslator translator) throws IOException {
            translator.beginObject("attribute");
            translator.addKeyValue("identifier", identifier);
            translator.addKeyValue("value", value);
            translator.endObject();
        }
    }

    public static final class AttributeValueTimestamp extends AttributeValue {
        private final String identifier;
        private final Long value;

        AttributeValueTimestamp(final String identifier, final Value value) {
            this.identifier = identifier;
            this.value = value.asInteger() / 1000L; // autoconvert to seconds
        }

        @Override
        public void translate(final DataTranslator translator) throws IOException {
            translator.beginObject("attribute");
            translator.addKeyValue("identifier", identifier);
            translator.addKeyValue("value", value);
            translator.endObject();
        }
    }

    public static final class AttributeValueTimestampInterval extends AttributeValue {
        private final String identifier;
        private final String value;

        AttributeValueTimestampInterval(final String identifier, final Value value) {
            this.identifier = identifier;
            final TimestampIntervalValue tiv = value.asTimestampInterval();
            this.value = (tiv.beginMillis / 1000L) + " " + (tiv.endMillis / 1000L); // autoconvert to seconds
        }

        @Override
        public void translate(final DataTranslator translator) throws IOException {
            translator.beginObject("attribute");
            translator.addKeyValue("identifier", identifier);
            translator.addKeyValue("value", value);
            translator.endObject();
        }
    }

    public static final class AttributeValueBoolean extends AttributeValue {
        private final String identifier;
        private final Boolean value;

        AttributeValueBoolean(final String identifier, final Value value) {
            this.identifier = identifier;
            this.value = value.asBoolean();
        }

        @Override
        public void translate(final DataTranslator translator) throws IOException {
            translator.beginObject("attribute");
            translator.addKeyValue("identifier", identifier);
            translator.addKeyValue("value", value);
            translator.endObject();
        }
    }

    public static final class AttributeValueFloat extends AttributeValue {
        private final String identifier;
        private final Double value;

        AttributeValueFloat(final String identifier, final Value value) {
            this.identifier = identifier;
            this.value = value.asFloat();
        }

        @Override
        public void translate(final DataTranslator translator) throws IOException {
            translator.beginObject("attribute");
            translator.addKeyValue("identifier", identifier);
            translator.addKeyValue("value", value);
            translator.endObject();
        }
    }

    public static final class AttributeValueTranslatable extends AttributeValue {
        private final String identifier;
        private final String value;
        private final String language;
        private final ArrayList<TranslatorAware> translations = new ArrayList<TranslatorAware>();

        AttributeValueTranslatable(final String identifier, final Value value, final boolean useShortLanguageCodes) {
            this.identifier = identifier;
            final TranslatableValue tv = value.asTranslatable();
            this.value = tv.value;
            final long originalLanguageId;
            if ((tv.language == null) || (Language.UNKNOWN.equals(tv.language.getIso639_2t()))) {
                this.language = null;
                originalLanguageId = -1L;
            }
            else {
                this.language = useShortLanguageCodes ? tv.language.getIso639_1() : tv.language.getIso639_2t();
                originalLanguageId = tv.language.getId();
            }
            for (final Map.Entry<Language, String> trEntry : tv.translations.entrySet()) {
                final Language l = trEntry.getKey();
                if (l == null) continue;
                final long lId = l.getId().longValue();
                if ((lId < 0L) || (lId == originalLanguageId)) continue;
                translations.add(new Translation(useShortLanguageCodes ? l.getIso639_1() : l.getIso639_2t(), trEntry.getValue()));
            }
        }

        @Override
        public void addTranslation(final String translationLanguage, final String translationValue) {
            translations.add(new Translation(translationLanguage, translationValue));
        }

        @Override
        public void translate(final DataTranslator translator) throws IOException {
            translator.beginObject("attribute");
            translator.addKeyValue("identifier", identifier);
            translator.addKeyValue("value", value);
            if (null != language) translator.addKeyValue("language", language);
            if (translations.size() > 0) translator.addKeyValue("translations", translations);
            translator.endObject();
        }
        
        public static final class Translation implements TranslatorAware {
            private final String language;
            private final String value;

            Translation(final String language, final String value) {
                this.language = language;
                this.value = value;
            }

            @Override
            public void translate(final DataTranslator translator) throws IOException {
                translator.beginObject("translation");
                translator.addKeyValue("language", language);
                translator.addKeyValue("value", value);
                translator.endObject();
            }
        }
    }
}
