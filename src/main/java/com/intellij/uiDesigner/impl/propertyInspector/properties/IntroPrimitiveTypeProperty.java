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
import com.intellij.uiDesigner.impl.propertyInspector.editors.PrimitiveTypeEditor;
import com.intellij.uiDesigner.impl.propertyInspector.renderers.LabelPropertyRenderer;
import jakarta.annotation.Nonnull;

import java.lang.reflect.Method;

/**
 * @author yole
 */
public class IntroPrimitiveTypeProperty<T> extends IntrospectedProperty<T> {
  private LabelPropertyRenderer<T> myRenderer;
  private PropertyEditor<T> myEditor;
  private final Class<T> myClass;

  public IntroPrimitiveTypeProperty(String name, Method readMethod, Method writeMethod, boolean storeAsClient,
                                    Class<T> aClass){
    super(name, readMethod, writeMethod, storeAsClient);
    myClass = aClass;
  }

  @Nonnull
  public PropertyRenderer<T> getRenderer(){
    if (myRenderer == null) {
      myRenderer = new LabelPropertyRenderer<>();
    }
    return myRenderer;
  }

  public PropertyEditor<T> getEditor(){
    if (myEditor == null) {
      myEditor = createEditor();
    }
    return myEditor;
  }

  protected PropertyEditor<T> createEditor() {
    return new PrimitiveTypeEditor<T>(myClass);
  }
}