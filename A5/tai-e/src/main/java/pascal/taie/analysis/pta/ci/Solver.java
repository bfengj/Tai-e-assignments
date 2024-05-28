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

package pascal.taie.analysis.pta.ci;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pascal.taie.World;
import pascal.taie.analysis.graph.callgraph.CallGraphs;
import pascal.taie.analysis.graph.callgraph.DefaultCallGraph;
import pascal.taie.analysis.graph.callgraph.Edge;
import pascal.taie.analysis.pta.core.heap.HeapModel;
import pascal.taie.analysis.pta.core.heap.Obj;
import pascal.taie.ir.exp.NewExp;
import pascal.taie.ir.exp.Var;
import pascal.taie.ir.stmt.*;
import pascal.taie.language.classes.ClassHierarchy;
import pascal.taie.language.classes.JField;
import pascal.taie.language.classes.JMethod;
import pascal.taie.language.type.Type;

import java.util.List;
import java.util.Set;

class Solver {

    private static final Logger logger = LogManager.getLogger(Solver.class);

    private final HeapModel heapModel;

    private DefaultCallGraph callGraph;

    private PointerFlowGraph pointerFlowGraph;

    private WorkList workList;

    private StmtProcessor stmtProcessor;

    private ClassHierarchy hierarchy;

    Solver(HeapModel heapModel) {
        this.heapModel = heapModel;
    }

    /**
     * Runs pointer analysis algorithm.
     */
    void solve() {
        initialize();
        analyze();
    }

    /**
     * Initializes pointer analysis.
     */
    private void initialize() {
        workList = new WorkList();
        pointerFlowGraph = new PointerFlowGraph();
        callGraph = new DefaultCallGraph();
        stmtProcessor = new StmtProcessor();
        hierarchy = World.get().getClassHierarchy();
        // initialize main method
        JMethod main = World.get().getMainMethod();
        callGraph.addEntryMethod(main);
        addReachable(main);
    }

    /**
     * Processes new reachable method.
     */
    private void addReachable(JMethod method) {
        // TODO - finish me
        // add reachable method to call graph
        if (callGraph.addReachableMethod(method)) {
            //Set of reachable statements
            List<Stmt> stmts = method.getIR().getStmts();
            //add stmts to Set of reachable statements
            for (Stmt stmt : stmts) {
                stmt.accept(stmtProcessor);
            }
        }

    }

    /**
     * Processes statements in new reachable methods.
     */
    private class StmtProcessor implements StmtVisitor<Void> {
        // TODO - if you choose to implement addReachable()
        //  via visitor pattern, then finish me
        public Void visit(New stmt) {
            ;
            Obj obj = heapModel.getObj(stmt);
            VarPtr varPtr = pointerFlowGraph.getVarPtr(stmt.getLValue());
            PointsToSet pts = new PointsToSet(obj);
            workList.addEntry(varPtr, pts);
            return null;
        }

        public Void visit(Copy stmt) {
            addPFGEdge(pointerFlowGraph.getVarPtr(stmt.getRValue()),pointerFlowGraph.getVarPtr(stmt.getLValue()));
            return null;
        }

        public Void visit(LoadField stmt) {
            JField field = stmt.getFieldRef().resolve();
            if (field.isStatic()) {

                StaticField staticField = pointerFlowGraph.getStaticField(field);
                VarPtr varPtr = pointerFlowGraph.getVarPtr(stmt.getLValue());
                addPFGEdge(staticField, varPtr);
            }
            return null;
        }

        public Void visit(StoreField stmt) {
            JField field = stmt.getFieldRef().resolve();
            if (field.isStatic()) {
                StaticField staticField = pointerFlowGraph.getStaticField(field);
                VarPtr varPtr = pointerFlowGraph.getVarPtr(stmt.getRValue());
                addPFGEdge(varPtr, staticField);
            }
            return null;
        }

        public Void visit(Invoke stmt) {
            //处理静态调用
            if (stmt.isStatic()) {
                JMethod callee = resolveCallee(null, stmt);
                if (callee != null) {
                    addReachable(callee);
                    callee.getIR().getParams().forEach(param -> {
                        addPFGEdge(pointerFlowGraph.getVarPtr(stmt.getInvokeExp().getArgs().get(param.getIndex())), pointerFlowGraph.getVarPtr(param));
                    });
                    callee.getIR().getReturnVars().forEach(retVar -> {
                        if (stmt.getResult() != null){
                            addPFGEdge(pointerFlowGraph.getVarPtr(retVar), pointerFlowGraph.getVarPtr(stmt.getResult()));
                        }
                    });
                }
            }
            return null;
        }
    }

