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

import com.intellij.uiDesigner.impl.XmlWriter;
import com.intellij.uiDesigner.impl.SwingProperties;
import com.intellij.uiDesigner.impl.snapShooter.SnapshotContext;
import com.intellij.uiDesigner.impl.radComponents.RadComponent;
import com.intellij.uiDesigner.impl.propertyInspector.IntrospectedProperty;
import com.intellij.uiDesigner.impl.propertyInspector.Property;
import com.intellij.uiDesigner.impl.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.impl.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.impl.propertyInspector.editors.IntRegexEditor;
import com.intellij.uiDesigner.impl.propertyInspector.renderers.DimensionRenderer;
import jakarta.annotation.Nonnull;

import javax.swing.*;
import java.awt.Dimension;
import java.lang.reflect.Method;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class IntroDimensionProperty extends IntrospectedProperty<Dimension> {
  private final Property[] myChildren;
  private final DimensionRenderer myRenderer;
  private final IntRegexEditor<Dimension> myEditor;

  public IntroDimensionProperty(String name, Method readMethod, Method writeMethod, boolean storeAsClient){
    super(name, readMethod, writeMethod, storeAsClient);
    myChildren = new Property[]{
      new IntFieldProperty(this, "width", -1, new Dimension(0, 0)),
      new IntFieldProperty(this, "height", -1, new Dimension(0, 0)),
    };
    myRenderer = new DimensionRenderer();
    myEditor = new IntRegexEditor<Dimension>(Dimension.class, myRenderer, new int[] { -1, -1 });
  }

  @Override
  public void write(@Nonnull Dimension value, XmlWriter writer) {
    writer.addAttribute("width", value.width);
    writer.addAttribute("height", value.height);
  }

  @Nonnull
  public Property[] getChildren(RadComponent component) {
    return myChildren;
  }

  @Nonnull
  public PropertyRenderer<Dimension> getRenderer() {
    return myRenderer;
  }

  public PropertyEditor<Dimension> getEditor() {
    return myEditor;
  }

  @Override
  public void importSnapshotValue(SnapshotContext context, JComponent component, RadComponent radComponent) {
    if (getName().equals(SwingProperties.MINIMUM_SIZE) ||
        getName().equals(SwingProperties.MAXIMUM_SIZE) ||
        getName().equals(SwingProperties.PREFERRED_SIZE)) {
      return;
    }
    super.importSnapshotValue(context, component, radComponent);
  }
}
