package com.r4intellij.psi.references;

import com.google.common.collect.Iterables;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.impl.scopes.LibraryScope;
import com.intellij.openapi.module.impl.scopes.ModuleScopeProviderImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.ProjectScopeImpl;
import com.intellij.psi.util.PsiTreeUtil;
import com.r4intellij.RPsiUtils;
import com.r4intellij.interpreter.SkeletonUpdater;
import com.r4intellij.packages.RIndexCache;
import com.r4intellij.psi.api.*;
import com.r4intellij.psi.stubs.RAssignmentNameIndex;
import com.r4intellij.settings.LibraryUtil;
import com.r4intellij.settings.RSettings;
import com.r4intellij.typing.RTypeProvider;
import com.r4intellij.typing.types.RType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.r4intellij.RFileType.DOT_R_EXTENSION;

public class RResolver {

    protected static final Logger LOG = Logger.getInstance("#" + RResolver.class.getName());


    static void addFromSkeletonsAndRLibrary(@NotNull final PsiElement element,
                                            @NotNull final List<ResolveResult> result,
                                            @NotNull final String... names) {

        SkeletonUpdater.updateSkeletonsLaterOptional(element.getProject());

        if (!RIndexCache.getInstance().isReady()) return;

        for (String name : names) {
            if (RSettings.getInstance().isResolveInModule()) {
                // https://intellij-support.jetbrains.com/hc/en-us/community/posts/206148389-Getting-the-module-for-a-PsiElement
                ProjectFileIndex fileIndex = ProjectRootManager.getInstance(element.getProject()).getFileIndex();
                Module moduleForFile = fileIndex.getModuleForFile(element.getContainingFile().getVirtualFile());

                if (moduleForFile != null) {
                    addFromModule(name, moduleForFile, result);
                }

//              addFromProject(name, element.getProject(), result);
            }

//            addFromLibrary(element, result, name, LibraryUtil.USER_SKELETONS);

            // by design always resolve from user library
            addFromLibrary(element, result, name, LibraryUtil.R_LIBRARY);

            // too unspecific and does not reflect imports
//            addFromLibrary(element, result, name, RSettingsConfigurable.R_SKELETONS);

            ResolveResult importResolve = addFromImports(element, name);
            if (importResolve != null) result.add(importResolve);
        }
    }


    private static ResolveResult addFromImports(@NotNull final PsiElement element, @NotNull final String name) {

        // get all imports for the current File
        List<String> imports = RIndexCache.getInstance().findImportsFor(element);

        // detect all libary elements with the same name
        List<ResolveResult> indexResults = findSkeletonMatches(name, element.getProject());

        // retain those for which there is an import statement
        List<ResolveResult> imported = indexResults.stream()
                .filter(resolve -> imports.contains(getSkeletonPckgName(resolve)))
                .collect(Collectors.toList());


        // resort to match import order in file
        imported.sort(Comparator.comparingInt(o -> imports.indexOf(getSkeletonPckgName(o))));

        if (!imported.isEmpty()) {
            // because of R environment rules we return the resolve from the last matching import before the element
//            return imported.get(0);
            return Iterables.getLast(imported);

        } else if (!indexResults.isEmpty()) {
            // no overlap --> highlight as error and suggest to import one!
            List<String> packageOptions = indexResults.stream()
                    .map(RResolver::getSkeletonPckgName).collect(Collectors.toList());

            return new MissingImportResolveResult(indexResults);
        }

        // we could neither resolve it nor find import options for the symbol
        return null;


        // Also provide importing packages as options (like tidyverse for mutate)
        // DISABLED: works but is adding too many confusing options
//                    Set<String> funPckgByImport = funPackageNames.stream().
//                            flatMap(pName -> packageService.getImporting(packageService.getByName(pName)).stream()).
//                            map(RPackage::getName).
//                            collect(Collectors.toSet());
//                    funPackageNames.addAll(funPckgByImport);

    }


