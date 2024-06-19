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

package pascal.taie.analysis.pta.cs;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pascal.taie.World;
import pascal.taie.analysis.graph.callgraph.CallGraphs;
import pascal.taie.analysis.graph.callgraph.CallKind;
import pascal.taie.analysis.graph.callgraph.Edge;
import pascal.taie.analysis.pta.PointerAnalysisResult;
import pascal.taie.analysis.pta.PointerAnalysisResultImpl;
import pascal.taie.analysis.pta.core.cs.CSCallGraph;
import pascal.taie.analysis.pta.core.cs.context.Context;
import pascal.taie.analysis.pta.core.cs.element.ArrayIndex;
import pascal.taie.analysis.pta.core.cs.element.CSCallSite;
import pascal.taie.analysis.pta.core.cs.element.CSManager;
import pascal.taie.analysis.pta.core.cs.element.CSMethod;
import pascal.taie.analysis.pta.core.cs.element.CSObj;
import pascal.taie.analysis.pta.core.cs.element.CSVar;
import pascal.taie.analysis.pta.core.cs.element.InstanceField;
import pascal.taie.analysis.pta.core.cs.element.MapBasedCSManager;
import pascal.taie.analysis.pta.core.cs.element.Pointer;
import pascal.taie.analysis.pta.core.cs.element.StaticField;
import pascal.taie.analysis.pta.core.cs.selector.ContextSelector;
import pascal.taie.analysis.pta.core.heap.HeapModel;
import pascal.taie.analysis.pta.core.heap.Obj;
import pascal.taie.analysis.pta.pts.PointsToSet;
import pascal.taie.analysis.pta.pts.PointsToSetFactory;
import pascal.taie.config.AnalysisOptions;
import pascal.taie.ir.exp.InvokeExp;
import pascal.taie.ir.exp.Var;
import pascal.taie.ir.stmt.*;
import pascal.taie.language.classes.JField;
import pascal.taie.language.classes.JMethod;
import pascal.taie.language.type.Type;

import java.util.List;

class Solver {

    private static final Logger logger = LogManager.getLogger(Solver.class);

    private final AnalysisOptions options;

    private final HeapModel heapModel;

    private final ContextSelector contextSelector;

    private CSManager csManager;

    private CSCallGraph callGraph;

    private PointerFlowGraph pointerFlowGraph;

    private WorkList workList;

    private PointerAnalysisResult result;

    Solver(AnalysisOptions options, HeapModel heapModel,
           ContextSelector contextSelector) {
        this.options = options;
        this.heapModel = heapModel;
        this.contextSelector = contextSelector;
    }

    void solve() {
        initialize();
        analyze();
    }

    private void initialize() {
        csManager = new MapBasedCSManager();
        callGraph = new CSCallGraph(csManager);
        pointerFlowGraph = new PointerFlowGraph();
        workList = new WorkList();
        // process program entry, i.e., main method
        Context defContext = contextSelector.getEmptyContext();
        JMethod main = World.get().getMainMethod();
        CSMethod csMethod = csManager.getCSMethod(defContext, main);
        callGraph.addEntryMethod(csMethod);
        addReachable(csMethod);
    }

    /**
     * Processes new reachable context-sensitive method.
     */
    private void addReachable(CSMethod csMethod) {
        // TODO - finish me
        // add reachable method to call graph
        if (callGraph.addReachableMethod(csMethod)) {
            //Set of reachable statements
            List<Stmt> stmts = csMethod.getMethod().getIR().getStmts();
            StmtProcessor stmtProcessor = new StmtProcessor(csMethod);
            //add stmts to Set of reachable statements
            for (Stmt stmt : stmts) {
                stmt.accept(stmtProcessor);
            }
        }
    }

    /**
     * Processes the statements in context-sensitive new reachable methods.
     */
    private class StmtProcessor implements StmtVisitor<Void> {

        private final CSMethod csMethod;

        private final Context context;

        private StmtProcessor(CSMethod csMethod) {
            this.csMethod = csMethod;
            this.context = csMethod.getContext();
        }

        // TODO - if you choose to implement addReachable()
        //  via visitor pattern, then finish me
        public Void visit(New stmt) {
            ;
            Obj obj = heapModel.getObj(stmt);
            Context ct = contextSelector.selectHeapContext(csMethod, obj);
            CSObj csObj = csManager.getCSObj(ct, obj);
            CSVar csVar = csManager.getCSVar(context, stmt.getLValue());

            PointsToSet pts = PointsToSetFactory.make(csObj);
            workList.addEntry(csVar, pts);
            return null;
        }

        public Void visit(Copy stmt) {
            addPFGEdge(csManager.getCSVar(context, stmt.getRValue()), csManager.getCSVar(context, stmt.getLValue()));
            return null;
        }

