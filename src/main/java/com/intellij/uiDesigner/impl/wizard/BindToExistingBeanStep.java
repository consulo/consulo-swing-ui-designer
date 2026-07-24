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
package com.intellij.uiDesigner.impl.wizard;

import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiType;
import com.intellij.java.language.psi.util.PropertyUtil;
import consulo.ide.impl.idea.ide.wizard.StepAdapter;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.ui.ex.awt.ComboBox;
import consulo.uiDesigner.impl.localize.UIDesignerLocalize;
import consulo.util.collection.ArrayUtil;
import jakarta.annotation.Nonnull;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
final class BindToExistingBeanStep extends StepAdapter
{
	private static final Logger LOG = Logger.getInstance(BindToExistingBeanStep.class);

	private JScrollPane myScrollPane;
	private JTable myTable;
	private final WizardData myData;
	private final MyTableModel myTableModel;
	private JCheckBox myChkIsModified;
	private JCheckBox myChkGetData;
	private JCheckBox myChkSetData;
	private JPanel myPanel;

	BindToExistingBeanStep(@Nonnull WizardData data)
	{
		myData = data;
		myTableModel = new MyTableModel();
		myTable.setModel(myTableModel);
		myTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		myTable.getColumnModel().setColumnSelectionAllowed(true);
		myScrollPane.getViewport().setBackground(myTable.getBackground());
		myTable.setSurrendersFocusOnKeystroke(true);

		// Customize "Form Property" column
		{
			TableColumn column = myTable.getColumnModel().getColumn(0/*Form Property*/);
			column.setCellRenderer(new FormPropertyTableCellRenderer(myData.myProject));
		}

		// Customize "Bean Property" column
		{
			TableColumn column = myTable.getColumnModel().getColumn(1/*Bean Property*/);
			column.setCellRenderer(new BeanPropertyTableCellRenderer());
			MyTableCellEditor cellEditor = new MyTableCellEditor();
			column.setCellEditor(cellEditor);

			DefaultCellEditor editor = (DefaultCellEditor) myTable.getDefaultEditor(Object.class);
			editor.setClickCountToStart(1);

			myTable.setRowHeight(cellEditor.myCbx.getPreferredSize().height);
		}

		myChkGetData.setSelected(true);
		myChkGetData.setEnabled(false);
		myChkSetData.setSelected(true);
		myChkSetData.setEnabled(false);
		myChkIsModified.setSelected(myData.myGenerateIsModified);
	}

	@Override
    public JComponent getComponent()
	{
		return myPanel;
	}

	@Override
    public void _init()
	{
		// Check that data is correct
		LOG.assertTrue(!myData.myBindToNewBean);
		LOG.assertTrue(myData.myBeanClass != null);
		myTableModel.fireTableDataChanged();
	}

	@Override
    public void _commit(boolean finishChosen)
	{
		// Stop editing if any
		TableCellEditor cellEditor = myTable.getCellEditor();
		if(cellEditor != null)
		{
			cellEditor.stopCellEditing();
		}

		myData.myGenerateIsModified = myChkIsModified.isSelected();

		// TODO[vova] check that at least one binding field exists
	}

	private final class MyTableModel extends AbstractTableModel
	{
		private final LocalizeValue[] myColumnNames = {
            UIDesignerLocalize.columnFormField(),
            UIDesignerLocalize.columnBeanProperty()
        };

		@Override
        public int getColumnCount()
		{
			return myColumnNames.length;
		}

		@Override
        public String getColumnName(int column)
		{
			return myColumnNames[column].get();
		}

		@Override
        public int getRowCount()
		{
			return myData.myBindings.length;
		}

		@Override
        public boolean isCellEditable(int row, int column)
		{
			return column == 1/*Bean Property*/;
		}

		@Override
        public Object getValueAt(int row, int column)
		{
			if(column == 0/*Form Property*/)
			{
				return myData.myBindings[row].myFormProperty;
			}
			else if(column == 1/*Bean Property*/)
			{
				return myData.myBindings[row].myBeanProperty;
			}
			else
			{
				throw new IllegalArgumentException("unknown column: " + column);
			}
		}

		@Override
        public void setValueAt(Object value, int row, int column)
		{
			LOG.assertTrue(column == 1/*Bean Property*/);
			FormProperty2BeanProperty binding = myData.myBindings[row];
			binding.myBeanProperty = (BeanProperty) value;
		}
	}

	private final class MyTableCellEditor extends AbstractCellEditor implements TableCellEditor
	{
		private final ComboBox<BeanProperty> myCbx;
		/* -1 if not defined*/
		private int myEditingRow;

