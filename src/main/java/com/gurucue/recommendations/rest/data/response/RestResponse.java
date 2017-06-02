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

import com.gurucue.recommendations.translator.DataTranslator;
import com.gurucue.recommendations.translator.JSONTranslator;
import com.gurucue.recommendations.ResponseStatus;
import com.gurucue.recommendations.translator.TranslatorAware;
import com.gurucue.recommendations.translator.XMLTranslator;

/**
 * Base output abstraction class for RESTful web services.
 * Every RESTful web service controller should perform its output
 * with an appropriate TranslatorAware class, which would
 * ideally be a descendant of RestResponse, because it already
 * implements the needed status fields.
 * <p>
 * Descending classes should add their fields and override the
 * {@link #translateRest(DataTranslator)} method to perform their
 * output.
 * @see com.gurucue.recommendations.translator.TranslatorAware
 * @see com.gurucue.recommendations.ResponseStatus
 */
public class RestResponse implements TranslatorAware {
    /**
     * The "OK" response as a shortcut, to be reused instead of
     * instantiated million times. It is initialized with
     * <code>ResponseStatus.OK</code>.
     */
    public static final RestResponse OK = new RestResponse(ResponseStatus.OK);

    public final Integer resultCode;
    public final String resultMessage;

    public RestResponse(final Integer resultCode, final String resultMessage) {
        this.resultCode = resultCode;
        this.resultMessage = resultMessage;
    }

    public RestResponse(final ResponseStatus status) {
        this(status.getCode(), status.getDescription());
    }

    public RestResponse(final ResponseStatus status, String resultMessage) {
        this(status.getCode(), resultMessage);
    }
    
    protected void translateRest(final DataTranslator translator) throws IOException {
        // to be overridden in child classes
    }

    @Override
    public final void translate(final DataTranslator translator) throws IOException {
        translator.beginObject("response");
        translator.addKeyValue("resultCode", resultCode);
        translator.addKeyValue("resultMessage", resultMessage);
        translateRest(translator);
        translator.endObject();
    }

    public final String toJSON() {
        return new JSONTranslator().translate(this);
    }

    public final String toXML() {
        return new XMLTranslator().translate(this);
    }
}
