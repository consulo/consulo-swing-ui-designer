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
package com.intellij.uiDesigner.impl.wizard;

import consulo.ui.ex.SimpleTextAttributes;
import consulo.ui.ex.awt.ColoredTableCellRenderer;
import com.intellij.uiDesigner.lw.LwComponent;
import com.intellij.uiDesigner.impl.palette.ComponentItem;
import com.intellij.uiDesigner.impl.palette.Palette;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.ui.image.Image;
import com.intellij.uiDesigner.impl.UIDesignerIcons;

import jakarta.annotation.Nonnull;
import javax.swing.*;
import java.awt.*;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
final class FormPropertyTableCellRenderer extends ColoredTableCellRenderer{
  private static final Logger LOG = Logger.getInstance(FormPropertyTableCellRenderer.class);

  private final Palette myPalette;
  private final SimpleTextAttributes myAttrs1;
  private final SimpleTextAttributes myAttrs2;
  private final SimpleTextAttributes myAttrs3;

  FormPropertyTableCellRenderer(@Nonnull Project project) {
    myPalette = Palette.getInstance(project);
    myAttrs1 = SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES;
    myAttrs2 = SimpleTextAttributes.REGULAR_ATTRIBUTES;
    myAttrs3 = new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, Color.GRAY);

    setFocusBorderAroundIcon(true);
  }

  protected void customizeCellRenderer(
    JTable table,
    Object value,
    boolean selected,
    boolean hasFocus,
    int row,
    int column
  ) {
    if (value == null) {
      return;
    }
    FormProperty property = (FormProperty)value;

    LwComponent component = property.getLwComponent();

    // Icon
    Image icon;
    String fqClassName = component.getComponentClassName();
    ComponentItem item = myPalette.getItem(fqClassName);
    if (item != null) {
      icon = item.getSmallIcon();
    }
    else {
      icon = UIDesignerIcons.Unknown;
    }
    setIcon(icon);

    // Binding
    append(component.getBinding(), myAttrs1);

    // Component class name and package
    String shortClassName;
    String packageName;
    int lastDotIndex = fqClassName.lastIndexOf('.');
    if (lastDotIndex != -1) {
      shortClassName = fqClassName.substring(lastDotIndex + 1);
      packageName = fqClassName.substring(0, lastDotIndex);
    }
    else { // default package
      shortClassName = fqClassName;
      packageName = null;
    }

    LOG.assertTrue(shortClassName.length() > 0);

    append(" ", myAttrs2); /*small gap between icon and class name*/
    append(shortClassName, myAttrs2);

    if (packageName != null) {
      append(" (", myAttrs3);
      append(packageName, myAttrs3);
      append(")", myAttrs3);
    }
  }
}
