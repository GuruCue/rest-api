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

import com.google.common.collect.ImmutableMap;
import com.gurucue.recommendations.blender.BlendParameters;
import com.gurucue.recommendations.data.DataManager;
import com.gurucue.recommendations.data.ProductTypeCodes;
import com.gurucue.recommendations.entity.DataType;
import com.gurucue.recommendations.entity.ProductType;
import com.gurucue.recommendations.entity.product.GeneralVideoProduct;
import com.gurucue.recommendations.entity.product.Product;
import com.gurucue.recommendations.entity.value.AttributeValues;
import com.gurucue.recommendations.parser.IntegerParser;
import com.gurucue.recommendations.parser.ProductTypeParser;
import com.gurucue.recommendations.parser.Rule;
import com.gurucue.recommendations.parser.StringParser;
import com.gurucue.recommendations.parser.StructuredTokenParser;
import com.gurucue.recommendations.parser.StructuredTokenParserMaker;
import com.gurucue.recommendations.ResponseException;
import com.gurucue.recommendations.ResponseStatus;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Parses the recommendations request.
 */
public final class RecommendationInput implements Serializable, StructuredTokenParser {
    private static final long serialVersionUID = 5669598215174058916L;

    static final String TAG_TYPE = "type";
    static final String TAG_USER_ID = "user-id";
    static final String TAG_MAX_RECOMMENDATIONS = "maxRecommendations";
    static final String TAG_ATTRIBUTES = "attributes";
    static final String TAG_PRODUCTS = "products"; // the products among which to choose
    static final String TAG_DATA = "data";
    static final String TAG_REF_PRODUCTS = "refProducts"; // the products to refer to, e.g. when searching for similar products
    static final String TAG_PRODUCT = "product"; // the single refProduct
    static final StructuredTokenParserMaker maker = new Maker();

    static final Rule parseRule = Rule.map("request", false, maker, new Rule[]{
            Rule.value(TAG_TYPE, true, StringParser.parser),
            Rule.value(TAG_USER_ID, false, StringParser.parser),
            Rule.value(TAG_MAX_RECOMMENDATIONS, true, IntegerParser.parser),
            Rule.list(TAG_ATTRIBUTES, true, AttributeInput.class, AttributeInput.parseRule),
            Rule.list(TAG_PRODUCTS, true, Product.class, ProductInput.parseRule),
            Rule.list(TAG_DATA, true, ConsumerEventDataInput.class, ConsumerEventDataInput.parseRule),
            Rule.list(TAG_REF_PRODUCTS, true, Product.class, ProductInput.parseRule),
            Rule.map(TAG_PRODUCT, true, ProductInput.maker, new Rule[] {
                    Rule.value(ProductInput.TAG_TYPE, false, ProductTypeParser.parser),
                    Rule.value(ProductInput.TAG_ID, false, StringParser.parser),
            })
    });

    public ImmutableMap<ResponseStatus, String> warningMessages;

    private String recommender;
    private String userId;
    private Integer maxRecommendations;
    private List<AttributeInput> attributes;
    private List<Product> products;
    private List<ConsumerEventDataInput> data;
    private List<Product> refProducts;
    private Product product;

    public RecommendationInput() {
        this.recommender = null;
        this.userId = null;
        this.maxRecommendations = null;
        this.attributes = Collections.emptyList();
        this.products = Collections.emptyList();
        this.data = Collections.emptyList();
        this.refProducts = null;
        this.product = null;
    }

    public String getRecommender() {
        return recommender;
    }

