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

import com.intellij.uiDesigner.core.UIFormXmlConstants;
import com.intellij.uiDesigner.impl.XmlWriter;
import com.intellij.uiDesigner.impl.propertyInspector.IntrospectedProperty;
import com.intellij.uiDesigner.impl.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.impl.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.impl.propertyInspector.editors.ListModelEditor;
import com.intellij.uiDesigner.impl.propertyInspector.renderers.LabelPropertyRenderer;
import com.intellij.uiDesigner.impl.radComponents.RadComponent;
import com.intellij.uiDesigner.impl.snapShooter.SnapshotContext;
import consulo.util.collection.ArrayUtil;
import org.jetbrains.annotations.NonNls;

import jakarta.annotation.Nonnull;
import javax.swing.*;
import java.lang.reflect.Method;

/**
 * @author yole
 */
public class IntroListModelProperty extends IntrospectedProperty<String[]> {
  private LabelPropertyRenderer<String[]> myRenderer;
  private ListModelEditor myEditor;
  @NonNls private static final String CLIENT_PROPERTY_KEY_PREFIX = "IntroListModelProperty_";

  public IntroListModelProperty(String name, Method readMethod, Method writeMethod, boolean storeAsClient) {
    super(name, readMethod, writeMethod, storeAsClient);
  }

  public void write(String[] value, XmlWriter writer) {
    for(String s: value) {
      writer.startElement(UIFormXmlConstants.ELEMENT_ITEM);
      writer.addAttribute(UIFormXmlConstants.ATTRIBUTE_VALUE, s);
      writer.endElement();
    }
  }

  @Nonnull
  public PropertyRenderer<String[]> getRenderer() {
    if (myRenderer == null) {
      myRenderer = new MyRenderer();
    }
    return myRenderer;
  }

  public PropertyEditor<String[]> getEditor() {
    if (myEditor == null) {
      myEditor = new ListModelEditor(getName());
    }
    return myEditor;
  }

  @Override public String[] getValue(RadComponent component) {
    String[] strings = (String[])component.getDelegee().getClientProperty(CLIENT_PROPERTY_KEY_PREFIX + getName());
    if (strings == null) {
      return ArrayUtil.EMPTY_STRING_ARRAY;
    }
    return strings;
  }

  @Override protected void setValueImpl(RadComponent component, String[] value) throws Exception {
    component.getDelegee().putClientProperty(CLIENT_PROPERTY_KEY_PREFIX + getName(), value);
    DefaultComboBoxModel model = new DefaultComboBoxModel();
    for(String s: value) {
      model.addElement(s);
    }
    invokeSetter(component, model);
  }

  @Override
  public void importSnapshotValue(SnapshotContext context, JComponent component, RadComponent radComponent) {
    ListModel listModel;
    try {
      listModel = (ListModel)myReadMethod.invoke(component, EMPTY_OBJECT_ARRAY);
    }
    catch (Exception e) {
      return;
    }
    if (listModel == null || listModel.getSize() == 0) return;
    String[] values = new String [listModel.getSize()];
    for(int i=0; i<listModel.getSize(); i++) {
      Object value = listModel.getElementAt(i);
      if (!(value instanceof String)) {
        return;
      }
      values [i] = (String) value;
    }
    try {
      setValue(radComponent, values);
    }
    catch (Exception e) {
      // ignore
    }
  }

  private static class MyRenderer extends LabelPropertyRenderer<String[]> {
    @Override protected void customize(String[] value) {
      setText(ListModelEditor.listValueToString(value));
    }
  }
}
