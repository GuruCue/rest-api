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
package com.gurucue.recommendations.rest.data.processing;

import com.gurucue.recommendations.data.AttributeCodes;
import com.gurucue.recommendations.data.DataManager;
import com.gurucue.recommendations.data.LanguageCodes;
import com.gurucue.recommendations.entity.Language;
import com.gurucue.recommendations.entity.value.AttributeValues;
import com.gurucue.recommendations.entity.value.LongValue;
import com.gurucue.recommendations.entity.value.TranslatableValue;
import com.gurucue.recommendations.entity.value.Value;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Calendar;
import java.util.Date;
import java.util.Map;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Heuristics for detecting episodes, based on the title in slovene language.
 */
public class EpisodeDetectionSlovene {
    private static final Logger log = LogManager.getLogger(EpisodeDetectionSlovene.class);

    /**
     * Enumerate title prefixes for titles that are 100% not episodes.
     */
    private static final String[] episodeStopPrefixes = {
            "007 - "
    };

    private static final String[] unchoppables = {
            "18-letnica", "30-letne umazanke", "Alpe-Donava-Jadran", "The 1975: MTV v živo"
    };

    private static final Pattern sezonaDelPattern1 = Pattern.compile("^(.*[^, ])(?:,| ) *(?:(\\d+)(?:\\.| )|(\\p{Alpha}+) ) *sezona(?:,| ) *(?:(\\d+)(?:\\.| )|(\\p{Alpha}+ )) *(?:del|epizoda)$");
    private static final Pattern epizodaDelPattern1 = Pattern.compile("^(.*[^, ])(?:,| ) *(?:(\\d+)(?:\\.| )|(\\p{Alpha}+) ) *epizoda +.*, +(?:(\\d+)(?:\\.| )|(\\p{Alpha}+ )) *del$");
    private static final Pattern beginsWithDate = Pattern.compile("^(\\d\\d)\\.(\\d\\d)\\. *(.*)$");
    private static final Pattern oddajaPattern1 = Pattern.compile("^((?:\\S(?:-|–)\\S|[^-–])+[^-–:, ]).* *(?:-|–|:|,) *(?:(\\d+)(?:\\.| )|(\\p{Alpha}+) ) *oddaja(?:\\W+.*)?$");
    private static final Pattern oddajaPattern2 = Pattern.compile("^(.*[^, ])(?:,| ) *(?:(\\d+)(?:\\.| )|(\\p{Alpha}+) ) *oddaja(?:\\W+.*)?$");
    private static final Pattern delPattern = Pattern.compile("^(?:(?:(?:\\d+)(?:\\.| ) *|(?:\\p{Alpha}+) +)del *(?:-|–|:) *)?([^-–:?]*[^-–:? ]).*(?:-|–|:|,|;) *(?:(\\d+)(?:\\.| )|(\\p{Alpha}+) ) *(?:del|dan)(?:\\W+.*)?$");
    private static final Pattern epizodaPattern1 = Pattern.compile("^(.*[^, ])(?:,| ) *(?:(\\d+)(?:\\.| )|(\\p{Alpha}+) ) *epizoda(?: +.*)?$");
    private static final Pattern seansaPattern1 = Pattern.compile("^(.*[^-–:? ]) *(?:-|–|:) *(?:[^-–]+) \\((?:(\\d+)(?:\\.| )|(\\p{Alpha}+) ) *seansa\\)$");
    private static final Pattern tedenPattern1 = Pattern.compile("^(.*[^-–:? ]) *(?:-|–|:) *(?:(\\d+)(?:\\.| )|(\\p{Alpha}+) ) *teden$");
    private static final Pattern datumPattern1 = Pattern.compile("^(.*[^-–:? ]) *(?:-|–|:) *(\\d\\d?)\\.(\\d\\d?)\\.(\\d\\d\\d\\d)$");
    private static final Pattern datumPattern2 = Pattern.compile("^(.*[^, ])(?:,| ) *(\\d\\d?)\\.(\\d\\d?)\\.(\\d\\d\\d\\d)$");
    private static final Pattern dashColonPattern = Pattern.compile("^((?:\\S(?:-|–|:)\\S|[^-–?:])+[^-–?: ])(?: +(?:-|–)|(?:-|–|:) ) *.+$");
    private static final Pattern romanCommaPattern1 = Pattern.compile("^(.*[^ ]) +(IX|IV|V?I{1,3})\\. *(?:-|–|:|,) *.+$");
    private static final Pattern romanCommaPattern2 = Pattern.compile("^(.*[^ ]) +(XC|XL|L?X{1,3})(IX|IV|V?I{0,3})\\. *(?:-|–|:|,) *.+$");
    private static final Pattern trailingRomanNumeralPattern1 = Pattern.compile("^(.*[^ ,:-])[ ,:-]+(IX|IV|V?I{1,3})\\.?$");
    private static final Pattern trailingRomanNumeralPattern2 = Pattern.compile("^(.*[^ ,:-])[ ,:-]+(XC|XL|L?X{1,3})(IX|IV|V?I{0,3})\\.?$");
    private static final Pattern voyoEpisodePattern = Pattern.compile("^(\\d+)\\. (?:del|oddaja)$"); // episodes on VOYO have only the part number in the title

