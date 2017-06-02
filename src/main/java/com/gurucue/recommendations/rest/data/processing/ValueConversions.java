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

import com.gurucue.recommendations.ResponseException;
import com.gurucue.recommendations.ResponseStatus;
import com.gurucue.recommendations.entity.value.TimestampIntervalValue;
import com.gurucue.recommendations.type.ValueType;

import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Contains methods to convert the given value to the given type.
 */
public final class ValueConversions {
    private static final TimeZone utcTimeZone = TimeZone.getTimeZone("UTC");

    private ValueConversions() {} // not instantiable

    public static Integer toInteger(final Object content) throws ResponseException {
        if (null == content) return null;
        else if (content instanceof Integer) return (Integer) content;
        else if (content instanceof Number) return ((Number)content).intValue();
        else if (content instanceof Boolean) {
            if (((Boolean)content).booleanValue()) return Integer.valueOf(1);
            return Integer.valueOf(0);
        }
        else if (content instanceof String) {
            final String s = (String)content;
            if (s.length() == 0) return null;
            try {
                return Integer.valueOf(s, 10);
            }
            catch (NumberFormatException e) {
                throw new ResponseException(ResponseStatus.ILLEGAL_INTEGER, "Illegal integer value: " + s);
            }
        }
        throw new ResponseException(ResponseStatus.ILLEGAL_INTEGER, "Illegal integer value: don't know how to parse " + content.getClass().getCanonicalName());
    }

    public static Double toDouble(final Object content) throws ResponseException {
        if (null == content) return null;
        else if (content instanceof Double) return (Double) content;
        else if (content instanceof Number) return ((Number)content).doubleValue();
        else if (content instanceof Boolean) {
            if (((Boolean)content).booleanValue()) return Double.valueOf(1.0d);
            return Double.valueOf(0.0d);
        }
        else if (content instanceof String) {
            final String s = (String)content;
            if (s.length() == 0) return null;
            try {
                return Double.valueOf(s);
            }
            catch (NumberFormatException e) {
                throw new ResponseException(ResponseStatus.ILLEGAL_DOUBLE, "Illegal double value: " + s);
            }
        }
        throw new ResponseException(ResponseStatus.ILLEGAL_DOUBLE, "Illegal double value: don't know how to parse " + content.getClass().getCanonicalName());
    }

    public static Boolean toBoolean(final Object content) throws ResponseException {
        if (null == content) return null;
        else if (content instanceof Boolean) return (Boolean)content;
        else if (content instanceof Number) return ((Number)content).doubleValue() != 0.0;
        else if (content instanceof String) {
            final String s = (String)content;
            if (s.length() == 0) return null;
            if ("yes".equals(s) || "true".equals(s)) return Boolean.TRUE;
            if ("no".equals(s) || "false".equals(s)) return Boolean.FALSE;
            try {
                final long l = Long.parseLong(s, 10);
                if (0 == l) return Boolean.FALSE;
                else return Boolean.TRUE;
            }
            catch (NumberFormatException nfe) {}
            try {
                final double d = Double.parseDouble(s);
                if (0 == d) return Boolean.FALSE;
                else return Boolean.TRUE;
            }
            catch (NumberFormatException nfe) {}
            throw new ResponseException(ResponseStatus.ILLEGAL_BOOLEAN, "Illegal boolean value: " + s);
        }
        throw new ResponseException(ResponseStatus.ILLEGAL_BOOLEAN, "Illegal boolean value: don't know how to parse " + content.getClass().getCanonicalName());
    }

