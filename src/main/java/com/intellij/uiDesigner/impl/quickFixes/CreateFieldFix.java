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

import com.intellij.java.language.psi.*;
import com.intellij.uiDesigner.impl.FormEditingUtil;
import com.intellij.uiDesigner.impl.GuiDesignerConfiguration;
import com.intellij.uiDesigner.impl.UIDesignerBundle;
import com.intellij.uiDesigner.impl.designSurface.GuiEditor;
import com.intellij.uiDesigner.lw.IContainer;
import com.intellij.uiDesigner.impl.radComponents.RadContainer;
import consulo.application.ApplicationManager;
import consulo.application.CommonBundle;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.ui.ex.awt.Messages;
import consulo.undoRedo.CommandProcessor;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class CreateFieldFix extends QuickFix{
  private static final Logger LOG = Logger.getInstance(CreateFieldFix.class);

  private final PsiClass myClass;
  private final String myFieldClassName;
  private final String myFieldName;

  public CreateFieldFix(
    GuiEditor editor,
    @Nonnull PsiClass aClass,
    @Nonnull String fieldClass,
    @Nonnull String fieldName
  ) {
    super(editor, UIDesignerBundle.message("action.create.field", fieldName), null);
    myClass = aClass;
    myFieldClassName = fieldClass;
    myFieldName = fieldName;
  }

  /**
   * @param showErrors if <code>true</code> the error messages will be shown to the
   * @param undoGroupId the group used to undo the action together with some other action.
   */
  public static void runImpl(@Nonnull final Project project,
                             @Nonnull final RadContainer rootContainer,
                             @Nonnull final PsiClass boundClass,
                             @Nonnull String fieldClassName,
                             @Nonnull final String fieldName,
                             final boolean showErrors,
                             @Nullable Object undoGroupId) {
    ApplicationManager.getApplication().assertReadAccessAllowed();

    PsiDocumentManager.getInstance(project).commitAllDocuments();

    // Do nothing if file becomes invalid
    if(!boundClass.isValid()){
      return;
    }

    if(!boundClass.isWritable()){
      if(showErrors) {
        if (!CommonRefactoringUtil.checkReadOnlyStatus(boundClass, project,
                                                       UIDesignerBundle.message("error.cannot.create.field", fieldClassName))) {
          return;
        }
      } else return;
    }

    final PsiClass fieldClass = JavaPsiFacade.getInstance(project)
      .findClass(fieldClassName.replace('$', '.'), GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(rootContainer.getModule()));
    if(fieldClass == null){
      if(showErrors){
        Messages.showErrorDialog(
          project,
          UIDesignerBundle.message("error.cannot.create.field.no.class", fieldName, fieldClassName),
          CommonBundle.getErrorTitle()
        );
      }
      return;
    }

    CommandProcessor.getInstance().executeCommand(
      project,
      new Runnable() {
        public void run() {
          ApplicationManager.getApplication().runWriteAction(
            new Runnable() {
              public void run() {
                createField(project, fieldClass, fieldName, boundClass, showErrors, rootContainer);
              }
            }
          );
        }
      },
      UIDesignerBundle.message("command.create.field"),
      undoGroupId
    );
  }

  private static void createField(final Project project,
                                  PsiClass fieldClass,
                                  final String fieldName,
                                  PsiClass boundClass,
                                  boolean showErrors,
                                  IContainer rootContainer) {
    // 1. Create field
    PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
    PsiType type = factory.createType(fieldClass);
    try {
      PsiField field = factory.createField(fieldName, type);
      String accessibility = GuiDesignerConfiguration.getInstance(project).DEFAULT_FIELD_ACCESSIBILITY;
      PsiModifierList modifierList = field.getModifierList();
      assert modifierList != null;
      String[] modifiers = {PsiModifier.PRIVATE, PsiModifier.PROTECTED, PsiModifier.PUBLIC};
      for(@PsiModifier.ModifierConstant String modifier: modifiers) {
        modifierList.setModifierProperty(modifier, accessibility.equals(modifier));
      }
      PsiField lastUiField = null;
      for(PsiField uiField: boundClass.getFields()) {
        if (FormEditingUtil.findComponentWithBinding(rootContainer, uiField.getName()) != null) {
          lastUiField = uiField;
        }
      }
      if (lastUiField != null) {
        boundClass.addAfter(field, lastUiField);
      }
      else {
        boundClass.add(field);
      }
    }
    catch (final IncorrectOperationException exc) {
      if (showErrors) {
        ApplicationManager.getApplication().invokeLater(
          new Runnable() {
            public void run() {
              Messages.showErrorDialog(
                project,
                UIDesignerBundle.message("error.cannot.create.field.reason", fieldName, exc.getMessage()),
                CommonBundle.getErrorTitle()
              );
            }
          }
        );
      }
    }
  }

  public void run() {
    runImpl(myEditor.getProject(), myEditor.getRootContainer(), myClass, myFieldClassName, myFieldName, true, null);
  }
}
