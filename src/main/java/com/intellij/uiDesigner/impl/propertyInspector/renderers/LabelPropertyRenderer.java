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
import jakarta.annotation.Nonnull;

import javax.swing.*;

/**
 * This is convenient class for implementing property renderers which
 * are based on JLabel.
 *
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public class LabelPropertyRenderer<V> extends JLabel implements PropertyRenderer<V> {
  private String myStaticText;

  public LabelPropertyRenderer() {
    setOpaque(true);
    putClientProperty("html.disable", true);
    setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 0));
  }

  public LabelPropertyRenderer(String staticText) {
    this();
    myStaticText = staticText;
  }

  public JLabel getComponent(RadRootContainer rootContainer, V value, boolean selected, boolean hasFocus){
    // Reset text and icon
    setText(null);
    setIcon(null);

    // Background and foreground
    if(selected){
      setForeground(UIUtil.getTableSelectionForeground());
      setBackground(UIUtil.getTableSelectionBackground());
    }else{
      setForeground(UIUtil.getTableForeground());
      setBackground(UIUtil.getTableBackground());
    }

    if (value != null) {
      customize(value);
    }

    return this;
  }

  /**
   * Here all subclasses should customize their text, icon and other
   * attributes. Note, that background and foreground colors are already
   * set.
   */
  protected void customize(@Nonnull V value) {
    setText(myStaticText != null ? myStaticText : value.toString());
  }
}