    private static final Lock dateFormatLock = new ReentrantLock();
    private static final SimpleDateFormat europeanDateFormat1 = new SimpleDateFormat("dd.MM.yyyy");
    private static final SimpleDateFormat europeanDateFormat2 = new SimpleDateFormat("dd-MM-yyyy");
    private static final SimpleDateFormat europeanDateFormat3 = new SimpleDateFormat("dd/MM/yyyy");
    private static final SimpleDateFormat europeanDateFormat4 = new SimpleDateFormat("dd MMM yyyy");
    private static final SimpleDateFormat europeanDateFormat5 = new SimpleDateFormat("dd. MMM yyyy");
    private static final SimpleDateFormat europeanDateFormat6 = new SimpleDateFormat("dd-MMM-yyyy");
    private static final SimpleDateFormat europeanDateFormat7 = new SimpleDateFormat("ddMMMyy");
    private static final SimpleDateFormat asianDateFormat1 = new SimpleDateFormat("yyyy-MM-dd");
    private static final SimpleDateFormat chineseDateFormat1 = new SimpleDateFormat("yyyy.MM.dd");
    private static final SimpleDateFormat hungarianDateFormat1 = new SimpleDateFormat("yyyy. MMM dd.");
    private static final SimpleDateFormat hungarianDateFormat2 = new SimpleDateFormat("yyyy. MMM. dd.");
    private static final SimpleDateFormat hungarianDateFormat3 = new SimpleDateFormat("yyyy. MM. dd.");
    private static final SimpleDateFormat americanDateFormat1 = new SimpleDateFormat("dd/MM/yyyy");
    private static final SimpleDateFormat americanDateFormat2 = new SimpleDateFormat("dd-MM-yyyy");
    private static final SimpleDateFormat americanDateFormat3 = new SimpleDateFormat("dd.MM.yyyy");
    private static final SimpleDateFormat americanDateFormat4 = new SimpleDateFormat("dd.MM.yy");
    private static final SimpleDateFormat americanDateFormat5 = new SimpleDateFormat("dd/MM/yy");
    private static final List<SimpleDateFormat> dateFormatList;
    static {
        List<SimpleDateFormat> formats = new ArrayList<SimpleDateFormat>();
        formats.add(europeanDateFormat1);
        formats.add(europeanDateFormat2);
        formats.add(europeanDateFormat3);
        formats.add(europeanDateFormat4);
        formats.add(europeanDateFormat5);
        formats.add(europeanDateFormat6);
        formats.add(europeanDateFormat7);
        formats.add(asianDateFormat1);
        formats.add(chineseDateFormat1);
        formats.add(hungarianDateFormat1);
        formats.add(hungarianDateFormat2);
        formats.add(hungarianDateFormat3);
        formats.add(americanDateFormat1);
        formats.add(americanDateFormat2);
        formats.add(americanDateFormat3);
        formats.add(americanDateFormat4);
        formats.add(americanDateFormat5);
        dateFormatList = Collections.unmodifiableList(formats);
    }

    private static final Lock isoTimestampFormatLock = new ReentrantLock();
    private static final SimpleDateFormat isoTimestampFormat1 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ssXXX");
    private static final SimpleDateFormat isoTimestampFormat2 = new SimpleDateFormat("yyyy-MM-dd HH:mmXXX");
    private static final SimpleDateFormat isoTimestampFormat3 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX");
    private static final SimpleDateFormat isoTimestampFormat4 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mmXXX");
    private static final SimpleDateFormat isoTimestampFormat5 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private static final SimpleDateFormat isoTimestampFormat6 = new SimpleDateFormat("yyyy-MM-dd HH:mm");
    private static final SimpleDateFormat isoTimestampFormat7 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
    private static final SimpleDateFormat isoTimestampFormat8 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm");
    private static final List<SimpleDateFormat> isoTimestampFormatList;
    static {
        List<SimpleDateFormat> formats = new ArrayList<SimpleDateFormat>();
        formats.add(isoTimestampFormat1);
        formats.add(isoTimestampFormat2);
        formats.add(isoTimestampFormat3);
        formats.add(isoTimestampFormat4);
        formats.add(isoTimestampFormat5);
        formats.add(isoTimestampFormat6);
        formats.add(isoTimestampFormat7);
        formats.add(isoTimestampFormat8);
        isoTimestampFormatList = Collections.unmodifiableList(formats);
    }

    public static Date toDate(Object content) throws ResponseException {
        if (null == content) return null;
        if (content instanceof Date) return (Date)content;
        if (content instanceof String) {
            final String stringContent = (String)content;
            dateFormatLock.lock();
            try {
                for (final SimpleDateFormat format : dateFormatList) {
                    try {
                        format.setTimeZone(utcTimeZone);
                        return format.parse(stringContent);
                    }
                    catch (java.text.ParseException e) {}
                }
            }
            finally {
                dateFormatLock.unlock();
            }
            try {
                content = Long.parseLong(stringContent, 10);
            }
            catch (NumberFormatException e) {
                // well, it's not a number
                throw new ResponseException(ResponseStatus.ILLEGAL_DATE, "Illegal date value: don't know how to parse \"" + stringContent.replace("\"", "\\\"") + "\"");
            }
        }
        if (content instanceof Long) {
            // seconds from the epoch
            return new Date((Long)content * 1000L);
        }
        if (content instanceof Integer) {
            // seconds from the epoch
            return new Date(((Integer)content).longValue() * 1000L);
        }
        throw new ResponseException(ResponseStatus.ILLEGAL_DATE, "Illegal date value: don't know how to parse " + content.getClass().getCanonicalName());
    }