        public Void visit(LoadField stmt) {
            JField field = stmt.getFieldRef().resolve();
            if (field.isStatic()) {
                StaticField staticField = csManager.getStaticField(field);
                CSVar csVar = csManager.getCSVar(context, stmt.getLValue());
                addPFGEdge(staticField, csVar);
            }
            return null;
        }

        public Void visit(StoreField stmt) {
            JField field = stmt.getFieldRef().resolve();
            if (field.isStatic()) {

                StaticField staticField = csManager.getStaticField(field);
                CSVar csVar = csManager.getCSVar(context, stmt.getRValue());
                addPFGEdge(csVar, staticField);
            }
            return null;
        }

        public Void visit(Invoke stmt) {
            //处理静态调用
            if (stmt.isStatic()) {
                JMethod callee = resolveCallee(null, stmt);
                if (callee != null) {
                    Context ct = contextSelector.selectContext(csManager.getCSCallSite(context, stmt), callee);
                    addReachable(csManager.getCSMethod(ct, callee));
                    callee.getIR().getParams().forEach(param -> {
                        addPFGEdge(csManager.getCSVar(context,stmt.getInvokeExp().getArgs().get(param.getIndex())), csManager.getCSVar(ct,param));
                    });
                    callee.getIR().getReturnVars().forEach(retVar -> {
                        if (stmt.getResult() != null){
                            addPFGEdge(csManager.getCSVar(ct,retVar), csManager.getCSVar(context,stmt.getResult()));
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
            if (pointer instanceof CSVar) {
                Var var = ((CSVar) pointer).getVar();
                Context context = ((CSVar) pointer).getContext();
                for (CSObj obj : diff) {
                    var.getLoadFields().forEach(loadField -> {

                        addPFGEdge(csManager.getInstanceField(obj, loadField.getFieldRef().resolve()), csManager.getCSVar(context, loadField.getLValue()));
                    });
                    var.getStoreFields().forEach(storeField -> {
                        addPFGEdge(csManager.getCSVar(context, storeField.getRValue()), csManager.getInstanceField(obj, storeField.getFieldRef().resolve()));
                    });
                    var.getLoadArrays().forEach(loadArray -> {
                        addPFGEdge(csManager.getArrayIndex(obj), csManager.getCSVar(context, loadArray.getLValue()));
                    });
                    var.getStoreArrays().forEach(storeArray -> {
                        addPFGEdge(csManager.getCSVar(context, storeArray.getRValue()), csManager.getArrayIndex(obj));
                    });
                    processCall((CSVar) pointer, obj);
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
            PointsToSet diff = PointsToSetFactory.make();
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
     * @param recv    the receiver variable
     * @param recvObj set of new discovered objects pointed by the variable.
     */
    private void processCall(CSVar recv, CSObj recvObj) {
        // TODO - finish me
        recv.getVar().getInvokes().forEach(invoke -> {
            JMethod callee = resolveCallee(recvObj, invoke);
            if (callee != null) {
                //pointerFlowGraph.get
                Context ct = contextSelector.selectContext(csManager.getCSCallSite(recv.getContext(), invoke),recvObj, callee);
                workList.addEntry(csManager.getCSVar(ct,callee.getIR().getThis() ), PointsToSetFactory.make(recvObj));

                if (callGraph.addEdge(new Edge<>(CallGraphs.getCallKind(invoke), csManager.getCSCallSite(recv.getContext(),invoke), csManager.getCSMethod(ct,callee)))) {
                    addReachable(csManager.getCSMethod(ct,callee));
                    callee.getIR().getParams().forEach(param -> {
                        addPFGEdge(csManager.getCSVar(recv.getContext(),invoke.getInvokeExp().getArgs().get(param.getIndex()-1)), csManager.getCSVar(ct,param));
                    });
                    callee.getIR().getReturnVars().forEach(retVar -> {
                        if (invoke.getResult() != null){
                            addPFGEdge(csManager.getCSVar(ct,retVar), csManager.getCSVar(recv.getContext(),invoke.getResult()));

                        }
                    });

                }

            }
        });
    }

    /**
     * Resolves the callee of a call site with the receiver object.
     *
     * @param recv the receiver object of the method call. If the callSite
     *             is static, this parameter is ignored (i.e., can be null).
     * @param callSite the call site to be resolved.
     * @return the resolved callee.
     */
    private JMethod resolveCallee(CSObj recv, Invoke callSite) {
        Type type = recv != null ? recv.getObject().getType() : null;
        return CallGraphs.resolveCallee(type, callSite);
    }

    PointerAnalysisResult getResult() {
        if (result == null) {
            result = new PointerAnalysisResultImpl(csManager, callGraph);
        }
        return result;
    }
}
