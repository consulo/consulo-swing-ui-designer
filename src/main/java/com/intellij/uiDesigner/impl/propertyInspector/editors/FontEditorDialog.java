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

import com.intellij.uiDesigner.impl.propertyInspector.properties.IntroFontProperty;
import com.intellij.uiDesigner.lw.FontDescriptor;
import consulo.project.Project;
import consulo.ui.ex.SimpleTextAttributes;
import consulo.ui.ex.awt.ColoredListCellRenderer;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.ui.ex.awt.UIUtil;
import consulo.uiDesigner.impl.localize.UIDesignerLocalize;
import jakarta.annotation.Nonnull;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;

/**
 * @author yole
 */
public class FontEditorDialog extends DialogWrapper {
  private JList<String> myFontNameList;
  private JList<String> myFontStyleList;
  private JList<String> myFontSizeList;
  private JPanel myRootPane;
  private JLabel myPreviewTextLabel;
  private JTextField myFontNameEdit;
  private JTextField myFontStyleEdit;
  private JSpinner myFontSizeEdit;
  private JList<FontDescriptor> mySwingFontList;
  private JTabbedPane myTabbedPane;
  private JCheckBox myFontNameCheckbox;
  private JCheckBox myFontStyleCheckbox;
  private JCheckBox myFontSizeCheckbox;
  private FontDescriptor myValue;

  protected FontEditorDialog(Project project, String propertyName) {
    super(project, false);
    init();
    setTitle(UIDesignerLocalize.fontChooserTitle(propertyName));
    myFontNameList.setListData(UIUtil.getValidFontNames(true));
    myFontNameList.addListSelectionListener(new MyListSelectionListener(myFontNameEdit));
    myFontStyleList.setListData(new String[] {
      UIDesignerLocalize.fontChooserRegular().get(),
      UIDesignerLocalize.fontChooserBold().get(),
      UIDesignerLocalize.fontChooserItalic().get(),
      UIDesignerLocalize.fontChooserBoldItalic().get()
    });
    myFontStyleList.addListSelectionListener(new MyListSelectionListener(myFontStyleEdit));
    myFontSizeList.setListData(UIUtil.getStandardFontSizes());
    myFontSizeList.addListSelectionListener(e -> {
      Integer selValue = Integer.valueOf(myFontSizeList.getSelectedValue());
      myFontSizeEdit.setValue(selValue);
      updateValue();
    });
    myFontSizeEdit.setModel(new SpinnerNumberModel(3, 3, 96, 1));
    myFontSizeEdit.addChangeListener(e -> {
      myFontSizeList.setSelectedValue(myFontSizeEdit.getValue().toString(), true);
      updateValue();
    });
    mySwingFontList.setListData(collectSwingFontDescriptors());
    mySwingFontList.setCellRenderer(new ColoredListCellRenderer() {
      @Override
      protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
        FontDescriptor descriptor = (FontDescriptor) value;
        clear();
        append(descriptor.getSwingFont(),
               selected ? SimpleTextAttributes.SELECTED_SIMPLE_CELL_ATTRIBUTES : SimpleTextAttributes.SIMPLE_CELL_ATTRIBUTES);
        append(" (" + fontToString(UIManager.getFont(descriptor.getSwingFont())) + ")",
               selected ? SimpleTextAttributes.SELECTED_SIMPLE_CELL_ATTRIBUTES : SimpleTextAttributes.GRAYED_ATTRIBUTES);
      }
    });
    mySwingFontList.addListSelectionListener(e -> {
      myValue = mySwingFontList.getSelectedValue();
      updatePreview();
      //showFont(myValue.getResolvedFont());
    });

