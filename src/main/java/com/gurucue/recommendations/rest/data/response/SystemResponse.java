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

import com.gurucue.recommendations.rest.servlet.RecommendationServlet;
import com.gurucue.recommendations.translator.DataTranslator;
import com.gurucue.recommendations.ResponseStatus;
import com.gurucue.recommendations.translator.TranslatorAware;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Response for the System REST API call.
 */
public class SystemResponse extends RestResponse {
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

    public SystemResponse() {
        super(ResponseStatus.OK, ResponseStatus.OK.getDescription());
    }

    @Override
    protected void translateRest(final DataTranslator translator) throws IOException {
        final Runtime r = Runtime.getRuntime();
        translator.addKeyValue("processors", r.availableProcessors());
        translator.addKeyValue("freeMemory", r.freeMemory());
        translator.addKeyValue("maxMemory", r.maxMemory());
        translator.addKeyValue("totalMemory", r.totalMemory());
        final List<TranslatorAware> threadData = new ArrayList<TranslatorAware>();
        for (Map.Entry<Thread, StackTraceElement[]> t : Thread.getAllStackTraces().entrySet()) {
            threadData.add(new ThreadData(t.getKey(), t.getValue()));
        }
        translator.addKeyValue("threads", threadData);
        final String time;
        synchronized (dateFormat) {
            time = dateFormat.format(new Date());
        }
        translator.addKeyValue("timestamp", time);
        final List<TranslatorAware> debuggedConsumers = new ArrayList<>();
        for (final String username : RecommendationServlet.debugLoggedConsumers.keySet()) {
            debuggedConsumers.add(new LoggedConsumerData(username));
        }
        translator.addKeyValue("debuggedConsumers", debuggedConsumers);
    }

    private static final class ThreadData implements TranslatorAware {
        final Thread thread;
        final StackTraceElement[] stackTrace;

        ThreadData(final Thread thread, final StackTraceElement[] stackTrace) {
            this.thread = thread;
            this.stackTrace = stackTrace;
        }

        @Override
        public void translate(final DataTranslator translator) throws IOException {
            translator.beginObject("thread");
            translator.addKeyValue("id", thread.getId());
            translator.addKeyValue("name", thread.getName());
            translator.addKeyValue("priority", thread.getPriority());
            translator.addKeyValue("state", thread.getState().toString());
            translator.addKeyValue("isDaemon", thread.isDaemon());
            final StringBuilder sb = new StringBuilder();
            if ((null != stackTrace) && (stackTrace.length > 0)) {
                sb.append(stackTrace[0].toString());
                for (int i = 1; i < stackTrace.length; i++) {
                    sb.append("\n");
                    sb.append(stackTrace[i]);
                }
            }
            translator.addKeyValue("stackTrace", sb.toString());
            translator.endObject();
        }
    }

    private static final class LoggedConsumerData implements TranslatorAware {
        final String username;

        LoggedConsumerData(final String username) {
            this.username = username;
        }

        @Override
        public void translate(final DataTranslator translator) throws IOException {
            translator.beginObject("consumer");
            translator.addKeyValue("username", username);
            translator.endObject();
        }
    }
}
