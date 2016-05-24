/*
 * Copyright (c) 2014, 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.r.runtime.rng.user;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.r.runtime.RError;
import com.oracle.truffle.r.runtime.ffi.DLL;
import com.oracle.truffle.r.runtime.ffi.DLL.DLLInfo;
import com.oracle.truffle.r.runtime.ffi.RFFIFactory;
import com.oracle.truffle.r.runtime.ffi.UserRngRFFI;
import com.oracle.truffle.r.runtime.rng.RNGInitAdapter;
import com.oracle.truffle.r.runtime.rng.RRNG.Kind;

/**
 * Interface to a user-supplied RNG.
 */
public final class UserRNG extends RNGInitAdapter {

    private static final String USER_UNIF_RAND = "user_unif_rand";
    private static final String USER_UNIF_INIT = "user_unif_init";
    private static final boolean OPTIONAL = true;

    @SuppressWarnings("unused") private long userUnifRand;
    @SuppressWarnings("unused") private long userUnifInit;
    private long userUnifNSeed;
    private long userUnifSeedloc;
    private UserRngRFFI userRngRFFI;
    private int nSeeds = 0;

    @Override
    @TruffleBoundary
    public void init(int seed) {
        DLLInfo dllInfo = DLL.findLibraryContainingSymbol(USER_UNIF_RAND);
        if (dllInfo == null) {
            throw RError.error(RError.NO_CALLER, RError.Message.RNG_SYMBOL, USER_UNIF_RAND);
        }
        userUnifRand = findSymbol(USER_UNIF_RAND, dllInfo, !OPTIONAL);
        userUnifInit = findSymbol(USER_UNIF_INIT, dllInfo, OPTIONAL);
        userUnifNSeed = findSymbol(USER_UNIF_INIT, dllInfo, OPTIONAL);
        userUnifSeedloc = findSymbol(USER_UNIF_INIT, dllInfo, OPTIONAL);
        userRngRFFI = RFFIFactory.getRFFI().getUserRngRFFI();
        userRngRFFI.setLibrary(dllInfo.path);
        userRngRFFI.init(seed);
        if (userUnifSeedloc != 0 && userUnifNSeed == 0) {
            RError.warning(RError.NO_CALLER, RError.Message.RNG_READ_SEEDS);
        }
        int ns = userRngRFFI.nSeed();
        if (ns < 0 || ns > 625) {
            RError.warning(RError.NO_CALLER, RError.Message.GENERIC, "seed length must be in 0...625; ignored");
        } else {
            nSeeds = ns;
            // TODO: if we ever (initially) share iSeed (as GNU R does) we may need to assign this
            // generator's iSeed here
        }
    }

    private static long findSymbol(String symbol, DLLInfo dllInfo, boolean optional) {
        long func = DLL.findSymbol(symbol, dllInfo.name, DLL.RegisteredNativeSymbol.any());
        if (func == DLL.SYMBOL_NOT_FOUND) {
            if (!optional) {
                throw RError.error(RError.NO_CALLER, RError.Message.RNG_SYMBOL, symbol);
            } else {
                return 0;
            }
        } else {
            return func;
        }
    }

    @Override
    @TruffleBoundary
    public void fixupSeeds(boolean initial) {
        // no fixup
    }

    @Override
    public int[] getSeeds() {
        if (userUnifSeedloc == 0) {
            return null;
        }
        int[] result = new int[nSeeds];
        userRngRFFI.seeds(result);
        return result;
    }

    @Override
    public double[] genrandDouble(int count) {
        double[] result = new double[count];
        for (int i = 0; i < count; i++) {
            result[i] = userRngRFFI.rand();
        }
        return result;
    }

    @Override
    public Kind getKind() {
        return Kind.USER_UNIF;
    }

    @Override
    public int getNSeed() {
        return nSeeds;
    }

}