    private static final Pattern romanNumeralSuffixPattern = Pattern.compile("(.*[^ ]) +(M{0,4})(CM|CD|D?C{0,3})(XC|XL|L?X{0,3})(IX|IV|V?I{0,3})\\.$");
    private static final Pattern numberPattern = Pattern.compile("^\\d+$");
    private static final DescriptiveNumberConverter[] descriptiveNumberConverters = new DescriptiveNumberConverter[]{
            new DescriptiveNumberConverter(1, new Pattern[]{Pattern.compile("^prv.$", Pattern.CASE_INSENSITIVE)}),
            new DescriptiveNumberConverter(2, new Pattern[]{Pattern.compile("^drug.$", Pattern.CASE_INSENSITIVE)}),
            new DescriptiveNumberConverter(3, new Pattern[]{Pattern.compile("^tretj.$", Pattern.CASE_INSENSITIVE)}),
            new DescriptiveNumberConverter(4, new Pattern[]{Pattern.compile("^četrt.$", Pattern.CASE_INSENSITIVE)}),
            new DescriptiveNumberConverter(5, new Pattern[]{Pattern.compile("^pet.$", Pattern.CASE_INSENSITIVE)}),
            new DescriptiveNumberConverter(6, new Pattern[]{Pattern.compile("^šest.$", Pattern.CASE_INSENSITIVE)}),
            new DescriptiveNumberConverter(7, new Pattern[]{Pattern.compile("^sedm.$", Pattern.CASE_INSENSITIVE)}),
            new DescriptiveNumberConverter(8, new Pattern[]{Pattern.compile("^osm.$", Pattern.CASE_INSENSITIVE)}),
            new DescriptiveNumberConverter(9, new Pattern[]{Pattern.compile("^devet.$", Pattern.CASE_INSENSITIVE)}),
            new DescriptiveNumberConverter(10, new Pattern[]{Pattern.compile("^deset.$", Pattern.CASE_INSENSITIVE)}),
            new DescriptiveNumberConverter(11, new Pattern[]{Pattern.compile("^enajst.$", Pattern.CASE_INSENSITIVE)}),
            new DescriptiveNumberConverter(12, new Pattern[]{Pattern.compile("^dvanajst.$", Pattern.CASE_INSENSITIVE)}),
            new DescriptiveNumberConverter(13, new Pattern[]{Pattern.compile("^trinajst.$", Pattern.CASE_INSENSITIVE)}),
            new DescriptiveNumberConverter(14, new Pattern[]{Pattern.compile("^štirinajst.$", Pattern.CASE_INSENSITIVE)}),
            new DescriptiveNumberConverter(15, new Pattern[]{Pattern.compile("^petnajst.$", Pattern.CASE_INSENSITIVE)}),
            new DescriptiveNumberConverter(16, new Pattern[]{Pattern.compile("^šestnajst.$", Pattern.CASE_INSENSITIVE)}),
            new DescriptiveNumberConverter(17, new Pattern[]{Pattern.compile("^sedemnajst.$", Pattern.CASE_INSENSITIVE)}),
            new DescriptiveNumberConverter(18, new Pattern[]{Pattern.compile("^osemnajst.$", Pattern.CASE_INSENSITIVE)}),
            new DescriptiveNumberConverter(19, new Pattern[]{Pattern.compile("^devetnajst.$", Pattern.CASE_INSENSITIVE)}),
            new DescriptiveNumberConverter(20, new Pattern[]{Pattern.compile("^dvajset.$", Pattern.CASE_INSENSITIVE)})
    };

