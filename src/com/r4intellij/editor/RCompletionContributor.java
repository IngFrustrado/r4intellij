

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
import com.r4intellij.psi.api.RCallExpression;
import com.r4intellij.psi.api.RStringLiteralExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;


/**
 * This would be our hook into any special handling for code completion.
 */
public class RCompletionContributor extends CompletionContributor {

    public static ArrayList<String> PACKAGE_IMPORT_METHODS = Lists.newArrayList("require", "library", "load_pack");


    public RCompletionContributor() {
        // also allow for within string completion
        // see https://intellij-support.jetbrains.com/hc/en-us/community/posts/206114249-How-to-complete-string-literal-expressions-

        // see https://intellij-support.jetbrains.com/hc/en-us/community/posts/206756005-Is-There-A-Standard-CompletionContributor-Which-Provides-Path-File-Completion
//        extend(PlatformPatterns.psiElement().inside(PsiJavaPatterns.literalExpression()));
    }

    //    public static final String test = new File("");


    @Override
    public void fillCompletionVariants(@NotNull final CompletionParameters parameters, @NotNull final CompletionResultSet result) {
//        if (parameters.getCompletionType() == CompletionType.BASIC && shouldPerformWordCompletion(parameters)) {

        if (isString(parameters.getPosition())) {
            String text = parameters.getPosition().getText();
            addPathVariants(result, text);
        }

        if (!isPackageContext(parameters.getPosition())) {
            addWordFromDocument(result, parameters, Collections.<String>emptySet());
        }
    }

    static boolean isString(PsiElement psiElement) {
        RStringLiteralExpression sl = PsiTreeUtil.getContextOfType(psiElement, RStringLiteralExpression.class);
        return sl != null;
    }

    // handle paths in system independent way by using normal slashes for all systems
    static void addPathVariants(CompletionResultSet result, @NotNull String str) {
        // unquote string literal
        str = str.substring(1, str.length());
        int lastSep = str.lastIndexOf('/');
        if (lastSep < 0) return;

        // include '/' to properly handle windows root paths e.g. c:/
        File path = new File(lastSep == 0 ? "/" : str.substring(0, lastSep + 1));
        if (!path.exists()) return;

        File[] list = path.listFiles();
        if (list != null) {
            for (File file : list) {
                String item = file.getPath().replace(File.separatorChar, '/');
                item = item.startsWith("/") ? item.substring(1) : item;
                result.addElement(new PathLookupElement(item, file.isDirectory()));
            }
        }
    }

    public static boolean isPackageContext(PsiElement psiElement) {
        RCallExpression pp = PsiTreeUtil.getContextOfType(psiElement, RCallExpression.class);

        return pp != null && PACKAGE_IMPORT_METHODS.contains(pp.getExpression().getText());
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

//        RCallExpression pp = PsiTreeUtil.getContextOfType(insertedElement, RCallExpression.class);

        final CompletionResultSet plainResultSet = result.
                withPrefixMatcher(CompletionUtil.findAlphanumericPrefix(parameters));

        for (final String word : new HashSet<>(getAllWords(insertedElement, startOffset))) {
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
