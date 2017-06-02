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
package com.gurucue.recommendations.rest.recommender;

import com.google.common.collect.ImmutableMap;

/**
 * Defines the groups of blenders.
 */
public enum BlenderGroup {
    Recommendations("recommenders"),
    Search("searchers");

    private static final ImmutableMap<String, BlenderGroup> pathMapping;
    static {
        final ImmutableMap.Builder<String, BlenderGroup> pathMappingBuilder = ImmutableMap.builder();
        for (final BlenderGroup bg : BlenderGroup.values()) {
            pathMappingBuilder.put(bg.getPath(), bg);
        }
        pathMapping = pathMappingBuilder.build();
    }

    private final String path;

    BlenderGroup(final String path) {
        this.path = path;
    }

    public String getPath() {
        return path;
    }

    public static BlenderGroup byPath(final String path) {
        return pathMapping.get(path);
    }
}
