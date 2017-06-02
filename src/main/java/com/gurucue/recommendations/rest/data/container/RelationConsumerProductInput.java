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
package com.gurucue.recommendations.rest.data.container;

import com.gurucue.recommendations.Transaction;
import com.gurucue.recommendations.data.DataLink;
import com.gurucue.recommendations.data.DataManager;
import com.gurucue.recommendations.entity.product.Product;
import com.gurucue.recommendations.entity.ProductType;
import com.gurucue.recommendations.entity.RelationConsumerProduct;
import com.gurucue.recommendations.entity.RelationType;
import com.gurucue.recommendations.parser.ProductTypeParser;
import com.gurucue.recommendations.parser.RelationTypeParser;
import com.gurucue.recommendations.parser.Rule;
import com.gurucue.recommendations.parser.StringParser;
import com.gurucue.recommendations.parser.StructuredTokenParser;
import com.gurucue.recommendations.parser.StructuredTokenParserMaker;
import com.gurucue.recommendations.parser.TimestampParser;
import com.gurucue.recommendations.rest.data.RequestCache;
import com.gurucue.recommendations.ResponseException;
import com.gurucue.recommendations.ResponseStatus;

import java.io.Serializable;
import java.sql.Timestamp;
import java.util.Map;

public final class RelationConsumerProductInput implements Serializable, StructuredTokenParser {
    private static final long serialVersionUID = 4661770734662601883L;

    static final String TAG_PRODUCT_TYPE = "product-type";
    static final String TAG_PRODUCT_ID = "product-id";
    static final String TAG_RELATION_TYPE = "relation-type";
    static final String TAG_RELATION_START = "relation-start";
    static final String TAG_RELATION_END = "relation-end";
    static final StructuredTokenParserMaker maker = new Maker();
    static final Rule parseRule = Rule.map("relation", true, maker, new Rule[]{
            Rule.value(TAG_PRODUCT_TYPE, false, ProductTypeParser.parser),
            Rule.value(TAG_PRODUCT_ID, false, StringParser.parser),
            Rule.value(TAG_RELATION_TYPE, false, RelationTypeParser.parser),
            Rule.value(TAG_RELATION_START, true, TimestampParser.parser),
            Rule.value(TAG_RELATION_END, true, TimestampParser.parser),
    });

    private ProductType productType;
    private String productId;
    private RelationType relationType;
    private Timestamp relationStart;
    private Timestamp relationEnd;

    public RelationConsumerProductInput() {
        this.productType = null;
        this.productId = null;
        this.relationType = null;
        this.relationStart = null;
        this.relationEnd = null;
    }

    public ProductType getProductType() {
        return productType;
    }

    public void setProductType(ProductType productType) {
        this.productType = productType;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public RelationType getRelationType() {
        return relationType;
    }

    public void setRelationType(RelationType relationType) {
        this.relationType = relationType;
    }

    public Timestamp getRelationStart() {
        return relationStart;
    }

    public void setRelationStart(Timestamp relationStart) {
        this.relationStart = relationStart;
    }

    public Timestamp getRelationEnd() {
        return relationEnd;
    }

    public void setRelationEnd(Timestamp relationEnd) {
        this.relationEnd = relationEnd;
    }

    // StructuredTokenParser interface

    @Override
    public void begin(final String memberName, final Map<String, Object> params) {}

    @SuppressWarnings("unchecked")
    @Override
    public void consume(final String memberName, final Object member)
            throws ResponseException {
        try {
            switch (memberName) {
                case TAG_PRODUCT_TYPE:
                    setProductType((ProductType) member);
                    break;
                case TAG_PRODUCT_ID:
                    setProductId((String) member);
                    break;
                case TAG_RELATION_TYPE:
                    setRelationType((RelationType) member);
                    break;
                case TAG_RELATION_START:
                    setRelationStart((Timestamp) member);
                    break;
                case TAG_RELATION_END:
                    setRelationEnd((Timestamp) member);
                    break;
                default:
                    throw new ResponseException(ResponseStatus.INTERNAL_PROCESSING_ERROR, "Attempted to set a value to an unknown member of an RelationConsumerProductInput instance: " + memberName);
            }
        }
        catch (ClassCastException e) {
            throw new ResponseException(ResponseStatus.INTERNAL_PROCESSING_ERROR, e, "Attempted to set a value of invalid type to the member " + memberName + " of an RelationConsumerProductInput instance: " + member.getClass().getCanonicalName());
        }
    }

    @Override
    public Object finish() throws ResponseException {
        DataLink link = DataManager.getCurrentLink();
        Product p = link.getProductManager().getProductByPartnerAndTypeAndCode(Transaction.get(), RequestCache.get().getPartner(), getProductType(), getProductId(), false);
        if (p == null) {
            throw new ResponseException(ResponseStatus.UNKNOWN_ERROR, "No product with type " + getProductType().getIdentifier() + " and id " + getProductId());
        }
        RelationConsumerProduct rcp = new RelationConsumerProduct(
                null,
                null,
                p,
                getRelationType(),
                getRelationStart(),
                getRelationEnd()
        );
        return rcp;
    }

    private static class Maker implements StructuredTokenParserMaker {
        @Override
        public StructuredTokenParser create(final Map<String, Object> params) {
            return new RelationConsumerProductInput();
        }
    }
}
