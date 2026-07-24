// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner.impl.binding;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiEnumConstant;
import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.psi.PsiJavaPackage;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.uiDesigner.impl.GuiFormFileType;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.ApplicationManager;
import consulo.application.ReadAction;
import consulo.application.progress.ProgressManager;
import consulo.application.util.function.CommonProcessors;
import consulo.content.scope.SearchScope;
import consulo.language.cacheBuilder.CacheManager;
import consulo.language.psi.*;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.scope.LocalSearchScope;
import consulo.language.psi.search.PsiSearchHelper;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.psi.search.ReferencesSearchQueryExecutor;
import consulo.language.psi.search.UsageSearchContext;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.module.Module;
import consulo.module.content.ProjectRootManager;
import consulo.project.Project;
import consulo.util.lang.CharArrayUtil;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.fileType.FileTypeRegistry;
import jakarta.annotation.Nonnull;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

@ExtensionImpl
public class FormReferencesSearcher implements ReferencesSearchQueryExecutor {
    @Override
    public boolean execute(@Nonnull ReferencesSearch.SearchParameters p, @Nonnull Predicate<? super PsiReference> consumer) {
        SearchScope userScope = p.getScopeDeterminedByUser();
        if (!scopeCanContainForms(userScope)) {
            return true;
        }
        PsiElement refElement = p.getElementToSearch();
        PsiFile psiFile = ReadAction.compute(() -> {
            if (!refElement.isValid()) {
                return null;
            }
            return refElement.getContainingFile();
        });
        if (psiFile == null) {
            return true;
        }
        VirtualFile virtualFile = psiFile.getVirtualFile();
        if (virtualFile == null) {
            return true;
        }
        GlobalSearchScope[] scope = new GlobalSearchScope[1];
        Project project = ReadAction.compute(() -> {
            Project project1 = psiFile.getProject();
            Module module = ProjectRootManager.getInstance(project1).getFileIndex().getModuleForFile(virtualFile);
            if (module != null) {
                scope[0] = GlobalSearchScope.moduleWithDependenciesScope(module);
            }
            return project1;
        });
        if (scope[0] == null) {
            return true;
        }
        LocalSearchScope filterScope = userScope instanceof LocalSearchScope ? (LocalSearchScope) userScope : null;

        PsiManager psiManager = PsiManager.getInstance(project);
        if (refElement instanceof PsiJavaPackage) {
            //no need to do anything
            //if (!UIFormUtil.processReferencesInUIForms(consumer, (PsiPackage)refElement, scope)) return false;
        }
        else if (refElement instanceof PsiClass) {
            if (!processReferencesInUIForms(consumer, psiManager, (PsiClass) refElement, scope[0], filterScope)) {
                return false;
            }
        }
        else if (refElement instanceof PsiEnumConstant) {
            if (!processEnumReferencesInUIForms(consumer, psiManager, (PsiEnumConstant) refElement, scope[0], filterScope)) {
                return false;
            }
        }
        else if (refElement instanceof PsiField) {
            if (!processReferencesInUIForms(consumer, psiManager, (PsiField) refElement, scope[0], filterScope)) {
                return false;
            }
        }
        else if (refElement instanceof IProperty) {
            if (!processReferencesInUIForms(consumer, psiManager, (Property) refElement, scope[0], filterScope)) {
                return false;
            }
        }
        else if (refElement instanceof PropertiesFile) {
            if (!processReferencesInUIForms(consumer, psiManager, (PropertiesFile) refElement, scope[0], filterScope)) {
                return false;
            }
        }

        return true;
    }

    private static boolean scopeCanContainForms(SearchScope scope) {
        if (!(scope instanceof LocalSearchScope)) {
            return true;
        }
        LocalSearchScope localSearchScope = (LocalSearchScope) scope;
        PsiElement[] elements = localSearchScope.getScope();
        for (PsiElement element : elements) {
            if (element instanceof PsiDirectory) {
                return true;
            }
            boolean isForm = ReadAction.compute(() -> {
                PsiFile file;
                if (element instanceof PsiFile) {
                    file = (PsiFile) element;
                }
                else {
                    if (!element.isValid()) {
                        return false;
                    }
                    file = element.getContainingFile();
                }
                return file.getFileType() == GuiFormFileType.INSTANCE;
            });
            if (isForm) {
                return true;
            }
        }
        return false;
    }

    private static boolean processReferencesInUIForms(Predicate<? super PsiReference> processor, PsiManager psiManager, PsiClass aClass, GlobalSearchScope scope, LocalSearchScope filterScope) {
        String className = getQualifiedName(aClass);
        return className == null || processReferencesInUIFormsInner(className, aClass, processor, scope, psiManager, filterScope);
    }

    public static String getQualifiedName(PsiClass aClass) {
        return ReadAction.compute(() -> {
            if (!aClass.isValid()) {
                return null;
            }
            return aClass.getQualifiedName();
        });
    }

    private static boolean processEnumReferencesInUIForms(Predicate<? super PsiReference> processor, PsiManager psiManager, PsiEnumConstant enumConstant, GlobalSearchScope scope, LocalSearchScope filterScope) {
        String className = ReadAction.compute(() -> enumConstant.getName());
        return processReferencesInUIFormsInner(className, enumConstant, processor, scope, psiManager, filterScope);
    }

