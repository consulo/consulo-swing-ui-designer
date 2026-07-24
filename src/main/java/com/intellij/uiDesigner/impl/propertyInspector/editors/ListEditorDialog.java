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

import consulo.ui.ex.awt.DialogWrapper;
import consulo.project.Project;
import com.intellij.uiDesigner.impl.UIDesignerBundle;
import consulo.util.collection.ArrayUtil;
import consulo.util.lang.StringUtil;
import org.jetbrains.annotations.NonNls;

import jakarta.annotation.Nullable;
import javax.swing.*;

/**
 * @author yole
 */
public class ListEditorDialog extends DialogWrapper
{
  private JPanel myRootPanel;
  private JTextArea myLinesTextArea;

  protected ListEditorDialog(Project project, String propertyName) {
    super(project, true);
    init();
    setTitle(UIDesignerBundle.message("list.editor.title", propertyName));
  }

  @Nullable
  protected JComponent createCenterPanel() {
    return myRootPanel;
  }

  @Override @NonNls
  protected String getDimensionServiceKey() {
    return "UIDesigner.ListEditorDialog";
  }

  public String[] getValue() {
    String text = myLinesTextArea.getText();
    if (text.length() == 0) return ArrayUtil.EMPTY_STRING_ARRAY;
    return text.split("\n");
  }

  public void setValue(String[] value) {
    myLinesTextArea.setText(value == null ? "" : StringUtil.join(value, "\n"));
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myLinesTextArea;
  }
}
