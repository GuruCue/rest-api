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
import com.gurucue.recommendations.data.DataManager;
import com.gurucue.recommendations.dto.ConsumerEntity;
import com.gurucue.recommendations.dto.RelationConsumerProductEntity;
import com.gurucue.recommendations.entitymanager.ConsumerManager;
import com.gurucue.recommendations.rest.data.RequestCache;
import com.gurucue.recommendations.rest.data.container.ConsumerProfileInput;
import com.gurucue.recommendations.rest.data.response.RestResponse;

import javax.servlet.annotation.WebServlet;
import java.util.Collections;

/**
 * Manages the consumer profiles.
 */
@WebServlet(name = "ConsumerProfile", urlPatterns = { "/rest/userprofile", "/rest/userprofile/*" }, description = "REST interface to consumer profiles.")
public class ConsumerProfileServlet extends RestServlet {
    private static final long serialVersionUID = 2552603763623799703L;

    public ConsumerProfileServlet() {
        super("ConsumerProfile");
    }

    @Override
    protected RestResponse restPut(final RequestCache cache, final String[] pathFragments, final MimeType requestFormat, final String request) throws ResponseException {
        final ConsumerManager cm = DataManager.getCurrentLink().getConsumerManager();
        final long partnerId = cache.getPartner().getId();
        try {
            final ConsumerProfileInput input = ConsumerProfileInput.parse(requestFormat.CONTENT_FORMAT.NAME, request, cache);
            final ConsumerEntity consumer = cm.getByPartnerIdAndUsernameAndTypeAndParent(partnerId, input.getUserId(), 1L, 0L);
            if ((consumer == null) || (consumer.status != 1)) {
                throw new ResponseException(ResponseStatus.UNKNOWN_ERROR, "There is no consumer " + input.getUserId());
            }
            final String userProfileId = input.getUserProfileId();
            if ((userProfileId == null) || (userProfileId.length() == 0)) {
                throw new ResponseException(ResponseStatus.UNKNOWN_ERROR, "No user profile ID provided, or it is empty");
            }
            cm.update(partnerId, userProfileId, false, Collections.<RelationConsumerProductEntity>emptyList(), 2L, consumer.id);
            return RestResponse.OK;
        }
        catch (Throwable e) {
            if (e instanceof DatabaseException) throw (DatabaseException)e;
            if (e instanceof ResponseException) throw (ResponseException)e;
            throw new ResponseException(ResponseStatus.UNKNOWN_ERROR, e, "Internal error: " + e.toString());
        }
    }

    @Override
    protected RestResponse restDelete(final RequestCache cache, final String[] pathFragments) throws ResponseException {
        if (pathFragments.length != 2) throw new ResponseException(ResponseStatus.MALFORMED_REQUEST);
        final ConsumerManager cm = DataManager.getCurrentLink().getConsumerManager();
        final long partnerId = cache.getPartner().getId();
        final ConsumerEntity consumer = cm.getByPartnerIdAndUsernameAndTypeAndParent(partnerId, pathFragments[0], 1L, 0L);
        if ((consumer == null) || (consumer.status != 1)) return RestResponse.OK;
        cm.delete(partnerId, pathFragments[1], 2L, consumer.id, false);
        return RestResponse.OK;
    }
}
