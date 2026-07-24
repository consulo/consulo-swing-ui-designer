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

import com.intellij.uiDesigner.compiler.Utils;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.impl.palette.ComponentItem;
import com.intellij.uiDesigner.impl.propertyInspector.Property;
import com.intellij.uiDesigner.impl.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.impl.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.impl.propertyInspector.editors.IntEnumEditor;
import com.intellij.uiDesigner.impl.propertyInspector.renderers.IntEnumRenderer;
import com.intellij.uiDesigner.impl.radComponents.RadComponent;
import consulo.uiDesigner.impl.localize.UIDesignerLocalize;
import jakarta.annotation.Nonnull;

/**
 * @author yole
 */
public abstract class AlignProperty extends Property<RadComponent, Integer> {
  private final boolean myHorizontal;
  private IntEnumRenderer myRenderer;
  private IntEnumEditor myEditor;

  public AlignProperty(boolean horizontal) {
    super(null, horizontal ? "Horizontal Align" : "Vertical Align");
    myHorizontal = horizontal;
  }

  @Override
  public Integer getValue(RadComponent component) {
    AlignPropertyProvider provider = getAlignPropertyProvider(component);
    if (provider != null) {
      return provider.getAlignment(component, myHorizontal);
    }
    return Utils.alignFromConstraints(component.getConstraints(), myHorizontal);
  }

  private static AlignPropertyProvider getAlignPropertyProvider(RadComponent component) {
    if (component.getParent().getLayoutManager() instanceof AlignPropertyProvider) {
      return ((AlignPropertyProvider) component.getParent().getLayoutManager());
    }
    return null;
  }

  @Override
  protected void setValueImpl(RadComponent component, Integer value) throws Exception {
    int anchorMask = myHorizontal ? 0x0C : 3;
    int fillMask = myHorizontal ? 1 : 2;
    int anchor = 0;
    int fill = 0;
    switch(value) {
      case GridConstraints.ALIGN_FILL:
        fill = myHorizontal ? GridConstraints.FILL_HORIZONTAL : GridConstraints.FILL_VERTICAL;
        break;
      case GridConstraints.ALIGN_LEFT:
        anchor = myHorizontal ? GridConstraints.ANCHOR_WEST : GridConstraints.ANCHOR_NORTH;
        break;
      case GridConstraints.ALIGN_RIGHT:
        anchor = myHorizontal ? GridConstraints.ANCHOR_EAST : GridConstraints.ANCHOR_SOUTH;
        break;
    }
    GridConstraints gc = component.getConstraints();
    GridConstraints oldGC = (GridConstraints) gc.clone();
    gc.setAnchor((gc.getAnchor() & ~anchorMask) | anchor);
    gc.setFill((gc.getFill() & ~fillMask) | fill);
    AlignPropertyProvider provider = getAlignPropertyProvider(component);
    if (provider != null) {
      provider.setAlignment(component, myHorizontal, value);
    }
    component.fireConstraintsChanged(oldGC);
  }

  @Override
  public boolean isModified(RadComponent component) {
    AlignPropertyProvider provider = getAlignPropertyProvider(component);
    if (provider != null) {
      return provider.isAlignmentModified(component, myHorizontal);
    }
    ComponentItem item = component.getPalette().getItem(component.getComponentClassName());
    if (item == null) return false;
    return Utils.alignFromConstraints(component.getConstraints(), myHorizontal) !=
           Utils.alignFromConstraints(item.getDefaultConstraints(), myHorizontal);
  }

  @Override
  public void resetValue(RadComponent component) throws Exception {
    AlignPropertyProvider provider = getAlignPropertyProvider(component);
    if (provider != null) {
      provider.resetAlignment(component, myHorizontal);
    }
    else {
      ComponentItem item = component.getPalette().getItem(component.getComponentClassName());
      if (item != null) {
        setValueEx(component, Utils.alignFromConstraints(item.getDefaultConstraints(), myHorizontal));
      }
    }
  }

  @Nonnull
  @Override
  public PropertyRenderer<Integer> getRenderer() {
    if (myRenderer == null) {
      myRenderer = new IntEnumRenderer(getPairs());
    }
    return myRenderer;
  }

  private IntEnumEditor.Pair[] getPairs() {
    return new IntEnumEditor.Pair[] {
      new IntEnumEditor.Pair(
        GridConstraints.ALIGN_LEFT,
        myHorizontal ? UIDesignerLocalize.propertyLeft().get() : UIDesignerLocalize.propertyTop().get()
      ),
      new IntEnumEditor.Pair(GridConstraints.ALIGN_CENTER, UIDesignerLocalize.propertyCenter().get()),
      new IntEnumEditor.Pair(
        GridConstraints.ALIGN_RIGHT,
        myHorizontal ? UIDesignerLocalize.propertyRight().get() : UIDesignerLocalize.propertyBottom().get()
      ),
      new IntEnumEditor.Pair(GridConstraints.ALIGN_FILL, UIDesignerLocalize.propertyFill().get())
    };
  }

  @Override
  public PropertyEditor<Integer> getEditor() {
    if (myEditor == null) {
      myEditor = new IntEnumEditor(getPairs());
    }
    return myEditor;
  }
}
