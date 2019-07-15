/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.javac.comp;

import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Symbol.VarSymbol;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.comp.MatchBindingsComputer.BindingSymbol;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.GenericSwitch.SwitchKind;
import com.sun.tools.javac.tree.JCTree.JCAssign;
import com.sun.tools.javac.tree.JCTree.JCBinary;
import com.sun.tools.javac.tree.JCTree.JCBlock;
import com.sun.tools.javac.tree.JCTree.JCBreak;
import com.sun.tools.javac.tree.JCTree.JCCase;
import com.sun.tools.javac.tree.JCTree.JCConditional;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCForLoop;
import com.sun.tools.javac.tree.JCTree.JCIdent;
import com.sun.tools.javac.tree.JCTree.JCIf;
import com.sun.tools.javac.tree.JCTree.JCInstanceOf;
import com.sun.tools.javac.tree.JCTree.JCLabeledStatement;
import com.sun.tools.javac.tree.JCTree.JCLiteralPattern;
import com.sun.tools.javac.tree.JCTree.JCLiteralPattern.LiteralPatternKind;
import com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import com.sun.tools.javac.tree.JCTree.JCStatement;
import com.sun.tools.javac.tree.JCTree.JCSwitch;
import com.sun.tools.javac.tree.JCTree.JCVariableDecl;
import com.sun.tools.javac.tree.JCTree.JCBindingPattern;
import com.sun.tools.javac.tree.JCTree.JCWhileLoop;
import com.sun.tools.javac.tree.JCTree.Tag;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.Assert;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Log;
import com.sun.tools.javac.util.Names;
import com.sun.tools.javac.util.Options;

import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.comp.MatchBindingsComputer.BindingSymbol;
import com.sun.tools.javac.jvm.Target;
import com.sun.tools.javac.tree.JCTree.JCBlock;
import com.sun.tools.javac.tree.JCTree.JCDoWhileLoop;
import com.sun.tools.javac.tree.JCTree.JCStatement;
import com.sun.tools.javac.util.List;
import static com.sun.tools.javac.code.TypeTag.BOOLEAN;
import static com.sun.tools.javac.code.TypeTag.BOT;
import static com.sun.tools.javac.tree.JCTree.Tag.SWITCH;

/**
 * This pass translates pattern-matching constructs, such as instanceof <pattern> and switch.
 */
public class TransPatterns extends TreeTranslator {

    protected static final Context.Key<TransPatterns> transPatternsKey = new Context.Key<>();

    public static TransPatterns instance(Context context) {
        TransPatterns instance = context.get(transPatternsKey);
        if (instance == null)
            instance = new TransPatterns(context);
        return instance;
    }

    private Symtab syms;
    private TreeMaker make;
    private Types types;
    private Operators operators;
    private Log log;
    private ConstFold constFold;
    private Names names;
    private Target target;

    BindingContext bindingContext = new BindingContext() {
        @Override
        VarSymbol getBindingFor(BindingSymbol varSymbol) {
            return null;
        }

        @Override
        JCStatement decorateStatement(JCStatement stat) {
            return stat;
        }

        @Override
        JCExpression decorateExpression(JCExpression expr) {
            return expr;
        }

        @Override
        BindingContext pop() {
            //do nothing
            return this;
        }

        @Override
        boolean tryPrepend(BindingSymbol binding, JCVariableDecl var) {
            return false;
        }
    };

    JCLabeledStatement pendingMatchLabel = null;

    boolean debugTransPatterns;

    private MethodSymbol currentMethodSym = null;

    protected TransPatterns(Context context) {
        context.put(transPatternsKey, this);
        syms = Symtab.instance(context);
        make = TreeMaker.instance(context);
        types = Types.instance(context);
        operators = Operators.instance(context);
        log = Log.instance(context);
        constFold = ConstFold.instance(context);
        names = Names.instance(context);
        target = Target.instance(context);
        debugTransPatterns = Options.instance(context).isSet("debug.patterns");
    }

