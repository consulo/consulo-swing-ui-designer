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

import com.intellij.uiDesigner.impl.propertyInspector.Property;
import com.intellij.uiDesigner.impl.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.impl.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.impl.propertyInspector.editors.IntRegexEditor;
import com.intellij.uiDesigner.impl.propertyInspector.renderers.InsetsPropertyRenderer;
import com.intellij.uiDesigner.impl.radComponents.RadComponent;
import jakarta.annotation.Nonnull;

import java.awt.*;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public abstract class AbstractInsetsProperty<T extends RadComponent> extends Property<T, Insets> {
  private final Property[] myChildren;
  private final InsetsPropertyRenderer myRenderer;
  private IntRegexEditor<Insets> myEditor;

  public AbstractInsetsProperty(String name) {
    this(null, name);
  }

  public AbstractInsetsProperty(Property parent, String name){
    super(parent, name);
    myChildren=new Property[]{
      new IntFieldProperty(this, "top", 0, new Insets(0, 0, 0, 0)),
      new IntFieldProperty(this, "left", 0, new Insets(0, 0, 0, 0)),
      new IntFieldProperty(this, "bottom", 0, new Insets(0, 0, 0, 0)),
      new IntFieldProperty(this, "right", 0, new Insets(0, 0, 0, 0)),
    };
    myRenderer=new InsetsPropertyRenderer();
  }

  @Nonnull
  @Override
  public final Property[] getChildren(RadComponent component) {
    return myChildren;
  }

  @Nonnull
  @Override
  public final PropertyRenderer<Insets> getRenderer() {
    return myRenderer;
  }

  @Override
  public final PropertyEditor<Insets> getEditor() {
    if (myEditor == null) {
      myEditor = new IntRegexEditor<>(Insets.class, myRenderer, new int[] { 0, 0, 0, 0 }) {
        @Override
        public Insets getValue() throws Exception {
          // if a single number has been entered, interpret it as same value for all parts (IDEADEV-7330)
          try {
            int value = Integer.parseInt(myTf.getText());
            Insets insets = new Insets(value, value, value, value);
            myTf.setText(myRenderer.formatText(insets));
            return insets;
          }
          catch(NumberFormatException ex) {
            return super.getValue();
          }
        }
      };
    }
    return myEditor;
  }
}
