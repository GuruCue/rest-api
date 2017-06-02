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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.google.common.collect.ImmutableMap;
import com.gurucue.recommendations.data.DataManager;
import com.gurucue.recommendations.entity.value.BooleanValue;
import com.gurucue.recommendations.entity.value.FloatValue;
import com.gurucue.recommendations.entity.value.LongValue;
import com.gurucue.recommendations.entity.value.MultiValue;
import com.gurucue.recommendations.entity.value.NullValue;
import com.gurucue.recommendations.entity.value.StringValue;
import com.gurucue.recommendations.entity.value.TranslatableValue;
import com.gurucue.recommendations.entity.value.Value;
import com.gurucue.recommendations.entitymanager.AttributeInputInterface;
import com.gurucue.recommendations.rest.data.RequestCache;
import com.gurucue.recommendations.ResponseException;
import com.gurucue.recommendations.ResponseStatus;
import com.gurucue.recommendations.parser.AttributeParser;
import com.gurucue.recommendations.parser.LanguageParser;
import com.gurucue.recommendations.parser.Rule;
import com.gurucue.recommendations.parser.StringParser;
import com.gurucue.recommendations.parser.StructuredTokenParser;
import com.gurucue.recommendations.parser.StructuredTokenParserMaker;
import com.gurucue.recommendations.rest.data.processing.ValueConversions;
import com.gurucue.recommendations.entity.Attribute;
import com.gurucue.recommendations.entity.Language;

public final class AttributeInput implements Serializable, StructuredTokenParser, AttributeInputInterface {
    private static final long serialVersionUID = -6343931980289505400L;

    static final String TAG_IDENTIFIER = "identifier";
    static final String TAG_VALUE = "value";
    static final String TAG_LANGUAGE = "language";
    static final String TAG_TRANSLATIONS = "translations";
    static final StructuredTokenParserMaker maker = new Maker();
    static final Rule parseRule = Rule.map("attribute", true, maker, new Rule[] {
            Rule.value(TAG_IDENTIFIER, false, AttributeParser.parser),
            Rule.value(TAG_VALUE, true, StringParser.parser),
            Rule.value(TAG_LANGUAGE, true, LanguageParser.parser),
            Rule.list(TAG_TRANSLATIONS, true, TranslationInput.class, TranslationInput.parseRule)
    });

    private final boolean isDeletion;
    private Map<Long, TranslationInput> translationPerLanguage;

    private Attribute attribute;
    private String value;
    private Language language;
    private List<TranslationInput> translations;

    public AttributeInput(final Map<String, ?> params) {
        attribute = null;
        value = null;
        language = null;
        translations = Collections.emptyList();
        if (params == null) isDeletion = false;
        else {
            final Object isDeletionObj = params.get("isDeletion");
            isDeletion = (isDeletionObj != null) && Boolean.TRUE.equals(isDeletionObj);
        }
    }
    
    public AttributeInput(final Attribute attribute, final String value, final Language language, final List<TranslationInput> translations) {
        this.attribute = attribute;
        this.value = value;
        this.language = language;
        this.translations = null == translations ? Collections.<TranslationInput>emptyList() : translations;
        isDeletion = false;
    }

    public Attribute getAttribute() {
        return attribute;
    }

    public void setAttribute(final Attribute attribute) {
        this.attribute = attribute;
    }

    public String getValue() {
        return value;
    }

    public void setValue(final String value) {
        this.value = value;
    }

    public Language getLanguage() {
        return language;
    }

    public void setLanguage(final Language language) {
        this.language = language;
    }

    public List<TranslationInput> getTranslations() {
        return translations;
    }

    public void setTranslations(final List<TranslationInput> translations) {
        this.translations = translations;
    }

    public TranslationInput getTranslationForLanguage(final Language language) {
        return translationPerLanguage.get(language.getId());
    }

