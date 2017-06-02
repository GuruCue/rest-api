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
import com.gurucue.recommendations.entity.ProductType;
import com.gurucue.recommendations.entity.product.Product;
import com.gurucue.recommendations.parser.ProductTypeParser;
import com.gurucue.recommendations.parser.Rule;
import com.gurucue.recommendations.parser.StringParser;
import com.gurucue.recommendations.parser.StructuredTokenParser;
import com.gurucue.recommendations.parser.StructuredTokenParserMaker;
import com.gurucue.recommendations.rest.data.RequestCache;
import com.gurucue.recommendations.ResponseException;
import com.gurucue.recommendations.ResponseStatus;

import java.io.Serializable;
import java.util.Map;

/**
 * Parses the product input data fragment, specified as a map of two values:
 * a product type and a partner product code. The result is a Product
 * instance.
 */
public final class ProductInput implements Serializable, StructuredTokenParser {
    private static final long serialVersionUID = 3661770734662601883L;

    static final String TAG_TYPE = "type";
    static final String TAG_ID = "id";
    static final StructuredTokenParserMaker maker = new Maker();
    static final Rule parseRule = Rule.map("product", false, maker, new Rule[]{
            Rule.value(TAG_TYPE, false, ProductTypeParser.parser),
            Rule.value(TAG_ID, false, StringParser.parser),
    });

    private ProductType type;
    private String id;

    public ProductInput() {
        this.type = null;
        this.id = null;
    }

    public ProductInput(final ProductType type, final String id) {
        this.type = type;
        this.id = id;
    }

    public ProductType getType() {
        return type;
    }

    public void setType(ProductType type) {
        this.type = type;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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
                case TAG_TYPE:
                    setType((ProductType) member);
                    break;
                case TAG_ID:
                    setId((String) member);
                    break;
                default:
                    throw new ResponseException(ResponseStatus.INTERNAL_PROCESSING_ERROR, "Attempted to set a value to an unknown member of an ProductInput instance: " + memberName);
            }
        }
        catch (ClassCastException e) {
            throw new ResponseException(ResponseStatus.INTERNAL_PROCESSING_ERROR, e, "Attempted to set a value of invalid type to the member " + memberName + " of an ProductInput instance: " + member.getClass().getCanonicalName());
        }
    }

    @Override
    public Product finish() throws ResponseException {
        DataLink link = DataManager.getCurrentLink();
        Product p = link.getProductManager().getProductByPartnerAndTypeAndCode(Transaction.get(), RequestCache.get().getPartner(), getType(), getId(), false);
        if (p == null) {
            throw new ResponseException(ResponseStatus.NO_SUCH_PRODUCT_ID, "The product of type " + getType().getIdentifier() + " with id \"" + getId() + "\" does not exist");
        }
        return p;
    }

    private static class Maker implements StructuredTokenParserMaker {
        @Override
        public StructuredTokenParser create(final Map<String, Object> params) {
            return new ProductInput();
        }
    }
}
