/*
 * Tai-e: A Static Analysis Framework for Java
 *
 * Copyright (C) 2022 Tian Tan <tiantan@nju.edu.cn>
 * Copyright (C) 2022 Yue Li <yueli@nju.edu.cn>
 *
 * This file is part of Tai-e.
 *
 * Tai-e is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * Tai-e is distributed in the hope that it will be useful,but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Tai-e. If not, see <https://www.gnu.org/licenses/>.
 */

package pascal.taie.analysis.dataflow.analysis.constprop;

import pascal.taie.analysis.dataflow.analysis.AbstractDataflowAnalysis;
import pascal.taie.analysis.graph.cfg.CFG;
import pascal.taie.config.AnalysisConfig;
import pascal.taie.ir.IR;
import pascal.taie.ir.exp.*;
import pascal.taie.ir.stmt.DefinitionStmt;
import pascal.taie.ir.stmt.Stmt;
import pascal.taie.language.type.PrimitiveType;
import pascal.taie.language.type.Type;
import pascal.taie.util.AnalysisException;

import java.util.List;
import java.util.Optional;

public class ConstantPropagation extends
        AbstractDataflowAnalysis<Stmt, CPFact> {

    public static final String ID = "constprop";

    public ConstantPropagation(AnalysisConfig config) {
        super(config);
    }

    @Override
    public boolean isForward() {
        return true;
    }

    @Override
    public CPFact newBoundaryFact(CFG<Stmt> cfg) {
        // TODO - finish me
        List<Var> params = cfg.getIR().getParams();
        CPFact fact = new CPFact();
        for (Var param : params) {
            if (canHoldInt(param)){
                fact.update(param, Value.getNAC());
            }
        }
        return fact;
    }

    @Override
    public CPFact newInitialFact() {
        // TODO - finish me
        return new CPFact();
    }

    @Override
    public void meetInto(CPFact fact, CPFact target) {
        // TODO - finish me
        for (Var var : fact.keySet()) {
            if (!canHoldInt(var))
                continue;
            target.update(var, meetValue(fact.get(var), target.get(var)));
        }
    }

    /**
     * Meets two Values.
     */
    public Value meetValue(Value v1, Value v2) {
        // TODO - finish me
        if (v1.isNAC() || v2.isNAC())
            return Value.getNAC();
        else if (v1.isUndef())
            return v2;
        else if (v2.isUndef())
            return v1;
        else if (v1.getConstant() == v2.getConstant())
            return v1;
        else
            return Value.getNAC();
    }

    @Override
    public boolean transferNode(Stmt stmt, CPFact in, CPFact out) {
        // TODO - finish me
        if (!(stmt instanceof DefinitionStmt<?, ?>)) {
            return out.copyFrom(in);

        }
        Optional<LValue> def = stmt.getDef();
        if (def.isEmpty()) {
            return out.copyFrom(in);
        }
        LValue lValue = def.get();
        if (lValue instanceof Var) {
            Var var = (Var) lValue;
            if (canHoldInt(var)) {
                RValue rValue = ((DefinitionStmt<?, ?>) stmt).getRValue();
                CPFact copyIn = in.copy();
                if (rValue instanceof Var useVar) {
                    if (canHoldInt(useVar)) {
                        copyIn.update(var, in.get(useVar));
                        return out.copyFrom(copyIn);
                    }
                } else if (rValue instanceof IntLiteral) {
                    copyIn.update(var, Value.makeConstant(((IntLiteral) rValue).getValue()));
                    return out.copyFrom(copyIn);
                } else if (rValue instanceof BinaryExp) {
                    Value value = evaluate(rValue, in);

                    copyIn.update(var, value);
                    return out.copyFrom(copyIn);
                }else {
                    copyIn.update(var,Value.getNAC());
                    return out.copyFrom(copyIn);
                }

            }
        }
        return out.copyFrom(in);
    }

    /**
     * @return true if the given variable can hold integer value, otherwise false.
     */
    public static boolean canHoldInt(Var var) {
        Type type = var.getType();
        if (type instanceof PrimitiveType) {
            switch ((PrimitiveType) type) {
                case BYTE:
                case SHORT:
                case INT:
                case CHAR:
                case BOOLEAN:
                    return true;
            }
        }
        return false;
    }

    /**
     * Evaluates the {@link Value} of given expression.
     *
     * @param exp the expression to be evaluated
     * @param in  IN fact of the statement
     * @return the resulting {@link Value}
     */
    public static Value evaluate(Exp exp, CPFact in) {
        // TODO - finish me
        //exp.getUses()
        Var operand1 = ((BinaryExp) exp).getOperand1();
        Var operand2 = ((BinaryExp) exp).getOperand2();
        if (!canHoldInt(operand1) || !canHoldInt(operand2))
            return Value.getUndef();
        if(in.get(operand2).isConstant() &&in.get(operand2).getConstant()==0&& (((BinaryExp) exp).getOperator().toString()=="/" || ((BinaryExp) exp).getOperator().toString()=="%")){
            return Value.getUndef();
        } else if (in.get(operand1).isConstant() && in.get(operand2).isConstant()) {
            int value1 = in.get(operand1).getConstant();
            int value2 = in.get(operand2).getConstant();
            switch (((BinaryExp) exp).getOperator().toString()) {
                case "+":
                    return Value.makeConstant(value1 + value2);
                case "-":
                    return Value.makeConstant(value1 - value2);
                case "*":
                    return Value.makeConstant(value1 * value2);
                case "/":
                    return Value.makeConstant(value1 / value2);
                case "%":
                    return Value.makeConstant(value1 % value2);
                case "==":
                    return Value.makeConstant(value1 == value2 ? 1 : 0);
                case "!=":
                    return Value.makeConstant(value1 != value2 ? 1 : 0);
                case "<":
                    return Value.makeConstant(value1 < value2 ? 1 : 0);
                case ">":
                    return Value.makeConstant(value1 > value2 ? 1 : 0);
                case "<=":
                    return Value.makeConstant(value1 <= value2 ? 1 : 0);
                case ">=":
                    return Value.makeConstant(value1 >= value2 ? 1 : 0);
                case "<<":
                    return Value.makeConstant(value1 << value2);
                case ">>":
                    return Value.makeConstant(value1 >> value2);
                case ">>>":
                    return Value.makeConstant(value1 >>> value2);
                case "|":
                    return Value.makeConstant(value1 | value2);
                case "&":
                    return Value.makeConstant(value1 & value2);
                case "^":
                    return Value.makeConstant(value1 ^ value2);
                default:
                    return Value.getNAC();
            }
        } else if (in.get(operand1).isNAC() || in.get(operand2).isNAC()) {
            return Value.getNAC();
        } else {
            return Value.getUndef();
        }

    }
}