    public static Timestamp toTimestamp(Object content) throws ResponseException {
        if (null == content) return null;
        // Timestamp is a subclass of Date, so we must test for Timestamp first
        if (content instanceof Timestamp) return (Timestamp)content;
        if (content instanceof Date) return new Timestamp(((Date)content).getTime());
        if (content instanceof String) {
            final String stringContent = (String)content;
            try {
                // first try to see if it's an integer masked as string, because natively we operate with numbers of seconds since epoch
                content = Long.parseLong(stringContent, 10);
            }
            catch (NumberFormatException e) {
                // well, it's not a number, so try ISO timestamp formats
                isoTimestampFormatLock.lock();
                try {
                    for (final SimpleDateFormat format : isoTimestampFormatList) {
                        try {
                            format.setTimeZone(utcTimeZone);
                            final Date d = format.parse(stringContent);
                            return new Timestamp(d.getTime());
                        }
                        catch (ParseException e2) {}
                    }
                }
                finally {
                    isoTimestampFormatLock.unlock();
                }
                throw new ResponseException(ResponseStatus.ILLEGAL_TIMESTAMP_VALUE, "Illegal timestamp value: don't know how to parse \"" + stringContent.replace("\"", "\\\"") + "\"");
            }
        }
        if (content instanceof Long) {
            // seconds from the epoch
            return new Timestamp((Long)content * 1000L);
        }
        if (content instanceof Integer) {
            // seconds from the epoch
            return new Timestamp(((Integer)content).longValue() * 1000L);
        }
        throw new ResponseException(ResponseStatus.ILLEGAL_TIMESTAMP_VALUE, "Illegal timestamp value: don't know how to parse " + content.getClass().getCanonicalName());
    }

    private static final Pattern timestampIntervalPattern1 = Pattern.compile("^\\s*(\\d+)\\W+(\\d+)\\s*$");
    private static final Pattern timestampIntervalPattern2 = Pattern.compile("^\\s*(\\d{1,4})-(\\d{1,2})-(\\d{1,2})[ T](\\d{1,2}):(\\d{1,2})(?::(\\d{1,2}))?(?:(-|\\+)(\\d{1,2})(?::?(\\d{1,2}))?)?\\W+(\\d{1,4})-(\\d{1,2})-(\\d{1,2})[ T](\\d{1,2}):(\\d{1,2})(?::(\\d{1,2}))?(?:(-|\\+)(\\d{1,2})(?::?(\\d{1,2}))?)?\\s*$");

