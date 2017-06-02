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

import com.gurucue.recommendations.translator.DataTranslator;
import com.gurucue.recommendations.ResponseStatus;
import com.gurucue.recommendations.translator.TranslatorAware;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ProductPairResponse extends RestResponse {
    private final Product movie1;
    private final Product movie2;

    public ProductPairResponse(Product movie1, Product movie2) {
        super(ResponseStatus.OK);
        this.movie1 = movie1;
        this.movie2 = movie2;
    }

    @Override
    protected void translateRest(final DataTranslator translator) throws IOException {
        translator.addKeyValue("movie1", movie1);
        translator.addKeyValue("movie2", movie2);
    }

    public static final class Product implements TranslatorAware {
        Long id = null;
        Value title = null;
        Integer productionYear = null;
        Integer runTime = null;
        String coverPicture = null;
        final List<TranslatorAware> genres = new ArrayList<TranslatorAware>();
        final List<TranslatorAware> directors = new ArrayList<TranslatorAware>();
        final List<TranslatorAware> actors = new ArrayList<TranslatorAware>();
        final List<TranslatorAware> voices = new ArrayList<TranslatorAware>();

        public void setId(Long id) {
            this.id = id;
        }

        public void setTitle(Value title) {
            this.title = title;
        }

        public void setProductionYear(Value productionYear) {
            if (null == productionYear) this.productionYear = null;
            else {
                Object value = productionYear.getValue();
                if (!(value instanceof Integer)) {
                    throw new IllegalArgumentException("Production year is not an integer: " + value.getClass().getName());
                }
                this.productionYear = (Integer) value;
            }
        }

        public void setRunTime(Value runTime) {
            if (null == runTime) this.runTime = null;
            else {
                Object value = runTime.getValue();
                if (!(value instanceof Integer)) {
                    throw new IllegalArgumentException("Run-time is not an integer: " + value.getClass().getName());
                }
                this.runTime = (Integer) value;
            }
        }

        public void setCoverPicture(Value coverPicture) {
            if (null == coverPicture) this.coverPicture = null;
            else {
                Object value = coverPicture.getValue();
                if (!(value instanceof String)) {
                    throw new IllegalArgumentException("Cover picture is not a string: " + value.getClass().getName());
                }
                this.coverPicture = (String) value;
            }
        }

        public void addGenre(Object genre) {
            addToList(genres, genre, "Genre");
        }

        public void addDirector(Object director) {
            addToList(directors, director, "Director");
        }

        public void addActor(Object actor) {
            addToList(actors, actor, "Actor");
        }

        public void addVoice(Object voice) {
            addToList(voices, voice, "Voice");
        }

        public void setGenre(Object genre) {
            genres.clear();
            addGenre(genre);
        }

        public void setDirector(Object director) {
            directors.clear();
            addDirector(director);
        }

        public void setActor(Object actor) {
            actors.clear();
            addActor(actor);
        }

        public void setVoice(Object voice) {
            voices.clear();
            addVoice(voice);
        }

        private void addToList(List<TranslatorAware>list, Object value, String errorLabel) {
            if (value instanceof List) {
                list.addAll((List)value);
            }
            else if (value instanceof Value) {
                list.add((Value) value);
            }
            else {
                throw new IllegalArgumentException(errorLabel + " is not a Value or a list of Values: " + value.getClass().getName());
            }
        }

        @Override
        public void translate(DataTranslator translator) throws IOException {
            translator.beginObject("movie");
            translator.addKeyValue("id", id);
            translator.addKeyValue("title", title);
            translator.addKeyValue("productionYear", productionYear);
            translator.addKeyValue("runTime", runTime);
            translator.addKeyValue("coverPicture", coverPicture);
            translator.addKeyValue("genres", genres);
            translator.addKeyValue("directors", directors);
            translator.addKeyValue("actors", actors);
            translator.addKeyValue("voices", voices);
            translator.endObject();
        }
    }

    public static final class Value implements TranslatorAware {
        private final Object value;
        private final String language;

        public Value(Object value, String language) {
            if (!((value instanceof String) || (value instanceof Integer) || (value instanceof Double))) {
                throw new IllegalArgumentException("Unsupported value type: " + value.getClass().getName());
            }
            this.value = value;
            this.language = language;
        }

        @Override
        public void translate(DataTranslator translator) throws IOException {
            translator.beginObject("value");
            if (value instanceof String) translator.addKeyValue("value", (String)value);
            else if (value instanceof Integer) translator.addKeyValue("value", (Integer)value);
            else if (value instanceof Double) translator.addKeyValue("value", (Double)value);
            if (null != language) translator.addKeyValue("language", language);
            translator.endObject();
        }

        public Object getValue() {
            return value;
        }

        public String getLanguage() {
            return language;
        }
    }
}