		public MyTableCellEditor()
		{
			myCbx = new ComboBox<>();
			myCbx.setEditable(true);
			myCbx.setRenderer(new BeanPropertyListCellRenderer());
			myCbx.putClientProperty("tableCellEditor", this);
			myCbx.putClientProperty("JComboBox.isTableCellEditor", Boolean.TRUE);

			JComponent editorComponent = (JComponent) myCbx.getEditor().getEditorComponent();
			editorComponent.setBorder(null);

			myEditingRow = -1;
		}

		/**
		 * @return whether it's possible to convert <code>type1</code> into <code>type2</code>
		 * and vice versa.
		 */
		private boolean canConvert(String type1, String type2)
		{
			if("boolean".equals(type1) || "boolean".equals(type2))
			{
				return type1.equals(type2);
			}
			else
			{
				return true;
			}
		}

		@Override
        public Component getTableCellEditorComponent(
            JTable table,
            Object value,
            boolean isSelected,
            int row,
            int column
		)
        {
			myEditingRow = row;
			DefaultComboBoxModel model = (DefaultComboBoxModel) myCbx.getModel();
			model.removeAllElements();
			model.addElement(null/*<not defined>*/);

			// Fill combobox with available bean's properties
			String[] rProps = PropertyUtil.getReadableProperties(myData.myBeanClass, true);
			String[] wProps = PropertyUtil.getWritableProperties(myData.myBeanClass, true);
			List<BeanProperty> rwProps = new ArrayList<>();

			outer:
			for(int i = rProps.length - 1; i >= 0; i--)
			{
				String propName = rProps[i];
				if(ArrayUtil.find(wProps, propName) != -1)
				{
					LOG.assertTrue(!rwProps.contains(propName));
					PsiMethod getter = PropertyUtil.findPropertyGetter(myData.myBeanClass, propName, false, true);
					if(getter == null)
					{
						// possible if the getter is static: getReadableProperties() does not filter out static methods, and
						// findPropertyGetter() checks for static/non-static
						continue;
					}
					PsiType returnType = getter.getReturnType();
					LOG.assertTrue(returnType != null);

					// There are two possible types: boolean and java.lang.String
					String typeName = returnType.getCanonicalText();
					LOG.assertTrue(typeName != null);
					if(!"boolean".equals(typeName) && !"java.lang.String".equals(typeName))
					{
						continue;
					}

					// Check that the property is not in use yet
					for(int j = myData.myBindings.length - 1; j >= 0; j--)
					{
						BeanProperty _property = myData.myBindings[j].myBeanProperty;
						if(j != row && _property != null && propName.equals(_property.myName))
						{
							continue outer;
						}
					}

					// Check that we conver types
					if(
							!canConvert(
									myData.myBindings[row].myFormProperty.getComponentPropertyClassName(),
									typeName
							)
					)
					{
						continue;
					}

					rwProps.add(new BeanProperty(propName, typeName));
				}
			}

			Collections.sort(rwProps);

			for(BeanProperty rwProp : rwProps)
			{
				model.addElement(rwProp);
			}

			// Set initially selected item
			if(myData.myBindings[row].myBeanProperty != null)
			{
				myCbx.setSelectedItem(myData.myBindings[row].myBeanProperty);
			}
			else
			{
				myCbx.setSelectedIndex(0/*<not defined>*/);
			}

			return myCbx;
		}

		@Override
        public Object getCellEditorValue()
		{
			LOG.assertTrue(myEditingRow != -1);
			try
			{
				// our ComboBox is editable so its editor can contain:
				// 1) BeanProperty object (it user just selected something from ComboBox)
				// 2) java.lang.String if user type something into ComboBox

				Object selectedItem = myCbx.getEditor().getItem();
				if(selectedItem instanceof BeanProperty)
				{
					return selectedItem;
				}
				else if(selectedItem instanceof String)
				{
					String fieldName = ((String) selectedItem).trim();

					if(fieldName.length() == 0)
					{
						return null; // binding is not defined
					}

					String fieldType = myData.myBindings[myEditingRow].myFormProperty.getComponentPropertyClassName();
					return new BeanProperty(fieldName, fieldType);
				}
				else
				{
					throw new IllegalArgumentException("unknown selectedItem: " + selectedItem);
				}
			}
			finally
			{
				myEditingRow = -1; // unset editing row. So it's possible to invoke this method only once per editing
			}
		}
	}
}
