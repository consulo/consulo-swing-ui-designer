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

import com.intellij.uiDesigner.core.AbstractLayout;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.impl.propertyInspector.Property;
import com.intellij.uiDesigner.impl.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.impl.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.impl.propertyInspector.editors.BooleanEditor;
import com.intellij.uiDesigner.impl.propertyInspector.renderers.BooleanRenderer;
import com.intellij.uiDesigner.impl.radComponents.RadContainer;
import org.jetbrains.annotations.NonNls;
import jakarta.annotation.Nonnull;

import java.awt.*;

/**
 * @author yole
 */
public abstract class AbstractGridLayoutProperty extends Property<RadContainer, Boolean> {
  protected final BooleanRenderer myRenderer = new BooleanRenderer();
  protected final BooleanEditor myEditor = new BooleanEditor();

  public AbstractGridLayoutProperty(final Property parent, @Nonnull @NonNls final String name) {
    super(parent, name);
  }

  @Override
  public Boolean getValue(final RadContainer component) {
    final LayoutManager layoutManager = component.getLayout();
    if (!(layoutManager instanceof GridLayoutManager)) return null;
    final GridLayoutManager gridLayoutManager = (GridLayoutManager) layoutManager;
    return getGridLayoutPropertyValue(gridLayoutManager);
  }

  @Override
  protected void setValueImpl(final RadContainer component, final Boolean value) throws Exception {
    final AbstractLayout layoutManager=(AbstractLayout) component.getLayout();
    if (!(layoutManager instanceof GridLayoutManager)) {
      throw new IllegalArgumentException("grid layout expected: "+layoutManager);
    }
    final GridLayoutManager gridLayoutManager = (GridLayoutManager)layoutManager;
    setGridLayoutPropertyValue(gridLayoutManager, value.booleanValue());
  }

  protected abstract boolean getGridLayoutPropertyValue(GridLayoutManager gridLayoutManager);

  protected abstract void setGridLayoutPropertyValue(GridLayoutManager gridLayoutManager, boolean booleanValue);

  @Nonnull
  @Override
  public PropertyRenderer<Boolean> getRenderer(){
    return myRenderer;
  }

  @Override
  public PropertyEditor<Boolean> getEditor(){
    return myEditor;
  }

  @Override public boolean isModified(final RadContainer component) {
    final Boolean value = getValue(component);
    return value != null && value;
  }

  @Override public void resetValue(RadContainer component) throws Exception {
    setValueImpl(component, Boolean.FALSE);
  }
}
