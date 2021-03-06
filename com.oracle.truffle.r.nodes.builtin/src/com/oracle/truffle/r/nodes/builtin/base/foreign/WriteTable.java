/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html
 *
 * Copyright (c) 1995-2012, The R Core Team
 * Copyright (c) 2003, The R Foundation
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates
 *
 * All rights reserved.
 */
package com.oracle.truffle.r.nodes.builtin.base.foreign;

import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.nullValue;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.stringValue;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.toBoolean;

import java.io.IOException;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef;
import com.oracle.truffle.r.nodes.builtin.RExternalBuiltinNode;
import com.oracle.truffle.r.nodes.builtin.base.printer.ComplexVectorPrinter;
import com.oracle.truffle.r.nodes.builtin.base.printer.DoubleVectorPrinter;
import com.oracle.truffle.r.nodes.function.ClassHierarchyNode;
import com.oracle.truffle.r.runtime.RError;
import com.oracle.truffle.r.runtime.RError.Message;
import com.oracle.truffle.r.runtime.RInternalError;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.conn.RConnection;
import com.oracle.truffle.r.runtime.data.RAttributable;
import com.oracle.truffle.r.runtime.data.RComplex;
import com.oracle.truffle.r.runtime.data.RNull;
import com.oracle.truffle.r.runtime.data.RRaw;
import com.oracle.truffle.r.runtime.data.RStringVector;
import com.oracle.truffle.r.runtime.data.RVector;
import com.oracle.truffle.r.runtime.data.model.RAbstractComplexVector;
import com.oracle.truffle.r.runtime.data.model.RAbstractContainer;
import com.oracle.truffle.r.runtime.data.model.RAbstractDoubleVector;
import com.oracle.truffle.r.runtime.data.model.RAbstractIntVector;
import com.oracle.truffle.r.runtime.data.model.RAbstractLogicalVector;
import com.oracle.truffle.r.runtime.data.model.RAbstractRawVector;
import com.oracle.truffle.r.runtime.data.model.RAbstractStringVector;
import com.oracle.truffle.r.runtime.data.model.RAbstractVector;

//Transcribed from GnuR, library/utils/src/io.c

public abstract class WriteTable extends RExternalBuiltinNode.Arg11 {

    static {
        Casts casts = new Casts(WriteTable.class);
        // file
        casts.arg(1).defaultError(Message.INVALID_CONNECTION).mustNotBeNull().asIntegerVector().findFirst();
        // nrows
        casts.arg(2).mustNotBeNull().asIntegerVector().findFirst().mustNotBeNA();
        // nc
        casts.arg(3).mustNotBeNull().asIntegerVector().findFirst().mustNotBeNA();
        // rnames
        casts.arg(4).allowNull().mustBe(stringValue()).asStringVector();
        // sep
        casts.arg(5).mustBe(stringValue()).asStringVector().findFirst();
        // eol
        casts.arg(6).mustBe(stringValue()).asStringVector().findFirst();
        // na
        casts.arg(7).mustBe(stringValue()).asStringVector().findFirst();
        // dec
        casts.arg(8).mustBe(stringValue()).asStringVector().findFirst().mustBe(Predef.length(1), RError.Message.GENERIC, "'dec' must be a single character");
        // quote
        casts.arg(9).mustNotBeMissing().mustBe(nullValue().not()).asIntegerVector();
        // qmethod
        casts.arg(10).mustNotBeNull().asLogicalVector().findFirst().mustNotBeNA().map(toBoolean());
    }

    // Transcribed from GnuR, library/utils/src/io.c

