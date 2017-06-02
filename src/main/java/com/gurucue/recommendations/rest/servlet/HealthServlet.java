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
package com.gurucue.recommendations.rest.servlet;

import com.gurucue.recommendations.rest.data.RequestCache;
import com.gurucue.recommendations.ResponseException;
import com.gurucue.recommendations.rest.data.response.RestResponse;

import javax.servlet.annotation.WebServlet;

/**
 * Returns the healths status of the REST service.
 */
@WebServlet(name = "Health", urlPatterns = { "/rest/health" }, description = "REST interface to return the health status.")
public class HealthServlet extends RestServlet {
    private static final long serialVersionUID = -3074958914089247482L;

    public HealthServlet() {
        super("Health");
    }

    @Override
    protected RestResponse restGet(final RequestCache cache, final String[] pathFragments) throws ResponseException {
        return RestResponse.OK;
    }
}