    myFontNameCheckbox.addChangeListener(e -> {
      myFontNameList.setEnabled(myFontNameCheckbox.isSelected());
      updateValue();
    });
    myFontStyleCheckbox.addItemListener(e -> {
      myFontStyleList.setEnabled(myFontStyleCheckbox.isSelected());
      updateValue();
    });
    myFontSizeCheckbox.addChangeListener(e -> {
      myFontSizeList.setEnabled(myFontSizeCheckbox.isSelected());
      myFontSizeEdit.setEnabled(myFontSizeCheckbox.isSelected());
      updateValue();
    });
  }

  private static String fontToString(Font font) {
    StringBuilder result = new StringBuilder(font.getFamily());
    result.append(" ").append(font.getSize());
    if ((font.getStyle() & Font.BOLD) != 0) {
      result.append(" ").append(UIDesignerLocalize.fontChooserBold().get());
    }
    if ((font.getStyle() & Font.ITALIC) != 0) {
      result.append(" ").append(UIDesignerLocalize.fontChooserItalic().get());
    }
    return result.toString();
  }

  private static FontDescriptor[] collectSwingFontDescriptors() {
    ArrayList<FontDescriptor> result = new ArrayList<>();
    UIDefaults defaults = UIManager.getDefaults();
    Enumeration e = defaults.keys ();
    while(e.hasMoreElements()) {
      Object key = e.nextElement();
      Object value = defaults.get(key);
      if (key instanceof String && value instanceof Font) {
        result.add(FontDescriptor.fromSwingFont((String) key));
      }
    }
    Collections.sort(result, (o1, o2) -> o1.getSwingFont().compareTo(o2.getSwingFont()));
    return result.toArray(new FontDescriptor[result.size()]);
  }

  public FontDescriptor getValue() {
    return myValue;
  }

  public void setValue(@Nonnull FontDescriptor value) {
    myValue = value;
    if (value.getSwingFont() != null) {
      myTabbedPane.setSelectedIndex(1);
      mySwingFontList.setSelectedValue(myValue, true);
    }
    else {
      myFontNameCheckbox.setSelected(value.getFontName() != null);
      myFontSizeCheckbox.setSelected(value.getFontSize() >= 0);
      myFontStyleCheckbox.setSelected(value.getFontStyle() >= 0);
      myFontNameList.setSelectedValue(value.getFontName(), true);
      myFontStyleList.setSelectedIndex(value.getFontStyle());
      if (value.getFontSize() >= 0) {
        myFontSizeList.setSelectedValue(Integer.toString(value.getFontSize()), true);
        if (myFontSizeList.getSelectedIndex() < 0) {
          myFontSizeEdit.setValue(value.getFontSize());
        }
      }
      else {
        myFontSizeList.setSelectedIndex(-1);
        myFontSizeEdit.setValue(0);
      }
    }
  }

  private void updateValue() {
    int fontSize = (Integer) myFontSizeEdit.getValue();
    myValue = new FontDescriptor(myFontNameCheckbox.isSelected() ? myFontNameList.getSelectedValue() : null,
                                 myFontStyleCheckbox.isSelected() ? myFontStyleList.getSelectedIndex() : -1,
                                 myFontSizeCheckbox.isSelected() ? fontSize : -1);
    updatePreview();
  }

  private void updatePreview() {
    myPreviewTextLabel.setText(IntroFontProperty.descriptorToString(myValue));
    myPreviewTextLabel.setFont(myValue.getResolvedFont(myRootPane.getFont()));
  }

  @Override
  protected JComponent createCenterPanel() {
    return myRootPane;
  }

  private class MyListSelectionListener implements ListSelectionListener {
    private final JTextField myTextField;

    public MyListSelectionListener(JTextField textField) {
      myTextField = textField;
    }

    @Override
    public void valueChanged(ListSelectionEvent e) {
      JList sourceList = (JList) e.getSource();
      Object selValue = sourceList.getSelectedValue();
      if (selValue != null) {
        myTextField.setText(selValue.toString());
      }
      else {
        myTextField.setText("");
      }
      updateValue();
    }
  }
}
