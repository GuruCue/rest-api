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

import com.gurucue.recommendations.DatabaseException;
import com.gurucue.recommendations.ResponseException;
import com.gurucue.recommendations.ResponseStatus;
import com.gurucue.recommendations.Timer;
import com.gurucue.recommendations.Transaction;
import com.gurucue.recommendations.blender.BlendEnvironment;
import com.gurucue.recommendations.blender.BlendParameters;
import com.gurucue.recommendations.blender.BlenderResult;
import com.gurucue.recommendations.blender.TopBlender;
import com.gurucue.recommendations.blender.VideoData;
import com.gurucue.recommendations.data.AttributeCodes;
import com.gurucue.recommendations.data.DataLink;
import com.gurucue.recommendations.data.DataManager;
import com.gurucue.recommendations.dto.ConsumerEntity;
import com.gurucue.recommendations.entity.LogSvcRecommendation;
import com.gurucue.recommendations.entity.Partner;
import com.gurucue.recommendations.rest.data.DatabaseWorkerJob;
import com.gurucue.recommendations.rest.data.DatabaseWorkerThread;
import com.gurucue.recommendations.rest.data.RequestCache;
import com.gurucue.recommendations.rest.data.RequestLogger;
import com.gurucue.recommendations.rest.data.container.SearchInput;
import com.gurucue.recommendations.rest.data.response.MovieRecommendationsResponse;
import com.gurucue.recommendations.rest.data.response.RestResponse;
import com.gurucue.recommendations.rest.recommender.BlenderGroup;
import com.gurucue.recommendations.rest.recommender.BlenderHandler;
import com.gurucue.recommendations.rest.recommender.RecommenderProviderImpl;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.servlet.annotation.WebServlet;
import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Servlet for handling search requests.
 */
@WebServlet(name = "Search", urlPatterns = { "/rest/search" }, description = "REST interface for search.")
public final class SearchServlet extends RestServlet {
    private static final long serialVersionUID = -4167206080184884738L;

    public SearchServlet() {
        super("Search");
    }