    private static final TimeZone utcTimeZone = TimeZone.getTimeZone("UTC");

    public final String title;
    public final TranslatableValue seriesTitle;
    public LongValue episodeNumber;
    public LongValue airDate;
    public LongValue seasonNumber;

    private EpisodeDetectionSlovene(
            final AttributeValues attributeValues,
            final Language language,
            final RecognitionData recognitionData
    ) {
        String seriesTitle = recognitionData.seriesTitle;
        Integer season = recognitionData.seasonNumber;
        Integer episode = recognitionData.episodeNumber;
        if (season == null) {
            final Matcher m = romanNumeralSuffixPattern.matcher(seriesTitle);
            if (m.matches()) {
                seriesTitle = m.group(1);
                season = convertRomanNumber(m.group(5), m.group(4), m.group(3), m.group(2));
            }
        }

        this.title = recognitionData.title;
        this.seriesTitle = new TranslatableValue(seriesTitle, language);
        final AttributeCodes attributeCodes = DataManager.getAttributeCodes();
        final StringBuilder sb = new StringBuilder(256);
        sb.append("Series detection: matcher=")
                .append(recognitionData.matcherName)
                .append(", title=\"")
                .append(recognitionData.title)
                .append("\", series title=\"")
                .append(seriesTitle)
                .append("\", air date=");
            if (airDate == null) {
                this.airDate = null;
                sb.append("(null)");
            }
            else {
                this.airDate = new LongValue(recognitionData.airDate.getTime() / 1000L, true);
                sb.append(recognitionData.airDate.toString());
            }

        sb.append(", season=");
            if (season == null) {
                this.seasonNumber = null;
                sb.append("(null)");
            }
            else {
                this.seasonNumber = new LongValue(season, false);
                sb.append(season);
            }

        sb.append(", episode=");
            if (episode == null) {
                this.episodeNumber = null;
                sb.append("(null)");
            }
            else {
                this.episodeNumber = new LongValue(episode, false);
                sb.append(episode);
            }

        log.debug(sb.toString());
    }

    @Override
    public String toString() {
        return EpisodeDetectionSlovene.class.getCanonicalName() +
                "(title=\"" + title.replace("\"", "\\\"") +
                "\", seriesTitle=\"" + seriesTitle.value.replace("\"", "\\\"") +
                ", airDate=" + (airDate == null ? "null" : "\"" + airDate.toString() + "\"") +
                ", seasonNumber=" + (seasonNumber == null ? "null" : seasonNumber) +
                ", episodeNumber=" + (episodeNumber == null ? "null" : episodeNumber) +
                ")";
    }

    public void toString(final StringBuilder output) {
        output.append("seriesTitle=");
        if (seriesTitle == null) output.append("(null)");
        else output.append("\"").append(seriesTitle.value.replace("\"", "\\\"")).append("\"");
        output.append(", season=");
        if (seasonNumber == null) output.append("(null)");
        else output.append(seasonNumber.asInteger());
        output.append(", episode=");
        if (episodeNumber == null) output.append("(null)");
        else output.append(episodeNumber.asInteger());
        output.append(", air-date=");
        if (airDate == null) output.append("(null)");
        else output.append(airDate.toString());
    }

