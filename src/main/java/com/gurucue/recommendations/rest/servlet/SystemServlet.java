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
import com.gurucue.recommendations.rest.data.container.SystemInput;
import com.gurucue.recommendations.rest.data.response.RestResponse;
import com.gurucue.recommendations.rest.data.response.SystemResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.servlet.annotation.WebServlet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Servlet used for retrieving and setting internal states/stats.
 */
@WebServlet(name = "System", urlPatterns = { "/rest/system" }, description = "REST interface to system settings/stats.")
public class SystemServlet extends RestServlet {
    private static final long serialVersionUID = -8246922464284744058L;
    private static final Logger log = LogManager.getLogger(SystemServlet.class);

    public SystemServlet() {
        super("System");
    }

    @Override
    protected RestResponse restGet(final RequestCache cache, final String[] pathFragments) throws ResponseException {
        return new SystemResponse();
    }

    @Override
    protected RestResponse restPost(final RequestCache cache, final String[] pathFragments, final MimeType requestFormat, final String request) throws ResponseException {
        final SystemInput input = SystemInput.parse(requestFormat.CONTENT_FORMAT.NAME, request);
        if (input.isRefreshCache()) {
            log.info("Clearing caches");
            DataManager.clearCaches();
        }
        if (input.isAiFullUpdate()) {
            log.info("Triggering full update of AI");
        }
        if (input.getNewConsumerEventQueueSize() != null) {
            log.info("Attempting to resize consumer event queue to " + input.getNewConsumerEventQueueSize());
            DataManager.resizeConsumerEventQueueSize(input.getNewConsumerEventQueueSize());
        }
        if (input.getNewConsumerEventThreadPoolSize() != null) {
            log.info("Attempting to resize consumer event queue processing thread pool size to " + input.getNewConsumerEventThreadPoolSize());
            DataManager.resizeConsumerEventQueueThreadPool(input.getNewConsumerEventThreadPoolSize());
        }
        final List<String> debuggedConsumers = input.getDebuggedConsumers();
        if (debuggedConsumers != null) {
            final int count = debuggedConsumers.size();
            if (count == 0) {
                RecommendationServlet.debugLoggedConsumers.clear();
                log.info("Cleared the set of debugged consumers");
            }
            else {
                final Boolean t = Boolean.TRUE;
                final Map<String, Boolean> m = new HashMap<>(count);
                final StringBuilder logBuilder = new StringBuilder(200);
                logBuilder.append("Replaced the set of debugged consumers with usernames: ");
                final Iterator<String> consumerIterator = debuggedConsumers.iterator();
                if (consumerIterator.hasNext()) {
                    String username = consumerIterator.next();
                    m.put(username, t);
                    logBuilder.append(username);
                    while (consumerIterator.hasNext()) {
                        username = consumerIterator.next();
                        m.put(username, t);
                        logBuilder.append(", ").append(username);
                    }
                }
                RecommendationServlet.debugLoggedConsumers.clear();
                RecommendationServlet.debugLoggedConsumers.putAll(m);
                log.info(logBuilder.toString());
            }
        }
        return RestResponse.OK;
    }
}