    @NotNull
    private static List<ResolveResult> findSkeletonMatches(@NotNull String name, Project project) {
        final Library library = getSkeletonLibrary(project);

        List<ResolveResult> indexResults = new ArrayList<>();

        if (library == null) {
            LOG.warn("library is null when resolving " + name);
            return indexResults;
        }

        // too wide scope in scratches?
        // see https://intellij-support.jetbrains.com/hc/en-us/community/posts/115000093684-Reference-search-scope-different-between-project-files-and-scratches-
        final Collection<RAssignmentStatement> assignmentStatements =
//                RAssignmentNameIndex.find(name, project, new LibraryScope(project, library));
                RAssignmentNameIndex.find(name, project, new LibraryScope(project, library));
//                RAssignmentNameIndex.find(name, project, new EverythingGlobalScope(project));

        //RAssignmentNameIndex.allKeys(project).stream().filter(f->f.equals("head")).collect(Collectors.toList())
        //RAssignmentNameIndex.allKeys(project).stream().filter(f->f.equals("iris")).collect(Collectors.toList())

        for (RAssignmentStatement statement : assignmentStatements) {
//            String containedPackage = getSkeletonPckgName(statement);
//            if (!imports.contains(containedPackage)) continue;

            final PsiElement assignee = statement.getAssignee();
            if (assignee == null) continue;

            indexResults.add(new PsiElementResolveResult(statement));
        }

        return indexResults;
    }


    public static Library getSkeletonLibrary(Project project) {
        boolean isGlobal = true; // decide later if this module attached libraries should also be supported

        LibraryTable.ModifiableModel model = isGlobal ?
                LibraryTablesRegistrar.getInstance().getLibraryTable().getModifiableModel() :
                ModifiableModelsProvider.SERVICE.getInstance().getLibraryTableModifiableModel(project);

        return model.getLibraryByName(LibraryUtil.R_SKELETONS);
    }


    private static void addFromLibrary(@NotNull final PsiElement element,
                                       @NotNull final List<ResolveResult> result,
                                       @NotNull final String name,
                                       @NotNull final String libraryName) {
        Project project = element.getProject();
        LibraryTable libraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(project);
        final Library library = libraryTable.getLibraryByName(libraryName);

        if (library == null) {
            return;
        }

        final Collection<RAssignmentStatement> assignmentStatements =
                RAssignmentNameIndex.find(name, project, new LibraryScope(project, library));

        for (RAssignmentStatement statement : assignmentStatements) {
            final PsiElement assignee = statement.getAssignee();
            if (assignee == null) continue;

            result.add(new PsiElementResolveResult(statement));
        }
    }


    private static void addFromProject(String name,
                                       @NotNull final Project project,
                                       @NotNull final List<ResolveResult> results) {

        //todo actually we should just resolve in module here and not in project
        Collection<RAssignmentStatement> statements =
                RAssignmentNameIndex.find(name, project,
                        new ProjectScopeImpl(project, FileIndexFacade
                                .getInstance(project)));
        for (RAssignmentStatement statement : statements) {
            final PsiElement assignee = statement.getAssignee();
            if (assignee == null) continue;
            results.add(new PsiElementResolveResult(statement));
        }
    }


    private static void addFromModule(String name,
                                      @NotNull final Module module,
                                      @NotNull final List<ResolveResult> results) {

        //todo actually we should just resolve in module here and not in project
        Collection<RAssignmentStatement> statements = RAssignmentNameIndex.find(name, module.getProject(), new ModuleScopeProviderImpl(module).getModuleContentScope());
        for (RAssignmentStatement statement : statements) {
            final PsiElement assignee = statement.getAssignee();
            if (assignee == null) continue;
            results.add(new PsiElementResolveResult(statement));
        }
    }


    //TODO: should we search in other libraries too?
    //TODO: why don't we use the stub index here? should be faster and more elegant
    public static void resolveWithNamespace(@NotNull final Project project,
                                            String name,
                                            String namespace,
                                            @NotNull final List<ResolveResult> result) {
        Library library = getSkeletonLibrary(project);

        if (library == null) {
            return;
        }

        VirtualFile file = Arrays.stream(library.getFiles(OrderRootType.CLASSES)).
                map(d -> d.findFileByRelativePath(namespace + DOT_R_EXTENSION)).
                filter(Objects::nonNull).findFirst().orElse(null);

        // FIXME support unit-test library layout out here (bad design!)
        if (file == null) {
            file = Arrays.stream(library.getFiles(OrderRootType.CLASSES))
                    .filter(f -> f.getName().equals(namespace + DOT_R_EXTENSION))
                    .findFirst().orElse(null);
        }

        if (file == null) {
            return;
        }

        // todo why don't we use the stub-index here, it should be much faster than parsing the file
        final PsiFile psiFile = PsiManager.getInstance(project).findFile(file);

        final RAssignmentStatement[] statements =
                PsiTreeUtil.getChildrenOfType(psiFile, RAssignmentStatement.class);

        if (statements != null) {
            for (RAssignmentStatement statement : statements) {
                final PsiElement assignee = statement.getAssignee();

                if (assignee != null && assignee.getText().equals(name)) {
                    result.add(new PsiElementResolveResult(assignee));
                }
            }
        }
    }


