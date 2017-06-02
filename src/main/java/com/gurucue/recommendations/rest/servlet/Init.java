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

import com.gurucue.recommendations.Timer;
import com.gurucue.recommendations.caching.CachedJdbcDataProvider;
import com.gurucue.recommendations.data.DataManager;
import com.gurucue.recommendations.data.jdbc.JdbcDataProvider;
import com.gurucue.recommendations.data.postgresql.PostgreSqlDataProvider;
import com.gurucue.recommendations.rest.GcPauseGauger;
import com.gurucue.recommendations.rest.PeriodicStatusLogger;
import com.gurucue.recommendations.rest.data.DatabaseWorkerThread;
import com.gurucue.recommendations.rest.data.processing.zap.ConsumerEventProcessor;
import com.gurucue.recommendations.rest.recommender.BlenderHandler;
import com.gurucue.recommendations.rest.recommender.RecommenderProviderImpl;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.ServletRegistration;
import javax.servlet.annotation.WebListener;
import java.util.Calendar;
import java.util.Map;

/**
 * Instantiated automatically by the container after the application
 * has been deployed. Used to auto-start things.
 */
@WebListener
public class Init implements ServletContextListener {
    private static final Logger logger = LogManager.getLogger(Init.class);

    @Override
    public void contextInitialized(ServletContextEvent servletContextEvent) {

        logger.info("rest-api initializing...");

        ServletContext ctx = servletContextEvent.getServletContext();
        Map<String, ? extends ServletRegistration> register = ctx.getServletRegistrations();
        for (String key : register.keySet()) {
            ServletRegistration value = register.get(key);
            logger.info("  " + key);
            for (String s : value.getMappings()) {
                logger.info("    " + s);
            }
        }

        // Measure GC pauses
        GcPauseGauger.INSTANCE.start();

        final Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(Timer.currentTimeMillis() + 60000L); // in a minute
        calendar.set(Calendar.MILLISECOND, 0); // round down to a minute
        calendar.set(Calendar.SECOND, 0);
        Timer.INSTANCE.schedule(calendar.getTimeInMillis(), new PeriodicStatusLogger());

        // Initialize the database layer
        JdbcDataProvider provider = CachedJdbcDataProvider.create(PostgreSqlDataProvider.create());
        DataManager.setProvider(provider);

        // Start the background database worker
        DatabaseWorkerThread.INSTANCE.start();
        // Initialize the AI engine: just referencing it will suffice
        RecommenderProviderImpl.INSTANCE.refreshRecommenders();
        BlenderHandler bh = BlenderHandler.INSTANCE;
        // Initialize the livetv-consumption conversion
        ConsumerEventProcessor.INSTANCE.start();
    }

    @Override
    public void contextDestroyed(ServletContextEvent servletContextEvent) {
        logger.info("Received shutdown signal, shutting down timer...");
        Timer.INSTANCE.stop();
        logger.info("Shutting down recommenders...");
        RecommenderProviderImpl.INSTANCE.shutdown();
        logger.info("Shutting down blenders...");
        BlenderHandler.INSTANCE.shutdown();
        logger.info("Shutting down livetv-consumption conversion...");
        ConsumerEventProcessor.INSTANCE.stop();
        logger.info("Shutting down database workers...");
        DatabaseWorkerThread.INSTANCE.stop();
        logger.info("Closing database...");
        DataManager.closeProvider();
        logger.info("Stopping pause gauger...");
        GcPauseGauger.INSTANCE.stop();
        logger.info("Shutdown complete.");
    }
}
