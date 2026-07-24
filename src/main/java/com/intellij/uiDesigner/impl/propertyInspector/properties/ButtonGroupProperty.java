/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.uiDesigner.impl.FormEditingUtil;
import com.intellij.uiDesigner.impl.propertyInspector.InplaceContext;
import com.intellij.uiDesigner.impl.propertyInspector.Property;
import com.intellij.uiDesigner.impl.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.impl.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.impl.propertyInspector.editors.ComboBoxPropertyEditor;
import com.intellij.uiDesigner.impl.propertyInspector.renderers.LabelPropertyRenderer;
import com.intellij.uiDesigner.impl.radComponents.RadButtonGroup;
import com.intellij.uiDesigner.impl.radComponents.RadComponent;
import com.intellij.uiDesigner.impl.radComponents.RadRootContainer;
import consulo.ui.ex.awt.ListCellRendererWrapper;
import consulo.uiDesigner.impl.localize.UIDesignerLocalize;
import jakarta.annotation.Nonnull;

import javax.swing.*;
import java.awt.event.ItemEvent;

/**
 * @author yole
 */
public class ButtonGroupProperty extends Property<RadComponent, RadButtonGroup> {
  private final LabelPropertyRenderer<RadButtonGroup> myRenderer = new LabelPropertyRenderer<>() {
    @Override protected void customize(@Nonnull RadButtonGroup value) {
      setText(value.getName());
    }
  };

  private final ComboBoxPropertyEditor<RadButtonGroup> myEditor = new MyPropertyEditor();

  public ButtonGroupProperty() {
    super(null, "Button Group");
  }

  @Override
  public RadButtonGroup getValue(RadComponent component) {
    RadRootContainer rootContainer = (RadRootContainer) FormEditingUtil.getRoot(component);
    return rootContainer == null ? null : (RadButtonGroup) FormEditingUtil.findGroupForComponent(rootContainer, component);
  }

  @Override
  protected void setValueImpl(RadComponent component, RadButtonGroup value) throws Exception {
    RadRootContainer radRootContainer = (RadRootContainer) FormEditingUtil.getRoot(component);
    assert radRootContainer != null;
    radRootContainer.setGroupForComponent(component, value);
  }

  @Nonnull
  @Override
  public PropertyRenderer<RadButtonGroup> getRenderer() {
    return myRenderer;
  }

  @Override
  public PropertyEditor<RadButtonGroup> getEditor() {
    return myEditor;
  }

  @Override public boolean isModified(RadComponent component) {
    return getValue(component) != null;
  }

  @Override public void resetValue(RadComponent component) throws Exception {
    setValueImpl(component, null);
  }

  private static class MyPropertyEditor extends ComboBoxPropertyEditor<RadButtonGroup> {
    private RadRootContainer myRootContainer;
    private RadComponent myComponent;

    public MyPropertyEditor() {
      myCbx.setRenderer(new ListCellRendererWrapper<>() {
        @Override
        public void customize(JList list, RadButtonGroup value, int index, boolean selected, boolean hasFocus) {
          if (value == null) {
            setText(UIDesignerLocalize.buttonGroupNone().get());
          }
          else if (value == RadButtonGroup.NEW_GROUP) {
            setText(UIDesignerLocalize.buttonGroupNew().get());
          }
          else {
            setText(value.getName());
          }
        }
      });

      myCbx.addItemListener(e -> {
        if (e.getStateChange() == ItemEvent.SELECTED && e.getItem() == RadButtonGroup.NEW_GROUP) {
          String newGroupName = myRootContainer.suggestGroupName();
          newGroupName = (String)JOptionPane.showInputDialog(
            myCbx,
            UIDesignerLocalize.buttonGroupNamePrompt().get(),
            UIDesignerLocalize.buttonGroupNameTitle().get(),
            JOptionPane.QUESTION_MESSAGE,
            null,
            null,
            newGroupName
          );
          if (newGroupName != null) {
            RadButtonGroup group = myRootContainer.createGroup(newGroupName);
            myRootContainer.setGroupForComponent(myComponent, group);
            updateModel();
          }
        }
      });
    }

    @Override
    public JComponent getComponent(RadComponent component, RadButtonGroup value, InplaceContext inplaceContext) {
      myComponent = component;
      myRootContainer = (RadRootContainer) FormEditingUtil.getRoot(myComponent);
      updateModel();
      return myCbx;
    }

    private void updateModel() {
      RadButtonGroup[] groups = myRootContainer.getButtonGroups();
      RadButtonGroup[] allGroups = new RadButtonGroup[groups.length+2];
      System.arraycopy(groups, 0, allGroups, 1, groups.length);
      allGroups [allGroups.length-1] = RadButtonGroup.NEW_GROUP;
      myCbx.setModel(new DefaultComboBoxModel<>(allGroups));
      myCbx.setSelectedItem(FormEditingUtil.findGroupForComponent(myRootContainer, myComponent));
    }
  }
}