    public static TimestampIntervalValue toTimestampInterval(final Object content) throws ResponseException {
        if (null == content) return null;
        if (content instanceof TimestampIntervalValue) return (TimestampIntervalValue)content;
        if (content instanceof String) {
            final String s = (String)content;
            final Matcher m1 = timestampIntervalPattern1.matcher(s);
            if (m1.matches()) {
                // convert from seconds
                final long beginTimeSeconds, endTimeSeconds;
                final String bs = m1.group(1);
                final String es = m1.group(2);
                try {
                    beginTimeSeconds = Long.parseLong(bs, 10);
                }
                catch (NumberFormatException e) {
                    throw new ResponseException(ResponseStatus.ILLEGAL_TIMESTAMP_INTERVAL_VALUE, "Illegal timestamp value in a timestamp interval: " + bs);
                }
                try {
                    endTimeSeconds = Long.parseLong(es, 10);
                }
                catch (NumberFormatException e) {
                    throw new ResponseException(ResponseStatus.ILLEGAL_TIMESTAMP_INTERVAL_VALUE, "Illegal timestamp value in a timestamp interval: " + es);
                }
                if (beginTimeSeconds <= endTimeSeconds) return TimestampIntervalValue.fromSeconds(beginTimeSeconds, endTimeSeconds);
                return TimestampIntervalValue.fromSeconds(endTimeSeconds, beginTimeSeconds);
            }
            final Matcher m2 = timestampIntervalPattern2.matcher(s);
            if (m2.matches()) {
                final Calendar c = Calendar.getInstance();
                final long beginTimeMillis, endTimeMillis;
                try {
                    final String offsetDirection1 = m2.group(7);
                    if (offsetDirection1 != null) {
                        int zoneOffset = Integer.parseInt(m2.group(8), 10) * 60; // in minutes
                        final String offsetMinutes1 = m2.group(9);
                        if (offsetMinutes1 != null) zoneOffset += Integer.parseInt(offsetMinutes1, 10);
                        if (offsetDirection1.equals("-")) zoneOffset = -zoneOffset;
                        c.set(Calendar.ZONE_OFFSET, zoneOffset * 60000); // in milliseconds
                    }
                    final int y1 = Integer.parseInt(m2.group(1), 10);
                    c.set(y1 < 1000 ? y1 + 1900 : y1, Integer.parseInt(m2.group(2), 10) - 1, Integer.parseInt(m2.group(3), 10), Integer.parseInt(m2.group(4), 10), Integer.parseInt(m2.group(5), 10));
                    final String sec1 = m2.group(6);
                    if (sec1 == null) c.set(Calendar.SECOND, 0);
                    else c.set(Calendar.SECOND, Integer.parseInt(sec1, 10));
                    c.set(Calendar.MILLISECOND, 0);
                    beginTimeMillis = c.getTimeInMillis();

                    final String offsetDirection2 = m2.group(16);
                    if (offsetDirection2 == null) {
                        c.clear(Calendar.ZONE_OFFSET);
                    } else {
                        int zoneOffset = Integer.parseInt(m2.group(17), 10) * 60; // in minutes
                        final String offsetMinutes1 = m2.group(18);
                        if (offsetMinutes1 != null) zoneOffset += Integer.parseInt(offsetMinutes1, 10);
                        if (offsetDirection2.equals("-")) zoneOffset = -zoneOffset;
                        c.set(Calendar.ZONE_OFFSET, zoneOffset * 60000); // in milliseconds
                    }
                    final int y2 = Integer.parseInt(m2.group(10), 10);
                    c.set(y2 < 1000 ? y2 + 1900 : y2, Integer.parseInt(m2.group(11), 10) - 1, Integer.parseInt(m2.group(12), 10), Integer.parseInt(m2.group(13), 10), Integer.parseInt(m2.group(14), 10));
                    final String sec2 = m2.group(15);
                    if (sec2 == null) c.set(Calendar.SECOND, 0);
                    else c.set(Calendar.SECOND, Integer.parseInt(sec2, 10));
                    c.set(Calendar.MILLISECOND, 0);
                    endTimeMillis = c.getTimeInMillis();
                }
                catch (NumberFormatException e) {
                    throw new ResponseException(ResponseStatus.ILLEGAL_TIMESTAMP_INTERVAL_VALUE, "Illegal timestamp interval value: " + s);
                }
                if (beginTimeMillis <= endTimeMillis) return TimestampIntervalValue.fromMillis(beginTimeMillis, endTimeMillis);
                return TimestampIntervalValue.fromMillis(endTimeMillis, beginTimeMillis);
            }
            throw new ResponseException(ResponseStatus.ILLEGAL_TIMESTAMP_INTERVAL_VALUE, "Illegal timestamp interval value: " + s);
        }
        throw new ResponseException(ResponseStatus.ILLEGAL_TIMESTAMP_INTERVAL_VALUE, "Illegal timestamp interval value: don't know how to parse " + content.getClass().getCanonicalName());
    }

    public static String toString(final Object object) {
        if (null == object) return null;
        if (object instanceof String) return (String)object;
        // Timestamp is a subclass of Date, so we must test for Timestamp first
        if (object instanceof Timestamp) {
            // use number of seconds since epoch, it's easier for machine processing
            return Long.toString(((Timestamp)object).getTime() / 1000L, 10);
        }
        if (object instanceof Date) {
            dateFormatLock.lock();
            try {
                europeanDateFormat1.setTimeZone(utcTimeZone);
                return europeanDateFormat1.format((Date)object);
            }
            finally {
                dateFormatLock.unlock();
            }
        }
        if (object instanceof TimestampIntervalValue) {
            final TimestampIntervalValue ti = (TimestampIntervalValue)object;
            return Long.toString(ti.beginMillis / 1000L) + " " + Long.toString(ti.endMillis / 1000L); // convert to seconds
        }
        return object.toString();
    }

    /**
     * Converts the given value to the value of the given type.
     * It throws an exception if conversion is not possible.
     *
     * @param content the value to convert
     * @param type the type to convert the value into
     * @return the converted value
     */
    public static Object convert(final Object content, final ValueType type) throws ResponseException {
        if (null == content) return null;
        switch (type) {
            case INTEGER:
                return toInteger(content);
            case STRING:
                return toString(content);
            case BOOLEAN:
                return toBoolean(content);
            case DATE:
                return toDate(content);
            case LISTING:
                return toString(content); // this is basically a restricted String: a String whose content is restricted by a list of possible Strings, thus impossible to verify it without the said list
            case FLOAT:
                return toDouble(content);
            case TIMESTAMP:
                return toTimestamp(content);
            case TIMESTAMP_INTERVAL:
                return toTimestampInterval(content);
            default:
                throw new ResponseException(ResponseStatus.UNKNOWN_ERROR, "No rule to convert to value of type " + type.getDescription() + " from value: " + content.toString());
        }
    }
}
