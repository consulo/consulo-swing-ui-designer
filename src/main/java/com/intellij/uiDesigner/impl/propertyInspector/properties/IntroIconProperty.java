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
import com.intellij.uiDesigner.impl.propertyInspector.editors.IconEditor;
import com.intellij.uiDesigner.impl.propertyInspector.renderers.IconRenderer;
import com.intellij.uiDesigner.impl.propertyInspector.renderers.LabelPropertyRenderer;
import com.intellij.uiDesigner.impl.radComponents.RadComponent;
import com.intellij.uiDesigner.lw.IconDescriptor;
import consulo.ide.impl.idea.openapi.module.ResourceFileUtil;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.module.Module;
import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.annotations.NonNls;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import javax.swing.*;
import java.lang.reflect.Method;

/**
 * @author yole
 */
public class IntroIconProperty extends IntrospectedProperty<IconDescriptor> {
  @NonNls private static final String CLIENT_PROPERTY_KEY_PREFIX = "IntroIconProperty_";

  private final LabelPropertyRenderer<IconDescriptor> myRenderer = new IconRenderer();
  private IconEditor myEditor;

  public IntroIconProperty(String name, Method readMethod, Method writeMethod, boolean storeAsClient) {
    super(name, readMethod, writeMethod, storeAsClient);
  }

  public void write(@Nonnull IconDescriptor value, XmlWriter writer) {
    writer.addAttribute(UIFormXmlConstants.ATTRIBUTE_VALUE, value.getIconPath());
  }

  @Nonnull
  public PropertyRenderer<IconDescriptor> getRenderer() {
    return myRenderer;
  }

  @Nullable public PropertyEditor<IconDescriptor> getEditor() {
    if (myEditor == null) {
      myEditor = new IconEditor();
    }
    return myEditor;
  }

  @Override public IconDescriptor getValue(RadComponent component) {
    return (IconDescriptor)component.getDelegee().getClientProperty(CLIENT_PROPERTY_KEY_PREFIX + getName());
  }

  @Override protected void setValueImpl(RadComponent component, IconDescriptor value) throws Exception {
    component.getDelegee().putClientProperty(CLIENT_PROPERTY_KEY_PREFIX + getName(), value);
    if (value != null) {
      ensureIconLoaded(component.getModule(), value);
      invokeSetter(component, value.getIcon());
    }
    else {
      invokeSetter(component, null);
    }
  }

  public static void ensureIconLoaded(Module module, IconDescriptor value) {
    if (value.getIcon() == null) {
      VirtualFile iconFile = ResourceFileUtil.findResourceFileInScope(value.getIconPath(), module.getProject(), GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module, true));
      if (iconFile != null) {
        loadIconFromFile(iconFile, value);
      }
    }
  }

  public static void loadIconFromFile(VirtualFile virtualFile, IconDescriptor descriptor) {
    if (virtualFile != null) {
      try {
        descriptor.setIcon(new ImageIcon(virtualFile.contentsToByteArray()));
      }
      catch (Exception e1) {
        descriptor.setIcon(null);
      }
    }
  }

  @Override public void resetValue(RadComponent component) throws Exception {
    component.getDelegee().putClientProperty(CLIENT_PROPERTY_KEY_PREFIX + getName(), null);
    super.setValueImpl(component, null);
    markTopmostModified(component, false);
  }

}
