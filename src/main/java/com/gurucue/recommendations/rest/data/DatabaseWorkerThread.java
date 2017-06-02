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
package com.gurucue.recommendations.rest.data;

import com.gurucue.recommendations.Transaction;
import com.gurucue.recommendations.data.DataLink;
import com.gurucue.recommendations.data.DataManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A thread designed to run database jobs in the background.
 */
public final class DatabaseWorkerThread {
    private static final Logger log = LogManager.getLogger(DatabaseWorkerThread.class);
    public static final DatabaseWorkerThread INSTANCE = new DatabaseWorkerThread();
    private final Worker worker = new Worker(this);
    private Thread thread;

    private DatabaseWorkerThread() {} // not instantiable from outside

    public void start() {
        if (worker.running.getAndSet(true)) throw new IllegalStateException("The thread is already running");
        thread = new Thread(worker, "Database worker");
        thread.setDaemon(false);
        thread.start();
    }

    public void stop() {
        if (!worker.running.getAndSet(false)) return;
        // don't interrupt the thread! all the jobs *must* be written to the database
        worker.signalAvailableJob();
        try {
            thread.join();
        } catch (InterruptedException e) {
            log.error("Interrupted while waiting for the thread to stop: " + e.toString(), e);
        }
        thread = null;
    }

    public void addJob(final DatabaseWorkerJob job) {
        if (!worker.running.get()) throw new IllegalStateException("The database worker is not active");
        worker.queue.add(job);
        worker.signalAvailableJob();
    }

    private static final class Worker implements Runnable {
        private final DatabaseWorkerThread owner;
        private final AtomicBoolean running = new AtomicBoolean(false);
        private final Queue<DatabaseWorkerJob> queue = new ConcurrentLinkedQueue<DatabaseWorkerJob>();
        private final Lock lock = new ReentrantLock();
        private final Condition jobAvailable = lock.newCondition();

        Worker(final DatabaseWorkerThread owner) {
            this.owner = owner;
        }

        @Override
        public void run() {
            log.info("Database worker thread started");
            int remainCount = 0;
            try {
                DatabaseWorkerJob job;
                DataLink dataLink = null;
                outside:
                for (; ; ) {
                    // the logic here may seem a little bit twisted, but it is designed for maximum throughput, i.e. minimum locking
                    job = queue.poll();
                    if (null == job) {
                        lock.lock();
                        try {
                            while (null == (job = queue.poll())) {
                                if (!running.get()) break outside;
                                try {
                                    jobAvailable.await();
                                } catch (InterruptedException e) {
                                    log.warn("Database worker thread: interrupted while awaiting a job", e);
                                }
                            }
                        } finally {
                            lock.unlock();
                        }
                    }

                    // here we have a job
                    for (int i = 0; i < 5; i++) { // a retry loop
                        // get a data link
                        while (null == dataLink) {
                            try {
                                dataLink = DataManager.getNewLink();
                            } catch (Exception e) {
                                if (!running.get()) {
                                    log.error("Failed to obtain a data link, and signalled to stop: bailing out with " + queue.size() + " jobs remaining to be run, AAAAAA!", e);
                                    break outside;
                                }
                                log.error("Failed to obtain a data link, sleeping for a second", e);
                                try {
                                    Thread.sleep(1000);
                                } catch (InterruptedException e1) {
                                    log.warn("Sleep was interrupted", e);
                                }
                            }
                        }

                        // here we have a job and a connection
                        Transaction transaction = null;
                        try {
                            transaction = Transaction.newTransaction(dataLink);
                            job.execute(transaction);
                            transaction.commit();
                            if (i > 0) log.warn("Job succeeded after " + i + " retries");
                            continue outside; // everything okay, get out of the retry loop, and begin the next iteration immediately
                        } catch (Exception e) {
                            log.error("Error while executing a job (try #" + i + "): " + e.toString(), e);
                            try {
                                if (transaction != null) {
                                    transaction.rollback();
                                    transaction = null;
                                }
                            } catch (Exception e1) {
                                log.error("Failed to rollback data link, closing it: " + e1.toString(), e1);
                            }
                            try {
                                dataLink.close();
                            } catch (Exception e2) {
                                log.error("Failed to close data link, forgetting it: " + e2.toString(), e2);
                            }
                            dataLink = null;
                        }

                        // if we got to here, then we retry, unless the retry count exceeded the maximum count
                        try {
                            Thread.sleep(100L);
                        } catch (InterruptedException e) {
                            log.warn("Interrupted during delay before retry: " + e.toString(), e);
                        }
                    }

                    // here we retried for the maximum amount, give up on the job
                    job.onFail();
                    log.error("Failed to execute a job (with retries)");
                }
                if (null == job) job = queue.poll();
                while (null != job) {
                    remainCount++;
                    job.onFail();
                    job = queue.poll();
                }
            }
            finally {
                log.info("Database worker thread exited, " + remainCount + " jobs remained (and invoked their fail handlers)");
            }
        }

        void signalAvailableJob() {
            lock.lock();
            try {
                jobAvailable.signal();
            }
            finally {
                lock.unlock();
            }
        }
    }
}
