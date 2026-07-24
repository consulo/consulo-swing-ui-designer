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

package com.intellij.uiDesigner.impl.radComponents;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.uiDesigner.compiler.Utils;
import com.intellij.uiDesigner.core.UIFormXmlConstants;
import com.intellij.uiDesigner.impl.*;
import com.intellij.uiDesigner.lw.CompiledClassPropertiesProvider;
import com.intellij.uiDesigner.lw.LwRootContainer;
import consulo.document.Document;
import consulo.document.FileDocumentManager;
import consulo.ide.impl.idea.openapi.module.ResourceFileUtil;
import consulo.logging.Logger;
import consulo.virtualFileSystem.VirtualFile;

import jakarta.annotation.Nonnull;
import javax.swing.*;
import java.awt.*;

/**
 * @author yole
 */
public class RadNestedForm extends RadComponent {
  private static final Logger LOG = Logger.getInstance(RadNestedForm.class);

  private final String myFormFileName;
  private final RadRootContainer myRootContainer;

  public RadNestedForm(ModuleProvider module, String formFileName, String id) throws Exception {
    super(module, JPanel.class, id);
    myFormFileName = formFileName;
    LOG.debug("Loading nested form " + formFileName);
    VirtualFile formFile = ResourceFileUtil.findResourceFileInDependents(getModule(), formFileName);
    if (formFile == null) {
      throw new IllegalArgumentException("Couldn't find virtual file for nested form " + formFileName);
    }
    Document doc = FileDocumentManager.getInstance().getDocument(formFile);
    ClassLoader classLoader = LoaderFactory.getInstance(getProject()).getLoader(formFile);
    LwRootContainer rootContainer = Utils.getRootContainer(doc.getText(), new CompiledClassPropertiesProvider(classLoader));
    myRootContainer = XmlReader.createRoot(module, rootContainer, classLoader, null);
    if (myRootContainer.getComponentCount() > 0) {
      getDelegee().setLayout(new BorderLayout());
      JComponent nestedFormDelegee = myRootContainer.getComponent(0).getDelegee();
      getDelegee().add(nestedFormDelegee, BorderLayout.CENTER);

      setRadComponentRecursive(nestedFormDelegee);
    }

    if (isCustomCreateRequired()) {
      setCustomCreate(true);
    }
  }

  private void setRadComponentRecursive(JComponent component) {
    component.putClientProperty(CLIENT_PROP_RAD_COMPONENT, this);
    for (int i = 0; i < component.getComponentCount(); i++) {
      Component child = component.getComponent(i);
      if (child instanceof JComponent) {
        setRadComponentRecursive((JComponent)child);
      }
    }
  }

  public void write(XmlWriter writer) {
    writer.startElement(UIFormXmlConstants.ELEMENT_NESTED_FORM);
    try {
      writeId(writer);
      writer.addAttribute(UIFormXmlConstants.ATTRIBUTE_FORM_FILE, myFormFileName);
      writeBinding(writer);
      writeConstraints(writer);
    }
    finally {
      writer.endElement(); // component
    }
  }

  @Override
  @Nonnull
  public String getComponentClassName() {
    return myRootContainer.getClassToBind();
  }

  @Override
  public boolean hasIntrospectedProperties() {
    return false;
  }

  @Override
  public boolean isCustomCreateRequired() {
    if (super.isCustomCreateRequired()) return true;
    PsiClass boundClass = FormEditingUtil.findClassToBind(getModule(), myRootContainer.getClassToBind());
    return isNonStaticInnerClass(boundClass);
  }

  private static boolean isNonStaticInnerClass(PsiClass boundClass) {
    if (boundClass == null) return false;
    return PsiUtil.isInnerClass(boundClass) || isNonStaticInnerClass(boundClass.getContainingClass());
  }
}
