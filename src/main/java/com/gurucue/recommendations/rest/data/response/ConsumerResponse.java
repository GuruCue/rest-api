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
import java.text.SimpleDateFormat;
import java.util.ArrayList;

import com.gurucue.recommendations.translator.DataTranslator;
import com.gurucue.recommendations.ResponseStatus;
import com.gurucue.recommendations.translator.TranslatorAware;

public class ConsumerResponse extends RestResponse {
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private final String username;
    private final ArrayList<TranslatorAware> properties = new ArrayList<TranslatorAware>();
    private final ArrayList<TranslatorAware> productRelations = new ArrayList<TranslatorAware>();
    private final ArrayList<TranslatorAware> userProfiles = new ArrayList<>();

    public ConsumerResponse(final String username) {
        super(ResponseStatus.OK); // no other status is sensible; for errors just a RestResponse should be returned
        this.username = username;
    }

    public void addProperty(final String identifier, final String value) {
        properties.add(new Property(identifier, value));
    }

    public void addProductRelation(final String productType, final String partnerProductCode, final String relationType, final long relationStart, final long relationEnd) {
        productRelations.add(new ProductRelation(productType, partnerProductCode, relationType, relationStart, relationEnd));
    }

    public void addUserProfileId(final String userProfileId) {
        userProfiles.add(new UserProfile(userProfileId));
    }

    @Override
    protected void translateRest(final DataTranslator translator) throws IOException {
        translator.addKeyValue("username", username);
        translator.addKeyValue("properties", properties);
        translator.addKeyValue("product-relations", productRelations);
        if (!userProfiles.isEmpty()) translator.addKeyValue("user-profiles", userProfiles);
    }

    private static final class Property implements TranslatorAware {
        final String identifier;
        final String value;

        Property(final String identifier, final String value) {
            this.identifier = identifier;
            this.value = value;
        }

        @Override
        public void translate(DataTranslator translator) throws IOException {
            translator.beginObject("property");
            translator.addKeyValue("identifier", identifier);
            translator.addKeyValue("sha256hash", value);
            translator.endObject();
        }
    }

    private static final class ProductRelation implements TranslatorAware {
        final String productType;
        final String partnerProductCode;
        final String relationType;
        final long relationStart;
        final long relationEnd;

        ProductRelation(final String productType, final String partnerProductCode, final String relationType, final long relationStart, final long relationEnd) {
            this.productType = productType;
            this.partnerProductCode = partnerProductCode;
            this.relationType = relationType;
            this.relationStart = relationStart;
            this.relationEnd = relationEnd;
        }

        @Override
        public void translate(DataTranslator translator) throws IOException {
            translator.beginObject("relation");
            translator.addKeyValue("product-type", productType);
            translator.addKeyValue("product-id", partnerProductCode);
            translator.addKeyValue("relation-type", relationType);

            if (relationStart >= 0L) {
                translator.addKeyValue("relation-start", relationStart / 1000L);
            }
            if (relationEnd >= 0L) {
                translator.addKeyValue("relation-end", relationEnd / 1000L);
            }

            translator.endObject();
        }
    }

    private static final class UserProfile implements TranslatorAware {
        private final String userProfileId;

        UserProfile(final String userProfileId) {
            this.userProfileId = userProfileId;
        }

        @Override
        public void translate(final DataTranslator translator) throws IOException {
            translator.beginObject("user-profile");
            translator.addKeyValue("user-profile-id", userProfileId);
            translator.endObject();
        }
    }
}