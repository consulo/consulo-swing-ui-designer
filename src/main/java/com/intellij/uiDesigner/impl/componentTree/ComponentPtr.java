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
package com.intellij.uiDesigner.impl.componentTree;

import jakarta.annotation.Nonnull;

import com.intellij.uiDesigner.impl.FormEditingUtil;
import com.intellij.uiDesigner.impl.radComponents.RadComponent;
import com.intellij.uiDesigner.impl.radComponents.RadContainer;
import com.intellij.uiDesigner.impl.designSurface.GuiEditor;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class ComponentPtr{
  private final GuiEditor myEditor;
  private final String myId;
  private RadComponent myComponent;

  /**
   * @param component
   */
  public ComponentPtr(@Nonnull GuiEditor editor, @Nonnull RadComponent component) {
    this(editor, component, true);
  }

  /**
   * @param component
   * @param validate
   */
  public ComponentPtr(@Nonnull GuiEditor editor, @Nonnull RadComponent component, boolean validate){
    myEditor=editor;
    myId=component.getId();

    if (validate) {
      validate();
      if(!isValid()){
      throw new IllegalArgumentException("invalid component: "+component);
      }
    }
    else {
      myComponent = component;
    }
  }

  /**
   * @return <code>RadComponent</code> which was calculated by the last
   * <code>validate</code> method.
   */
  public RadComponent getComponent(){
    return myComponent;
  }

  /**
   * @return <code>true</code> if and only if the pointer is valid.
   * It means that last <code>validate</code> call was successful and
   * pointer refers to live component.
   */
  public boolean isValid(){
    return myComponent!=null;
  }

  /**
   * Validates (updates) the state of the pointer
   */
  public void validate(){
    // Try to find component with myId starting from root container
    RadContainer container=myEditor.getRootContainer();
    myComponent= (RadComponent)FormEditingUtil.findComponent(container,myId);
  }

  public boolean equals(Object obj){
    if(!(obj instanceof ComponentPtr)){
      return false;
    }
    return myId.equals(((ComponentPtr)obj).myId);
  }

  public int hashCode(){
    return myId.hashCode();
  }
}
