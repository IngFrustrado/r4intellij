package com.r4intellij.inspections;

import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import org.jetbrains.annotations.NotNull;

import static com.r4intellij.inspections.UnresolvedReferenceInspectionTest.createLibraryFromPckgNames;

public class UnusedVariableInspectionTest extends RInspectionTest {

    // TODO test annotation options to whitelist symobls and functions

    // False negative tests: unused annotation should be present


    public void testUnusedVariable() {
        doExprTest("<warning descr=\"Variable 'a' is never used\">a</warning> = 3");
    }


    public void testUnusedVariableInFunExpr() {
        doExprTest(readTestDataFile());
    }


    public void testUnusedAnonymousFunExpr() {
        doExprTest("function(x)x");
    }


    public void testUnusedFunction() {
        doExprTest("<warning descr=\"Variable 'myfun' is never used\">myfun</warning> = function(x)x");
    }


    /**
     * Make sure to not mistake overriden functions symbols.
     */
    public void testOverrideFunWithSymbol() {
        // c should b be tagged as unused
        // c() should not resolve to c but to base::c
        CodeInsightTestFixture fixture = doExprTest("<warning =\"Variable 'c' is never used\">c</warning> = 1; c('foo', 'bar')");

        //todo
//        cAssign = fixture.getFile().
//        funCall = ...
//        funcCall.resolve() != cAssign
    }


    // False positive tests: Unused annotation might be present (or was by regression) but should not


    public void testOutsideBlockUsage() {
        // since (in contrary to java) is legal in R; scoping works different somehow
        doExprTest("{ a = 3; }; a");
    }


    /**
     * array modifications are regular usage, so a should be flagged as used; 1+a has printing as side effect.
     */
    public void testArrayModification() {
        doExprTest("a = 1:5; a[4] = 3; 1 + a");
    }


    /**
     * This is weired r magic. Make sure that don't tag <code>a</code>  nor <code>rownames(a)</code> as unused.
     * See https://cran.r-project.org/doc/manuals/r-release/R-lang.html#Attributes
     * <p>
     * TODO also ensure that rownames resolves to the correct <code>`rownames<-`</code>. TBD: Where would it matter?
     */
    public void testDedicatedAccessorFunction() {
        createLibraryFromPckgNames(myFixture, "base");

        doExprTest("a = data.frame(col1=1:3, col2=NA); rownames(a) = c('foo', 'bar');  a");

        // todo maybe we could/should ensure that foo is tagged as unused in
        // foo = iris; names(foo) <- 1:3
    }




    public void testUsageOutsideIfElse() {
        // since (in contrary to java) is legal in R; scoping works different somehow
        doExprTest("if(T){ a = 3; }else{ b = 2; }; a ; b");
    }


    public void testDonFlagReturn() {
        assertAllUsed("function(){ if(T){ head(iris); return(1) };  return(2); { return(3) }; return(4) }()");
    }


    /**
     * Last expression of function expression should be flagged because its return value as side effect
     */
    public void testDontFlagLastFunExprStatement() {
        assertAllUsed("function(){ a = 3 }()");
    }


    /**
     * The last statement of a block is its return value in R.
     */
    public void testDontFlagLastBlockExprStatement() {
        assertAllUsed("{ foo= 3; foo }");
    }

    // todo finish this --> NoSideEffectsInspection
//    public void testFlagNoSideEffectExprInFunction(){
//        assertAllUsed("myFun = function(){ <warning descr=\"Expression '1+1' has no side effects\">1+1<\warning>; 3 }; myFun()");
//    }
//
//    public void testFlagNoSideEffectExprInBlock(){
//        assertAllUsed("{ <warning descr=\"Expression '1+1' has no side effects\">1+1<\warning>; 3 }");
//    }


    public void testFlagLastBlockIfNotAssignedOrReturn() {
        // a bit more artificial but still valid
        assertAllUsed("myfun = function(){ head(iris); { a = 3} }; myfun()");

        // bar printing is usage side-effect -> all used
        assertAllUsed("bar = { foo = 3}; bar");

        // no assignment no side-effect --> flag unused
        doExprTest("{ <warning descr=\"Variable 'foo' is never used\">foo</warning> = 3}");
    }


    public void testDontFlagExprInTerminalIfElse() { // this already not really realistic, but for sake of completeness
        assertAllUsed("function(){ head(iris); if(T){ a = 3} }()");
    }


    public void testDontFlagFunctionArgUsedAsUnnamedArg() {
        assertAllUsed("function(usedArg) head(usedArg)");
    }


    public void testDontFlagFunctionArgUsedAsNamedArg() {
        // todo this will fail because it's the last statment in the file --> Disable for some unit-tests
        assertAllUsed("function(usedArg) head(x=usedArg)");
    }


    // this should if all be optional
//    public void dontFlagLastExprInFile () {
//        doExprTest("a = 3");
//    }


    @NotNull
    @Override
    Class<? extends RInspection> getInspection() {
        return UnusedVariableInspection.class;
    }
}
