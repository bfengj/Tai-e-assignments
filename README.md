# Tai-e-assignments

## Introduction

南京大学《软件分析》配套的实验，主要是记录做实验写的代码和自己的一些笔记。

## 作业 1：活跃变量分析和迭代求解器

基本算是入门的实验了，因为之前的编译原理课手写过一个编译器并且稍微做过一点后端的优化，所以对于代码有一定的了解，给的各种类基本都知道是干什么的，大概看一下就直接上手了。



总的来说就是实现6个函数，都是非常简单的实现，基本就是简单的初始化，`union`操作。

稍微复杂的要实现的那个大while循环，需要了解一个课上讲的点就是不一定非要从exit开始迭代，课上专门讲了为什么迭代一定会停止，因此从开头开始迭代一样最后是可以结束的，是不过可能要多几轮。

坑点大概是两个，初始化的时候虽然算法里只把IN初始化为空，实际上OUT同样要初始化为空。第二个坑点就是需要对`transferNode`中的类型专门做一下考虑：

```java
        def.ifPresent(lValue -> {
            if (lValue instanceof Var)
                copyOut.remove((Var) lValue);
        });
```

有的左值不能被转换成`Var`，因此需要判断。别的实验一就都很简单了。



此外有一说一，Github copilot插件确实好用，很久没有写过java了，对java的语法来停留在java8阶段，在写代码的过程中用Github copilot插件然后按tab就可以实现功能并且用一些我都没见过的新语法（例如上面的代码块），不得不说现在科技发展太快，各种语言的语法也在不断更新，如果不是常用的话，花时间去学真不如多用插件 tab自己生成，多见一次就会用了，确实究极提高生产力。

## 作业 2：常量传播和 Worklist 求解器

比第一次作业要难蛮多的，ppt的内容是要仔细看的，ppt和作业的介绍里面把可能涉及到的情况都涉及到了。

`newBoundaryFact`要注意的就是还要把函数的参数设置成`getNAC`，`newInitialFact`就是正常的空Fact，`meetInto`和作业一的差不多，需要检测`canHoldInt`。

`meetValue`的处理ppt中也有提到，稍微复杂一点的是`transferNode`。我的做法是首先判断是不是`DefinitionStmt`，不是则`return out.copyFrom(in);`。

判断了是`DefinitionStmt`，就要依次看左值和右值，左值也要`canHoldInt`，右值我建议通过`RValue rValue = ((DefinitionStmt<?, ?>) stmt).getRValue();`，然后开始判断类型，比如字面量，变量，`BinaryExp`等。

`evaluate`比较坑，作业介绍中提到任何东西`/`或者`%`0都会得到`undef`，一定要考虑到第一个操作数不一定是常量，然后再考虑`canHoldInt`，都考虑到就没事。



`initializeForward`和作业一差不多，`doSolveForward`的话需要用队列List`。别的基本没太多的坑点，只要仔细的阅读作业的要求和看ppt就ok。



## 作业 3：死代码检测

要把作业1和2的内容结合起来，去除不可达代码和无用赋值。一些基本的边界初始化和`doSolveBackward`就`doSolveForward`不说了。

不可达代码分为控制流不可达和分支不可达，控制流不可达这个通过遍历一遍CFG就可以知道哪些node没有被遍历到就说明这个node是不可达的，分支不可达我的做法是先遍历一遍所有的node，找到if或者switch，根据常量传播判断条件是否是常量，如果是则将不可达的那个分支的首个node加入到`deadCode`。

然后再遍历一遍`node`，先额外判断是这样一种情况：当前的`node`是死代码，且当前分支的前一个分支没有break，判断的条件是当前的`node`有非死代码的前驱而且有If或者switch语句的前驱（这两个前驱不是同一个前驱）。

如果不是这种情况，则先将当前`node`加入`deadCode`，如果存在非死代码的前驱就将这个`node`从`deadCode`中删除。

最后再遍历一遍`node`，处理无用赋值，这一步比较简单，记得要检查`hasNoSideEffect`。



这是我的原始代码：

```c

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
        Queue<Stmt> reachNodes = new LinkedList<>();
        while (!workList.isEmpty()) {
            Stmt node = workList.poll();
            if (!reachNodes.contains(node)){
                workList.addAll(cfg.getSuccsOf(node));
                reachNodes.add(node);
            }

        }

        for (Stmt node : nodes) {
            if (deadCode.contains(node)||cfg.isEntry(node)||cfg.isExit(node))
                continue;
            //去除控制流不可达代码
            if (!reachNodes.contains(node)) {
                deadCode.add(node);
            }else if (node instanceof If) {
                ConditionExp condition = ((If) node).getCondition();

                Value evaluateResult = ConstantPropagation.evaluate(condition, constants.getInFact(node));
                if (evaluateResult.isConstant()){
                    if(evaluateResult.getConstant()!=0){
                        cfg.getOutEdgesOf(node).forEach(edge -> {
                            if (edge.getKind() == Edge.Kind.IF_FALSE){
                                deadCode.add(edge.getTarget());
                            }
                        });
                    }else {
                        Stmt ifTarget = ((If) node).getTarget();
                        deadCode.add(ifTarget);
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
                            if (edge.getCaseValue()!=constVal){
                                deadCode.add(edge.getTarget());
                            }else {
                                isMatch = true;
                            }
                        }
                    }
                    for (Edge<Stmt> edge : outEdgesOf) {
                        if (edge.getKind() == Edge.Kind.SWITCH_DEFAULT){
                            if (isMatch){
                                deadCode.add(edge.getTarget());
                            }
                        }
                    }
                }
            }
        }


        for (Stmt node : nodes) {
            if (cfg.isEntry(node)||cfg.isExit(node))
                continue;

            Set<Stmt> predsOf = cfg.getPredsOf(node);
            if (deadCode.contains(node)){
                //检查有没有没写break的
                boolean hasNoDeadPred = false;
                boolean predIsIfOrSwitch = false;
                for (Stmt pred : predsOf) {
                    if (pred instanceof If || pred instanceof SwitchStmt){
                        predIsIfOrSwitch = true;
                    }else if (!deadCode.contains(pred)){
                        hasNoDeadPred = true;
                    }
                }
                if (hasNoDeadPred&&predIsIfOrSwitch){
                    deadCode.remove(node);
                }
                continue;
            }
            deadCode.add(node);
            for (Stmt pred : predsOf) {
                if (!deadCode.contains(pred)){
                    deadCode.remove(node);
                    break;
                }
            }
        }
        for (Stmt node : nodes){
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

            }
        }

        return deadCode;
    }

```

后来看了一眼旭神的代码才发现我写的太麻烦了， 完全可以在第一次遍历CFG的时候，将所有的工作都完成。仍然是遍历CFG的流程，但是维护一个`workList`和`liveNodes`，每次遍历到一个`node`时将其加入`liveNodes`，同时判断它是`assignstmt`还是`if`和`switch`，如果是这种情况的话依次处理，对于`if`和`switch`根据条件将可达的路径加入到`workList`，这样遍历CFG的时候就会遍历不到其他不可达的分支，最后再将所有不可达的node加入`deadCode`，非常的优雅。



## References

[Java静态分析框架Tai-e的简单使用 - Y4er的博客](https://y4er.com/posts/simple-use-of-the-java-static-analysis-framework-tai-e/)

[Z3ratu1/Tai-e-assignments: Tai-e assignments for static program analysis](https://github.com/Z3ratu1/Tai-e-assignments/tree/main)
