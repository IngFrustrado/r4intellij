package com.r4intellij.typing;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.r4intellij.psi.api.*;
import com.r4intellij.typing.types.RFunctionType;
import com.r4intellij.typing.types.RType;
import com.r4intellij.typing.types.RUnknownType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ArgumentMatcher {

    private boolean firstArgInjected;


    public void setFirstArgInjected(boolean firstArgInjected) {
        this.firstArgInjected = firstArgInjected;
    }


    public void checkArguments(PsiReference referenceToFunction, List<RExpression> arguments) throws MatchingException {
        if (referenceToFunction != null) {
            PsiElement assignmentStatement = referenceToFunction.resolve();

            if (assignmentStatement != null && assignmentStatement instanceof RAssignmentStatement) {
                RAssignmentStatement assignment = (RAssignmentStatement) assignmentStatement;
                RPsiElement assignedValue = assignment.getAssignedValue();

                if (assignedValue != null && assignedValue instanceof RFunctionExpression) {
                    RFunctionExpression function = (RFunctionExpression) assignedValue;

                    checkTypes(arguments, function);
                }
            }
        }
    }


    private void checkTypes(List<RExpression> arguments, RFunctionExpression functionExpression) throws MatchingException {
        Map<RExpression, RParameter> matchedParams = new HashMap<RExpression, RParameter>();
        //        RType type = RTypeProvider.getType(functionExpression);
//        if (!RFunctionType.class.isInstance(type)) {
//            return; // TODO: fix me properly
//        }
//        RFunctionType functionType = (RFunctionType) type;
        RFunctionType functionType = new RFunctionType(functionExpression);

        matchArgs(arguments, matchedParams, new ArrayList<>(), functionType);

        // todo re-enable once type system is back
//        checkArgumentTypes(matchedParams, functionType);
    }


    public void matchArgs(List<RExpression> arguments,
                          Map<RExpression, RParameter> matchedParams,
                          List<RExpression> matchedByTripleDot,
                          // function type just needed to get optional parameter list
                          RFunctionType functionType) throws MatchingException {

        List<RParameter> formalArguments = functionType.getFormalArguments();
        List<RExpression> suppliedArguments = new ArrayList<>(arguments);

        exactMatching(formalArguments, suppliedArguments, matchedParams);
        partialMatching(formalArguments, suppliedArguments, matchedParams);
        positionalMatching(formalArguments, suppliedArguments, matchedParams, matchedByTripleDot, functionType, firstArgInjected);
    }


    private static void partialMatching(List<RParameter> formalArguments,
                                        List<RExpression> suppliedArguments,
                                        Map<RExpression, RParameter> matchedParams) throws MatchingException {
        matchParams(formalArguments, suppliedArguments, true, matchedParams);
    }


    private static void exactMatching(List<RParameter> formalArguments,
                                      List<RExpression> suppliedArguments,
                                      Map<RExpression, RParameter> matchedParams) throws MatchingException {
        matchParams(formalArguments, suppliedArguments, false, matchedParams);
    }


    private static void matchParams(List<RParameter> parameters, List<RExpression> arguments,
                                    boolean usePartialMatching,
                                    Map<RExpression, RParameter> matchedParams) throws MatchingException {
        List<RExpression> namedArguments = getNamedArguments(arguments);
        for (RExpression namedArg : namedArguments) {
            String name = namedArg.getName();
            List<RParameter> matches = getMatches(name, parameters, usePartialMatching);
            if (matches.size() > 1) {
                throw new MatchingException("formal argument " + name + " matched by multiply actual arguments");
            }
            if (matches.size() == 1) {
                matchedParams.put(namedArg, matches.get(0));
            }
        }

        for (Map.Entry<RExpression, RParameter> entry : matchedParams.entrySet()) {
            arguments.remove(entry.getKey());
            parameters.remove(entry.getValue());
        }
    }


    private static void positionalMatching(List<RParameter> formalArguments,
                                           List<RExpression> suppliedArguments,
                                           Map<RExpression, RParameter> matchedParams,
                                           List<RExpression> matchedByTripleDot,
                                           RFunctionType functionType,
                                           boolean isPipeInjected) throws MatchingException {

        List<RExpression> matchedArguments = new ArrayList<>();
        List<RParameter> matchedParameter = new ArrayList<>();
        int suppliedSize = suppliedArguments.size();
        boolean wasTripleDot = false;

        if (isPipeInjected) {
            formalArguments = formalArguments.subList(1, formalArguments.size());
        }

        for (int i = 0; i < formalArguments.size(); i++) {
            RParameter param = formalArguments.get(i);
            if (param.getText().equals("...")) {
                wasTripleDot = true;
                break;
            }
            if (i >= suppliedSize) {
                break;
            }
            RExpression arg = suppliedArguments.get(i);
            if (arg instanceof RAssignmentStatement && ((RAssignmentStatement) arg).isEqual()) {
                String argName = ((RAssignmentStatement) arg).getAssignee().getText();
                if (!argName.equals(param.getName())) {
                    wasTripleDot = true;
                    break;
                }
            }
            matchedArguments.add(arg);
            matchedParameter.add(param);
            matchedParams.put(arg, param);
        }

        formalArguments.removeAll(matchedParameter);
        suppliedArguments.removeAll(matchedArguments);

        if (wasTripleDot) {
            matchedByTripleDot.addAll(suppliedArguments);
            suppliedArguments.clear();
        }

        List<RParameter> unmatched = new ArrayList<RParameter>();
        for (RParameter parameter : formalArguments) {
            if (parameter.getText().equals("...")) {
                continue;
            }
            RExpression defaultValue = parameter.getExpression();
            if (defaultValue != null) {
                matchedParams.put(defaultValue, parameter);
            } else {
                unmatched.add(parameter);
            }
        }

        if (!unmatched.isEmpty()) {
            unmatched.removeAll(functionType.getOptionalParams());
            if (!unmatched.isEmpty()) {
                throw new MatchingException(generateMissingArgErrorMessage(unmatched, 0));
            }
        }

        if (!suppliedArguments.isEmpty()) {
            checkUnmatchedArgs(suppliedArguments);
        }
    }


    private static void checkArgumentTypes(Map<RExpression, RParameter> matchedParams, RFunctionType functionType) throws MatchingException {
        for (Map.Entry<RExpression, RParameter> entry : matchedParams.entrySet()) {
            RParameter parameter = entry.getValue();
            RType paramType = RTypeProvider.getParamType(parameter, functionType);
            if (paramType == null || paramType instanceof RUnknownType) {
                continue;
            }

            boolean isOptional = functionType.isOptional(parameter.getName());
            RType argType = RTypeProvider.getType(entry.getKey());

            if (argType != null && !RUnknownType.class.isInstance(argType)) {
                if (!TypeUtil.matchTypes(paramType, argType, isOptional)) {
                    throw new MatchingException(parameter.getText() + " expected to be of type " + paramType +
                            ", found type " + argType);
                }
            }
        }
    }


    private static String generateMissingArgErrorMessage(List<RParameter> parameters, int i) {
        String noDefaultMessage = " missing, with no default";
        if (i == parameters.size() - 1) {
            return "argument \'" + parameters.get(i).getText() + "\' is" + noDefaultMessage;
        }
        StringBuilder stringBuilder = new StringBuilder("arguments ");
        while (i < parameters.size()) {
            stringBuilder.append("\"").append(parameters.get(i).getText()).append("\"").append(", ");
            i++;
        }
        int length = stringBuilder.length();
        return stringBuilder.delete(length - 2, length - 1).append("are").append(noDefaultMessage).toString();
    }


    private static List<RParameter> getMatches(String name, List<RParameter> parameters, boolean usePartial) {
        List<RParameter> matches = new ArrayList<RParameter>();
        for (RParameter param : parameters) {
            if (usePartial && param.getText().equals("...")) {
                return matches;
            }
            String paramName = param.getName();
            if (paramName != null) {
                if (usePartial) {
                    if (paramName.startsWith(name)) {
                        matches.add(param);
                    }
                } else {
                    if (paramName.equals(name)) {
                        matches.add(param);
                    }
                }
            }
        }
        return matches;
    }


    private static List<RExpression> getNamedArguments(List<RExpression> arguments) {
        List<RExpression> namedArgs = new ArrayList<RExpression>();
        for (RExpression arg : arguments) {
            if (arg instanceof RAssignmentStatement && arg.getName() != null) {
                namedArgs.add(arg);
            }
        }
        return namedArgs;
    }


    private static void checkUnmatchedArgs(List<RExpression> arguments) throws MatchingException {
        int size = arguments.size();
        if (size == 1) {
            throw new MatchingException("unused argument " + arguments.get(0).getText());
        }
        if (size > 0) {
            StringBuilder errorMessage = new StringBuilder("unused arguments: ");
            for (RExpression expression : arguments) {
                errorMessage.append(expression.getText()).append(", ");
            }
            int lastComma = errorMessage.lastIndexOf(",");
            throw new MatchingException(errorMessage.delete(lastComma, lastComma + 1).toString());
        }
    }


}