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
package com.gurucue.recommendations.rest.data.processing.product;

import com.google.common.collect.ImmutableMap;
import com.gurucue.recommendations.entity.Language;
import com.gurucue.recommendations.entity.value.TranslatableValue;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TitleSanitizer {
    private static final Pattern hdPattern = Pattern.compile("^(.*?) *(?:-|–|:|,|\\?)? +[Hh][Dd]$");
    private static final Pattern threeDPattern = Pattern.compile("^(.*) +(?:-|–|\\?) +3[Dd]$");

    public final TranslatableValue rawValue;
    public final TranslatableValue sanitizedValue;
    public final boolean isHD;

    private TitleSanitizer(final TranslatableValue value, final TranslatableValue value2) {
        rawValue = value;
        boolean isHD = false;

        // primary title
        final Map<Language, String> translations1 = new HashMap<>(value.translations); // this one must be mutable
        translations1.put(value.language, value.value); // just in case the default value does not exist
        for (final Map.Entry<Language, String> entry : translations1.entrySet()) {
            final String originalValue = entry.getValue();
            final SanitizationResult s = sanitize(originalValue);
            if (s.value != originalValue) entry.setValue(s.value);
            if (s.isHD) isHD = true;
        }

        if (value2 != null) {
            // secondary title
            final Map<Language, String> translations2; // this one can be read-only
            if (value2.translations.containsKey(value2.language)) translations2 = value2.translations;
            else {
                translations2 = new HashMap<>(value2.translations);
                translations2.put(value2.language, value2.value);
            }
            for (final Map.Entry<Language, String> entry : translations2.entrySet()) {
                final Language thisLang = entry.getKey();
                final SanitizationResult s = sanitize(entry.getValue());
                final String thisValue = s.value;
                if (s.isHD) isHD = true;
                final String thatValue = translations1.get(thisLang);
                if (thatValue == null) {
                    // add the value, it is not contained in the original
                    translations1.put(thisLang, thisValue);
                }
                else if (thisValue.toUpperCase().contains(thatValue.toUpperCase())) {
                    // if title is contained within title2, then replace the title with title2
                    translations1.put(thisLang, thisValue);
                }
                else {
                    // if title is not contained within title2 (case insensitive), then merge the titles
                    translations1.put(thisLang, thatValue + " - " + thisValue);
                }
            }
        }

        this.isHD = isHD;
        sanitizedValue = new TranslatableValue(translations1.get(value.language), value.language, ImmutableMap.copyOf(translations1));
    }

    private static SanitizationResult sanitize(final String value) {
        Matcher m = hdPattern.matcher(value);
        if (m.matches()) {
            return new SanitizationResult(m.group(1), true);
        }

        m = threeDPattern.matcher(value);
        if (m.matches()) {
            return new SanitizationResult(m.group(1), false);
        }

        return new SanitizationResult(value, false);
    }

    public static TitleSanitizer sanitize(final TranslatableValue titleValue, final TranslatableValue title2Value) {
        if (titleValue == null) throw  new NullPointerException("Cannot sanitize the title: it is null");
        return new TitleSanitizer(titleValue, title2Value);
    }

    private static final class SanitizationResult {
        final String value;
        final boolean isHD;
        SanitizationResult(final String value, final boolean isHD) {
            this.value = value;
            this.isHD = isHD;
        }
    }
}
