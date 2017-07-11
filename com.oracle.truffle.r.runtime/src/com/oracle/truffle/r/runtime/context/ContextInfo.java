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
package com.oracle.truffle.r.runtime.context;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.TimeZone;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import com.oracle.truffle.api.vm.PolyglotEngine;
import com.oracle.truffle.api.vm.PolyglotEngine.Builder;
import com.oracle.truffle.r.launcher.RCmdOptions;
import com.oracle.truffle.r.launcher.RStartParams;
import com.oracle.truffle.r.launcher.RCmdOptions.Client;
import com.oracle.truffle.r.runtime.RInternalError;
import com.oracle.truffle.r.runtime.context.RContext.ContextKind;

final class ConsoleHandlerInputStream extends InputStream {
    @Override
    public int read() throws IOException {
        return 0;
    }
}

final class ConsoleHandlerOutputStream extends OutputStream {

    @Override
    public void write(int b) throws IOException {

    }
}

/**
 * Represents custom initialization state for an R instance.
 *
 * Use {@link #createVM()} to apply this information to a newly-built {@link PolyglotEngine}
 * instance (it will be stored in the "fastrContextInfo" global symbol).
 */
public final class ContextInfo {
    static final String CONFIG_KEY = "fastrContextInfo";

    static final AtomicInteger contextInfoIds = new AtomicInteger();
    static final AtomicInteger multiSlotInds = new AtomicInteger(-1);

    private final RStartParams startParams;
    private final String[] env;
    private final RContext.ContextKind kind;
    private final TimeZone systemTimeZone;

    /**
     * Any context created by another has a parent. When such a context is destroyed we must reset
     * the RContext.threadLocalContext to the parent.
     */
    private final RContext parent;
    private final InputStream stdin;
    private final OutputStream stdout;
    private final OutputStream stderr;
    private final int id;
    private final int multiSlotInd;
    private PolyglotEngine vm;
    public Executor executor;

    private ContextInfo(RStartParams startParams, String[] env, ContextKind kind, RContext parent, InputStream stdin, OutputStream stdout, OutputStream stderr, TimeZone systemTimeZone, int id,
                    int multiSlotInd) {
        this.startParams = startParams;
        this.env = env;
        this.kind = kind;
        this.parent = parent;
        this.stdin = stdin;
        this.stdout = stdout;
        this.stderr = stderr;
        this.systemTimeZone = systemTimeZone;
        this.multiSlotInd = multiSlotInd;
        this.id = id;
    }

    /**
     * Correctness of this method relies on the fact that parallel contexts are started only after
     * all of them (and their info) is created (in FastRContext).
     */
    public static int contextNum() {
        return multiSlotInds.get() + 1;
    }

    public static void resetMultiSlotIndexGenerator() {
        multiSlotInds.set(0); // to account for primordial context
    }

    public PolyglotEngine createVM() {
        Builder builder = PolyglotEngine.newBuilder();
        if (startParams.isInteractive()) {
            this.executor = Executors.newSingleThreadExecutor();
            builder = builder.executor(executor);
        }
        PolyglotEngine newVM = builder.config("application/x-r", CONFIG_KEY, this).setIn(stdin).setOut(stdout).setErr(stderr).build();
        this.vm = newVM;
        return newVM;
    }

    public PolyglotEngine createVM(PolyglotEngine.Builder builder) {
        PolyglotEngine newVM = builder.config("application/x-r", CONFIG_KEY, this).setIn(stdin).setOut(stdout).setErr(stderr).build();
        this.vm = newVM;
        return newVM;
    }

    /**
     * Create a context configuration object.
     *
     * @param startParams the start parameters passed this R session
     * @param kind defines the degree to which this context shares base and package environments
     *            with its parent
     * @param parent if non-null {@code null}, the parent creating the context
     * @param systemTimeZone the system's time zone
     */
    public static ContextInfo create(RStartParams startParams, String[] env, ContextKind kind, RContext parent, InputStream stdin, OutputStream stdout, OutputStream stderr, TimeZone systemTimeZone) {
        int id = contextInfoIds.incrementAndGet();
        int multiSlotInd = multiSlotInds.get();
        if (kind == ContextKind.SHARE_ALL || kind == ContextKind.SHARE_NOTHING) {
            multiSlotInd = multiSlotInds.incrementAndGet();
        }
        // no increment for SHARE_PARENT_RW as it accesses the same data as its parent whose
        // execution is suspended
        if (kind == ContextKind.SHARE_PARENT_RO) {
            throw RInternalError.shouldNotReachHere();
        }
        assert kind != ContextKind.SHARE_PARENT_RW || (kind == ContextKind.SHARE_PARENT_RW && parent.getKind() == ContextKind.SHARE_NOTHING && parent.getMultiSlotInd() == 0);
        return new ContextInfo(startParams, env, kind, parent, stdin, stdout, stderr, systemTimeZone, id, kind == ContextKind.SHARE_PARENT_RW ? 0 : multiSlotInd);
    }

    public static ContextInfo create(RStartParams startParams, String[] env, ContextKind kind, RContext parent, InputStream stdin, OutputStream stdout, OutputStream stderr) {
        return create(startParams, env, kind, parent, stdin, stdout, stderr, TimeZone.getDefault());
    }

    /**
     * Create a context configuration object such that FastR does not restore previously stored
     * sessions on startup.
     *
     * @param env TODO
     * @param kind defines the degree to which this context shares base and package environments
     *            with its parent
     * @param parent if non-null {@code null}, the parent creating the context
     */
    public static ContextInfo createNoRestore(Client client, String[] env, ContextKind kind, RContext parent, InputStream stdin, OutputStream stdout, OutputStream stderr) {
        RStartParams params = new RStartParams(RCmdOptions.parseArguments(client, new String[]{"R", "--vanilla", "--slave", "--silent", "--no-restore"}, false), false);
        return create(params, env, kind, parent, stdin, stdout, stderr);
    }

    public RStartParams getStartParams() {
        return startParams;
    }

    public String[] getEnv() {
        return env;
    }

    public ContextKind getKind() {
        return kind;
    }

    public RContext getParent() {
        return parent;
    }

    public TimeZone getSystemTimeZone() {
        return systemTimeZone;
    }

    public int getId() {
        return id;
    }

    public int getMultiSlotInd() {
        return multiSlotInd;
    }

    public PolyglotEngine getVM() {
        return vm;
    }

    public InputStream getStdin() {
        return stdin;
    }

    public OutputStream getStdout() {
        return stdout;
    }

    public OutputStream getStderr() {
        return stderr;
    }
}