    public Value asValue() throws ResponseException {
        if (value == null) {
            return NullValue.INSTANCE;
        }
        else {
            if (attribute.getIsTranslatable()) {
                if ((translationPerLanguage == null) || (translationPerLanguage.size() == 0)) return new TranslatableValue(value, language);
                final long unknownId = DataManager.getLanguageCodes().idForUknown;
                final long originalId = language.getId().longValue();
                final ImmutableMap.Builder<Language, String> builder = ImmutableMap.builder();
                for (final TranslationInput input : translationPerLanguage.values()) {
                    final Language trLanguage = input.getLanguage();
                    final long trLanguageId = trLanguage.getId().longValue();
                    if ((trLanguageId == unknownId) || (trLanguageId == originalId)) continue; // ignore
                    final String trValue = input.getValue();
                    builder.put(trLanguage, trValue == null ? "" : trValue);
                }
                return new TranslatableValue(value, language, builder.build());
            }
            else {
                switch (attribute.getValueType()) {
                    case STRING:
                        return new StringValue(ValueConversions.toString(value));
                    case INTEGER:
                        return new LongValue(ValueConversions.toInteger(value).longValue(), false);
                    case TIMESTAMP:
                        return new LongValue(ValueConversions.toInteger(value).longValue(), true);
                    case BOOLEAN:
                        return new BooleanValue(ValueConversions.toBoolean(value));
                    case FLOAT:
                        return new FloatValue(ValueConversions.toDouble(value));
                    case TIMESTAMP_INTERVAL:
                        return ValueConversions.toTimestampInterval(value);
                    default:
                        throw new ResponseException(ResponseStatus.UNKNOWN_ERROR, "Internal error: unsupported value type for attribute " + attribute.getIdentifier() + ": " + attribute.getValueType());
                }
            }
        }
    }

    /**
     * For copying translations from a duplicate attribute on the upper level (e.g. ProductAddInput).
     * @param translations
     */
    void addTranslations(final List<TranslationInput> translations) throws ResponseException {
        if ((translations == null) || (translations.size() == 0)) return; // nothing to do
        if (this.translations.size() == 0) this.translations = translations;
        else this.translations.addAll(translations);
        verifyTranslations();
    }

    // StructuredTokenParser interface

    @Override
    public void begin(final String memberName, final Map<String, Object> params) {}

    @SuppressWarnings("unchecked")
    @Override
    public void consume(final String memberName, final Object member) throws ResponseException {
        try {
            switch (memberName) {
                case TAG_IDENTIFIER:
                    setAttribute((Attribute) member);
                    break;
                case TAG_VALUE:
                    String value = (String) member;
                    if (value != null) value = value.trim(); // trim any white space from the value
                    setValue(value);
                    break;
                case TAG_LANGUAGE:
                    setLanguage((Language) member);
                    break;
                case TAG_TRANSLATIONS:
                    setTranslations((List<TranslationInput>) member);
                    break;
                default:
                    throw new ResponseException(ResponseStatus.INTERNAL_PROCESSING_ERROR, "Attempted to set a value to an unknown member of an AttributeInput instance: " + memberName);
            }
        }
        catch (ClassCastException e) {
            throw new ResponseException(ResponseStatus.INTERNAL_PROCESSING_ERROR, e, "Attempted to set a value of invalid type to the member " + memberName + " of an AttributeInput instance: " + member.getClass().getCanonicalName());
        }
    }

    @Override
    public AttributeInput finish() throws ResponseException {
        if (attribute == null) {
            // if we don't have a value, then we give an abridged exception description
            if ((value == null) || (value.length() == 0)) throw new ResponseException(ResponseStatus.ATTRIBUTE_MISSING, "Attribute identifier is missing");
            // we can include the value in the exception description
            throw new ResponseException(ResponseStatus.ATTRIBUTE_MISSING, "Attribute identifier is missing for value \"" + value.replace("\"", "\\\"") + "\"");
        }
        if (translations == null) translations = Collections.emptyList();

        if (isDeletion) {
            if ((value != null) && (value.length() == 0)) value = null; // zero-length is basically null
            if (translations.size() > 0) throw new ResponseException(ResponseStatus.ATTRIBUTE_TRANSLATIONS_ILLEGAL_WHEN_DELETING, "Translations must not be present for attributes scheduled for deletion: \"" + attribute.getIdentifier() + "\"");
        }
        else {
            if ((value == null) || (value.length() == 0)) { // treat an empty value as if this product attribute has not been specified at all
                RequestCache.get().getLogger().subLogger(getClass().getSimpleName()).debug("Ignoring input attribute value due to an empty or null value for attribute: " + attribute.getIdentifier());
                return null;
            }
            if (attribute.getIsTranslatable() && (language == null)) {
                // inject unknown language
                language = DataManager.getLanguageCodes().unknown;
            }
        }

        // verify the type of input and normalize it
        if (value != null) value = ValueConversions.toString(ValueConversions.convert(value, attribute.getValueType()));

        verifyTranslations();

        return this;
    }

