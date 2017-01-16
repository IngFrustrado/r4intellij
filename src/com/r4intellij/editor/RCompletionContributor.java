/*
 * Copyright 2011 Holger Brandl
 *
 * This code is licensed under BSD. For details see
 * http://www.opensource.org/licenses/bsd-license.php
 */

package com.r4intellij.editor;

import com.google.common.collect.Lists;
import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.cache.impl.id.IdTableBuilding;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.webcore.packaging.RepoPackage;
import com.r4intellij.packages.RPackagesUtil;
import com.r4intellij.psi.api.RCallExpression;
import org.jetbrains.annotations.Nullable;

import java.util.*;


/**
 * This would be our hook into any special handling for code completion.
 */
public class RCompletionContributor extends CompletionContributor {

    @Override
    public void fillCompletionVariants(final CompletionParameters parameters, final CompletionResultSet result) {
//        if (parameters.getCompletionType() == CompletionType.BASIC && shouldPerformWordCompletion(parameters)) {
        addWordCompletionVariants(result, parameters, Collections.<String>emptySet());
//        }
    }

    // see http://www.jetbrains.org/intellij/sdk/docs/tutorials/custom_language_support/completion_contributor.html


    private static void addWordCompletionVariants(CompletionResultSet result, CompletionParameters parameters, Set<String> excludes) {
        addWordFromDocument(result, parameters, excludes);

        // auto-completion for require and libary
        PsiElement insertedElement = parameters.getPosition();

        RCallExpression pp = PsiTreeUtil.getContextOfType(insertedElement, RCallExpression.class);


        boolean isPackageContext = pp != null && Lists.newArrayList("require", "library", "load_pack").
                contains(pp.getExpression().getText());

        if (isPackageContext) {
//            List<RepoPackage> allPackages = new RPackageManagementService(insertedElement.getProject()).getAllPackages();
//            List<RepoPackage> allPackages = RPackageService.getInstance().allPackages;

            List<RepoPackage> allPackages = RPackagesUtil.getOrLoadPackages();

            final CompletionResultSet plainResultSet = result.
                    withPrefixMatcher(CompletionUtil.findAlphanumericPrefix(parameters));


            for (RepoPackage allPackage : allPackages) {
                plainResultSet.addElement(LookupElementBuilder.create(allPackage.getName()));
            }
        }
    }


    private static void addWordFromDocument(CompletionResultSet result, CompletionParameters parameters, Set<String> excludes) {
        Set<String> realExcludes = new HashSet<String>(excludes);
        for (String exclude : excludes) {
            String[] words = exclude.split("[ \\.-]");
            if (words.length > 0 && StringUtil.isNotEmpty(words[0])) {
                realExcludes.add(words[0]);
            }
        }

        int startOffset = parameters.getOffset();
        PsiElement insertedElement = parameters.getPosition();

        RCallExpression pp = PsiTreeUtil.getContextOfType(insertedElement, RCallExpression.class);

        final CompletionResultSet plainResultSet = result.
                withPrefixMatcher(CompletionUtil.findAlphanumericPrefix(parameters));

        for (final String word : new HashSet<String>(getAllWords(insertedElement, startOffset))) {
            if (!realExcludes.contains(word)) {
                plainResultSet.addElement(LookupElementBuilder.create(word));
            }
        }
    }


    private static Set<String> getAllWords(final PsiElement context, final int offset) {
        final Set<String> words = new LinkedHashSet<String>();
        if (StringUtil.isEmpty(CompletionUtil.findJavaIdentifierPrefix(context, offset))) {
            return words;
        }

        final CharSequence chars = context.getContainingFile().getViewProvider().getContents(); // ??
        IdTableBuilding.scanWords(new IdTableBuilding.ScanWordProcessor() {
            public void run(final CharSequence chars, @Nullable char[] charsArray, final int start, final int end) {
                if (start > offset || offset > end) {
                    words.add(chars.subSequence(start, end).toString());
                }
            }
        }, chars, 0, chars.length());
        return words;
    }


}