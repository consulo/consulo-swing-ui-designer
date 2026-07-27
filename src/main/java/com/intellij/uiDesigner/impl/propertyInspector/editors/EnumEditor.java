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

package com.intellij.uiDesigner.impl.propertyInspector.editors;

import com.intellij.uiDesigner.impl.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.impl.propertyInspector.InplaceContext;
import com.intellij.uiDesigner.impl.radComponents.RadComponent;

import javax.swing.*;

/**
 * @author yole
 */
public class EnumEditor extends PropertyEditor<Enum> {
  private final JComboBox myCbx;

  public EnumEditor(Class enumClass) {
    myCbx = new JComboBox(enumClass.getEnumConstants());
    myCbx.setBorder(BorderFactory.createEmptyBorder());
  }

  @Override
  public Enum getValue() throws Exception {
    return (Enum) myCbx.getSelectedItem();
  }

  @Override
  public JComponent getComponent(RadComponent component, Enum value, InplaceContext inplaceContext) {
    if (value == null) {
      return myCbx;
    }
    ComboBoxModel model = myCbx.getModel();
    for (int i = model.getSize() - 1; i >= 0; i--) {
      if (model.getElementAt(i) == value) {
        myCbx.setSelectedIndex(i);
        return myCbx;
      }
    }
    throw new IllegalArgumentException("unknown value: " + value);
  }

  @Override
  public void updateUI() {
    SwingUtilities.updateComponentTreeUI(myCbx);
    SwingUtilities.updateComponentTreeUI((JComponent)myCbx.getRenderer());
  }
}