    void verifyTranslations() throws ResponseException {
        if (attribute.getIsTranslatable()) {
            if (translations.size() > 0) {
                // there are translations: map them per language, remove any duplicates, and check for inconsistencies
                // precondition: value != null, fulfilled by: finish()
                translationPerLanguage = new HashMap<>();
                final Long originalLanguageId = language.getId();
                final Iterator<TranslationInput> translationsIterator = translations.iterator();
                while (translationsIterator.hasNext()) {
                    final TranslationInput tr = translationsIterator.next();
                    final Language trLanguage = tr.getLanguage();
                    if (trLanguage == null) throw new ResponseException(ResponseStatus.TRANSLATION_WITHOUT_LANGUAGE, "A translation for attribute " + attribute.getIdentifier() + " with value \"" + value.replace("\"", "\\\"") + "\" does not specify its language");
                    // don't treat empty translation value as an error, so we get the ability to delete translations
                    final String trValue = tr.getValue() == null ? "" : tr.getValue();
                    final TranslationInput existingTranslation = translationPerLanguage.get(trLanguage.getId());
                    if (existingTranslation == null) {
                        // this is the first translation for its language, now check whether the language matches the original value's language and if so, if values match
                        if (originalLanguageId.equals(trLanguage.getId())) {
                            if (!value.equals(trValue)) throw new ResponseException(ResponseStatus.TRANSLATED_VALUE_NOT_EQUAL_TO_ORIGINAL, "Translation in the original language for attribute " + attribute.getIdentifier() + " with value \"" + value.replace("\"", "\\\"") + "\" does not match the attribute value: \"" + trValue.replace("\"", "\\\"") + "\"");
                            // remove the translation in the original language, it is superfluous, the service processing layer will deal with changes to the original value
                            translationsIterator.remove();
                        }
                        else {
                            translationPerLanguage.put(trLanguage.getId(), tr);
                        }
                    }
                    else {
                        // there is an existing translation for this language already, verify if the values match, and if so remove the superfluous translation, otherwise it's an error
                        if (!trValue.equals(existingTranslation.getValue())) throw new ResponseException(ResponseStatus.MULTIPLE_DIFFERENT_TRANSLATIONS_IN_THE_SAME_LANGUAGE, "There are multiple different translations in language " + trLanguage.getIso639_2t() + " for attribute " + attribute.getIdentifier() + " with value \"" + value.replace("\"", "\\\"") + "\"");
                        translationsIterator.remove();
                    }
                }
            }
            else {
                translationPerLanguage = Collections.emptyMap();
            }
        }
        else {
            if (language != null) throw new ResponseException(ResponseStatus.ATTRIBUTE_LANGUAGE_FORBIDDEN, "Attribute " + attribute.getIdentifier() + " is not translatable, it does not accept a language");
            if (translations.size() > 0) throw new ResponseException(ResponseStatus.TRANSLATIONS_FORBIDDEN, "Attribute " + attribute.getIdentifier() + " is not translatable, it does not accept translations");
            translationPerLanguage = Collections.emptyMap();
        }
    }

