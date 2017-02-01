package com.r4intellij.inspections;

import org.jetbrains.annotations.NotNull;

public class RTypeCheckerInspectionTest extends RInspectionTest {


    @Override
    protected String getTestDataPath() {
        return super.getTestDataPath() + "/inspections/typing/";
    }


    public void testNoWarnings() {
        doTest("NoWarnings.R");
    }


    public void testWrongTypeParameter() {
        doTest("WrongTypeParameter.R");
    }


    public void testArgumentsMatching() {
        doTest("ArgumentsMatching.R");
    }


    public void testTripleDot() {
        doTest("TripleDot.R");
    }


    public void testRule() {
        doTest("Rule.R");
    }


    public void testGuessReturnFromBody() {
        doTest("GuessReturnFromBody.R");
    }


    public void testIfElseType() {
        doTest("IfElseType.R");
    }


    public void testOptional() {
        doTest("TestOptional.R");
    }


    public void _testList() {
        doTest("List.R");
    }


    public void _testBinary() {
        doTest("Binary.R");
    }


    public void _testSlice() {
        doTest("Slice.R");
    }


    public void testVector() {
        doTest("Vector.R");
    }


    public void testDefaultValue() {
        doTest("DefaultValue.R");
    }

    @NotNull
    @Override
    Class<? extends RInspection> getInspection() {
        return RTypeCheckerInspection.class;
    }
}