    @Override
    public void visitTypeTest(JCInstanceOf tree) {
        if (tree.pattern.hasTag(Tag.BINDINGPATTERN)) {
            JCBindingPattern patt = (JCBindingPattern)tree.pattern;
            VarSymbol pattSym = patt.symbol;
            Type tempType = tree.expr.type.hasTag(BOT) ?
                    syms.objectType
                    : tree.expr.type;
            VarSymbol temp = new VarSymbol(pattSym.flags() | Flags.SYNTHETIC,
                    names.fromString(pattSym.name.toString() + target.syntheticNameChar() + "temp"),
                    tempType,
                    patt.symbol.owner);
            JCExpression translatedExpr = translate(tree.expr);
            Type castTargetType = types.boxedTypeOrType(pattSym.erasure(types));
            if (patt.vartype == null || tree.expr.type.isPrimitive()) {
                result = make.Literal(BOOLEAN,1).setType(syms.booleanType);
            } else {
                result = makeTypeTest(make.Ident(temp), make.Type(castTargetType));
            }

            VarSymbol bindingVar = bindingContext.getBindingFor(patt.symbol);
            if (bindingVar != null) {
                JCAssign fakeInit = (JCAssign)make.at(tree.pos).Assign(
                        make.Ident(bindingVar), convert(make.Ident(temp), castTargetType)).setType(bindingVar.erasure(types));
                result = makeBinary(Tag.AND, (JCExpression)result,
                        makeBinary(Tag.EQ, fakeInit, convert(make.Ident(temp), castTargetType)));
            }
            result = make.at(tree.pos).LetExpr(make.VarDef(temp, translatedExpr), (JCExpression)result).setType(syms.booleanType);
        } else if (tree.pattern.hasTag(Tag.LITERALPATTERN)) {
            //XXX: should this be here, or do we change switch desugaring?
            JCLiteralPattern patt = (JCLiteralPattern)tree.pattern;
            if (patt.patternKind == LiteralPatternKind.TYPE) {
                result = makeTypeTest(tree.expr, patt.value);
            } else {
                JCExpression ce = patt.value;
                JCExpression lhs = ce.type.hasTag(BOT) ?
                        tree.expr
                        : make.TypeCast(make.Type(ce.type), tree.expr).setType(ce.type.baseType());
                if (!ce.type.hasTag(BOT) && tree.expr.type.isReference()) {
                    result = translate(makeBinary(
                            Tag.AND,
                            makeTypeTest(tree.expr, make.Type(types.boxedTypeOrType(ce.type))),
                            makeBinary(JCTree.Tag.EQ, lhs, ce)));
                } else {
                    result = translate(makeBinary(JCTree.Tag.EQ, lhs, ce));
                }
            }
        } else {
            super.visitTypeTest(tree);
        }
    }

    @Override
    public void visitBinary(JCBinary tree) {
        List<BindingSymbol> matchBindings;
        switch (tree.getTag()) {
            case AND:
                matchBindings = Attr.getMatchBindings(types, log, tree.lhs, true);
                break;
            case OR:
                matchBindings = Attr.getMatchBindings(types, log, tree.lhs, false);
                break;
            default:
                matchBindings = List.nil();
                break;
        }

        bindingContext = new BasicBindingContext(matchBindings);
        try {
            super.visitBinary(tree);
            result = bindingContext.decorateExpression(tree);
        } finally {
            bindingContext.pop();
        }
    }

    @Override
    public void visitBreak(JCBreak tree) {
        if (tree.target.hasTag(SWITCH) && ((JCSwitch) tree.target).kind == SwitchKind.MATCHING) {
            Assert.checkNonNull(pendingMatchLabel);
            tree.target = pendingMatchLabel;
            result = tree;
        } else {
            super.visitBreak(tree);
        }
    }

    @Override
    public void visitConditional(JCConditional tree) {
        bindingContext = new BasicBindingContext(
                Attr.getMatchBindings(types, log, tree.cond, true)
                        .appendList(Attr.getMatchBindings(types, log, tree.cond, false)));
        try {
            super.visitConditional(tree);
            result = bindingContext.decorateExpression(tree);
        } finally {
            bindingContext.pop();
        }
    }

    @Override
    public void visitIf(JCIf tree) {
        bindingContext = new BasicBindingContext(getMatchBindings(tree.cond));
        try {
            super.visitIf(tree);
            result = bindingContext.decorateStatement(tree);
        } finally {
            bindingContext.pop();
        }
    }

    @Override
    public void visitForLoop(JCForLoop tree) {
        bindingContext = new BasicBindingContext(getMatchBindings(tree.cond));
        try {
            super.visitForLoop(tree);
            result = bindingContext.decorateStatement(tree);
        } finally {
            bindingContext.pop();
        }
    }

    @Override
    public void visitWhileLoop(JCWhileLoop tree) {
        bindingContext = new BasicBindingContext(getMatchBindings(tree.cond));
        try {
            super.visitWhileLoop(tree);
            result = bindingContext.decorateStatement(tree);
        } finally {
            bindingContext.pop();
        }
    }

