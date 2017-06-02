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

import com.gurucue.recommendations.ResponseException;
import com.gurucue.recommendations.ResponseStatus;
import com.gurucue.recommendations.Timer;
import com.gurucue.recommendations.Transaction;
import com.gurucue.recommendations.data.DataLink;
import com.gurucue.recommendations.data.DataManager;
import com.gurucue.recommendations.data.DataTypeCodes;
import com.gurucue.recommendations.data.ProductTypeCodes;
import com.gurucue.recommendations.dto.ConsumerEntity;
import com.gurucue.recommendations.dto.RelationConsumerProductEntity;
import com.gurucue.recommendations.entity.Consumer;
import com.gurucue.recommendations.entity.ConsumerEvent;
import com.gurucue.recommendations.entity.DataType;
import com.gurucue.recommendations.entity.Partner;
import com.gurucue.recommendations.entity.product.Product;
import com.gurucue.recommendations.rest.data.RequestCache;
import com.gurucue.recommendations.rest.data.container.ConsumerEventDataInput;
import com.gurucue.recommendations.rest.data.container.EventInput;
import com.gurucue.recommendations.rest.data.processing.zap.ConsumerEventProcessor;
import com.gurucue.recommendations.rest.data.response.RestResponse;
import com.gurucue.recommendations.DatabaseException;

import javax.servlet.annotation.WebServlet;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.Timestamp;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Processes the consumer event REST requests.
 */
@WebServlet(name = "Event", urlPatterns = { "/rest/event" }, description = "REST interface for events.")
public final class EventServlet extends RestServlet {
    private static final long serialVersionUID = 7142104276642767759L;

    public EventServlet() {
        super("Event");
    }

