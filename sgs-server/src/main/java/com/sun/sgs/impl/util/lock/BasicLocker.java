/*
 * Copyright 2010 The RedDwarf Authors.  All rights reserved
 * Portions of this file have been modified as part of RedDwarf
 * The source code is governed by a GPLv2 license that can be found
 * in the LICENSE file.
 */
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

package com.sun.sgs.impl.util.lock;

/**
 * Records information about an entity that requests locks from a {@link
 * LockManager} and that permits only a single active lock request.
 *
 * @param    <K> the type of key
 */
public class BasicLocker<K> extends Locker<K> {

    /**
     * The result of the lock request that this locker is waiting for, or
     * {@code null} if it is not waiting.  Synchronize on this locker when
     * accessing this field.
     */
    private LockAttemptResult<K> waitingFor;

    /* -- Constructor -- */

    /**
     * Creates an instance of this class.
     *
     * @param    lockManager the lock manager for this locker
     */
    public BasicLocker(LockManager<K> lockManager) {
        super(lockManager);
    }

    /* -- Package access methods -- */

    /**
     * {@inheritDoc} <p>
     * <p>
     * This implementation returns the lock attempt request associated with
     * this locker, if any.
     */
    @Override
    LockAttemptResult<K> getWaitingFor() {
        assert lockManager.checkAllowLockerSync(this);
        synchronized (this) {
            return waitingFor;
        }
    }

    /**
     * {@inheritDoc} <p>
     * <p>
     * This implementation sets the lock attempt request associated with this
     * locker.
     *
     * @throws IllegalArgumentException {@inheritDoc}
     */
    @Override
    void setWaitingFor(LockAttemptResult<K> waitingFor) {
        assert lockManager.checkAllowLockerSync(this);
        if (waitingFor != null && waitingFor.conflict == null) {
            throw new IllegalArgumentException(
                    "Attempt to specify a lock attempt result that is not a" +
                            " conflict: " + waitingFor);
        }
        synchronized (this) {
            this.waitingFor = waitingFor;
        }
    }
}
