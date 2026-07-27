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
import com.intellij.uiDesigner.impl.designSurface.GuiEditor;
import com.intellij.uiDesigner.impl.radComponents.RadContainer;
import com.intellij.uiDesigner.lw.IContainer;
import consulo.annotation.access.RequiredWriteAction;
import consulo.application.Application;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.platform.base.localize.CommonLocalize;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.awt.Messages;
import consulo.uiDesigner.impl.localize.UIDesignerLocalize;
import consulo.undoRedo.CommandProcessor;
import consulo.undoRedo.builder.RunnableCommandBuilder;
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
    super(editor, UIDesignerLocalize.actionCreateField(fieldName).get(), null);
    myClass = aClass;
    myFieldClassName = fieldClass;
    myFieldName = fieldName;
  }

  /**
   * @param showErrors if <code>true</code> the error messages will be shown to the
   * @param undoGroupId the group used to undo the action together with some other action.
   */
  @RequiredUIAccess
  public static void runImpl(
    @Nonnull Project project,
    @Nonnull RadContainer rootContainer,
    @Nonnull PsiClass boundClass,
    @Nonnull String fieldClassName,
    @Nonnull String fieldName,
    boolean showErrors,
    @Nullable Object undoGroupId
  ) {
    Application.get().assertReadAccessAllowed();

    PsiDocumentManager.getInstance(project).commitAllDocuments();

    // Do nothing if file becomes invalid
    if(!boundClass.isValid()){
      return;
    }

    if(!boundClass.isWritable()){
      if(showErrors) {
        if (!CommonRefactoringUtil.checkReadOnlyStatus(
            boundClass,
            project,
            UIDesignerLocalize.errorCannotCreateField(fieldClassName)
        )) {
          return;
        }
      } else return;
    }

    PsiClass fieldClass = JavaPsiFacade.getInstance(project)
      .findClass(fieldClassName.replace('$', '.'), GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(rootContainer.getModule()));
    if(fieldClass == null){
      if(showErrors){
        Messages.showErrorDialog(
          project,
          UIDesignerLocalize.errorCannotCreateFieldNoClass(fieldName, fieldClassName).get(),
          CommonLocalize.titleError().get()
        );
      }
      return;
    }

    RunnableCommandBuilder<?, ?> builder = CommandProcessor.getInstance().newCommand()
      .project(project);
    if (undoGroupId != null) {
        builder = builder.groupId(undoGroupId);
    }
    builder.name(UIDesignerLocalize.commandCreateField())
      .inWriteAction()
      .run(() -> createField(project, fieldClass, fieldName, boundClass, showErrors, rootContainer));
  }

  @RequiredWriteAction
  private static void createField(
    Project project,
    PsiClass fieldClass,
    String fieldName,
    PsiClass boundClass,
    boolean showErrors,
    IContainer rootContainer
  ) {
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
    catch (IncorrectOperationException exc) {
      if (showErrors) {
        Application.get().invokeLater(() -> Messages.showErrorDialog(
          project,
          UIDesignerLocalize.errorCannotCreateFieldReason(fieldName, exc.getMessage()).get(),
          CommonLocalize.titleError().get()
        ));
      }
    }
  }

  @Override
  @RequiredUIAccess
  public void run() {
    runImpl(myEditor.getProject(), myEditor.getRootContainer(), myClass, myFieldClassName, myFieldName, true, null);
  }
}