    /**
     * Add this instance to the given list of input attributes, with
     * full checking for attribute duplicates, translation collisions,
     * and other inconsistencies.
     *
     * @param list the list of input attributes where to add ourselves
     * @throws ResponseException if adding this instance to the given list would result in an inconsistent state
     */
    void addToList(final List<AttributeInput> list) throws ResponseException {
        final long attributeId = attribute.getId().longValue();
        if (isDeletion) {
            // attribute deletions are handled differently: there are no translations, and less specific deletions already contain more specific deletions
            if ((value == null) && (language == null)) {
                // the least specific deletion: only the attribute is given
                // this means any more specific deletion is superfluous, so we remove it from the list
                final Iterator<AttributeInput> iterator = list.iterator();
                while (iterator.hasNext()) {
                    final AttributeInput candidate = iterator.next();
                    // must be the same attribute
                    if (candidate.getAttribute().getId().longValue() != attributeId) continue;
                    // if this is a duplicate, then we're done, assuming no more duplicates or more specific deletions with this attribute
                    if ((candidate.getValue() == null) && (candidate.getLanguage() == null)) return;
                    // else this is a more specific deletion and thus we remove it
                    iterator.remove();
                }
                list.add(this);
            }
            else if (language == null) {
                // a value-specific deletion, for any language
                final Iterator<AttributeInput> iterator = list.iterator();
                while (iterator.hasNext()) {
                    final AttributeInput candidate = iterator.next();
                    // must be the same attribute
                    if (candidate.getAttribute().getId().longValue() != attributeId) continue;
                    if (candidate.getValue() == null) {
                        // if there is already a more general deletion, then we're done, assuming there are no more deletions with this attribute and value
                        if (candidate.getLanguage() == null) return;
                    }
                    else if (value.equals(candidate.getValue())) {
                        // if this is a duplicate, then we're done, assuming no more duplicates or more specific deletions with this attribute and value
                        if (candidate.getLanguage() == null) return;
                        // else this is a more specific deletion and thus we remove it
                        iterator.remove();
                    }
                }
                list.add(this);
            }
            else if (value == null) {
                // a language-specific deletion, for any value
                final long languageId = language.getId().longValue();
                final Iterator<AttributeInput> iterator = list.iterator();
                while (iterator.hasNext()) {
                    final AttributeInput candidate = iterator.next();
                    // must be the same attribute
                    if (candidate.getAttribute().getId().longValue() != attributeId) continue;
                    if (candidate.getLanguage() == null) {
                        // if there is already a more general deletion, then we're done, assuming there are no more deletions with this attribute and language
                        if (candidate.getValue() == null) return;
                    }
                    else if (candidate.getLanguage().getId().longValue() == languageId) {
                        // if this is a duplicate, then we're done, assuming no more duplicates or more specific deletions with this attribute and language
                        if (candidate.getValue() == null) return;
                        // else this is a more specific deletion and thus we remove it
                        iterator.remove();
                    }
                }
                list.add(this);
            }
            else {
                // the most specific deletion: both language and value are given
                final long languageId = language.getId().longValue();
                final Iterator<AttributeInput> iterator = list.iterator();
                while (iterator.hasNext()) {
                    final AttributeInput candidate = iterator.next();
                    // must be the same attribute
                    if (candidate.getAttribute().getId().longValue() != attributeId) continue;
                    // if there is already a more general deletion, then we're done, assuming there are no more deletions with this attribute
                    if ((candidate.getValue() == null) || (candidate.getLanguage() == null)) return;
                    // if this is a duplicate, then we're done, assuming no more duplicates
                    if (value.equals(candidate.getValue()) && (candidate.getLanguage().getId().longValue() == languageId)) return;
                }
                list.add(this);
            }
        }
        else { // not a deletion
            // first see if there's already an attribute with the current value
            int seenSameAttribute = 0;
            boolean addMyself = true;
            for (final AttributeInput candidate : list) {
                // must be the same attribute
                if (candidate.getAttribute().getId().longValue() != attributeId) continue;
                // check candidate's original value against our original value
                if (candidate.value.equals(value) && ((candidate.language == language) || ((language != null) && (language.getId().longValue() == candidate.language.getId().longValue())))) {
                    // found the same attribute value already defined, merge translations
                    if (translationPerLanguage != null) {
                        if (candidate.translationPerLanguage == null) {
                            // the existing attribute value contains no translations, overwrite
                            candidate.translationPerLanguage = translationPerLanguage;
                            candidate.translations = translations;
                        }
                        else {
                            // merge
                            for (final TranslationInput ourTranslation : translationPerLanguage.values()) {
                                final TranslationInput candidateTranslation = candidate.translationPerLanguage.get(ourTranslation.getLanguage().getId());
                                if (candidateTranslation == null) {
                                    candidate.translationPerLanguage.put(ourTranslation.getLanguage().getId(), ourTranslation);
                                    candidate.translations.add(ourTranslation);
                                }
                                else if (!candidateTranslation.getValue().equals(ourTranslation.getValue())) {
                                    throw new ResponseException(ResponseStatus.MULTIPLE_DIFFERENT_TRANSLATIONS_IN_THE_SAME_LANGUAGE, "The attribute \"" + attribute.getIdentifier() + "\" with the value \"" + value.replace("\\", "\\\"") + "\", language: " + language.getIso639_2t() + " is defined multiple times, each time with a different translation for the language " + ourTranslation.getLanguage().getIso639_2t() + ": \"" + ourTranslation.getValue().replace("\"", "\\\"") + "\" != \"" + candidateTranslation.getValue().replace("\"", "\\\"") + "\"");
                                }
                            }
                        }
                    }
                    addMyself = false;
                    break;
                }
                else {
                    // check candidate's translations against our original value
                    if ((candidate.translationPerLanguage != null) && (language != null)) {
                        final TranslationInput candidateTranslation = candidate.translationPerLanguage.get(language.getId());
                        if ((candidateTranslation != null) && candidateTranslation.getValue().equals(value)) {
                            throw new ResponseException(ResponseStatus.MULTIPLE_ATTRIBUTES_WITH_SAME_TRANSLATION, "The attribute \"" + attribute.getIdentifier() + "\" having the value: \"" + value.replace("\\", "\\\"") + "\", language: " + language.getIso639_2t() + ", is also among translations of the value \"" + candidate.value.replace("\"", "\\\"") + "\", language: " + candidate.language.getIso639_2t());
                        }
                    }
                    // check candidate's original value against our translations
                    if ((translationPerLanguage != null) && candidate.language != null) {
                        final TranslationInput candidateTranslation = translationPerLanguage.get(candidate.language.getId());
                        if ((candidateTranslation != null) && candidateTranslation.getValue().equals(candidate.value)) {
                            throw new ResponseException(ResponseStatus.MULTIPLE_ATTRIBUTES_WITH_SAME_TRANSLATION, "The attribute \"" + attribute.getIdentifier() + "\" having the value: \"" + candidate.value.replace("\\", "\\\"") + "\", language: " + candidate.language.getIso639_2t() + ", is also among translations of the value \"" + value.replace("\"", "\\\"") + "\", language: " + language.getIso639_2t());
                        }
                    }
                    // check candidate's translations against our translations
                    if ((translationPerLanguage != null) && (candidate.translationPerLanguage != null)) {
                        for (final TranslationInput ourTranslation : translationPerLanguage.values()) {
                            final TranslationInput candidateTranslation = candidate.translationPerLanguage.get(ourTranslation.getLanguage().getId());
                            if ((candidateTranslation != null) && candidateTranslation.getValue().equals(ourTranslation.getValue())) {
                                throw new ResponseException(ResponseStatus.MULTIPLE_ATTRIBUTES_WITH_SAME_TRANSLATION, "The attribute \"" + attribute.getIdentifier() + "\" instances having the values \"" + candidate.value.replace("\\", "\\\"") + "\", language: " + candidate.language.getIso639_2t() + " and \"" + value.replace("\"", "\\\"") + "\", language: " + language.getIso639_2t() + " share the same translation: \"" + ourTranslation.getValue().replace("\"", "\\\"") + "\", language: " + ourTranslation.getLanguage().getIso639_2t());
                            }
                        }
                    }
                    seenSameAttribute++;
                }
            }

            // now check whether we're multi-value and if not, whether the list is empty
            if (!(attribute.getIsMultivalue() || (seenSameAttribute == 0))) throw new ResponseException(ResponseStatus.MULTIPLE_VALUES_OF_SINGLE_VALUED_ATTRIBUTE, "Attribute \"" + attribute.getIdentifier() + "\" is specified with multiple different values, but only one value is permitted (the attribute is single-valued)");

            // no duplicate of us found in the existing list, add ourselves
            if (addMyself) list.add(this);
        }
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj == null) return false;
        if (obj instanceof AttributeInput) {
            AttributeInput other = (AttributeInput) obj;
            // compare attributes
            boolean ret = (this.getAttribute() == other.getAttribute()) ||
                    ((this.getAttribute() != null) && this.getAttribute().equals(other.getAttribute()));
            // compare values
            ret = ret && ((this.getValue() == other.getValue()) ||
                    ((this.getValue() != null) && this.getValue().equals(other.getValue())));
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
        result = 31 * result + (null == getAttribute() ? 0 : getAttribute().hashCode());
        result = 31 * result + (null == getValue() ? 0 : getValue().hashCode());
        result = 31 * result + (null == getLanguage() ? 0 : getLanguage().hashCode());
        return result;
    }

