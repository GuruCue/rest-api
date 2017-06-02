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

import com.gurucue.recommendations.data.DataManager;
import com.gurucue.recommendations.rest.data.RequestCache;
import com.gurucue.recommendations.ResponseException;
import com.gurucue.recommendations.rest.data.response.RestResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.servlet.annotation.WebServlet;

/**
 * Drops internal caches. Useful in case an external process changes the database.
 */
@WebServlet(name = "DropCaches", urlPatterns = { "/rest/dropCaches" }, description = "REST interface for dropping caches.")
public class CacheServlet extends RestServlet {
    private static final long serialVersionUID = 2057221594217796626L;
    private static final Logger log = LogManager.getLogger(CacheServlet.class);

    public CacheServlet() {
        super("DropCaches");
    }

    @Override
    protected RestResponse restGet(final RequestCache cache, final String[] pathFragments) throws ResponseException {
        log.warn("Clearing caches");
        DataManager.clearCaches();
        //TODO: uncomment and fix
        //CachingProductFilter.clearAll();
        log.warn("Caches cleared");
        return RestResponse.OK;
    }
}
