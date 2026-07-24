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

import com.intellij.uiDesigner.impl.propertyInspector.IntrospectedProperty;
import com.intellij.uiDesigner.impl.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.impl.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.impl.propertyInspector.editors.IntEditor;
import com.intellij.uiDesigner.impl.propertyInspector.renderers.LabelPropertyRenderer;
import com.intellij.uiDesigner.impl.snapShooter.SnapshotContext;
import com.intellij.uiDesigner.impl.radComponents.RadComponent;
import com.intellij.uiDesigner.impl.SwingProperties;
import jakarta.annotation.Nonnull;

import javax.swing.*;
import java.lang.reflect.Method;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class IntroIntProperty extends IntrospectedProperty<Integer> {
  private PropertyRenderer<Integer> myRenderer;
  private PropertyEditor<Integer> myEditor;

  public IntroIntProperty(String name, Method readMethod, Method writeMethod, boolean storeAsClient) {
    this(name, readMethod, writeMethod, null, null, storeAsClient);
  }

  public IntroIntProperty(String name,
                          Method readMethod,
                          Method writeMethod,
                          PropertyRenderer<Integer> renderer,
                          PropertyEditor<Integer> editor,
                          boolean storeAsClient){
    super(name, readMethod, writeMethod, storeAsClient);
    myRenderer = renderer;
    myEditor = editor;
  }

  @Nonnull
  public PropertyRenderer<Integer> getRenderer() {
    if (myRenderer == null) {
      myRenderer = new LabelPropertyRenderer<>();
    }
    return myRenderer;
  }

  public PropertyEditor<Integer> getEditor() {
    if (myEditor == null) {
      myEditor = new IntEditor(Integer.MIN_VALUE);
    }
    return myEditor;
  }

  @Override
  public void importSnapshotValue(SnapshotContext context, JComponent component, RadComponent radComponent) {
    // exclude property from snapshot import to avoid exceptions because of not imported model 
    if (!getName().equals(SwingProperties.SELECTED_INDEX)) {
      super.importSnapshotValue(context, component, radComponent);
    }
  }
}