    @Specialization
    @TruffleBoundary
    protected static Object writetable(Object xx, int file, int nr, int nc, Object rnames, String csep, String ceol, String cna, String dec, RAbstractIntVector quote, boolean qmethod) {
        char cdec = dec.charAt(0);
        boolean[] quoteCol = new boolean[nc];
        boolean quoteRn = false;
        for (int i = 0; i < quote.getLength(); i++) {
            int qi = quote.getDataAt(i);
            if (qi == 0) {
                quoteRn = true;
            }
            if (qi > 0) {
                quoteCol[qi - 1] = true;
            }
        }
        try (RConnection con = RConnection.fromIndex(file).forceOpen("wt")) {
            if (xx instanceof RAttributable && ClassHierarchyNode.hasClass((RAttributable) xx, RRuntime.CLASS_DATA_FRAME)) {
                executeDataFrame(con, (RVector<?>) xx, nr, nc, rnames, csep, ceol, cna, cdec, qmethod, quoteCol, quoteRn);
            } else { /* A matrix */

                // if (!isVectorAtomic(x))
                // UNIMPLEMENTED_TYPE("write.table, matrix method", x);
                RVector<?> x = (RVector<?>) xx;
                /* quick integrity check */
                if (x.getLength() != nr * nc) {
                    throw new IllegalArgumentException("corrupt matrix -- dims not not match length");
                }

                StringBuilder tmp = new StringBuilder();
                for (int i = 0; i < nr; i++) {
                    if (!(rnames instanceof RNull)) {
                        tmp.append(encodeElement2((RAbstractStringVector) rnames, i, quoteRn, qmethod, cdec));
                        tmp.append(csep);
                    }
                    for (int j = 0; j < nc; j++) {
                        if (j > 0) {
                            tmp.append(csep);
                        }
                        if (isna(x, i + j * nr)) {
                            tmp.append(cna);
                        } else {
                            tmp.append(encodeElement2(x, i + j * nr, quoteCol[j], qmethod, cdec));
                            /* if(cdec) change_dec(tmp, cdec, TYPEOF(x)); */
                        }
                    }
                    tmp.append(ceol);
                    con.writeString(tmp.toString(), false);
                    tmp.setLength(0);
                }
            }
        } catch (IOException | IllegalArgumentException ex) {
            throw RError.error(RError.SHOW_CALLER, RError.Message.GENERIC, ex.getMessage());
        }
        return RNull.instance;
    }

    private static void executeDataFrame(RConnection con, RVector<?> x, int nr, int nc, Object rnames, String csep, String ceol, String cna, char cdec, boolean qmethod, boolean[] quoteCol,
                    boolean quoteRn)
                    throws IOException {

        /* handle factors internally, check integrity */
        RStringVector[] levels = new RStringVector[nc];
        for (int j = 0; j < nc; j++) {
            Object xjObj = x.getDataAtAsObject(j);
            if (xjObj instanceof RAbstractContainer) {
                RAbstractContainer xj = (RAbstractContainer) xjObj;
                if (xj.getLength() != nr) {
                    throw new IllegalArgumentException("corrupt data frame -- length of column " + (j + 1) + " does not not match nrows");
                }
                if (isFactor(xj)) {
                    levels[j] = (RStringVector) xj.getAttributes().get("levels");
                }
            } else {
                if (nr != 1) {
                    throw new IllegalArgumentException("corrupt data frame -- length of column " + (j + 1) + " does not not match nrows");
                }
            }
        }

        StringBuilder tmp = new StringBuilder();
        for (int i = 0; i < nr; i++) {
            // if (i % 1000 == 999)
            // R_CheckUserInterrupt();
            if (!(rnames instanceof RNull)) {
                tmp.append(encodeElement2((RAbstractStringVector) rnames, i, quoteRn, qmethod, cdec)).append(csep);
            }
            for (int j = 0; j < nc; j++) {
                Object xjObj = x.getDataAtAsObject(j);
                if (j > 0) {
                    tmp.append(csep);
                }
                if (xjObj instanceof RAbstractContainer) {
                    RAbstractContainer xj = (RAbstractContainer) xjObj;
                    if (isna(xj, i)) {
                        tmp.append(cna);
                    } else {
                        if (levels[j] != null) {
                            tmp.append(encodeElement2(levels[j], (int) xj.getDataAtAsObject(i) - 1, quoteCol[j], qmethod, cdec));
                        } else {
                            tmp.append(encodeElement2((RAbstractVector) xj, i, quoteCol[j], qmethod, cdec));
                        }
                        /* if(cdec) change_dec(tmp, cdec, TYPEOF(xj)); */
                    }
                } else {
                    tmp.append(encodePrimitiveElement(xjObj, cna, quoteRn, qmethod));
                    /* if(cdec) change_dec(tmp, cdec, TYPEOF(xj)); */
                }
            }
            tmp.append(ceol);
            con.writeString(tmp.toString(), false);
            tmp.setLength(0);
        }
    }

    private static String encodeStringElement(String p0, boolean quote, boolean qmethod) {
        if (!quote) {
            return p0;
        }
        StringBuilder sb = new StringBuilder();
        sb.append('"');
        for (int i = 0; i < p0.length(); i++) {
            char p = p0.charAt(i);
            if (p == '"') {
                sb.append(qmethod ? '\\' : '"');
            }
            sb.append(p);
        }
        sb.append('"');
        return sb.toString();
    }