    private static boolean processReferencesInUIFormsInner(String name, PsiElement element, Predicate<? super PsiReference> processor, GlobalSearchScope scope1, PsiManager manager, LocalSearchScope filterScope) {
        GlobalSearchScope scope = GlobalSearchScope.projectScope(manager.getProject()).intersectWith(scope1);
        List<PsiFile> files = FormClassIndex.findFormsBoundToClass(manager.getProject(), name, scope);

        return processReferencesInFiles(files, manager, name, element, filterScope, processor);
    }

    private static boolean processReferencesInUIForms(Predicate<? super PsiReference> processor, PsiManager psiManager, PsiField field, GlobalSearchScope scope1, LocalSearchScope filterScope) {
        GlobalSearchScope scope = GlobalSearchScope.projectScope(psiManager.getProject()).intersectWith(scope1);
        PsiClass containingClass = ReadAction.compute(() -> field.getContainingClass());
        if (containingClass == null) {
            return true;
        }
        String fieldName = ReadAction.compute(() -> field.getName());
        List<PsiFile> files = FormClassIndex.findFormsBoundToClass(psiManager.getProject(), containingClass, scope);
        return processReferencesInFiles(files, psiManager, fieldName, field, filterScope, processor);
    }

    private static boolean processReferences(Predicate<? super PsiReference> processor, PsiFile file, String name, PsiElement element, LocalSearchScope filterScope) {
        CharSequence chars = ApplicationManager.getApplication().runReadAction((Supplier<CharSequence>) () -> {
            if (filterScope != null) {
                boolean isInScope = false;
                for (PsiElement filterElement : filterScope.getScope()) {
                    if (PsiTreeUtil.isAncestor(filterElement, file, false)) {
                        isInScope = true;
                        break;
                    }
                }
                if (!isInScope) {
                    return null;
                }
            }
            return file.getViewProvider().getContents();
        });
        if (chars == null) {
            return true;
        }
        int index = 0;
        int offset = name.lastIndexOf('.');
        while (true) {
            index = CharArrayUtil.indexOf(chars, name, index);

            if (index < 0) {
                break;
            }
            int finalIndex = index;
            Boolean searchDone = ApplicationManager.getApplication().runReadAction((Supplier<Boolean>) () -> {
                PsiReference ref = file.findReferenceAt(finalIndex + offset + 1);
                if (ref != null && ref.isReferenceTo(element)) {
                    return processor.test(ref);
                }
                return true;
            });
            if (!searchDone.booleanValue()) {
                return false;
            }
            index++;
        }

        return true;
    }

    private static boolean processReferencesInUIForms(Predicate<? super PsiReference> processor, PsiManager psiManager, Property property, GlobalSearchScope globalSearchScope, LocalSearchScope filterScope) {
        Project project = psiManager.getProject();

        GlobalSearchScope scope = GlobalSearchScope.projectScope(project).intersectWith(globalSearchScope);
        String name = ReadAction.compute(() -> property.getName());
        if (name == null) {
            return true;
        }

        psiManager.startBatchFilesProcessingMode();

        try {
            CommonProcessors.CollectProcessor<VirtualFile> collector = new CommonProcessors.CollectProcessor<>() {
                @Override
                protected boolean accept(VirtualFile virtualFile) {
                    return FileTypeRegistry.getInstance().isFileOfType(virtualFile, GuiFormFileType.INSTANCE);
                }
            };

            PsiSearchHelper.getInstance(project).processFilesWithText(scope, UsageSearchContext.IN_PLAIN_TEXT, true, name, collector);

            for (VirtualFile vfile : collector.getResults()) {
                ProgressManager.checkCanceled();

                PsiFile file = ReadAction.compute(() -> PsiManager.getInstance(project).findFile(vfile));
                if (!processReferences(processor, file, name, property, filterScope)) {
                    return false;
                }
            }
        }
        finally {
            psiManager.finishBatchFilesProcessingMode();
        }

        return true;
    }

    private static boolean processReferencesInUIForms(Predicate<? super PsiReference> processor, PsiManager psiManager, PropertiesFile propFile, GlobalSearchScope globalSearchScope, LocalSearchScope filterScope) {
        Project project = psiManager.getProject();
        GlobalSearchScope scope = GlobalSearchScope.projectScope(project).intersectWith(globalSearchScope);
        String baseName = ReadAction.compute(() -> propFile.getResourceBundle().getBaseName());
        PsiFile containingFile = ReadAction.compute(() -> propFile.getContainingFile());

        List<PsiFile> files = Arrays.asList(CacheManager.getInstance(project).getFilesWithWord(baseName, UsageSearchContext.IN_PLAIN_TEXT, scope, true));
        return processReferencesInFiles(files, psiManager, baseName, containingFile, filterScope, processor);
    }

    private static boolean processReferencesInFiles(List<PsiFile> files, PsiManager psiManager, String baseName, PsiElement element, LocalSearchScope filterScope, Predicate<? super PsiReference> processor) {
        psiManager.startBatchFilesProcessingMode();

        try {
            for (PsiFile file : files) {
                ProgressManager.checkCanceled();

                if (file.getFileType() != GuiFormFileType.INSTANCE) {
                    continue;
                }
                if (!processReferences(processor, file, baseName, element, filterScope)) {
                    return false;
                }
            }
        }
        finally {
            psiManager.finishBatchFilesProcessingMode();
        }
        return true;
    }
}
