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

import com.gurucue.recommendations.entity.Attribute;

import java.util.List;

public interface AttributeInputSelectable {
    /**
     * Returns a list of input values with the specified attribute.
     * This is a utility getter to ease further processing.
     * @param attribute the attribute for which to return a list of AttributeInput
     * @return a list of AttributeInput instances having the specified attribute
     */
    List<AttributeInput> getValuesOfAttribute(Attribute attribute);

    /**
     * Returns a list of all input values.
     * @return a list of <code>AttributeInput</code> instances
     */
    List<AttributeInput> getAllValues();

    /**
     * Returns a list of all values of public attributes.
     * @return
     */
    List<AttributeInput> getPublicValues();

    /**
     * Returns a list of all values of private attributes.
     * @return
     */
    List<AttributeInput> getPrivateValues();
}
