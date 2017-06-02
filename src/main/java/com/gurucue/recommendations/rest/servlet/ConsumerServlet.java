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

import com.gurucue.recommendations.Timer;
import com.gurucue.recommendations.Transaction;
import com.gurucue.recommendations.data.DataLink;
import com.gurucue.recommendations.data.DataManager;
import com.gurucue.recommendations.data.ProductTypeCodes;
import com.gurucue.recommendations.dto.ConsumerEntity;
import com.gurucue.recommendations.dto.RelationConsumerProductEntity;
import com.gurucue.recommendations.entity.RelationConsumerProduct;
import com.gurucue.recommendations.entity.product.Product;
import com.gurucue.recommendations.entitymanager.ConsumerManager;
import com.gurucue.recommendations.entitymanager.ProductManager;
import com.gurucue.recommendations.entitymanager.RelationTypeManager;
import com.gurucue.recommendations.rest.data.RequestCache;
import com.gurucue.recommendations.ResponseException;
import com.gurucue.recommendations.ResponseStatus;
import com.gurucue.recommendations.rest.data.container.ConsumerInput;
import com.gurucue.recommendations.rest.data.response.ConsumerResponse;
import com.gurucue.recommendations.rest.data.response.RestResponse;
import com.gurucue.recommendations.DatabaseException;

import javax.servlet.annotation.WebServlet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Processes the consumer-handling REST requests.
 */
@WebServlet(name = "ConsumerServlet", urlPatterns = { "/rest/consumer", "/rest/consumer/*" }, description = "REST interface for consumer management.")
public class ConsumerServlet extends RestServlet {
    private static final long serialVersionUID = -1616296212825841519L;

    public ConsumerServlet() {
        super("Consumer");
    }

    @Override
    protected RestResponse restGet(final RequestCache cache, final String[] pathFragments) throws ResponseException {
        if (pathFragments.length != 1) throw new ResponseException(ResponseStatus.MALFORMED_REQUEST);
        final DataLink link = DataManager.getCurrentLink();
        final ConsumerEntity consumer = link.getConsumerManager().getByPartnerIdAndUsernameAndTypeAndParent(cache.getPartner().getId().longValue(), pathFragments[0], 1L, 0L);
        if ((consumer == null) || (consumer.status != 1)) throw new ResponseException(ResponseStatus.UNKNOWN_ERROR, "There is no consumer " + pathFragments[0]);

        final ProductManager productManager = link.getProductManager();
        final RelationTypeManager relationTypeManager = link.getRelationTypeManager();
        final ProductTypeCodes productTypeCodes = DataManager.getProductTypeCodes();
        final Transaction transaction = Transaction.get();
        final long now = Timer.currentTimeMillis();
        ConsumerResponse response = new ConsumerResponse(consumer.username);
        for (RelationConsumerProductEntity productRelation : consumer.relations) {
            if ((productRelation.relationEnd < 0L) || (productRelation.relationEnd > now)) {
                final Product p = productManager.getById(transaction, cache.getPartner(), productRelation.productId, false);
                response.addProductRelation(
                        productTypeCodes.byId(p.productTypeId).getIdentifier(),
                        p.partnerProductCode,
                        relationTypeManager.getById(productRelation.relationTypeId).getIdentifier(),
                        productRelation.relationStart,
                        productRelation.relationEnd
                );
            }
        }
        final List<ConsumerEntity> children = link.getConsumerManager().getActiveChildren(consumer.id, false);
        if ((children != null) && !children.isEmpty()) {
            for (final ConsumerEntity child : children) {
                if ((child.username != null) && (child.username.length() > 0)) {
                    response.addUserProfileId(child.username);
                }
            }
        }

        return response;
    }

