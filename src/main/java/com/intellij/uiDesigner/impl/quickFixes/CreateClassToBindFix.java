/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.uiDesigner.impl.quickFixes;

import com.intellij.java.impl.refactoring.PackageWrapper;
import com.intellij.java.impl.refactoring.util.RefactoringUtil;
import com.intellij.java.language.psi.*;
import com.intellij.uiDesigner.impl.FormEditingUtil;
import com.intellij.uiDesigner.impl.designSurface.GuiEditor;
import consulo.application.Application;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiManager;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.module.Module;
import consulo.module.content.ProjectFileIndex;
import consulo.module.content.ProjectRootManager;
import consulo.platform.base.localize.CommonLocalize;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.awt.Messages;
import consulo.uiDesigner.impl.localize.UIDesignerLocalize;
import consulo.undoRedo.CommandProcessor;
import consulo.util.lang.ref.SimpleReference;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class CreateClassToBindFix extends QuickFix {
    private static final Logger LOG = Logger.getInstance(CreateClassToBindFix.class);

    private final String myClassName;

    public CreateClassToBindFix(GuiEditor editor, @Nonnull String className) {
        super(editor, UIDesignerLocalize.actionCreateClass(className).get(), null);
        myClassName = className;
    }

    @Override
    @RequiredUIAccess
    public void run() {
        Project project = myEditor.getProject();
        ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
        VirtualFile sourceRoot = fileIndex.getSourceRootForFile(myEditor.getFile());
        if (sourceRoot == null) {
            Messages.showErrorDialog(
                myEditor,
                UIDesignerLocalize.errorCannotCreateClassNotInSourceRoot().get(),
                CommonLocalize.titleError().get()
            );
            return;
        }

        CommandProcessor.getInstance().newCommand()
            .project(project)
            .name(LocalizeValue.ofNullable(getName()))
            .inWriteAction()
            .run(() -> {
                // 1. Create all necessary packages
                int indexOfLastDot = myClassName.lastIndexOf('.');
                String packageName = myClassName.substring(0, indexOfLastDot != -1 ? indexOfLastDot : 0);
                PsiDirectory psiDirectory;
                if (packageName.length() > 0) {
                    PackageWrapper packageWrapper = new PackageWrapper(PsiManager.getInstance(project), packageName);
                    try {
                        psiDirectory = RefactoringUtil.createPackageDirectoryInSourceRoot(packageWrapper, sourceRoot);
                        LOG.assertTrue(psiDirectory != null);
                    }
                    catch (IncorrectOperationException e) {
                        Application.get().invokeLater(() -> Messages.showErrorDialog(
                            myEditor,
                            UIDesignerLocalize.errorCannotCreatePackage(packageName, e.getMessage()).get(),
                            CommonLocalize.titleError().get()
                        ));
                        return;
                    }
                }
                else {
                    psiDirectory = PsiManager.getInstance(project).findDirectory(sourceRoot);
                    LOG.assertTrue(psiDirectory != null);
                }

                // 2. Create class in the package
                try {
                    String name = myClassName.substring(indexOfLastDot != -1 ? indexOfLastDot + 1 : 0);
                    PsiClass aClass = JavaDirectoryService.getInstance().createClass(psiDirectory, name);
                    createBoundFields(aClass);
                }
                catch (IncorrectOperationException e) {
                    Application.get().invokeLater(() -> Messages.showErrorDialog(
                        myEditor,
                        UIDesignerLocalize.errorCannotCreateClass(myClassName, e.getMessage()).get(),
                        CommonLocalize.titleError().get()
                    ));
                }
            });
    }

    private void createBoundFields(PsiClass formClass) throws IncorrectOperationException {
        Module module = myEditor.getRootContainer().getModule();
        GlobalSearchScope scope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module);
        PsiManager psiManager = PsiManager.getInstance(myEditor.getProject());

        SimpleReference<IncorrectOperationException> exception = new SimpleReference<>();
        FormEditingUtil.iterate(myEditor.getRootContainer(), component -> {
            if (component.getBinding() != null) {
                PsiClass fieldClass = JavaPsiFacade.getInstance(psiManager.getProject()).findClass(
                    component.getComponentClassName(),
                    scope
                );
                if (fieldClass != null) {
                    PsiType fieldType = JavaPsiFacade.getInstance(psiManager.getProject()).getElementFactory().createType(fieldClass);
                    try {
                        PsiField field = JavaPsiFacade.getInstance(psiManager.getProject()).getElementFactory()
                            .createField(component.getBinding(), fieldType);
                        formClass.add(field);
                    }
                    catch (IncorrectOperationException e) {
                        exception.set(e);
                        return false;
                    }
                }
            }
            return true;
        });

        if (!exception.isNull()) {
            throw exception.get();
        }
    }
}
