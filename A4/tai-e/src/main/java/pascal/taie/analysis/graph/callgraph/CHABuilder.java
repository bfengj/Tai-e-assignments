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

package pascal.taie.analysis.graph.callgraph;

import pascal.taie.World;
import pascal.taie.ir.proginfo.MethodRef;
import pascal.taie.ir.stmt.Invoke;
import pascal.taie.language.classes.ClassHierarchy;
import pascal.taie.language.classes.JClass;
import pascal.taie.language.classes.JMethod;
import pascal.taie.language.classes.Subsignature;

import java.util.*;


/**
 * Implementation of the CHA algorithm.
 */
class CHABuilder implements CGBuilder<Invoke, JMethod> {

    private ClassHierarchy hierarchy;

    @Override
    public CallGraph<Invoke, JMethod> build() {
        hierarchy = World.get().getClassHierarchy();
        return buildCallGraph(World.get().getMainMethod());
    }

    private CallGraph<Invoke, JMethod> buildCallGraph(JMethod entry) {
        DefaultCallGraph callGraph = new DefaultCallGraph();
        callGraph.addEntryMethod(entry);
        // TODO - finish me
        LinkedList<JMethod> workList = new LinkedList<>();
        workList.add(entry);

        while (!workList.isEmpty()){
            JMethod method = workList.poll();
            if (!callGraph.contains(method)){
                callGraph.addReachableMethod(method);
                callGraph.callSitesIn(method).forEach(callSite -> {
                    Set<JMethod> tMethods = resolve(callSite);
                    tMethods.forEach(tMethod -> {
                        callGraph.addEdge(new Edge<>(CallGraphs.getCallKind(callSite),callSite,tMethod));
                        workList.add(tMethod);
                    });
                });
            }
        }

        return callGraph;
    }

    /**
     * Resolves call targets (callees) of a call site via CHA.
     */
    private Set<JMethod> resolve(Invoke callSite) {
        // TODO - finish me
        Set<JMethod> tMethods = new HashSet<JMethod>();
        MethodRef methodRef = callSite.getMethodRef();
        Subsignature subsignature = methodRef.getSubsignature();
        JClass declaringClass = methodRef.getDeclaringClass();
        switch (CallGraphs.getCallKind(callSite)){
            case STATIC:
                JMethod staticMethod = declaringClass.getDeclaredMethod(subsignature);
                if (staticMethod!=null)
                    tMethods.add(staticMethod);
                break;
            case SPECIAL:
                JMethod specialMethod = dispatch(declaringClass,subsignature);
                if (specialMethod!=null)
                    tMethods.add(specialMethod);
                break;
            case VIRTUAL,INTERFACE:
                LinkedList<JClass> workList = new LinkedList<>();
                workList.add(declaringClass);
                while (!workList.isEmpty()){
                    JClass jClass = workList.poll();
                    JMethod virtualMethod = dispatch(jClass,subsignature);
                    if (virtualMethod!=null)
                        tMethods.add(virtualMethod);
                    if (jClass.isInterface()) {
                        workList.addAll(hierarchy.getDirectImplementorsOf(jClass));
                        workList.addAll(hierarchy.getDirectSubinterfacesOf(jClass));
                    }
                    else
                        workList.addAll(hierarchy.getDirectSubclassesOf(jClass));
                }
        }
        return tMethods;
    }

    /**
     * Looks up the target method based on given class and method subsignature.
     *
     * @return the dispatched target method, or null if no satisfying method
     * can be found.
     */
    private JMethod dispatch(JClass jclass, Subsignature subsignature) {
        // TODO - finish me
        JMethod declaredMethod = jclass.getDeclaredMethod(subsignature);
        if (declaredMethod==null|| declaredMethod.isAbstract()){
            JClass superClass = jclass.getSuperClass();
            if (superClass==null)
                return null;
            return dispatch(superClass,subsignature);
        }
        return declaredMethod;
    }
}