    @Override
    public void visitSwitch(JCSwitch tree) {
        if (tree.kind == SwitchKind.MATCHING) {
            JCLabeledStatement prevMatchLabel = pendingMatchLabel;
            try {
                pendingMatchLabel = make.Labelled(names.fromString("match$" + tree.pos), null);
                VarSymbol fallthroughSym = new VarSymbol(0, names.fromString("fallthrough$" + tree.pos), syms.booleanType, currentMethodSym);

                JCStatement fallthroughInit = make.at(tree.pos).VarDef(fallthroughSym, make.Literal(BOOLEAN, 0).setType(syms.booleanType));

                List<JCStatement> resultStatements = List.of(fallthroughInit);

                for (JCCase clause : tree.cases) {
                    Assert.check(clause.pats.size() <= 1);
                    final JCExpression jcMatches = clause.pats.nonEmpty() ? make.TypeTest(tree.selector, clause.pats.head) : make.Literal(BOOLEAN, 1);
                    jcMatches.setType(syms.booleanType);
                    JCStatement body;
                    List<JCStatement> stats = clause.stats;
                    if (clause.completesNormally) {
                        stats = stats.append(make.at(tree.pos).Exec(make.Assign(make.Ident(fallthroughSym), make.Literal(BOOLEAN, 1).setType(syms.booleanType)).setType(syms.booleanType)));
                    }
                    body = make.Block(0, stats);
                    JCStatement translatedIf = translate(make.If(jcMatches, body, null));
                    JCIf testStatement = translatedIf.hasTag(Tag.IF) ? (JCIf)translatedIf : (JCIf) ((JCBlock)translatedIf).stats.tail.head;

                    testStatement.cond = makeBinary(Tag.OR,
                            make.Ident(fallthroughSym),
                            testStatement.cond);

                    resultStatements = resultStatements.append(translatedIf);
                }
                pendingMatchLabel.body = make.Block(0, resultStatements);
                result = pendingMatchLabel;
            } finally {
                pendingMatchLabel = prevMatchLabel;
            }
        } else {
            super.visitSwitch(tree);
        }
    }

    @Override
    public void visitDoLoop(JCDoWhileLoop tree) {
        bindingContext = new BasicBindingContext(getMatchBindings(tree.cond));
        try {
            super.visitDoLoop(tree);
            result = bindingContext.decorateStatement(tree);
        } finally {
            bindingContext.pop();
        }
    }

    @Override
    public void visitMethodDef(JCMethodDecl tree) {
        MethodSymbol prevMethodSym = currentMethodSym;
        try {
            currentMethodSym = tree.sym;
            super.visitMethodDef(tree);
        } finally {
            currentMethodSym = prevMethodSym;
        }
    }

    @Override
    public void visitIdent(JCIdent tree) {
        VarSymbol bindingVar = null;
        if ((tree.sym.flags() & Flags.MATCH_BINDING) != 0) {
            bindingVar = bindingContext.getBindingFor((BindingSymbol)tree.sym);
        }
        if (bindingVar == null) {
            super.visitIdent(tree);
        } else {
            result = make.at(tree.pos).Ident(bindingVar);
        }
    }

    @Override
    public void visitBlock(JCBlock tree) {
        ListBuffer<JCStatement> statements = new ListBuffer<>();
        bindingContext = new BasicBindingContext(List.nil()) {
            boolean tryPrepend(BindingSymbol binding, JCVariableDecl var) {
                hoistedVarMap.put(binding, var.sym);
                statements.append(var);
                return true;
            }
        };
        try {
            for (List<JCStatement> l = tree.stats; l.nonEmpty(); l = l.tail) {
                statements.append(translate(l.head));
            }

            tree.stats = statements.toList();
            result = tree;
        } finally {
            bindingContext.pop();
        }
    }

    public JCTree translateTopLevelClass(Env<AttrContext> env, JCTree cdef, TreeMaker make) {
        try {
            this.make = make;
            translate(cdef);
        } finally {
            // note that recursive invocations of this method fail hard
            this.make = null;
        }

        if (debugTransPatterns) {
            System.err.println(cdef);
        }
        return cdef;
    }

    /** Make an instanceof expression.
     *  @param lhs      The expression.
     *  @param type     The type to be tested.
     */

    JCInstanceOf makeTypeTest(JCExpression lhs, JCExpression type) {
        JCInstanceOf tree = make.TypeTest(lhs, type);
        tree.type = syms.booleanType;
        return tree;
    }

