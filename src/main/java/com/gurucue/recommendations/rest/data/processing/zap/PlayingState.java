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
package com.gurucue.recommendations.rest.data.processing.zap;

/**
 * Enumerates possible values for the "action" consumer event data.
 */
enum PlayingState {
    PLAY,
    STOP,
    PAUSE,
    INTERNAL_STOP;

    /**
     * Returns from permitted subset of values based on the given description,
     * as possible in events. Those values are: PLAY, STOP, PAUSE.
     *
     * @param stateName
     * @return
     */
    public static PlayingState fromString(final String stateName) {
        switch (stateName) {
            case "play":
            case "PLAY":
            case "Play":
                return PLAY;
            case "stop":
            case "STOP":
            case "Stop":
                return STOP;
            case "pause":
            case "PAUSE":
            case "Pause":
                return PAUSE;
            default:
                return null;
        }
    }
}