    protected RestResponse restPost(final RequestCache cache, final String[] pathFragments, final MimeType requestFormat, final String request) throws ResponseException {
        final long startNano = System.nanoTime();
        final RequestLogger logger = cache.getLogger().subLogger(getClass().getSimpleName());

        BlendParameters recInputBlendParams = null;

        Throwable error = null;

        final Partner partner = cache.getPartner();
        final LogSvcRecommendation serviceLog = new LogSvcRecommendation();
        final long currentTimestampMillis = Timer.currentTimeMillis();
        serviceLog.setRequestTimestamp(new java.sql.Timestamp(currentTimestampMillis));
        serviceLog.setPartner(partner);
        serviceLog.setResponseCode(-1);

        final DataLink link = DataManager.getCurrentLink();
        final Transaction transaction = Transaction.get();
        final AttributeCodes attributeCodes = DataManager.getAttributeCodes();
        BlenderResult<VideoData> blenderResult = null;

        try {
            // ----
            // -- Parse the request and set all available log values early.
            // ----
            final SearchInput searchInput = SearchInput.parse(requestFormat.CONTENT_FORMAT.NAME, request);
            final Integer maxRecommendations = searchInput.getMaxResults();
            serviceLog.setMaxRecommendations(maxRecommendations);
            final String requestedRecommender = searchInput.getType();
            serviceLog.setPartnerRecommenderName(requestedRecommender);
            recInputBlendParams = searchInput.asBlendParameters();
            final StringBuilder blenderLog = new StringBuilder(4096);

            final String username = searchInput.getUserId();

            final long timeObtainConsumer = System.nanoTime();
            final String responseMessage;
            ConsumerEntity consumerEntity = link.getConsumerManager().getByPartnerIdAndUsernameAndTypeAndParent(partner.getId().longValue(), username, 1L, 0L);
            if (consumerEntity == null) {
                consumerEntity = link.getConsumerManager().merge(partner.getId().longValue(), username, false, null, 1L, 0L);
                responseMessage = "OK, added consumer " + username;
                blenderLog.append("Created a new consumer ").append(consumerEntity.id).append(" (").append(username).append("); ");
            }
            else {
                responseMessage = "OK";
                blenderLog.append("Serving an existing consumer ").append(consumerEntity.id).append(" (").append(username).append("); ");
            }

            final long timeServiceLog1 = System.nanoTime();
            serviceLog.setConsumerId(consumerEntity.id);


            // ----
            // -- get the blender and do the blending
            // ----

            final long timeObtainBlender = System.nanoTime();

            final TopBlender blender = BlenderHandler.INSTANCE.getTopBlender(partner.getUsername(), BlenderGroup.Search);

            if (blender == null) throw new ResponseException(ResponseStatus.NO_BLENDER_FOR_PARTNER);

            serviceLog.setBlenderName(blender.getClass().getName()); // TODO: fix this

            final long timeBlendingStart = System.nanoTime();
            final BlendEnvironment environment = new BlendEnvironment(link.getProvider(), RecommenderProviderImpl.INSTANCE, transaction, partner, consumerEntity, currentTimestampMillis, RecommendationServlet.debugLoggedConsumers.containsKey(username));
            blenderResult = blender.blend(VideoData.class, environment, recInputBlendParams);
            final long timeBlendingEnd = System.nanoTime();

            final long timeLoggingStart = System.nanoTime();
            blenderLog.append("blending finished, timings:\n    obtaining consumer: ").append(timeServiceLog1 - timeObtainConsumer)
                    .append(" ns\n    initializing service log: ").append(timeObtainBlender - timeServiceLog1)
                    .append(" ns\n    obtaining the blender: ").append(timeBlendingStart - timeObtainBlender)
                    .append(" ns\n    total blending time: ").append(timeBlendingEnd - timeBlendingStart)
                    .append(" ns\nBlending logs:\n");

            blenderResult.dataSet.writeLog(blenderLog);
            final long timeLoggingEnd = System.nanoTime();

            // ----
            // -- format the response
            // ----

            final MovieRecommendationsResponse response = MovieRecommendationsResponse.fromDataSet(blenderResult.dataSet, responseMessage, attributeCodes, logger, partner);

            final long timeResponseFormattingEnd = System.nanoTime();
            // the last output finished with a newline
            blenderLog.append("Additional timings:\n    assembling this log output: ").append(timeLoggingEnd - timeLoggingStart)
                    .append(" ns\n    formatting the ").append(requestFormat.CONTENT_FORMAT.NAME).append(" response: ").append(timeResponseFormattingEnd - timeLoggingEnd)
                    .append(" ns\n    total processing time: ").append(timeResponseFormattingEnd - timeObtainConsumer)
                    .append(" ns"); // don't end with a newline, the logger does that
            logger.debug(blenderLog.toString());
            // ----
            // -- Return the response.
            // ----
            serviceLog.setResponseCode(response.resultCode);
            return response;
        }
        catch (ResponseException e) {
            error = e;
            serviceLog.setResponseCode(e.getStatus().getCode());
            throw e;
        }
        catch (Throwable e) {
            error = e;
            serviceLog.setResponseCode(ResponseStatus.UNKNOWN_ERROR.getCode());
            if (e instanceof DatabaseException) throw (DatabaseException)e;
            throw new ResponseException(ResponseStatus.UNKNOWN_ERROR, e, "Internal error: " + e.toString());
        }
        finally {
            try {
                if (null != error) {
                    serviceLog.setFailedRequest(request);

                    // print error to string
                    StringWriter sw = new StringWriter();
                    error.printStackTrace(new PrintWriter(sw));
                    serviceLog.setFailureCondition(sw.toString());
                }
                serviceLog.setRequestDuration((System.nanoTime() - startNano) / 1000000L);
            }
            finally {
                DatabaseWorkerThread.INSTANCE.addJob(new LogJob(serviceLog, blenderResult, recInputBlendParams));
            }
        }
    }

    public static final class LogJob extends DatabaseWorkerJob {
        private static final Logger log = LogManager.getLogger(DatabaseWorkerJob.class);

        final LogSvcRecommendation serviceLog;
        final BlenderResult<VideoData> blenderResult;
        final BlendParameters params;

        LogJob(final LogSvcRecommendation serviceLog, final BlenderResult<VideoData> blenderResult, final BlendParameters params) {
            this.serviceLog = serviceLog;
            this.blenderResult = blenderResult;
            this.params = params;
        }

        @Override
        public void execute(final Transaction transaction) {
            try {
                serviceLog.blenderParamsToJson(params);
                if (blenderResult != null) {
                    serviceLog.setBlenderName(blenderResult.blenderName);
                    serviceLog.setResponse(blenderResult.dataSet);
                    serviceLog.setJsonFeedback(blenderResult.blenderFeedback);
                }
            }
            catch (RuntimeException e) {
                log.error("Exception while preparing search request log entry: " + e.toString(), e);
            }

            transaction.getLink().getLogSvcSearchManager().put(serviceLog); // TODO: implement LogSvcSearchManager and use it to store logs
        }
    }
}