    @Override
    protected RestResponse restDelete(final RequestCache cache, final String[] pathFragments) throws ResponseException {
        if ((pathFragments.length < 1) || (pathFragments.length > 2)) throw new ResponseException(ResponseStatus.MALFORMED_REQUEST);
        if (pathFragments.length == 2) {
            if (!"now".equalsIgnoreCase(pathFragments[1])) throw new ResponseException(ResponseStatus.MALFORMED_REQUEST);
        }
        if (DataManager.getCurrentLink().getConsumerManager().delete(cache.getPartner().getId(), pathFragments[0], 1L, 0L, pathFragments.length < 2) == null) {
            return new RestResponse(2, "OK, consumer does not exist.");
        }
        return RestResponse.OK;
    }


    /**
     * Handle consumer PUT request.
     *
     * @param cache            The RequestCache instance.
     * @param requestFormat    The format that the request is in.
     * @param request          The request content.
     * @return RestResponse
     * @throws com.gurucue.recommendations.ResponseException
     */
    @Override
    protected RestResponse restPut(final RequestCache cache, final String[] pathFragments, final MimeType requestFormat, final String request) throws ResponseException {
        return process(cache, pathFragments, requestFormat, request, true);
    }

    @Override
    protected RestResponse restPost(final RequestCache cache, final String[] pathFragments, final MimeType requestFormat, final String request) throws ResponseException {
        // TODO: basically this should do consumerManager.merge()
        return process(cache, pathFragments, requestFormat, request, false);
    }

    private RestResponse process(final RequestCache cache, final String[] pathFragments, final MimeType requestFormat, final String request, final boolean replace) throws ResponseException {
        ConsumerInput consumerInput;
        final long now = (Timer.currentTimeMillis() * 1000L) / 1000L; // resolution is 1 second

        DataLink link = DataManager.getCurrentLink();

        try {
            // ----
            // -- Parse the request.
            // ----
            consumerInput = ConsumerInput.parse(requestFormat.CONTENT_FORMAT.NAME, request, cache);

            final ConsumerManager consumerManager = link.getConsumerManager();
            final long partnerId = cache.getPartner().getId().longValue();
            final String username = consumerInput.getUserId();
            final ConsumerEntity existingConsumer = consumerManager.getByPartnerIdAndUsernameAndTypeAndParent(partnerId, username, 1L, 0L);

            final List<RelationConsumerProductEntity> newRelations;
            final List<RelationConsumerProduct> inputRelations = consumerInput.getProductRelations();
            if ((inputRelations == null) || (inputRelations.size() == 0)) newRelations = Collections.emptyList();
            else {
                newRelations = new ArrayList<>(inputRelations.size());
                for (final RelationConsumerProduct inputRelation : inputRelations) {
                    newRelations.add(new RelationConsumerProductEntity(
                            -1L,
                            -1L,
                            inputRelation.getProduct().getId(),
                            inputRelation.getRelationType().getId(),
                            inputRelation.getRelationStart() == null ? now : inputRelation.getRelationStart().getTime(),
                            inputRelation.getRelationEnd() == null ? -1L : inputRelation.getRelationEnd().getTime()
                    ));
                }
            }

            if (replace) consumerManager.update(partnerId, username, consumerInput.getDeleteHistory(), newRelations, 1L, 0L);
            else consumerManager.merge(partnerId, username, consumerInput.getDeleteHistory(), newRelations, 1L, 0L);

            final RestResponse resp;
            if ((existingConsumer == null) || (existingConsumer.status != 1)) resp = new RestResponse(ResponseStatus.OK.getCode(), "OK, added consumer " + username);
            else resp = new RestResponse(ResponseStatus.OK.getCode(), "OK, updated existing consumer " + username);

            // ----
            // -- Return the response.
            // ----
            return resp;
        }
        catch (Throwable e) {
            if (e instanceof DatabaseException) throw (DatabaseException)e;
            if (e instanceof ResponseException) throw (ResponseException)e;
            throw new ResponseException(ResponseStatus.UNKNOWN_ERROR, e, "Internal error: " + e.toString());
        }
    }
}