    private static Integer convertRomanNumber(final String ones, final String tens, final String hundreds, final String thousands) {
        int n = 0;
        switch (thousands) {
            case "M": n += 1000; break;
            case "MM": n += 2000; break;
            case "MMM": n += 3000; break;
            case "MMMM": n += 4000; break;
        }
        switch (hundreds) {
            case "C": n += 100; break;
            case "CC": n += 200; break;
            case "CCC": n += 300; break;
            case "CD": n += 400; break;
            case "D": n += 500; break;
            case "DC": n += 600; break;
            case "DCC": n += 700; break;
            case "DCCC": n += 800; break;
            case "CM": n += 900; break;
        }
        switch (tens) {
            case "X": n += 10; break;
            case "XX": n += 20; break;
            case "XXX": n += 30; break;
            case "XL": n += 40; break;
            case "L": n += 50; break;
            case "LX": n += 60; break;
            case "LXX": n += 70; break;
            case "LXXX": n += 80; break;
            case "XC": n += 90; break;
        }
        switch (ones) {
            case "I": n += 1; break;
            case "II": n += 2; break;
            case "III": n += 3; break;
            case "IV": n += 4; break;
            case "V": n += 5; break;
            case "VI": n += 6; break;
            case "VII": n += 7; break;
            case "VIII": n += 8; break;
            case "IX": n += 9; break;
        }
        return n;
    }

    private static Integer convertDescriptiveNumber(final String number, final String title) {
        if (number == null) return null;
        if (numberPattern.matcher(number).matches()) return Integer.valueOf(number, 10); // content is already numeric
        for (int i = descriptiveNumberConverters.length - 1; i >= 0; i--) {
            final Integer numeric = descriptiveNumberConverters[i].convert(number);
            if (numeric != null) return numeric; // recognized and converted into numeric form
        }
        log.error("convertDescriptiveNumber(): unable to convert \"" + number + "\" to an integer in the title \"" + title + "\"");
        return null;
    }

    public static EpisodeDetectionSlovene detect(final TranslatableValue title, final AttributeValues attributeValues) {
        final AttributeCodes attributeCodes = DataManager.getAttributeCodes();
        boolean definitelyIsSeries, isSeries;
        boolean isMovie = false;
        boolean isSeriesIfTitleTwoPart = false;
        if (attributeValues != null) {
            definitelyIsSeries = isSeries = attributeValues.contains(attributeCodes.seasonNumber) || attributeValues.contains(attributeCodes.episodeNumber) || attributeValues.contains(attributeCodes.airDate);
            final String[] genres = attributeValues.getAsStrings(attributeCodes.genre);
            for (int i = genres.length - 1; i >= 0; i--) {
                final String genre = genres[i];
                isMovie = isMovie || genre.startsWith("Film") || genre.contains("film");
                isSeries = isSeries || "Serije".equals(genre) || "Nadaljevanka".equals(genre) || "Nanizanka".equals(genre) || "Oddaje".equals(genre) || "Risane serije".equals(genre) || "Telenovele".equals(genre) || "Informativne oddaje".equals(genre) || "Dnevno-informativna oddaja".equals(genre) || "Resničnostna TV".equals(genre) || "Otroška ali mladinska serija".equals(genre);
                isSeriesIfTitleTwoPart = isSeriesIfTitleTwoPart || "Šport".equals(genre) || "Avto-moto športi".equals(genre) || "Verska oddaja".equals(genre) || "Otroška oddaja".equals(genre) || "Razvedrilni program".equals(genre);
            }
        }
        else definitelyIsSeries = isSeries = false;
        if (isMovie && !definitelyIsSeries) return null; // definitely a movie

        final LanguageCodes languageCodes = DataManager.getLanguageCodes();
        final Language slovenian = languageCodes.byIso639_2t("slv");

        // try slovenian translation first
        String translation = title.translations.get(slovenian);
        if (translation != null) {
            return detect(slovenian, translation, isSeries, isSeriesIfTitleTwoPart, attributeValues, attributeCodes);
        }

        // try a translation in an unknown language
        translation = title.translations.get(languageCodes.unknown);
        if (translation != null) {
            return detect(languageCodes.unknown, translation, isSeries, isSeriesIfTitleTwoPart, attributeValues, attributeCodes);
        }
        return null;
    }

