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

import consulo.util.lang.Comparing;
import com.intellij.uiDesigner.impl.XmlWriter;
import com.intellij.uiDesigner.lw.ColorDescriptor;
import com.intellij.uiDesigner.impl.propertyInspector.IntrospectedProperty;
import com.intellij.uiDesigner.impl.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.impl.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.impl.propertyInspector.editors.ColorEditor;
import com.intellij.uiDesigner.impl.propertyInspector.renderers.ColorRenderer;
import com.intellij.uiDesigner.impl.radComponents.RadComponent;
import com.intellij.uiDesigner.impl.snapShooter.SnapshotContext;
import org.jetbrains.annotations.NonNls;
import jakarta.annotation.Nonnull;

import jakarta.annotation.Nullable;
import javax.swing.*;
import java.awt.Color;
import java.lang.reflect.Method;

/**
 * @author yole
 */
public class IntroColorProperty extends IntrospectedProperty<ColorDescriptor> {
  private ColorRenderer myColorRenderer = null;
  private ColorEditor myColorEditor = null;
  @NonNls private static final String CLIENT_PROPERTY_KEY_PREFIX = "IntroColorProperty_";

  public IntroColorProperty(String name, Method readMethod, Method writeMethod, boolean storeAsClient) {
    super(name, readMethod, writeMethod, storeAsClient);
  }

  @Nonnull
  public PropertyRenderer<ColorDescriptor> getRenderer() {
    if (myColorRenderer == null) {
      myColorRenderer = new ColorRenderer();
    }
    return myColorRenderer;
  }

  @Nullable
  public PropertyEditor<ColorDescriptor> getEditor() {
    if (myColorEditor == null) {
      myColorEditor = new ColorEditor(getName());
    }
    return myColorEditor;
  }

  public void write(@Nonnull ColorDescriptor value, XmlWriter writer) {
    writer.writeColorDescriptor(value);
  }

  @Override public ColorDescriptor getValue(RadComponent component) {
    ColorDescriptor colorDescriptor = (ColorDescriptor)component.getDelegee().getClientProperty(CLIENT_PROPERTY_KEY_PREFIX + getName());
    if (colorDescriptor == null) {
      return new ColorDescriptor((Color) invokeGetter(component));
    }
    return colorDescriptor;
  }

  @Override protected void setValueImpl(RadComponent component, ColorDescriptor value) throws Exception {
    component.getDelegee().putClientProperty(CLIENT_PROPERTY_KEY_PREFIX + getName(), value);
    if (value != null && value.isColorSet()) {
      invokeSetter(component, value.getResolvedColor());
    }
  }

  @Override public void resetValue(RadComponent component) throws Exception {
    super.resetValue(component);
    component.getDelegee().putClientProperty(CLIENT_PROPERTY_KEY_PREFIX + getName(), null);
  }

  @Override
  public void importSnapshotValue(SnapshotContext context, JComponent component, RadComponent radComponent) {
    try {
      if (component.getParent() != null) {
        Color componentColor = (Color) myReadMethod.invoke(component, EMPTY_OBJECT_ARRAY);
        Color parentColor = (Color) myReadMethod.invoke(component.getParent(), EMPTY_OBJECT_ARRAY);
        ColorDescriptor defaultColor = getDefaultValue(component);
        if (componentColor != null && !Comparing.equal(componentColor, parentColor) && !Comparing.equal(componentColor, defaultColor)) {
          setValue(radComponent, new ColorDescriptor(componentColor));
        }
      }
    }
    catch (Exception e) {
      // ignore
    }
  }
}
