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

import com.intellij.java.language.impl.JavaFileType;
import com.intellij.java.language.psi.JavaCodeFragmentFactory;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiJavaPackage;
import com.intellij.java.language.util.TreeClassChooser;
import com.intellij.java.language.util.TreeClassChooserFactory;
import com.intellij.uiDesigner.impl.FormEditingUtil;
import com.intellij.uiDesigner.impl.propertyInspector.InplaceContext;
import com.intellij.uiDesigner.impl.propertyInspector.Property;
import com.intellij.uiDesigner.impl.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.impl.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.impl.propertyInspector.renderers.ClassToBindRenderer;
import com.intellij.uiDesigner.impl.radComponents.RadComponent;
import com.intellij.uiDesigner.impl.radComponents.RadRootContainer;
import consulo.document.Document;
import consulo.language.editor.ui.awt.EditorTextField;
import consulo.language.psi.PsiCodeFragment;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.module.content.ProjectFileIndex;
import consulo.module.content.ProjectRootManager;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.CommonShortcuts;
import consulo.ui.ex.awt.ComponentWithBrowseButton;
import consulo.uiDesigner.impl.localize.UIDesignerLocalize;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class ClassToBindProperty extends Property<RadRootContainer, String> {
  private final ClassToBindRenderer myRenderer;
  private final MyEditor myEditor;

  public ClassToBindProperty(Project project) {
    super(null, "bind to class");
    myRenderer = new ClassToBindRenderer();
    myEditor = new MyEditor(project);
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
  public String getValue(RadRootContainer component) {
    return component.getClassToBind();
  }

  @Override
  protected void setValueImpl(RadRootContainer component, String value) throws Exception {
    String className = value;

    if (className != null && className.length() == 0) {
      className = null;
    }

    component.setClassToBind(className);
  }

  private final class MyEditor extends PropertyEditor<String> {
    private final EditorTextField myEditorTextField;
    private Document myDocument;
    private final ComponentWithBrowseButton<EditorTextField> myTfWithButton;
    private String myInitialValue;
    private final Project myProject;
    private final ClassToBindProperty.MyEditor.MyActionListener myActionListener;

    public MyEditor(final Project project) {
      myProject = project;
      myEditorTextField = new EditorTextField("", project, JavaFileType.INSTANCE) {
        @Override
        protected boolean shouldHaveBorder() {
          return false;
        }
      };
      myActionListener = new MyActionListener();
      myTfWithButton = new ComponentWithBrowseButton<>(myEditorTextField, myActionListener);
      myEditorTextField.setBorder(null);
      new MyCancelEditingAction().registerCustomShortcutSet(CommonShortcuts.ESCAPE, myTfWithButton);
      /*
      myEditorTextField.addActionListener(
        new ActionListener() {
          public void actionPerformed(final ActionEvent e) {
            fireValueCommitted();
          }
        }
      );
      */
    }

    @Override
    public String getValue() throws Exception {
      String value = myDocument.getText();
      if (value.length() == 0 && myInitialValue == null) {
        return null;
      }
      return value.replace('$', '.'); // PSI works only with dots
    }

    @Override
    public JComponent getComponent(RadComponent component, String value, InplaceContext inplaceContext) {
      myInitialValue = value;
      setEditorText(value != null ? value : "");
      myActionListener.setComponent(component);
      return myTfWithButton;
    }

    private void setEditorText(String s) {
      JavaCodeFragmentFactory factory = JavaCodeFragmentFactory.getInstance(myProject);
      PsiJavaPackage defaultPackage = JavaPsiFacade.getInstance(myProject).findPackage("");
      PsiCodeFragment fragment = factory.createReferenceCodeFragment(s, defaultPackage, true, true);
      myDocument = PsiDocumentManager.getInstance(myProject).getDocument(fragment);
      myEditorTextField.setDocument(myDocument);
    }

    @Override
    public void updateUI() {
      SwingUtilities.updateComponentTreeUI(myTfWithButton);
    }

    private final class MyActionListener implements ActionListener{
      RadComponent myComponent;

      public void setComponent(RadComponent component) {
        myComponent = component;
      }

      @Override
      @RequiredUIAccess
      public void actionPerformed(ActionEvent e){
        String className = myEditorTextField.getText();
        PsiClass aClass = FormEditingUtil.findClassToBind(myComponent.getModule(), className);

        Project project = myComponent.getProject();
        ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
        TreeClassChooser chooser = TreeClassChooserFactory.getInstance(project).createWithInnerClassesScopeChooser(
          UIDesignerLocalize.titleChooseClassToBind().get(),
          GlobalSearchScope.projectScope(project),
          thisClass -> { // we need show classes from the sources roots only
            VirtualFile vFile = thisClass.getContainingFile().getVirtualFile();
            return vFile != null && fileIndex.isInSource(vFile);
          },
          aClass
        );
        chooser.showDialog();

        PsiClass result = chooser.getSelected();
        if (result != null) {
          setEditorText(result.getQualifiedName());
        }

        myEditorTextField.requestFocus(); // todo[anton] make it via providing proper parent
      }
    }

    private final class MyCancelEditingAction extends AnAction
	{
      @Override
      @RequiredUIAccess
      public void actionPerformed(AnActionEvent e) {
        fireEditingCancelled();
      }
    }
  }
}