    private static final Recognizer[] recognizers = {
            // some episodes from VOYO have really bad metadata
            new Recognizer() {
                @Override
                RecognitionData recognize(final String title, final AttributeValues attributeValues, final AttributeCodes attributeCodes) {
                    final Matcher m = voyoEpisodePattern.matcher(title);
                    if (m.matches()) {
                        final TranslatableValue titleValue = attributeValues.getAsTranslatable(attributeCodes.title);
                        String titleInOtherLanguage = null;
                        for (final Map.Entry<Language, String> entry : titleValue.translations.entrySet()) {
                            if (title.equals(entry.getValue())) continue;
                            titleInOtherLanguage = entry.getValue();
                            break;
                        }
                        if (titleInOtherLanguage != null) {
                            // we have the title in other language, which is the series title, now extract season and episode numbers
                            final Value seasonValue = attributeValues.get(attributeCodes.seasonNumber);
                            final Value episodeValue = attributeValues.get(attributeCodes.episodeNumber);
                            final long season = seasonValue == null ? 0L : seasonValue.asInteger();
                            long episode = episodeValue == null ? 0L : episodeValue.asInteger();
                            if (episode == 0L) {
                                // episode number not present among metadata, extract it from the title
                                try {
                                    episode = Long.parseLong(m.group(1));
                                }
                                catch (NumberFormatException e) {
                                    episode = 0L;
                                }
                            }
                            if (episode > 0L) {
                                // we have episodic data, run with it
                                return new RecognitionData("voyoEpisodePattern", title, titleInOtherLanguage, null, (int)season, (int)episode);
                            }
                        }
                    }
                    return null;
                }
            },
            // complex matches (multiple information extraction)
            new Recognizer() {
                @Override
                public RecognitionData recognize(final String title, final AttributeValues attributeValues, final AttributeCodes attributeCodes) {
                    final Matcher m = sezonaDelPattern1.matcher(title);
                    if (m.matches()) {
                        final String numericSeason = m.group(2);
                        final String numericPart = m.group(4);
                        return fixNumbers("sezonaDelPattern1", true, title, m.group(1), null, numericSeason == null ? m.group(3) : numericSeason, numericPart == null ? m.group(5) : numericPart);
                    }
                    return null;
                }
            },
            new Recognizer() {
                @Override
                public RecognitionData recognize(final String title, final AttributeValues attributeValues, final AttributeCodes attributeCodes) {
                    final Matcher m = epizodaDelPattern1.matcher(title);
                    if (m.matches()) {
                        final String numericSeason = m.group(2);
                        final String numericPart = m.group(4);
                        return fixNumbers("epizodaDelPattern1", true, title, m.group(1), null, numericSeason == null ? m.group(3) : numericSeason, numericPart == null ? m.group(5) : numericPart);
                    }
                    return null;
                }
            },
            // simple matches (single information extraction)
            new Recognizer() {
                @Override
                public RecognitionData recognize(final String title, final AttributeValues attributeValues, final AttributeCodes attributeCodes) {
                    final Matcher m = beginsWithDate.matcher(title);
                    if (m.matches()) {
                        final Calendar calendar = Calendar.getInstance();
                        final int currentMonth = calendar.get(Calendar.MONTH);
                        final int currentYear = calendar.get(Calendar.YEAR);
                        final int day = Integer.parseInt(m.group(1), 10);
                        final int month = Integer.parseInt(m.group(2), 10) - 1;
                        final int year = (month > currentMonth) || ((month == currentMonth) && (day > calendar.get(Calendar.DAY_OF_MONTH))) ? currentYear-1 : currentYear;
                        calendar.clear();
                        calendar.setTimeZone(utcTimeZone); // switch to UTC to get the timestamp
                        calendar.set(year, month, day, 0, 0, 0);
                        return new RecognitionData("beginsWithDate", title, m.group(3), calendar.getTime(), null, null);
                    }
                    return null;
                }
            },
            new Recognizer() {
                @Override
                public RecognitionData recognize(final String title, final AttributeValues attributeValues, final AttributeCodes attributeCodes) {
                    final Matcher m = oddajaPattern1.matcher(title);
                    if (m.matches()) {
                        final String numericEpisode = m.group(2);
                        return fixNumbers("oddajaPattern1", true, title, m.group(1), null, null, numericEpisode == null ? m.group(3) : numericEpisode);
                    }
                    return null;
                }
            },
            new Recognizer() {
                @Override
                public RecognitionData recognize(final String title, final AttributeValues attributeValues, final AttributeCodes attributeCodes) {
                    final Matcher m = oddajaPattern2.matcher(title);
                    if (m.matches()) {
                        final String numericEpisode = m.group(2);
                        return fixNumbers("oddajaPattern2", true, title, m.group(1), null, null, numericEpisode == null ? m.group(3) : numericEpisode);
                    }
                    return null;
                }
            },
            new Recognizer() {
                @Override
                public RecognitionData recognize(final String title, final AttributeValues attributeValues, final AttributeCodes attributeCodes) {
                    final Matcher m = delPattern.matcher(title); // this one must be first, because the ones below would match, also
                    if (m.matches()) {
                        final String numericEpisode = m.group(2);
                        return fixNumbers("delPattern", true, title, m.group(1), null, null, numericEpisode == null ? m.group(3) : numericEpisode);
                    }
                    return null;
                }
            },
            new Recognizer() {
                @Override
                public RecognitionData recognize(final String title, final AttributeValues attributeValues, final AttributeCodes attributeCodes) {
                    final Matcher m = epizodaPattern1.matcher(title);
                    if (m.matches()) {
                        final String numericEpisode = m.group(2);
                        return fixNumbers("epizodaPattern1", true, title, m.group(1), null, null, numericEpisode == null ? m.group(3) : numericEpisode);
                    }
                    return null;
                }
            },
            new Recognizer() {
                @Override
                public RecognitionData recognize(final String title, final AttributeValues attributeValues, final AttributeCodes attributeCodes) {
                    final Matcher m = seansaPattern1.matcher(title);
                    if (m.matches()) {
                        final String numericEpisode = m.group(2);
                        return fixNumbers("seasonPattern1", true, title, m.group(1), null, null, numericEpisode == null ? m.group(3) : numericEpisode);
                    }
                    return null;
                }
            },
            new Recognizer() {
                @Override
                public RecognitionData recognize(final String title, final AttributeValues attributeValues, final AttributeCodes attributeCodes) {
                    final Matcher m = tedenPattern1.matcher(title);
                    if (m.matches()) {
                        final String numericPart = m.group(2); // the part
                        final Calendar calendar = Calendar.getInstance(); // the season
                        return fixNumbers("tedenPattern1", true, title, m.group(1), null, Integer.toString(calendar.get(Calendar.YEAR), 10), numericPart == null ? m.group(3) : numericPart);
                    }
                    return null;
                }
            },
            new Recognizer() {
                @Override
                public RecognitionData recognize(final String title, final AttributeValues attributeValues, final AttributeCodes attributeCodes) {
                    final Matcher m = datumPattern1.matcher(title);
                    if (m.matches()) {
                        final int day = Integer.parseInt(m.group(2), 10);
                        final int month = Integer.parseInt(m.group(3), 10) - 1;
                        final int year = Integer.parseInt(m.group(4), 10);
                        final Calendar calendar = Calendar.getInstance(utcTimeZone); // use UTC to obtain the timestamp
                        calendar.clear();
                        calendar.set(year, month, day, 0, 0, 0);
                        return new RecognitionData("datumPattern1", title, m.group(1), calendar.getTime(), null, null);
                    }
                    return null;
                }
            },
            new Recognizer() {
                @Override
                public RecognitionData recognize(final String title, final AttributeValues attributeValues, final AttributeCodes attributeCodes) {
                    final Matcher m = datumPattern2.matcher(title);
                    if (m.matches()) {
                        final int day = Integer.parseInt(m.group(2), 10);
                        final int month = Integer.parseInt(m.group(3), 10) - 1;
                        final int year = Integer.parseInt(m.group(4), 10);
                        final Calendar calendar = Calendar.getInstance(utcTimeZone); // use UTC to obtain the timestamp
                        calendar.clear();
                        calendar.set(year, month, day, 0, 0, 0);
                        return new RecognitionData("datumPattern2", title, m.group(1), calendar.getTime(), null, null);
                    }
                    return null;
                }
            },
            new Recognizer() {
                @Override
                public RecognitionData recognize(final String title, final AttributeValues attributeValues, final AttributeCodes attributeCodes) {
                    final Matcher m = romanCommaPattern2.matcher(title);;
                    if (m.matches()) {
                        return new RecognitionData("romanCommaPattern", title, m.group(1), null, convertRomanNumber(m.group(3), m.group(2), "", ""), null);
                    }
                    return null;
                }
            },
            new Recognizer() {
                @Override
                RecognitionData recognize(final String title, final AttributeValues attributeValues, final AttributeCodes attributeCodes) {
                    final Matcher m = romanCommaPattern1.matcher(title);
                    if (m.matches()) {
                        return new RecognitionData("romanCommaPattern1", title, m.group(1), null, convertRomanNumber(m.group(2), "", "", ""), null);
                    }
                    return null;
                }
            }
    };

