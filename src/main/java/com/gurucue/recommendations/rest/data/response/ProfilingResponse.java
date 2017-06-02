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

import com.gurucue.recommendations.translator.DataTranslator;
import com.gurucue.recommendations.ResponseStatus;

import java.io.IOException;

public class ProfilingResponse extends RestResponse {
    private final String token;
    private final String profilingUrl;

    public ProfilingResponse(final String token, final String profilingUrl) {
        super(ResponseStatus.OK); // no other status is sensible; for errors just a RestResponse should be returned
        this.token = token;
        this.profilingUrl = profilingUrl;
    }

    @Override
    protected void translateRest(final DataTranslator translator) throws IOException {
        translator.addKeyValue("token", token);
        translator.addKeyValue("profilingUrl", profilingUrl);
    }
}