    /**
     * Perform event request.
     *
     * @param cache            The RequestCache instance.
     * @param requestFormat    The format that the request is in.
     * @param request          The request content.
     * @return RestResponse
     * @throws com.gurucue.recommendations.ResponseException
     */
    @Override
    protected final RestResponse restPut(final RequestCache cache, final String[] pathFragments, final MimeType requestFormat, final String request) throws ResponseException {
        final long startNano = System.nanoTime();
        final DataLink dataLink = DataManager.getCurrentLink();
        final Partner partner = cache.getPartner();

        final ConsumerEvent event = new ConsumerEvent();
        event.setRequestTimestamp(new java.sql.Timestamp(Timer.currentTimeMillis()));
        event.setPartner(partner);
        event.setResponseCode(-1);

        Product product = null;

        // collect some stats
        boolean consumerWasCreated = false;
        long parseNano = startNano;
        long consumerNano = startNano;
        long productNano = startNano;
        long assemblyNano = startNano;
        Integer responseCode = ResponseStatus.OK.getCode();
        StringBuilder responseMessage = new StringBuilder(64);
        String addedConsumer = null;

        try {
        	
            // ----
            // -- Parse the request and create the response.
            // ----
            final EventInput eventInput = EventInput.parse(requestFormat.CONTENT_FORMAT.NAME, request);
            parseNano = System.nanoTime();
            final String username = eventInput.getUserId();


            ConsumerEntity consumerEntity = dataLink.getConsumerManager().getByPartnerIdAndUsernameAndTypeAndParent(partner.getId().longValue(), username, 1L, 0L);
            if (consumerEntity == null) {
                consumerEntity = dataLink.getConsumerManager().merge(partner.getId().longValue(), username, false, null, 1L, 0L);
                addedConsumer = username;
                consumerWasCreated = true;
            }
            else {
                consumerWasCreated = false;
            }
            consumerNano = System.nanoTime();

            final Consumer consumer = new Consumer(consumerEntity.id, username, partner, new Timestamp(consumerEntity.activated)); // TODO: this is for backwards compatibility
            
            // ----
            // -- Construct data structures.
            // ----
            final DataTypeCodes codes = DataManager.getDataTypeCodes();
            final Map<DataType, String> data = new HashMap<>(); // create array for event descriptors early, we may need it for additional error descriptors
            if (eventInput.getProductType() == null) {
                if (eventInput.getProductId() != null) {
                    responseCode = ResponseStatus.INVALID_PRODUCT_ID.getCode();
                    responseMessage.append("Product ID was specified, but product type is missing");
                    data.put(codes._errProductType, null); // TODO: test if nulls get through
                    data.put(codes._errProductId, eventInput.getProductId());
                }
            }
            else if (eventInput.getProductId() == null) {
                responseCode = ResponseStatus.INVALID_PRODUCT_ID.getCode();
                responseMessage.append("Product type was specified, but product ID is missing");
                data.put(codes._errProductType, eventInput.getProductType().getIdentifier());
                data.put(codes._errProductId, null); // TODO: test if nulls get through
            }
            else {
                product = dataLink.getProductManager().getProductByPartnerAndTypeAndCode(Transaction.get(), partner, eventInput.getProductType(), eventInput.getProductId(), false);
                if (product == null) {
                    responseCode = ResponseStatus.NO_SUCH_PRODUCT_ID.getCode();
                    responseMessage.append("The product of type ").append(eventInput.getProductType().getIdentifier()).append(" with ID ").append(eventInput.getProductId()).append(" does not exist");
                    data.put(codes._errProductType, eventInput.getProductType().getIdentifier());
                    data.put(codes._errProductId, eventInput.getProductId());
                }
            }
            productNano = System.nanoTime();
            //TODO set the profile id for this event

            ConsumerEventDataInput contentDuration = null;
            ConsumerEventDataInput watchPercentage = null;
            ConsumerEventDataInput watchDuration = null;
            final long contentDurationCode = codes.idForContentDuration;
            final long watchPercentageCode = codes.idForWatchPercentage;
            final long watchDurationCode = codes.idForWatchDuration;
            String userProfile = "";
            for (final ConsumerEventDataInput dataInput : eventInput.getData()) {
                final long id = dataInput.getType().getId().longValue();
                if (id == contentDurationCode) contentDuration = dataInput;
                else if (id == watchPercentageCode) watchPercentage = dataInput;
                else if (id == watchDurationCode) watchDuration = dataInput;
                if (id == codes.idForUserProfileId) {
                	userProfile = dataInput.getValue();
                }
                else {
                	data.put(dataInput.getType(), dataInput.getValue());
                }
            }
            
            final long userProfileId = getUserProfileId(userProfile,dataLink,partner,consumerEntity,cache);
            
            event.setEventTimestamp(eventInput.getTimestamp());
            event.setPartner(partner);
            event.setProduct(product);
            event.setConsumer(consumer);
            event.setEventType(eventInput.getEventType());
            event.setUserProfileId(userProfileId);
            event.setData(data);
            
            
            if ((contentDuration != null) && (watchDuration != null) && (watchPercentage == null)) {
                // artificially add the watch percentage
                long content = -1L;
                try {
                    content = Long.parseLong(contentDuration.getValue(), 10);
                }
                catch (NumberFormatException e) {}
                long watch = -1L;
                try {
                    watch = Long.parseLong(watchDuration.getValue(), 10);
                }
                catch (NumberFormatException e) {}
                if ((content > 0L) && (watch >= 0L)) {
                    final long percentage = (watch * 100L) / content;
                    data.put(codes.watchPercentage, percentage >= 100 ? "100" : Long.toString(percentage, 10));
                }
            }

            assemblyNano = System.nanoTime();

            // ----
            // -- Return the response.
            // ----
            final RestResponse response;
            if (responseCode.intValue() == ResponseStatus.OK.getCode().intValue()) {
                if (addedConsumer == null) {
                    response = RestResponse.OK;
                }
                else {
                    responseMessage.append("OK, added consumer ").append(addedConsumer);
                    response = new RestResponse(responseCode, responseMessage.toString());
                }
            }
            else {
                response = new RestResponse(responseCode, responseMessage.toString());
            }

            event.setResponseCode(response.resultCode);
            if (responseCode.intValue() != ResponseStatus.OK.getCode().intValue()) {
                event.setFailedRequest(request);
                event.setFailureCondition(response.resultMessage);
            }
            return response;

        }
        catch (Throwable e) {
            final boolean isResponseException = e instanceof ResponseException;
            event.setResponseCode(isResponseException ? ((ResponseException)e).getStatus().getCode() : ResponseStatus.UNKNOWN_ERROR.getCode());
            event.setFailedRequest(request);
            // print error to string
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            event.setFailureCondition(sw.toString());
            if (isResponseException) throw (ResponseException)e;
            if (e instanceof DatabaseException) throw (DatabaseException)e;
            throw new ResponseException(ResponseStatus.UNKNOWN_ERROR, e, "Internal error: " + e.toString());
        }
        finally {
            try {
                event.setRequestDuration((System.nanoTime() - startNano) / 1000000L);
            }
            finally {
                final long preQueueNano = System.nanoTime();
                ConsumerEventProcessor.INSTANCE.saveNewServiceEvent(event);
                final long postQueueNano = System.nanoTime();
                final long totalNano = postQueueNano - startNano;
                if (totalNano > 5000000L) { // more than 5 ms: log timings
                    final StringBuilder sb = new StringBuilder(250);
                    sb.append("Timings: parsing: ");
                    sb.append(parseNano - startNano);
                    sb.append(" ns, finding consumer: ");
                    sb.append(consumerNano - parseNano);
                    sb.append(" ns");
                    if (consumerWasCreated) {
                        sb.append(" (consumer was created)");
                    }
                    sb.append(", finding product: ");
                    sb.append(productNano - consumerNano);
                    sb.append(" ns, response assembly: ");
                    sb.append(assemblyNano - productNano);
                    sb.append(" ns, event queueing: ");
                    sb.append(postQueueNano - preQueueNano);
                    sb.append(" ns, total: ");
                    sb.append(postQueueNano - startNano);
                    sb.append(" ns");
                    cache.getLogger().debug(sb.toString());
                }
            }
        }

    }
    
    private static long getUserProfileId(final String userProfile , final DataLink dataLink, final Partner partner,final ConsumerEntity consumerEntity, final RequestCache cache) {
        final List<ConsumerEntity> childrenIds = dataLink.getConsumerManager().getActiveChildren(consumerEntity.id, false);
        final long userProfileId = 0;
        
        for(ConsumerEntity profile : childrenIds){
            if(profile.username.equals(userProfile)){
                return profile.id;
            }
        }

        ConsumerEntity profileEntity = dataLink.getConsumerManager().update(partner.getId().longValue(), userProfile, false, Collections.<RelationConsumerProductEntity>emptyList(), 2L, consumerEntity.id);

        //log the creation of the new profile
        StringBuilder profileCreatedLogBuilder = new StringBuilder(75);

        profileCreatedLogBuilder.append("created new profile: ");
        profileCreatedLogBuilder.append(userProfileId);
        profileCreatedLogBuilder.append(" '");
        profileCreatedLogBuilder.append(userProfile);
        profileCreatedLogBuilder.append("', for consumer: ");
        profileCreatedLogBuilder.append(consumerEntity.id);
        profileCreatedLogBuilder.append(" '");
        profileCreatedLogBuilder.append(consumerEntity.username);
        profileCreatedLogBuilder.append("'");

        cache.getLogger().warn(profileCreatedLogBuilder.toString());
        return profileEntity.id;
    }
}
