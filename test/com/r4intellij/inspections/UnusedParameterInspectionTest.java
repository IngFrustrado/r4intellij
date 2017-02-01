package com.r4intellij.inspections;

import org.jetbrains.annotations.NotNull;

public class UnusedParameterInspectionTest extends RInspectionTest {

    @Override
    protected String getTestDataPath() {
        return super.getTestDataPath() + "/inspections/" + getTestName(true);
    }


    public void testUnusedParameterInspection() {
        doTest(getTestName(true) + ".R");
    }


    @NotNull
    @Override
    Class<? extends RInspection> getInspection() {
        return UnusedParameterInspection.class;
    }
}