    private static EpisodeDetectionSlovene detect(final Language language, final String title, final boolean isSeries, final boolean isSeriesIfTitleTwoPart, final AttributeValues attributeValues, final AttributeCodes attributeCodes) {

        for (int i = episodeStopPrefixes.length - 1; i >= 0; i--) {
            if (title.startsWith(episodeStopPrefixes[i])) return null; // such titles are 100% not episodes
        }

        final int n = recognizers.length;
        for (int i = 0; i < n; i++) {
            final RecognitionData rd = recognizers[i].recognize(title, attributeValues, attributeCodes);
            if (rd != null) {
                return new EpisodeDetectionSlovene(attributeValues, language, rd);
            }
        }

        if (!(isSeries || isSeriesIfTitleTwoPart)) return null; // we give up, it's probably a movie

        // see if there's an episode title

        int i = 0;
        while (i < unchoppables.length) {
            if (unchoppables[i].equals(title)) break;
            i++;
        }

        if (i >= unchoppables.length) {
            Matcher m = dashColonPattern.matcher(title);
            if (m.matches()) {
                return new EpisodeDetectionSlovene(attributeValues, language, new RecognitionData("dashColonPattern", title, m.group(1), null, null, null));
            }

            if (isSeries) {
                m = trailingRomanNumeralPattern2.matcher(title);
                if (m.matches()) {
                    return new EpisodeDetectionSlovene(attributeValues, language, new RecognitionData("trailingRomanNumeralPattern2", title, m.group(1), null, convertRomanNumber(m.group(3), m.group(2), "", ""), null));
                }

                m = trailingRomanNumeralPattern1.matcher(title);
                if (m.matches()) {
                    return new EpisodeDetectionSlovene(attributeValues, language, new RecognitionData("trailingRomanNumeralPattern1", title, m.group(1), null, convertRomanNumber(m.group(2), "", "", ""), null));
                }
            }
        }

        // we're really desperate now
        if (isSeries) return new EpisodeDetectionSlovene(attributeValues, language, new RecognitionData("default(isSeries=true)", title, title, null, null, null));

        // give up
        return null;
    }

