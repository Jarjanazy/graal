/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022, 2022, Red Hat Inc. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package com.oracle.svm.test.jfr;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.oracle.svm.core.jfr.JfrEvent;
import org.junit.Test;

import jdk.jfr.consumer.RecordedClass;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedThread;

public class TestJavaMonitorWaitNotifyAllEvent extends JfrTest {
    private static final int MILLIS = 50;

    private final Helper helper = new Helper();
    private Thread producerThread1;
    private Thread producerThread2;
    private Thread consumerThread;
    private boolean notifierFound;
    private int waitersFound;

    @Override
    public String[] getTestedEvents() {
        return new String[]{JfrEvent.JavaMonitorWait.getName()};
    }

    @Override
    public void validateEvents() throws Throwable {
        for (RecordedEvent event : getEvents()) {
            String eventThread = event.<RecordedThread> getValue("eventThread").getJavaName();
            String notifThread = event.<RecordedThread> getValue("notifier") != null ? event.<RecordedThread> getValue("notifier").getJavaName() : null;
            if (!eventThread.equals(producerThread1.getName()) &&
                            !eventThread.equals(producerThread2.getName()) &&
                            !eventThread.equals(consumerThread.getName())) {
                continue;
            }
            if (!event.<RecordedClass> getValue("monitorClass").getName().equals(Helper.class.getName())) {
                continue;
            }

            assertTrue("Event is wrong duration.", event.getDuration().toMillis() >= MILLIS);
            if (eventThread.equals(consumerThread.getName())) {
                assertTrue("Should have timed out.", event.<Boolean> getValue("timedOut").booleanValue());
                notifierFound = true;
            } else {
                assertFalse("Should not have timed out.", event.<Boolean> getValue("timedOut").booleanValue());
                assertTrue("Notifier thread name is incorrect", notifThread.equals(consumerThread.getName()));
                waitersFound++;
            }

        }
        assertTrue("Couldn't find expected wait events. NotifierFound: " + notifierFound + " waitersFound: " + waitersFound,
                        notifierFound && waitersFound == 2);
    }

    @Test
    public void test() throws Exception {
        Runnable producer = () -> {
            try {
                helper.doWork();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        };

        Runnable consumer = () -> {
            try {
                helper.doWork();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        };

        producerThread1 = new Thread(producer);
        producerThread2 = new Thread(producer);
        consumerThread = new Thread(consumer);

        producerThread1.start();

        consumerThread.join();
        producerThread1.join();
        producerThread2.join();
    }

    private class Helper {
        public synchronized void doWork() throws InterruptedException {
            if (Thread.currentThread().equals(consumerThread)) {
                wait(MILLIS);
                notifyAll(); // should wake up both producers
            } else if (Thread.currentThread().equals(producerThread1)) {
                producerThread2.start();
                wait();
            } else if (Thread.currentThread().equals(producerThread2)) {
                consumerThread.start();
                wait();
            }
        }
    }
}
