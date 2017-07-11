/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.r.test.generate;

import com.oracle.truffle.r.runtime.context.ContextInfo;
import com.oracle.truffle.r.test.TestBase;

/**
 * Represents an R session that may be interactive or non-interactive.
 */
public interface RSession {
    /**
     * Returns the result of evaluating {@code expression} including errors and warnings.
     *
     * If {@code contextInfo is non-null} it is used for the evaluation, else the choice is left to
     * the implementation. If the implementation uses timeouts, {@code longTimeout} indicates that
     * this evaluation is expected to take (much) longer than normal.
     *
     * If {@code testClass} is not null, then it represents the subclass of {@link TestBase} that
     * caused the evaluation.
     *
     * This result will always be non-null or an exception will be thrown in, say, a timeout
     * occurring.
     */
    String eval(TestBase testClass, String expression, ContextInfo contextInfo, boolean longTimeout) throws Throwable;

    /**
     * A name to identify the session.
     */
    String name();
}
