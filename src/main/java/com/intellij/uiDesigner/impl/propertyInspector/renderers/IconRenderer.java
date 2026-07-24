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

import com.intellij.uiDesigner.lw.IconDescriptor;
import com.intellij.uiDesigner.impl.propertyInspector.properties.IntroIconProperty;
import com.intellij.uiDesigner.impl.radComponents.RadRootContainer;
import jakarta.annotation.Nonnull;

import javax.swing.*;
import java.awt.*;

/**
 * @author yole
*/
public class IconRenderer extends LabelPropertyRenderer<IconDescriptor> {
  @Override
  public JLabel getComponent(RadRootContainer rootContainer, IconDescriptor iconDescriptor,
                             boolean selected, boolean hasFocus) {
    if (iconDescriptor != null) {
      IntroIconProperty.ensureIconLoaded(rootContainer.getModule(), iconDescriptor);
    }
    JLabel component = super.getComponent(rootContainer, iconDescriptor, selected, hasFocus);
    if (!selected && iconDescriptor != null && iconDescriptor.getIcon() == null) {
      setForeground(Color.RED);
    }
    return component;
  }

  protected void customize(@Nonnull IconDescriptor value) {
    setIcon(value.getIcon());
    setText(value.getIconPath());
  }
}
