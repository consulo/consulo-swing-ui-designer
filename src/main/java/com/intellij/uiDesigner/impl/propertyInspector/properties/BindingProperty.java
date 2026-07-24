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
package com.intellij.uiDesigner.impl.propertyInspector.properties;

import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.java.language.psi.codeStyle.VariableKind;
import com.intellij.uiDesigner.compiler.AsmCodeGenerator;
import com.intellij.uiDesigner.impl.FormEditingUtil;
import com.intellij.uiDesigner.impl.GuiFormFileType;
import com.intellij.uiDesigner.impl.designSurface.GuiEditor;
import com.intellij.uiDesigner.impl.designSurface.InsertComponentProcessor;
import com.intellij.uiDesigner.impl.inspections.FormInspectionUtil;
import com.intellij.uiDesigner.impl.propertyInspector.DesignerToolWindowManager;
import com.intellij.uiDesigner.impl.propertyInspector.Property;
import com.intellij.uiDesigner.impl.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.impl.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.impl.propertyInspector.editors.BindingEditor;
import com.intellij.uiDesigner.impl.propertyInspector.renderers.LabelPropertyRenderer;
import com.intellij.uiDesigner.impl.quickFixes.CreateFieldFix;
import com.intellij.uiDesigner.impl.radComponents.RadComponent;
import com.intellij.uiDesigner.impl.radComponents.RadRootContainer;
import consulo.application.dumb.IndexNotReadyException;
import consulo.language.editor.refactoring.rename.RenameProcessor;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.platform.base.localize.CommonLocalize;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.awt.Messages;
import consulo.ui.ex.awt.UIUtil;
import consulo.uiDesigner.impl.localize.UIDesignerLocalize;
import consulo.undoRedo.CommandProcessor;
import consulo.util.lang.Comparing;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class BindingProperty extends Property<RadComponent, String> {
  private final PropertyRenderer<String> myRenderer = new LabelPropertyRenderer<>() {
    @Override
    protected void customize(@Nonnull final String value) {
      setText(value);
    }
  };
  private final BindingEditor myEditor;
  private static final String PREFIX_HTML = "<html>";

  public BindingProperty(final Project project){
    super(null, "field name");
    myEditor = new BindingEditor(project);
  }

  @Override
  public PropertyEditor<String> getEditor(){
    return myEditor;
  }

  @Nonnull
  @Override
  public PropertyRenderer<String> getRenderer(){
    return myRenderer;
  }

  @Override
  public String getValue(final RadComponent component){
    return component.getBinding();
  }

  @Override
  @RequiredUIAccess
  protected void setValueImpl(final RadComponent component, final String value) throws Exception {
    if (Comparing.strEqual(value, component.getBinding(), true)) {
      return;
    }

    if (value.length() > 0 && !JavaPsiFacade.getInstance(component.getProject()).getNameHelper().isIdentifier(value)) {
      throw new Exception("Value '" + value + "' is not a valid identifier");
    }

    final RadRootContainer root = (RadRootContainer) FormEditingUtil.getRoot(component);
    final String oldBinding = getValue(component);

    // Check that binding remains unique

    if (value.length() > 0) {
      if (!FormEditingUtil.isBindingUnique(component, value, root)) {
        throw new Exception(UIDesignerLocalize.errorBindingNotUnique().get());
      }

      component.setBinding(value);
      component.setDefaultBinding(false);
    }
    else {
      if (component.isCustomCreateRequired()) {
        throw new Exception(UIDesignerLocalize.errorCustomCreateBindingRequired().get());
      }
      component.setBinding(null);
      component.setCustomCreate(false);
    }

    // Set new value or rename old one. It means that previous binding exists
    // and the new one doesn't exist we need to ask user to create new field
    // or rename old one.

    updateBoundFieldName(root, oldBinding, value, component.getComponentClassName());
  }

  @RequiredUIAccess
  public static void updateBoundFieldName(final RadRootContainer root, final String oldName, final String newName, final String fieldClassName) {
    final String classToBind = root.getClassToBind();
    if (classToBind == null) return;

    final Project project = root.getProject();
    if (newName.length() == 0) {
      checkRemoveUnusedField(root, oldName, FormEditingUtil.getNextSaveUndoGroupId(project));
      return;
    }

    final PsiClass aClass = JavaPsiFacade.getInstance(project).findClass(classToBind, GlobalSearchScope.allScope(project));
    if(aClass == null){
      return;
    }

    if(oldName == null) {
      if (aClass.findFieldByName(newName, true) == null) {
        CreateFieldFix.runImpl(project, root, aClass, fieldClassName, newName, false,
                               FormEditingUtil.getNextSaveUndoGroupId(project));
      }
      return;
    }

    final PsiField oldField = aClass.findFieldByName(oldName, true);
    if(oldField == null){
      return;
    }

    if(aClass.findFieldByName(newName, true) != null) {
      checkRemoveUnusedField(root, oldName, FormEditingUtil.getNextSaveUndoGroupId(project));
      return;
    }

    // Show question to the user

    if (!isFieldUnreferenced(oldField)) {
      int option = Messages.showYesNoDialog(
        project,
        UIDesignerLocalize.messageRenameField(oldName, newName).get(),
        UIDesignerLocalize.titleRename().get(),
        UIUtil.getQuestionIcon()
      );

      if(option != 0/*Yes*/){
        return;
      }
    }

    // Commit document before refactoring starts
    GuiEditor editor = DesignerToolWindowManager.getInstance(project).getActiveFormEditor();
    if (editor != null) {
      editor.refreshAndSave(false);
    }
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, aClass)) {
      return;
    }

    final RenameProcessor processor = new RenameProcessor(project, oldField, newName, true, true);
    processor.run();
  }


  @Override
  public boolean isModified(final RadComponent component) {
    return component.getBinding() != null;
  }

  @Override
  @RequiredUIAccess
  public void resetValue(final RadComponent component) throws Exception {
    setValueImpl(component, "");
  }

  @Override
  public boolean appliesToSelection(final List<RadComponent> selection) {
    return selection.size() == 1;
  }

  @Nullable
  public static PsiField findBoundField(@Nonnull final RadRootContainer root, final String fieldName) {
    final Project project = root.getProject();
    final String classToBind = root.getClassToBind();
    if (classToBind != null) {
      final PsiManager manager = PsiManager.getInstance(project);
      PsiClass aClass = JavaPsiFacade.getInstance(manager.getProject()).findClass(classToBind, GlobalSearchScope.allScope(project));
      if (aClass != null) {
        final PsiField oldBindingField = aClass.findFieldByName(fieldName, false);
        if (oldBindingField != null) {
          return oldBindingField;
        }
      }
    }
    return null;
  }

  @RequiredUIAccess
  public static void checkRemoveUnusedField(final RadRootContainer rootContainer, final String fieldName, final Object undoGroupId) {
    final PsiField oldBindingField = findBoundField(rootContainer, fieldName);
    if (oldBindingField == null) {
      return;
    }
    final Project project = oldBindingField.getProject();
    final PsiClass aClass = oldBindingField.getContainingClass();
    if (isFieldUnreferenced(oldBindingField)) {
      if (!CommonRefactoringUtil.checkReadOnlyStatus(project, aClass)) {
        return;
      }
      CommandProcessor.getInstance().newCommand()
        .project(project)
        .groupId(undoGroupId)
        .name(UIDesignerLocalize.commandDeleteUnusedField())
        .inWriteAction()
        .run(() -> {
          try {
            oldBindingField.delete();
          }
          catch (IncorrectOperationException e) {
            Messages.showErrorDialog(
              project,
              UIDesignerLocalize.errorCannotDeleteUnusedField(e.getMessage()).get(),
              CommonLocalize.titleError().get()
            );
          }
        });
    }
  }

  private static boolean isFieldUnreferenced(final PsiField field) {
    try {
      return ReferencesSearch.search(field).forEach(t -> {
        PsiFile f = t.getElement().getContainingFile();
        if (f != null && f.getFileType().equals(GuiFormFileType.INSTANCE)) {
          return true;
        }
        PsiMethod method = PsiTreeUtil.getParentOfType(t.getElement(), PsiMethod.class);
        if (method != null && method.getName().equals(AsmCodeGenerator.SETUP_METHOD_NAME)) {
          return true;
        }
        return false;
      });
    }
    catch (IndexNotReadyException e) {
      return false;
    }
  }

  public static void checkCreateBindingFromText(final RadComponent component, final String text) {
    if (!component.isDefaultBinding()) {
      return;
    }
    RadRootContainer root = (RadRootContainer)FormEditingUtil.getRoot(component);
    PsiField boundField = findBoundField(root, component.getBinding());
    if (boundField == null || !isFieldUnreferenced(boundField)) {
      return;
    }

    String binding = suggestBindingFromText(component, text);
    if (binding != null) {
      new BindingProperty(component.getProject()).setValueEx(component, binding);
      // keep the binding marked as default
      component.setDefaultBinding(true);
    }
  }

  @Nullable
  public static String suggestBindingFromText(final RadComponent component, String text) {
    if (StringUtil.startsWithIgnoreCase(text, PREFIX_HTML)) {
      text = Pattern.compile("<.+?>").matcher(text).replaceAll("");
    }
    List<String> words = new ArrayList<>(StringUtil.getWordsIn(text));
    if (words.size() > 0) {
      StringBuilder nameBuilder = new StringBuilder(StringUtil.decapitalize(words.get(0)));
      for(int i=1; i<words.size() && i < 4; i++) {
        nameBuilder.append(StringUtil.capitalize(words.get(i)));
      }
      final String shortClassName = StringUtil.capitalize(InsertComponentProcessor.getShortClassName(component.getComponentClassName()));
      if (shortClassName.equalsIgnoreCase(nameBuilder.toString())) {
        // avoid "buttonButton" case
        return null;
      }
      nameBuilder.append(shortClassName);

      RadRootContainer root = (RadRootContainer) FormEditingUtil.getRoot(component);
      Project project = root.getProject();
      String binding = JavaCodeStyleManager.getInstance(project).propertyNameToVariableName(nameBuilder.toString(), VariableKind.FIELD);
      if (FormEditingUtil.findComponentWithBinding(root, binding, component) != null) {
        binding = InsertComponentProcessor.getUniqueBinding(root, nameBuilder.toString());
      }
      return binding;
    }
    return null;
  }

  public static String getDefaultBinding(final RadComponent c) {
    RadRootContainer root = (RadRootContainer) FormEditingUtil.getRoot(c);
    String binding = null;
    String text = FormInspectionUtil.getText(c.getModule(), c);
    if (text != null) {
      binding = suggestBindingFromText(c, text);
    }
    if (binding == null) {
      binding = InsertComponentProcessor.suggestBinding(root, c.getComponentClassName());
    }
    return binding;
  }
}