    public static <T> Predicate<T> not(Predicate<T> t) {
        return t.negate();
    }


    static void resolveNameArgument(@NotNull final PsiElement element,
                                    String elementName,
                                    @NotNull final List<ResolveResult> result) {
        RCallExpression callExpression = PsiTreeUtil.getParentOfType(element, RCallExpression.class);
        if (callExpression != null) {
            RFunctionExpression functionExpression = RPsiUtils.getFunction(callExpression);
            RParameterList parameterList = PsiTreeUtil.getChildOfType(functionExpression, RParameterList.class);
            if (parameterList != null) {
                for (RParameter parameter : parameterList.getParameterList()) {
                    String name = parameter.getName();
                    if (name != null && name.equals(elementName)) {
                        result.add(0, new PsiElementResolveResult(parameter));
                        return;
                    }
                }
            }
        }
    }


    // TODO: massive refactoring awaits!!!
    static void resolveFunctionCall(PsiElement myElement, String name, List<ResolveResult> result) {
        PsiElement parent = myElement.getParent();

        if (parent != null && parent instanceof RCallExpression) {
            RCallExpression call = ((RCallExpression) parent);
            List<RExpression> arguments = call.getArgumentList().getExpressionList();

            List<ResolveResult> myResult = new ArrayList<ResolveResult>();

            resolveInFileOrLibrary(myElement, name, myResult);

            // since the downstream analysis relies on having an argument, we stop if the call does not have any args
            if (call.getExpression().equals(myElement) && !arguments.isEmpty()) {

                // we now process resolve candidates for the function call:
                // if they are RHS function expression assignments we can try to infer the correct resolvant by
                // comparing the provided argument (if present) type
                for (ResolveResult resolveResult : myResult) {
                    PsiElement resolved = resolveResult.getElement();

                    if (resolved instanceof RAssignmentStatement) {
                        RPsiElement assignedValue = ((RAssignmentStatement) resolved).getAssignedValue();

                        if (assignedValue instanceof RFunctionExpression) {
                            RFunctionExpression function = ((RFunctionExpression) assignedValue);
                            List<RCallExpression> nestedCalls = PsiTreeUtil.getChildrenOfTypeAsList(function, RCallExpression.class);

                            // we just care about nested UseMethod calls (i.e. S3 method dispatch)
                            // see http://adv-r.had.co.nz/OO-essentials.html#s3
                            for (RCallExpression nestedCall : nestedCalls) {
                                if ("UseMethod".equals(nestedCall.getExpression().getText())) {
                                    RExpression firstArgument = arguments.get(0);
                                    RType firstType = RTypeProvider.getType(firstArgument);
                                    List<String> s3Classes = firstType.getS3Classes();
                                    s3Classes.add("default");

                                    for (String s3Class : s3Classes) {
                                        String genericName = name + "." + s3Class;
                                        List<ResolveResult> genericResult = new ArrayList<>();

                                        resolveInFileOrLibrary(myElement, genericName, genericResult);

                                        if (!genericResult.isEmpty()) {
                                            result.addAll(genericResult);
                                            return;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                result.addAll(myResult);
            }
        }
    }


    public static void resolveInFileOrLibrary(PsiElement element, String name, List<ResolveResult> myResult) {
        myResult.addAll(new FileContextResolver().resolveFromInner(element, element, name));

        if (myResult.isEmpty()) {
            addFromSkeletonsAndRLibrary(element, myResult, name);
        }
    }


    public static String getSkeletonPckgName(ResolveResult o1) {
        return getTrimmedFileName(o1.getElement());
    }


    @Nullable
    public static String getTrimmedFileName(PsiElement element) {
        if (element.getContainingFile() == null) return null;

        return element.getContainingFile().getVirtualFile().getName().replaceAll(".[rR]$", "");
    }
}