    /**
     * Adds an edge "source -> target" to the PFG.
     */
    private void addPFGEdge(Pointer source, Pointer target) {
        // TODO - finish me
        if (pointerFlowGraph.addEdge(source, target))
            if (!source.getPointsToSet().isEmpty())
                workList.addEntry(target, source.getPointsToSet());

    }

    /**
     * Processes work-list entries until the work-list is empty.
     */
    private void analyze() {
        // TODO - finish me
        while (!workList.isEmpty()) {

            WorkList.Entry entry = workList.pollEntry();
            Pointer pointer = entry.pointer();
            PointsToSet pointsToSet = entry.pointsToSet();

            PointsToSet diff = propagate(pointer, pointsToSet);
            if (pointer instanceof VarPtr) {
                Var var = ((VarPtr) pointer).getVar();
                //null?
                for (Obj obj : diff) {
                    var.getLoadFields().forEach(loadField -> {
                        addPFGEdge(pointerFlowGraph.getInstanceField(obj, loadField.getFieldRef().resolve()), pointerFlowGraph.getVarPtr(loadField.getLValue()));
                    });
                    var.getStoreFields().forEach(storeField -> {
                        addPFGEdge(pointerFlowGraph.getVarPtr(storeField.getRValue()), pointerFlowGraph.getInstanceField(obj, storeField.getFieldRef().resolve()));
                    });
                    var.getLoadArrays().forEach(loadArray -> {
                        addPFGEdge(pointerFlowGraph.getArrayIndex(obj), pointerFlowGraph.getVarPtr(loadArray.getLValue()));
                    });
                    var.getStoreArrays().forEach(storeArray -> {
                        addPFGEdge(pointerFlowGraph.getVarPtr(storeArray.getRValue()), pointerFlowGraph.getArrayIndex(obj));
                    });
                    processCall(var, obj);
                }
            }
        }

    }

    /**
     * Propagates pointsToSet to pt(pointer) and its PFG successors,
     * returns the difference set of pointsToSet and pt(pointer).
     */
    private PointsToSet propagate(Pointer pointer, PointsToSet pointsToSet) {
        // TODO - finish me
        if (!pointsToSet.isEmpty()) {
            PointsToSet ptn = pointer.getPointsToSet();
            //计算pointsToSet和ptn的差集，并把pointsToSet加入ptn
            PointsToSet diff = new PointsToSet();
            pointsToSet.objects().forEach(obj -> {
                if (ptn.addObject(obj)) {
                    diff.addObject(obj);
                }
            });
            if (!diff.isEmpty()){
                pointerFlowGraph.getSuccsOf(pointer).forEach(succ -> {
                    workList.addEntry(succ, pointsToSet);
                });
            }
            return diff;

        }
        return null;
    }

    /**
     * Processes instance calls when points-to set of the receiver variable changes.
     *
     * @param var  the variable that holds receiver objects
     * @param recv a new discovered object pointed by the variable.
     */
    private void processCall(Var var, Obj recv) {
        // TODO - finish me
        var.getInvokes().forEach(invoke -> {
            JMethod callee = resolveCallee(recv, invoke);
            if (callee != null) {
                //pointerFlowGraph.get
                workList.addEntry(pointerFlowGraph.getVarPtr(callee.getIR().getThis()), new PointsToSet(recv));
                if (callGraph.addEdge(new Edge<>(CallGraphs.getCallKind(invoke), invoke, callee))) {
                    addReachable(callee);
                    callee.getIR().getParams().forEach(param -> {
                        addPFGEdge(pointerFlowGraph.getVarPtr(invoke.getInvokeExp().getArgs().get(param.getIndex()-1)), pointerFlowGraph.getVarPtr(param));
                    });
                    callee.getIR().getReturnVars().forEach(retVar -> {
                        if (invoke.getResult() != null){
                            addPFGEdge(pointerFlowGraph.getVarPtr(retVar), pointerFlowGraph.getVarPtr(invoke.getResult()));
                        }
                    });

                }

            }
        });
    }

    /**
     * Resolves the callee of a call site with the receiver object.
     *
     * @param recv     the receiver object of the method call. If the callSite
     *                 is static, this parameter is ignored (i.e., can be null).
     * @param callSite the call site to be resolved.
     * @return the resolved callee.
     */
    private JMethod resolveCallee(Obj recv, Invoke callSite) {
        Type type = recv != null ? recv.getType() : null;
        return CallGraphs.resolveCallee(type, callSite);
    }

    CIPTAResult getResult() {
        return new CIPTAResult(pointerFlowGraph, callGraph);
    }
}
