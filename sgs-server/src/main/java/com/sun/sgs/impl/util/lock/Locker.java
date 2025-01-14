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

import static com.sun.sgs.impl.sharedutil.Objects.checkNull;
import static com.sun.sgs.impl.util.Numbers.addCheckOverflow;

/**
 * Records information about an entity that requests locks from a {@link
 * LockManager}.
 *
 * @param    <K> the type of key
 */
public abstract class Locker<K> {

    /**
     * The lock manager for this locker.
     */
    final LockManager<K> lockManager;

    /* -- Constructor -- */

    /**
     * Creates an instance of this class.
     *
     * @param    lockManager the lock manager for this locker
     */
    protected Locker(LockManager<K> lockManager) {
        checkNull("lockManager", lockManager);
        this.lockManager = lockManager;
    }

    /* -- Public methods -- */

    /**
     * Returns the lock manager for this locker.
     *
     * @return the lock manager for this locker.
     */
    public LockManager<K> getLockManager() {
        return lockManager;
    }

    /* -- Protected methods -- */

    /**
     * Returns the time when a lock attempt should stop, given the current time
     * and the lock timeout supplied by the caller.  Subclasses can override
     * this method, for example to enforce a transaction timeout.
     *
     * @param    now the current time in milliseconds
     * @param    lockTimeout the amount of time in milliseconds to wait for a
     * lock
     * @return the time in milliseconds when the lock attempt should timeout
     */
    protected long getLockTimeoutTime(long now, long lockTimeout) {
        return addCheckOverflow(now, lockTimeout);
    }

    /**
     * Creates a new lock request. <p>
     * <p>
     * The default implementation creates and returns an instance of {@link
     * LockRequest}.
     *
     * @param    key the key that identifies the lock
     * @param    forWrite whether the request is for write
     * @param    upgrade whether the request is for an upgrade
     * @return the lock request
     */
    protected LockRequest<K> newLockRequest(
            K key, boolean forWrite, boolean upgrade) {
        return new LockRequest<K>(this, key, forWrite, upgrade);
    }

    /**
     * Checks if there is a conflict that should cause this locker's
     * current request to be denied.  Returns {@code null} if there was no
     * conflict. <p>
     * <p>
     * The default implementation of this method always returns {@code null}.
     *
     * @return the conflicting request or {@code null}
     */
    protected LockConflict<K> getConflict() {
        return null;
    }

    /**
     * Clears the conflict that should cause this locker's current request to
     * be denied.  If there is no conflict, then this method has no effect.  If
     * the conflict is a deadlock, represented by a non-{@code null} return
     * value from {@link #getConflict getConflict} with a {@code type} field
     * equal to {@link LockConflictType#DEADLOCK DEADLOCK}, then the conflict
     * cannot be cleared and {@code IllegalStateException} will be thrown. <p>
     * <p>
     * The default implementation of this method does nothing.
     *
     * @throws IllegalStateException if the conflict is a deadlock
     */
    protected void clearConflict() {
    }

    /* -- Package access methods -- */

    /**
     * Checks if this locker is waiting for a lock.
     *
     * @return the result of the attempt to request a lock that this locker
     * is waiting for, or {@code null} if it is not waiting
     */
    abstract LockAttemptResult<K> getWaitingFor();

    /**
     * Records that this locker is waiting for a lock, or marks that it is not
     * waiting if the argument is {@code null}.  If {@code waitingFor} is not
     * {@code null}, then it should represent a conflict, and it's {@code
     * conflict} field must not be {@code null}.
     *
     * @param    waitingFor the lock or {@code null}
     * @throws IllegalArgumentException if {@code waitingFor} is not {@code
     * null} and its {@code conflict} field is {@code null}
     */
    abstract void setWaitingFor(LockAttemptResult<K> waitingFor);
}
