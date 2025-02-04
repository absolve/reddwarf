/*
 * Copyright 2007-2010 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation and
 * distributed hereunder to you.
 *
 * Project Darkstar Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * --
 */

package com.sun.sgs.test.impl.kernel;

import com.sun.sgs.impl.kernel.AccessCoordinatorHandle;
import com.sun.sgs.impl.profile.ProfileCollectorHandle;
import com.sun.sgs.service.TransactionProxy;
import com.sun.sgs.tools.test.FilteredNameRunner;
import org.junit.runner.RunWith;

import java.lang.reflect.Constructor;
import java.util.Properties;

/**
 * Tests the {@link TrackingAccessCoordinator} class.
 */
@RunWith(FilteredNameRunner.class)
public class TestTrackingAccessCoordinator
        extends BasicAccessCoordinatorTest<AccessCoordinatorHandle> {
    /**
     * Creates a {@code TrackingAccessCoordinator}.
     */
    protected AccessCoordinatorHandle createAccessCoordinator() {
        try {
            Constructor<? extends AccessCoordinatorHandle> constructor =
                    Class.forName(
                            "com.sun.sgs.impl.kernel.TrackingAccessCoordinator")
                            .asSubclass(AccessCoordinatorHandle.class)
                            .getDeclaredConstructor(Properties.class,
                                    TransactionProxy.class,
                                    ProfileCollectorHandle.class);
            constructor.setAccessible(true);
            return constructor.newInstance(
                    properties, txnProxy, profileCollector);
        } catch (Exception e) {
            throw new RuntimeException("Unexpected exception: " + e, e);
        }
    }
}
