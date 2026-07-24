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
import com.intellij.uiDesigner.impl.UIDesignerBundle;
import com.intellij.uiDesigner.impl.designSurface.GuiEditor;
import com.intellij.uiDesigner.lw.IComponent;
import consulo.application.ApplicationManager;
import consulo.application.CommonBundle;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiManager;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.module.Module;
import consulo.module.content.ProjectFileIndex;
import consulo.module.content.ProjectRootManager;
import consulo.project.Project;
import consulo.ui.ex.awt.Messages;
import consulo.undoRedo.CommandProcessor;
import consulo.util.lang.ref.Ref;
import consulo.virtualFileSystem.VirtualFile;

import jakarta.annotation.Nonnull;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class CreateClassToBindFix extends QuickFix{
  private static final Logger LOG = Logger.getInstance(CreateClassToBindFix.class);

  private final String myClassName;

  public CreateClassToBindFix(GuiEditor editor, @Nonnull String className) {
    super(editor, UIDesignerBundle.message("action.create.class", className), null);
    myClassName = className;
  }

  public void run() {
    final Project project = myEditor.getProject();
    ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    final VirtualFile sourceRoot = fileIndex.getSourceRootForFile(myEditor.getFile());
    if(sourceRoot == null){
      Messages.showErrorDialog(
        myEditor,
        UIDesignerBundle.message("error.cannot.create.class.not.in.source.root"),
        CommonBundle.getErrorTitle()
      );
      return;
    }

    ApplicationManager.getApplication().runWriteAction(
      new Runnable() {
        public void run() {
          CommandProcessor.getInstance().executeCommand(
            project,
            new Runnable() {
              public void run() {
                // 1. Create all necessary packages
                int indexOfLastDot = myClassName.lastIndexOf('.');
                final String packageName = myClassName.substring(0, indexOfLastDot != -1 ? indexOfLastDot : 0);
                PsiDirectory psiDirectory;
                if(packageName.length() > 0){
                  PackageWrapper packageWrapper = new PackageWrapper(PsiManager.getInstance(project), packageName);
                  try {
                    psiDirectory = RefactoringUtil.createPackageDirectoryInSourceRoot(packageWrapper, sourceRoot);
                    LOG.assertTrue(psiDirectory != null);
                  }
                  catch (final IncorrectOperationException e) {
                    ApplicationManager.getApplication().invokeLater(new Runnable(){
                                        public void run() {
                                          Messages.showErrorDialog(
                                            myEditor,
                                            UIDesignerBundle.message("error.cannot.create.package", packageName, e.getMessage()),
                                            CommonBundle.getErrorTitle()
                                          );
                                        }
                                      });
                    return;
                  }
                }
                else{
                  psiDirectory = PsiManager.getInstance(project).findDirectory(sourceRoot);
                  LOG.assertTrue(psiDirectory != null);
                }

                // 2. Create class in the package
                try {
                  String name = myClassName.substring(indexOfLastDot != -1 ? indexOfLastDot + 1 : 0);
                  PsiClass aClass = JavaDirectoryService.getInstance().createClass(psiDirectory, name);
                  createBoundFields(aClass);
                }
                catch (final IncorrectOperationException e) {
                  ApplicationManager.getApplication().invokeLater(new Runnable() {
                                    public void run() {
                                      Messages.showErrorDialog(
                                        myEditor,
                                        UIDesignerBundle.message("error.cannot.create.class", packageName, e.getMessage()),
                                        CommonBundle.getErrorTitle()
                                      );
                                    }
                                  });
                }
              }
            },
            getName(),
            null
          );
        }
      }
    );
  }

  private void createBoundFields(final PsiClass formClass) throws IncorrectOperationException {
    Module module = myEditor.getRootContainer().getModule();
    final GlobalSearchScope scope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module);
    final PsiManager psiManager = PsiManager.getInstance(myEditor.getProject());

    final Ref<IncorrectOperationException> exception = new Ref<>();
    FormEditingUtil.iterate(myEditor.getRootContainer(), new FormEditingUtil.ComponentVisitor() {
      public boolean visit(IComponent component) {
        if (component.getBinding() != null) {
          PsiClass fieldClass = JavaPsiFacade.getInstance(psiManager.getProject()).findClass(component.getComponentClassName(), scope);
          if (fieldClass != null) {
            PsiType fieldType = JavaPsiFacade.getInstance(psiManager.getProject()).getElementFactory().createType(fieldClass);
            try {
              PsiField field = JavaPsiFacade.getInstance(psiManager.getProject()).getElementFactory().createField(component.getBinding(), fieldType);
              formClass.add(field);
            }
            catch (IncorrectOperationException e) {
              exception.set(e);
              return false;
            }
          }
        }
        return true;
      }
    });

    if (!exception.isNull()) {
      throw exception.get();
    }
  }
}
