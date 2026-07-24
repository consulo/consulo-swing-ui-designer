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

import com.intellij.uiDesigner.impl.FormEditingUtil;
import com.intellij.uiDesigner.impl.radComponents.RadComponent;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.impl.propertyInspector.Property;
import com.intellij.uiDesigner.impl.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.impl.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.impl.propertyInspector.editors.BooleanEditor;
import com.intellij.uiDesigner.impl.propertyInspector.renderers.BooleanRenderer;
import com.intellij.uiDesigner.impl.propertyInspector.renderers.SizePolicyRenderer;
import org.jetbrains.annotations.NonNls;
import jakarta.annotation.Nonnull;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public abstract class SizePolicyProperty extends Property<RadComponent, Integer> {
  private final Property[] myChildren;
  private final SizePolicyRenderer myRenderer;

  public SizePolicyProperty(@NonNls String name){
    super(null, name);
    myChildren=new Property[]{
      new MyBooleanProperty("Can Shrink", GridConstraints.SIZEPOLICY_CAN_SHRINK),
      new MyBooleanProperty("Can Grow", GridConstraints.SIZEPOLICY_CAN_GROW),
      new MyBooleanProperty("Want Grow", GridConstraints.SIZEPOLICY_WANT_GROW)
    };
    myRenderer=new SizePolicyRenderer();
  }

  protected abstract int getValueImpl(GridConstraints constraints);

  protected abstract void setValueImpl(GridConstraints constraints,int policy);

  public final Integer getValue(RadComponent component) {
    return getValueImpl(component.getConstraints());
  }

  protected final void setValueImpl(RadComponent component, Integer value) throws Exception {
    setValueImpl(component.getConstraints(), value.intValue());
  }

  @Nonnull
  public final Property[] getChildren(RadComponent component){
    return myChildren;
  }

  @Nonnull
  public final PropertyRenderer<Integer> getRenderer(){
    return myRenderer;
  }

  public final PropertyEditor<Integer> getEditor(){
    return null;
  }

  @Override public boolean isModified(RadComponent component) {
    GridConstraints defaultConstraints = FormEditingUtil.getDefaultConstraints(component);
    return getValueImpl(component.getConstraints()) != getValueImpl(defaultConstraints);
  }

  @Override public void resetValue(RadComponent component) throws Exception {
    setValueImpl(component, getValueImpl(FormEditingUtil.getDefaultConstraints(component)));
  }

  /**
   * Subproperty for "can shrink" bit
   */
  private class MyBooleanProperty extends Property<RadComponent, Boolean> {
    private final BooleanRenderer myRenderer;
    private final BooleanEditor myEditor;
    private final int myPropertyMask;

    public MyBooleanProperty(@NonNls String name, int propertyMask) {
      super(SizePolicyProperty.this, name);
      myPropertyMask = propertyMask;
      myRenderer=new BooleanRenderer();
      myEditor=new BooleanEditor();
    }

    public final Boolean getValue(RadComponent component) {
      GridConstraints constraints=component.getConstraints();
      return (getValueImpl(constraints) & myPropertyMask) != 0;
    }

    protected final void setValueImpl(RadComponent component, Boolean value) throws Exception{
      boolean canShrink=value.booleanValue();
      int newValue=getValueImpl(component.getConstraints());
      if(canShrink){
        newValue|=myPropertyMask;
      }else{
        newValue&=~myPropertyMask;
      }
      SizePolicyProperty.this.setValueImpl(component.getConstraints(),newValue);
    }

    @Nonnull
    public final PropertyRenderer<Boolean> getRenderer(){
      return myRenderer;
    }

    public final PropertyEditor<Boolean> getEditor(){
      return myEditor;
    }
  }
}