    private static class Maker implements StructuredTokenParserMaker {
        @Override
        public StructuredTokenParser create(final Map<String, Object> params) {
            return new AttributeInput(params);
        }
    }

    public static ImmutableMap<Attribute, Value> asAttributeValues(final List<AttributeInput> theList) throws ResponseException {
        if ((theList == null) || theList.isEmpty()) return ImmutableMap.of();
        final Map<Attribute, Object> mapping = new HashMap<>(theList.size());
        for (final AttributeInput input : theList) {
            final Attribute a = input.getAttribute();
            if (a.getIsMultivalue()) {
                final Object existingObject = mapping.get(a);
                if (existingObject == null) {
                    final List<Value> valueList = new ArrayList<>();
                    valueList.add(input.asValue());
                    mapping.put(a, valueList);
                }
                else {
                    ((List<Value>)existingObject).add(input.asValue());
                }
            }
            else mapping.put(a, input.asValue());
        }
        final ImmutableMap.Builder<Attribute, Value> builder = ImmutableMap.builder();
        for (final Map.Entry<Attribute, Object> entry : mapping.entrySet()) {
            final Attribute a = entry.getKey();
            if (a.getIsMultivalue()) {
                final List<Value> l = (List<Value>)entry.getValue();
                builder.put(a, new MultiValue(l.toArray(new Value[l.size()])));
            }
            else {
                builder.put(a, (Value)entry.getValue());
            }
        }
        return builder.build();
    }
}
