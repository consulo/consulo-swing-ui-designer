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
package com.intellij.uiDesigner.impl.propertyInspector.editors;

import com.intellij.uiDesigner.impl.FormEditingUtil;
import com.intellij.uiDesigner.impl.propertyInspector.InplaceContext;
import com.intellij.uiDesigner.impl.propertyInspector.renderers.ComponentRenderer;
import com.intellij.uiDesigner.impl.radComponents.RadComponent;
import com.intellij.uiDesigner.impl.radComponents.RadContainer;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * @author yole
 */
public class ComponentEditor extends ComboBoxPropertyEditor<String> {
  private final Class myPropertyType;
  private final Predicate<RadComponent> myFilter;
  private String myOldValue;

  public ComponentEditor(Class propertyType, Predicate<RadComponent> filter) {
    myPropertyType = propertyType;
    myFilter = filter;
    myCbx.setRenderer(new ComponentRenderer());
  }

  @Override
  public JComponent getComponent(RadComponent component, String value, InplaceContext inplaceContext) {
    RadComponent[] components = collectFilteredComponents(component);
    // components [0] = null (<none>)
    myCbx.setModel(new DefaultComboBoxModel(components));
    myOldValue = value;
    if (value == null || myOldValue.length() == 0) {
      myCbx.setSelectedIndex(0);
    }
    else {
      for(int i=1; i<components.length; i++) {
        if (components [i].getId().equals(value)) {
          myCbx.setSelectedIndex(i);
          break;
        }
      }
    }
    return myCbx;
  }

  protected RadComponent[] collectFilteredComponents(final RadComponent component) {
    List<RadComponent> result = new ArrayList<>();
    result.add(null);

    RadContainer container = component.getParent();
    while(container.getParent() != null) {
      container = container.getParent();
    }

    FormEditingUtil.iterate(container, component1 -> {
      RadComponent radComponent = (RadComponent) component1;
      final JComponent delegee = radComponent.getDelegee();
      if (!myPropertyType.isInstance(delegee)) {
        return true;
      }
      if (myFilter == null || myFilter.test(radComponent)) {
        result.add(radComponent);
      }
      return true;
    });

    return result.toArray(new RadComponent[result.size()]);
  }

  @Override
  public String getValue() throws Exception {
    final RadComponent selection = (RadComponent)myCbx.getSelectedItem();
    if (selection == null) {
      return myOldValue == null ? null : "";
    }
    return selection.getId();
  }
}
