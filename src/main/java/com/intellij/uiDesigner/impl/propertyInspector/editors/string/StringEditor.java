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
package com.intellij.uiDesigner.impl.propertyInspector.editors.string;

import consulo.project.Project;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.CommonShortcuts;
import consulo.logging.Logger;
import consulo.ui.ex.awt.TextFieldWithBrowseButton;
import consulo.ui.ex.awt.event.DocumentAdapter;
import com.intellij.uiDesigner.impl.UIDesignerBundle;
import com.intellij.uiDesigner.impl.designSurface.GuiEditor;
import com.intellij.uiDesigner.lw.StringDescriptor;
import com.intellij.uiDesigner.impl.propertyInspector.DesignerToolWindowManager;
import com.intellij.uiDesigner.impl.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.impl.propertyInspector.InplaceContext;
import com.intellij.uiDesigner.impl.propertyInspector.properties.IntroStringProperty;
import com.intellij.uiDesigner.impl.radComponents.RadComponent;
import consulo.ui.ex.awt.UIUtil;
import consulo.undoRedo.CommandProcessor;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class StringEditor extends PropertyEditor<StringDescriptor> {
  private static final Logger LOG = Logger.getInstance(StringEditor.class);

  @Nullable private IntroStringProperty myProperty;
  private final TextFieldWithBrowseButton myTfWithButton;
  /* Initial value of string property that was passed into getComponent() method */
  private StringDescriptor myValue;
  private Project myProject;
  private RadComponent myComponent;
  private boolean myTextFieldModified = false;

  public StringEditor(Project project) {
    this(project, null);
  }

  public StringEditor(Project project, IntroStringProperty property) {
    myProject = project;
    myProperty = property;
    myTfWithButton = new TextFieldWithBrowseButton(new MyActionListener());
    myTfWithButton.getTextField().setBorder(null);

    JTextField textField = myTfWithButton.getTextField();
    textField.addActionListener(
      new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          fireValueCommitted(false, false);
        }
      }
    );
    textField.getDocument().addDocumentListener(
      new DocumentAdapter() {
        protected void textChanged(DocumentEvent e) {
          preferredSizeChanged();
          myTextFieldModified = true;
        }
      }
    );

    MyCancelEditingAction cancelEditingAction = new MyCancelEditingAction();
    cancelEditingAction.registerCustomShortcutSet(CommonShortcuts.ESCAPE, myTfWithButton);
  }

  /**
   * @return current preferred size of the editor component
   */
  public Dimension getPreferredSize(){
    return myTfWithButton.getPreferredSize();
  }

  public void updateUI() {
    SwingUtilities.updateComponentTreeUI(myTfWithButton);
  }

  /**
   * Applies specified bundle to the myTfWithBrowseButton
   */
  private void setValue(StringDescriptor descriptor){
    myValue = descriptor;
    JTextField textField = myTfWithButton.getTextField();
    if(descriptor != null){
      String value = descriptor.getValue();
      if(value != null){ // plain value
        textField.setEditable(true);
        textField.setText(value);
        textField.selectAll();
        myTextFieldModified = false;
      }
      else{ // bundled value
        textField.setEditable(false);
        textField.setBackground(UIUtil.getTextFieldBackground());
        textField.setText("[" + descriptor.getKey() + " / " + descriptor.getDottedBundleName() + "]");
      }
    }
    else{
      textField.setEditable(true);
      textField.setText(null);
    }
  }

  public JComponent getPreferredFocusedComponent(@Nonnull JComponent component) {
    return ((TextFieldWithBrowseButton)component).getTextField();
  }

  public JComponent getComponent(RadComponent component, StringDescriptor value, InplaceContext inplaceContext) {
    myComponent = component;
    setValue(value);

    myTfWithButton.getTextField().setBorder(null);
    if (inplaceContext != null && inplaceContext.isStartedByTyping()) {
      myTfWithButton.setText(Character.toString(inplaceContext.getStartChar()));
    }

    return myTfWithButton;
  }

  public StringDescriptor getValue(){
    if(myValue == null || (myValue.getValue() != null && myTextFieldModified)) {
      // editor is for "trivial" StringDescriptor
      String value = myTfWithButton.getText();
      if (myValue == null && value.length() == 0) {
        return null;
      }
      else{
        StringDescriptor stringDescriptor = StringDescriptor.create(value);
        if (myValue != null && myValue.isNoI18n()) {
          stringDescriptor.setNoI18n(true);
        }
        return stringDescriptor;
      }
    }
    else{ // editor is for "bundled" StringDescriptor
      return myValue;
    }
  }

  private final class MyCancelEditingAction extends AnAction{
    public void actionPerformed(AnActionEvent e) {
      fireEditingCancelled();
    }
  }

  private final class MyActionListener implements ActionListener{
    public void actionPerformed(ActionEvent e) {
      // 1. Show editor dialog

      final GuiEditor guiEditor = DesignerToolWindowManager.getInstance(myProject).getActiveFormEditor();
      LOG.assertTrue(guiEditor != null);

      final StringEditorDialog dialog = new StringEditorDialog(
        myTfWithButton.getTextField(),
        getValue(), // we have pass here "live" (modified) value
        guiEditor.getStringDescriptorLocale(),
        guiEditor
      );

      CommandProcessor.getInstance().executeCommand(
        myProject,
        new Runnable() {
          public void run() {
            if (!guiEditor.ensureEditable()) {
              return;
            }
            dialog.show();
            if(!dialog.isOK()){
              return;
            }

            // 2. Apply new value
            StringDescriptor descriptor = dialog.getDescriptor();
            if(descriptor == null){
              return;
            }
            setValue(descriptor);
            fireValueCommitted(true, false);
            if (myProperty != null) {
              myProperty.refreshValue(myComponent);
            }
            guiEditor.refreshAndSave(false);
          }
        }, UIDesignerBundle.message("command.edit.string.property"), null); 
    }
  }
}