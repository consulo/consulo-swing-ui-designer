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
package com.intellij.uiDesigner.impl.inspections;

import com.intellij.uiDesigner.core.SupportCode;
import com.intellij.uiDesigner.impl.FormEditingUtil;
import com.intellij.uiDesigner.impl.StringDescriptorManager;
import com.intellij.uiDesigner.impl.SwingProperties;
import com.intellij.uiDesigner.impl.designSurface.GuiEditor;
import com.intellij.uiDesigner.impl.propertyInspector.properties.IntroStringProperty;
import com.intellij.uiDesigner.impl.quickFixes.QuickFix;
import com.intellij.uiDesigner.impl.radComponents.RadComponent;
import com.intellij.uiDesigner.impl.radComponents.RadContainer;
import com.intellij.uiDesigner.lw.IProperty;
import com.intellij.uiDesigner.lw.StringDescriptor;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.awt.Messages;
import consulo.ui.ex.awt.UIUtil;
import consulo.uiDesigner.impl.localize.UIDesignerLocalize;
import consulo.util.collection.ArrayUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class AssignMnemonicFix extends QuickFix {
  public AssignMnemonicFix(final GuiEditor editor, final RadComponent component, final String name) {
    super(editor, name, component);
  }

  @Override
  @RequiredUIAccess
  public void run() {
    IProperty textProperty = FormInspectionUtil.findProperty(myComponent, SwingProperties.TEXT);
    StringDescriptor descriptor = (StringDescriptor) textProperty.getPropertyValue(myComponent);
    String value = StringDescriptorManager.getInstance(myComponent.getModule()).resolve(myComponent, descriptor);
    String[] variants = fillMnemonicVariants(SupportCode.parseText(value).myText);
      String result = Messages.showEditableChooseDialog(
      UIDesignerLocalize.inspectionMissingMnemonicsQuickfixPrompt().get(),
      UIDesignerLocalize.inspectionMissingMnemonicsQuickfixTitle().get(),
      UIUtil.getQuestionIcon(),
      variants,
      variants[0],
      null
    );
    if (result != null) {
      if (!myEditor.ensureEditable()) {
        return;
      }
      FormInspectionUtil.updateStringPropertyValue(myEditor, myComponent, (IntroStringProperty)textProperty, descriptor, result);
    }
  }

  private String[] fillMnemonicVariants(final String value) {
    final StringBuffer usedMnemonics = new StringBuffer();
    RadContainer container = myComponent.getParent();
    if (container != null) {
      while (container.getParent() != null) {
        container = container.getParent();
      }
      FormEditingUtil.iterate(container, component -> {
        SupportCode.TextWithMnemonic twm = DuplicateMnemonicInspection.getTextWithMnemonic(myEditor.getModule(), component);
        if (twm != null) {
          usedMnemonics.append(twm.getMnemonicChar());
        }
        return true;
      });
    }

    List<String> variants = new ArrayList<>();
    // try upper-case and word start characters
    for(int i=0; i<value.length(); i++) {
      final char ch = value.charAt(i);
      if (i == 0 || Character.isUpperCase(ch) || (i > 0 && value.charAt(i-1) == ' ')) {
        if (Character.isLetter(ch) && usedMnemonics.indexOf(String.valueOf(ch).toUpperCase()) < 0) {
          variants.add(value.substring(0, i) + "&" + value.substring(i));
        }
      }
    }

    if (variants.size() == 0) {
      // try any unused characters
      for(int i=0; i<value.length(); i++) {
        final char ch = value.charAt(i);
        if (Character.isLetter(ch) && usedMnemonics.indexOf(String.valueOf(ch).toUpperCase()) < 0) {
          variants.add(value.substring(0, i) + "&" + value.substring(i));
        }
      }
    }

    if (variants.size() == 0) {
      variants.add(value);
    }
    return ArrayUtil.toStringArray(variants);
  }
}
