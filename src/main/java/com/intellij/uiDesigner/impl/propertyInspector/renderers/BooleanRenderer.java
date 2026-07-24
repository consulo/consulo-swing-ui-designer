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
package com.intellij.uiDesigner.impl.propertyInspector.renderers;

import com.intellij.uiDesigner.impl.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.impl.radComponents.RadRootContainer;
import consulo.ui.ex.awt.UIUtil;

import javax.swing.*;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public class BooleanRenderer extends JCheckBox implements PropertyRenderer<Boolean> {
  @Override
  public JComponent getComponent(RadRootContainer rootContainer, Boolean value, boolean selected, boolean hasFocus){
    // Background and foreground
    if(selected){
      setForeground(UIUtil.getTableSelectionForeground());
      setBackground(UIUtil.getTableSelectionBackground());
    }else{
      setForeground(UIUtil.getTableForeground());
      setBackground(UIUtil.getTableBackground());
    }

    setSelected(value != null && value);

    return this;
  }
}