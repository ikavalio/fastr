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
package com.oracle.truffle.r.nodes.unary;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.r.nodes.primitive.UnaryMapNode;
import com.oracle.truffle.r.nodes.profile.TruffleBoundaryNode;
import com.oracle.truffle.r.runtime.RError;
import com.oracle.truffle.r.runtime.RType;
import com.oracle.truffle.r.runtime.data.RMissing;
import com.oracle.truffle.r.runtime.data.model.RAbstractComplexVector;
import com.oracle.truffle.r.runtime.data.model.RAbstractDoubleVector;
import com.oracle.truffle.r.runtime.data.model.RAbstractIntVector;
import com.oracle.truffle.r.runtime.data.model.RAbstractLogicalVector;
import com.oracle.truffle.r.runtime.data.model.RAbstractVector;
import com.oracle.truffle.r.runtime.nodes.RBaseNode;
import com.oracle.truffle.r.runtime.ops.UnaryArithmetic;
import com.oracle.truffle.r.runtime.ops.UnaryArithmeticFactory;

public abstract class UnaryArithmeticNode extends UnaryNode {

    protected final UnaryArithmeticFactory unary;

    public UnaryArithmeticNode(UnaryArithmeticFactory factory) {
        this.unary = factory;
    }

    public abstract Object execute(Object value);

    @Specialization(guards = {"cachedNode != null", "cachedNode.isSupported(operand)"})
    protected Object doCached(Object operand,
                    @Cached("createCachedFast(operand)") UnaryMapNode cachedNode) {
        return cachedNode.apply(operand);
    }

    protected UnaryMapNode createCachedFast(Object operand) {
        if (isNumericVector(operand)) {
            return createCached(unary.createOperation(), operand);
        }
        return null;
    }

    protected static UnaryMapNode createCached(UnaryArithmetic arithmetic, Object operand) {
        if (operand instanceof RAbstractVector) {
            RAbstractVector castOperand = (RAbstractVector) operand;
            RType operandType = castOperand.getRType();
            if (operandType.isNumeric()) {
                RType type = RType.maxPrecedence(operandType, arithmetic.getMinPrecedence());
                RType resultType = arithmetic.calculateResultType(type);
                return UnaryMapNode.create(new ScalarUnaryArithmeticNode(arithmetic), castOperand, type, resultType);
            }
        }
        return null;
    }

    protected static boolean isNumericVector(Object value) {
        return value instanceof RAbstractIntVector || value instanceof RAbstractDoubleVector || value instanceof RAbstractComplexVector || value instanceof RAbstractLogicalVector;
    }

    @Specialization(replaces = "doCached", guards = {"isNumericVector(operand)"})
    @TruffleBoundary
    protected Object doGeneric(Object operand,
                    @Cached("unary.createOperation()") UnaryArithmetic arithmetic,
                    @Cached("new(createCached(arithmetic, operand))") GenericNumericVectorNode generic) {
        RAbstractVector operandVector = (RAbstractVector) operand;
        return generic.get(arithmetic, operandVector).apply(operandVector);
    }

    @Override
    public RBaseNode getErrorContext() {
        return this;
    }

    @Fallback
    protected Object invalidArgType(Object operand) {
        CompilerDirectives.transferToInterpreter();
        UnaryArithmetic op = unary.createOperation();
        if (operand instanceof RMissing) {
            throw error(RError.Message.ARGUMENTS_PASSED, 0, "'" + op.getClass().getSimpleName().toLowerCase() + "'", 1);
        } else {
            throw error(op.getArgumentError());
        }
    }

    protected static final class GenericNumericVectorNode extends TruffleBoundaryNode {

        @Child private UnaryMapNode cached;

        public GenericNumericVectorNode(UnaryMapNode cachedOperation) {
            this.cached = cachedOperation;
        }

        public UnaryMapNode get(UnaryArithmetic arithmetic, RAbstractVector operand) {
            UnaryMapNode next = cached;
            if (!next.isSupported(operand)) {
                next = cached.replace(createCached(arithmetic, operand));
            }
            return next;
        }
    }
}
