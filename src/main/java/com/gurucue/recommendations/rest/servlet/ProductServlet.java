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

import com.gurucue.recommendations.Transaction;
import com.gurucue.recommendations.data.DataLink;
import com.gurucue.recommendations.data.DataManager;
import com.gurucue.recommendations.entity.product.Product;
import com.gurucue.recommendations.entity.ProductType;
import com.gurucue.recommendations.rest.data.RequestCache;
import com.gurucue.recommendations.ResponseException;
import com.gurucue.recommendations.ResponseStatus;
import com.gurucue.recommendations.rest.data.container.ProductAddInput;
import com.gurucue.recommendations.rest.data.container.ProductModificationInput;
import com.gurucue.recommendations.rest.data.processing.product.Processor;
import com.gurucue.recommendations.rest.data.response.ProductResponse;
import com.gurucue.recommendations.rest.data.response.RestResponse;

import javax.servlet.annotation.WebServlet;

/**
 * Processes the product management REST requests.
 */
@WebServlet(name = "Product", urlPatterns = { "/rest/product", "/rest/product/*" }, description = "REST interface for managing products.")
public class ProductServlet extends RestServlet {

    public ProductServlet() {
        super("Product");
    }

    @Override
    protected RestResponse restGet(final RequestCache cache, final String[] pathFragments) throws ResponseException {
        if (pathFragments.length != 2) throw new ResponseException(ResponseStatus.MALFORMED_REQUEST);
        final DataLink link = DataManager.getCurrentLink();
        final ProductType productType = link.getProductTypeManager().getByIdentifier(pathFragments[0]);
        if (productType == null) throw new ResponseException(ResponseStatus.INVALID_PRODUCT_TYPE, "There is no product type " + pathFragments[0]);
        final Product product = link.getProductManager().getProductByPartnerAndTypeAndCode(Transaction.get(), cache.getPartner(), productType, pathFragments[1], false);
        if ((product == null) || (product.deleted != null)) throw new ResponseException(ResponseStatus.INVALID_PRODUCT_ID, "There is no product of type " + productType.getIdentifier() + " with ID " + pathFragments[1]);
        return new ProductResponse(product, productType.getIdentifier(), true); // TODO: add to Partner the field useShortLanguageCodes
    }

    @Override
    protected RestResponse restPost(final RequestCache cache, final String[] pathFragments, final MimeType requestFormat, final String request) throws ResponseException {
        final ProductModificationInput input = ProductModificationInput.parse(requestFormat.CONTENT_FORMAT.NAME, request);
        Processor.modify(cache, Transaction.get(), cache.getPartner(), input.getProductType(), input.getProductCode(), input.getAttributeValuesClear(), input.getAttributeValuesSet());
        return RestResponse.OK;
    }

    @Override
    protected RestResponse restPut(final RequestCache cache, final String[] pathFragments, final MimeType requestFormat, final String request) throws ResponseException {
        final ProductAddInput input = ProductAddInput.parse(requestFormat.CONTENT_FORMAT.NAME, request);
        Processor.add(cache, Transaction.get(), cache.getPartner(), input.getProductType(), input.getProductCode(), input.getAttributeValues());
        return RestResponse.OK;
    }

    @Override
    protected RestResponse restDelete(final RequestCache cache, final String[] pathFragments) throws ResponseException {
        if (pathFragments.length != 2) throw new ResponseException(ResponseStatus.MALFORMED_REQUEST);
        final DataLink link = DataManager.getCurrentLink();
        final ProductType productType = link.getProductTypeManager().getByIdentifier(pathFragments[0]);
        if (productType == null)
            throw new ResponseException(ResponseStatus.INVALID_PRODUCT_TYPE, "There is no product type " + pathFragments[0]);
        link.getProductManager().deleteByPartnerAndTypeAndCode(Transaction.get(), cache.getPartner(), productType, pathFragments[1]);
        return RestResponse.OK;
    }
}