    private static class DescriptiveNumberConverter {
        final Integer number;
        final Pattern[] recognizers;

        DescriptiveNumberConverter(final Integer number, final Pattern[] recognizers) {
            this.number = number;
            this.recognizers = recognizers;
        }

        Integer convert(final String candidate) {
            for (int i = recognizers.length - 1; i >= 0; i--) {
                if (recognizers[i].matcher(candidate).matches()) return number;
            }
            return null;
        }
    }

    static final class RecognitionData {
        final String title;
        final String seriesTitle;
        final Date airDate;
        final Integer seasonNumber;
        final Integer episodeNumber;
        final String matcherName;

        RecognitionData(
                final String matcherName,
                final String title,
                final String seriesTitle,
                final Date airDate,
                final Integer seasonNumber,
                final Integer episodeNumber
        ) {
            this.matcherName = matcherName;
            this.title = title;
            this.seriesTitle = seriesTitle;
            this.airDate = airDate;
            this.seasonNumber = seasonNumber;
            this.episodeNumber = episodeNumber;
        }
    }

    static abstract class Recognizer {
        abstract RecognitionData recognize(final String title, final AttributeValues attributeValues, final AttributeCodes attributeCodes);

        final static RecognitionData fixNumbers(
                final String matcherName,
                final boolean episodicAttributesMustBePresent,
                final String title,
                final String seriesTitle,
                final Date airDate,
                final String seasonNumber,
                final String episodeNumber
        ) {
            final Integer season = convertDescriptiveNumber(seasonNumber, title);
            final Integer episode = convertDescriptiveNumber(episodeNumber, title);
            if (episodicAttributesMustBePresent) {
                if ((season == null) && (episode == null) && (airDate == null)) return null;
            }
            return new RecognitionData(matcherName, title, seriesTitle, airDate, season, episode);
        }
    }
}
