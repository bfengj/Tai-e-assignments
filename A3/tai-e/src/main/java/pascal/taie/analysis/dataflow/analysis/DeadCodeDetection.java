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

package pascal.taie.analysis.dataflow.analysis;

import pascal.taie.analysis.MethodAnalysis;
import pascal.taie.analysis.dataflow.analysis.constprop.CPFact;
import pascal.taie.analysis.dataflow.analysis.constprop.ConstantPropagation;
import pascal.taie.analysis.dataflow.analysis.constprop.Value;
import pascal.taie.analysis.dataflow.fact.DataflowResult;
import pascal.taie.analysis.dataflow.fact.SetFact;
import pascal.taie.analysis.graph.cfg.CFG;
import pascal.taie.analysis.graph.cfg.CFGBuilder;
import pascal.taie.analysis.graph.cfg.Edge;
import pascal.taie.config.AnalysisConfig;
import pascal.taie.ir.IR;
import pascal.taie.ir.exp.*;
import pascal.taie.ir.stmt.*;
import soot.jimple.GotoStmt;

import java.util.*;

public class DeadCodeDetection extends MethodAnalysis {

    public static final String ID = "deadcode";

    public DeadCodeDetection(AnalysisConfig config) {
        super(config);
    }


    @Override
    public Set<Stmt> analyze(IR ir) {
        // obtain CFG
        CFG<Stmt> cfg = ir.getResult(CFGBuilder.ID);
        // obtain result of constant propagation
        DataflowResult<Stmt, CPFact> constants =
                ir.getResult(ConstantPropagation.ID);
        // obtain result of live variable analysis
        DataflowResult<Stmt, SetFact<Var>> liveVars =
                ir.getResult(LiveVariableAnalysis.ID);
        // keep statements (dead code) sorted in the resulting set


        Set<Stmt> deadCode = new TreeSet<>(Comparator.comparing(Stmt::getIndex));
        // TODO - finish me
        // Your task is to recognize dead code in ir and add it to deadCode
        Set<Stmt> nodes = cfg.getNodes();

        Queue<Stmt> workList = new LinkedList<>();
        workList.add(cfg.getEntry());
        workList.add(cfg.getExit());
        Queue<Stmt> liveNodes = new LinkedList<>();
        while (!workList.isEmpty()) {
            boolean addAll = true;
            Stmt node = workList.poll();
            if (liveNodes.contains(node))
                continue;
            else {
                liveNodes.add(node);
            }
            if (node instanceof AssignStmt) {
                LValue lValue = ((AssignStmt<?, ?>) node).getLValue();
                RValue rValue = ((AssignStmt<?, ?>) node).getRValue();
                if (lValue instanceof Var) {
                    Var var = (Var) lValue;
                    if (!liveVars.getOutFact(node).contains(var)) {
                        if (hasNoSideEffect(rValue))
                            deadCode.add(node);
                    }
                }

            }else if (node instanceof If) {
                ConditionExp condition = ((If) node).getCondition();

                Value evaluateResult = ConstantPropagation.evaluate(condition, constants.getInFact(node));
                if (evaluateResult.isConstant()){
                    if(evaluateResult.getConstant()==0){
                        Set<Edge<Stmt>> outEdgesOf = cfg.getOutEdgesOf(node);
                        for (Edge<Stmt> edge : outEdgesOf) {
                            if (edge.getKind() == Edge.Kind.IF_FALSE){
                                workList.add(edge.getTarget());
                                addAll = false;
                            }
                        }

                    }else {
                        Stmt ifTarget = ((If) node).getTarget();
                        workList.add(ifTarget);
                        addAll = false;
                    }
                }
            } else if (node instanceof SwitchStmt) {
                Value value = constants.getInFact(node).get(((SwitchStmt) node).getVar());
                if (value.isConstant()){
                    int constVal = value.getConstant();
                    //把case遍历完再看default，因为java中default位置可能不一定
                    boolean isMatch = false;
                    Set<Edge<Stmt>> outEdgesOf = cfg.getOutEdgesOf(node);
                    for (Edge<Stmt> edge : outEdgesOf) {
                        if (edge.getKind() == Edge.Kind.SWITCH_CASE){
                            if (edge.getCaseValue()==constVal){
                                workList.add(edge.getTarget());
                                isMatch = true;
                                addAll = false;
                            }
                        }
                    }
                    for (Edge<Stmt> edge : outEdgesOf) {
                        if (edge.getKind() == Edge.Kind.SWITCH_DEFAULT){
                            if (!isMatch){
                                workList.add(edge.getTarget());
                                addAll = false;
                            }
                        }
                    }
                }
            }
            if (addAll){
                workList.addAll(cfg.getSuccsOf(node));
            }
        }



        for (Stmt node : nodes) {
            if (!liveNodes.contains(node)) {
                deadCode.add(node);
            }
        }

        return deadCode;
    }

    /**
     * @return true if given RValue has no side effect, otherwise false.
     */
    private static boolean hasNoSideEffect(RValue rvalue) {
        // new expression modifies the heap
        if (rvalue instanceof NewExp ||
                // cast may trigger ClassCastException
                rvalue instanceof CastExp ||
                // static field access may trigger class initialization
                // instance field access may trigger NPE
                rvalue instanceof FieldAccess ||
                // array access may trigger NPE
                rvalue instanceof ArrayAccess) {
            return false;
        }
        if (rvalue instanceof ArithmeticExp) {
            ArithmeticExp.Op op = ((ArithmeticExp) rvalue).getOperator();
            // may trigger DivideByZeroException
            return op != ArithmeticExp.Op.DIV && op != ArithmeticExp.Op.REM;
        }
        return true;
    }
}