    /** Make an attributed binary expression (copied from Lower).
     *  @param optag    The operators tree tag.
     *  @param lhs      The operator's left argument.
     *  @param rhs      The operator's right argument.
     */
    JCBinary makeBinary(JCTree.Tag optag, JCExpression lhs, JCExpression rhs) {
        JCBinary tree = make.Binary(optag, lhs, rhs);
        tree.operator = operators.resolveBinary(tree, optag, lhs.type, rhs.type);
        tree.type = tree.operator.type.getReturnType();
        return tree;
    }

    JCExpression convert(JCExpression expr, Type target) {
        JCExpression result = make.at(expr.pos()).TypeCast(make.Type(target), expr);
        result.type = (expr.type.constValue() != null) ?
                constFold.coerce(expr.type, target) : target;
        return result;
    }

    JCExpression makeDefaultValue(int pos, Type type) {
        if (type.isReference()) {
            return make.at(pos).Literal(BOT, null).setType(syms.botType);
        } else {
            final Object value;
            switch (type.getTag()) {
                case BYTE:
                    value = (byte)0;
                    break;
                case SHORT:
                    value = (short)0;
                    break;
                case INT:
                    value = 0;
                    break;
                case FLOAT:
                    value = 0f;
                    break;
                case LONG:
                    value = 0L;
                    break;
                case DOUBLE:
                    value = 0D;
                    break;
                case CHAR:
                    value = (char)0;
                    break;
                case BOOLEAN:
                    value = false;
                    break;
                default:
                    Assert.error();
                    return null;
            }
            return make.at(pos).Literal(value);
        }
    }

    private List<BindingSymbol> getMatchBindings(JCExpression cond) {
        return Attr.getMatchBindings(types, log, cond, true)
                        .appendList(Attr.getMatchBindings(types, log, cond, false));
    }
    abstract class BindingContext {
        abstract VarSymbol getBindingFor(BindingSymbol varSymbol);
        abstract JCStatement decorateStatement(JCStatement stat);
        abstract JCExpression decorateExpression(JCExpression expr);
        abstract BindingContext pop();
        abstract boolean tryPrepend(BindingSymbol binding, JCVariableDecl var);
    }

    class BasicBindingContext extends BindingContext {
        List<BindingSymbol> matchBindings;
        Map<BindingSymbol, VarSymbol> hoistedVarMap;
        BindingContext parent;

        public BasicBindingContext(List<BindingSymbol> matchBindings) {
            this.matchBindings = matchBindings;
            this.parent = bindingContext;
            this.hoistedVarMap = matchBindings.stream()
                    .filter(v -> parent.getBindingFor(v) == null)
                    .collect(Collectors.toMap(v -> v, v -> new VarSymbol(v.flags(), v.name, v.type, v.owner)));
        }

        @Override
        VarSymbol getBindingFor(BindingSymbol varSymbol) {
            VarSymbol res = parent.getBindingFor(varSymbol);
            if (res != null) {
                return res;
            }
            return hoistedVarMap.entrySet().stream()
                    .filter(e -> e.getKey().isAliasFor(varSymbol))
                    .findFirst()
                    .map(e -> e.getValue()).orElse(null);
        }

        @Override
        JCStatement decorateStatement(JCStatement stat) {
            if (hoistedVarMap.isEmpty()) return stat;
            ListBuffer<JCStatement> stats = new ListBuffer<>();
            for (Entry<BindingSymbol, VarSymbol> e : hoistedVarMap.entrySet()) {
                JCVariableDecl decl = makeHoistedVarDecl(stat.pos, e.getValue());
                if (!e.getKey().isPreserved() ||
                    !parent.tryPrepend(e.getKey(), decl)) {
                    stats.add(decl);
                }
            }
            if (stats.nonEmpty()) {
                stats.add(stat);
                stat = make.at(stat.pos).Block(0, stats.toList());
            }
            return stat;
        }

        @Override
        JCExpression decorateExpression(JCExpression expr) {
            for (VarSymbol vsym : hoistedVarMap.values()) {
                expr = make.at(expr.pos).LetExpr(makeHoistedVarDecl(expr.pos, vsym), expr).setType(expr.type);
            }
            return expr;
        }

        @Override
        BindingContext pop() {
            return bindingContext = parent;
        }

        @Override
        boolean tryPrepend(BindingSymbol binding, JCVariableDecl var) {
            return false;
        }

        private JCVariableDecl makeHoistedVarDecl(int pos, VarSymbol varSymbol) {
            return make.at(pos).VarDef(varSymbol, makeDefaultValue(pos, varSymbol.erasure(types)));
        }
    }
}