    public void setRecommender(final String recommender) {
        this.recommender = recommender;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(final String username) {
        this.userId = username;
    }

    public Integer getMaxRecommendations() {
        return maxRecommendations;
    }

    public void setMaxRecommendations(final Integer maxRecommendations) {
        this.maxRecommendations = maxRecommendations;
    }

    public List<AttributeInput> getAttributes() {
        return attributes;
    }

    public void setAttributes(final List<AttributeInput> attributes) {
        this.attributes = attributes;
    }

    public List<Product> getProducts() {
        return products;
    }

    public void setProducts(final List<Product> products) {
        this.products = products;
    }

    public List<ConsumerEventDataInput> getData() {
        return data;
    }

    public void setData(final List<ConsumerEventDataInput> data) {
        this.data = data;
    }

    public List<Product> getRefProducts() {
        return refProducts;
    }

    public void setRefProducts(final List<Product> refProducts) {
        this.refProducts = refProducts;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(final Product product) {
        this.product = product;
    }

    public BlendParameters asBlendParameters() throws ResponseException {
        final ImmutableMap.Builder<DataType, String> dataBuilder = ImmutableMap.builder();
        data.forEach((final ConsumerEventDataInput input) -> dataBuilder.put(input.getType(), input.getValue()));
        final BlendParameters result = new BlendParameters(recommender, userId, dataBuilder.build());
        if (maxRecommendations != null) {
            result.addInput("maxItems", maxRecommendations);
        }
        if ((attributes != null) && (attributes.size() > 0)) {
            result.addInput("attributes", new AttributeValues(AttributeInput.asAttributeValues(attributes)));
        }
        if ((refProducts != null) && (refProducts.size() > 0)) {
            result.addInput("referencedProducts", transformVideoProducts(refProducts));
        }
        if ((products != null) && (products.size() > 0)) {
            result.addInput("products", transformVideoProducts(products));
        }
        return result;
    }

    // utility methods

    // StructuredTokenParser interface

    @Override
    public void begin(final String memberName, final Map<String, Object> params) {}

    @SuppressWarnings("unchecked")
    @Override
    public void consume(final String memberName, final Object member) throws ResponseException {
        try {
            switch (memberName) {
                case TAG_TYPE:
                    setRecommender((String) member);
                    break;
                case TAG_USER_ID:
                    setUserId((String) member);
                    break;
                case TAG_MAX_RECOMMENDATIONS:
                    setMaxRecommendations((Integer) member);
                    break;
                case TAG_ATTRIBUTES:
                    setAttributes((List<AttributeInput>) member);
                    break;
                case TAG_PRODUCTS:
                    setProducts((List<Product>) member);
                    break;
                case TAG_DATA:
                    setData((List<ConsumerEventDataInput>) member);
                    break;
                case TAG_REF_PRODUCTS:
                    setRefProducts((List<Product>) member);
                    break;
                case TAG_PRODUCT:
                    setProduct((Product) member);
                    break;
                default:
                    throw new ResponseException(ResponseStatus.INTERNAL_PROCESSING_ERROR, "Attempted to set a value to an unknown member of a RecommendationInput instance: " + memberName);
            }
        }
        catch (ClassCastException e) {
            throw new ResponseException(ResponseStatus.INTERNAL_PROCESSING_ERROR, e, "Attempted to set a value of invalid type to the member " + memberName + " of a RecommendationInput instance: " + member.getClass().getCanonicalName());
        }
    }

    @Override
    public RecommendationInput finish() throws ResponseException {
        final ImmutableMap.Builder<ResponseStatus, String> warningsBuilder = ImmutableMap.builder();
        if ((refProducts != null) && (product != null)) {
            warningsBuilder.put(ResponseStatus.DONT_USE_REFPRODUCTS_AND_PRODUCT_SIMULTANEOUSLY, ResponseStatus.DONT_USE_REFPRODUCTS_AND_PRODUCT_SIMULTANEOUSLY.getDescription());
            // build a new array, the array of refProducts, but only if the product is not already among refProducts
            final long productId = product.id;
            boolean found = false;
            for (final Product p : refProducts) {
                if (p.id == productId) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                // add to the end of the list
                refProducts.add(product);
            }
        }
        else if (product != null) {
            // we know already that refProducts does not exist, so we create a singleton list with this product
            refProducts = new ArrayList<>();
            refProducts.add(product);
        }
        // we check that all products in the refProducts list are GeneralVideoProducts; we remove those that aren't
        verifyProducts(refProducts, "The following reference products are not video or tv-programme products and were ignored: ", ResponseStatus.REFPRODUCT_NOT_A_VIDEO_PRODUCT, warningsBuilder);
        // we check that all products in the refProducts list are GeneralVideoProducts; we remove those that aren't
        verifyProducts(products, "The following products are not video or tv-programme products and were ignored: ", ResponseStatus.PRODUCT_NOT_A_VIDEO_PRODUCT, warningsBuilder);
        warningMessages = warningsBuilder.build();
        return this;
    }

    private static void verifyProducts(final List<Product> products, final String messagePrefix, final ResponseStatus status, final ImmutableMap.Builder<ResponseStatus, String> warningsBuilder) {
        if ((products == null) || (products.size() == 0)) return;
        final ProductTypeCodes productTypeCodes = DataManager.getProductTypeCodes();
        final StringBuilder idsBuilder = new StringBuilder(512);
        idsBuilder.append(messagePrefix);
        final int startingLength = idsBuilder.length();
        for (int i = products.size() - 1; i >= 0; i--) {
            final Product p = products.get(i);
            if (!(p instanceof GeneralVideoProduct)) {
                products.remove(i);
                if (idsBuilder.length() > startingLength) idsBuilder.append(", ");
                final ProductType pt = productTypeCodes.byId(p.productTypeId);
                if (pt == null) idsBuilder.append("UNKNOWN PRODUCT TYPE");
                else idsBuilder.append(pt.getIdentifier());
                idsBuilder.append(" ").append(p.partnerProductCode);
            }
        }
        if (idsBuilder.length() > startingLength) {
            warningsBuilder.put(status,  idsBuilder.toString());
        }
    }

    private static GeneralVideoProduct[] transformVideoProducts(final List<Product> products) {
        final int n = products.size();
        final GeneralVideoProduct[] result = new GeneralVideoProduct[n];
        int targetIndex = 0;
        for (final Product p : products) {
            try {
                result[targetIndex] = (GeneralVideoProduct)p;
                targetIndex++;
            }
            catch (ClassCastException e) {
                // TODO: report the error
            }
        }
        if (targetIndex < n) return Arrays.copyOf(result, targetIndex);
        return result;
    }

    // driver code

    public static RecommendationInput parse(final String format, final String input) throws ResponseException {
        final Object result = Rule.parse(format, input, parseRule, null);
        if (result instanceof RecommendationInput) return (RecommendationInput)result;
        throw new ResponseException(ResponseStatus.INTERNAL_PROCESSING_ERROR, "Internal error: parse did not result in a RecommendationInput instance, but instead " + result.getClass().getCanonicalName());
    }

    private static class Maker implements StructuredTokenParserMaker {
        @Override
        public StructuredTokenParser create(final Map<String, Object> params) {
            return new RecommendationInput();
        }
    }
}