    /* a version of EncodeElement with different escaping of char strings */
    private static String encodeElement2(RAbstractVector x, int indx, boolean quote, boolean qmethod, char cdec) {
        if (indx < 0 || indx >= x.getLength()) {
            throw new IllegalArgumentException("index out of range");
        }
        if (x instanceof RAbstractStringVector) {
            RAbstractStringVector sx = (RAbstractStringVector) x;
            String p0 = /* translateChar */sx.getDataAt(indx);
            return encodeStringElement(p0, quote, qmethod);
        }
        return encodeElement(x, indx, quote ? '"' : 0, cdec);
    }

    private static String encodePrimitiveElement(Object o, String cna, boolean quote, boolean qmethod) {
        if (o instanceof Integer) {
            int v = (int) o;
            return RRuntime.isNA(v) ? cna : RRuntime.intToStringNoCheck(v);
        } else if (o instanceof Double) {
            double v = (double) o;
            return RRuntime.isNA(v) ? cna : DoubleVectorPrinter.encodeReal(v);
        } else if (o instanceof Byte) {
            byte v = (byte) o;
            return RRuntime.isNA(v) ? cna : RRuntime.logicalToStringNoCheck(v);
        } else if (o instanceof String) {
            String v = (String) o;
            return RRuntime.isNA(v) ? cna : encodeStringElement(v, quote, qmethod);
        } else if (o instanceof RComplex) {
            RComplex v = (RComplex) o;
            return RRuntime.isNA(v) ? cna : ComplexVectorPrinter.encodeComplex(v);
        } else if (o instanceof RRaw) {
            RRaw v = (RRaw) o;
            return RRuntime.rawToHexString(v.getValue());
        }
        throw RInternalError.unimplemented();
    }

    private static boolean isna(RAbstractContainer x, int indx) {
        if (x instanceof RAbstractLogicalVector) {
            return RRuntime.isNA(((RAbstractLogicalVector) x).getDataAt(indx));
        } else if (x instanceof RAbstractDoubleVector) {
            return RRuntime.isNA(((RAbstractDoubleVector) x).getDataAt(indx));
        } else if (x instanceof RAbstractIntVector) {
            return RRuntime.isNA(((RAbstractIntVector) x).getDataAt(indx));
        } else if (x instanceof RAbstractStringVector) {
            return RRuntime.isNA(((RAbstractStringVector) x).getDataAt(indx));
        } else if (x instanceof RAbstractComplexVector) {
            RAbstractComplexVector cvec = (RAbstractComplexVector) x;
            RComplex c = cvec.getDataAt(indx);
            return c.isNA();
        } else {
            return false;
        }
    }

    private static String encodeElement(Object x, int indx, @SuppressWarnings("unused") char quote, @SuppressWarnings("unused") char dec) {
        if (x instanceof RAbstractDoubleVector) {
            RAbstractDoubleVector v = (RAbstractDoubleVector) x;
            return DoubleVectorPrinter.encodeReal(v.getDataAt(indx));
        }
        if (x instanceof RAbstractIntVector) {
            RAbstractIntVector v = (RAbstractIntVector) x;
            return RRuntime.intToString(v.getDataAt(indx));
        }
        if (x instanceof RAbstractLogicalVector) {
            RAbstractLogicalVector v = (RAbstractLogicalVector) x;
            return RRuntime.logicalToString(v.getDataAt(indx));
        }
        if (x instanceof RAbstractComplexVector) {
            RAbstractComplexVector v = (RAbstractComplexVector) x;
            return ComplexVectorPrinter.encodeComplex(v.getDataAt(indx));
        }
        if (x instanceof RAbstractRawVector) {
            RAbstractRawVector v = (RAbstractRawVector) x;
            return RRuntime.rawToHexString(v.getRawDataAt(indx));
        }
        throw RInternalError.unimplemented();
    }

    @TruffleBoundary
    private static boolean isFactor(RAbstractContainer v) {
        RStringVector hierarchy = ClassHierarchyNode.getClassHierarchy(v);
        for (int i = 0; i < hierarchy.getLength(); i++) {
            if (hierarchy.getDataAt(i).equals("factor")) {
                return true;
            }
        }
        return false;
    }
}
