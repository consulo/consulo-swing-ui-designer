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

import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiNameHelper;
import consulo.ide.impl.idea.ide.wizard.CommitStepException;
import consulo.ide.impl.idea.ide.wizard.StepAdapter;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.uiDesigner.impl.localize.UIDesignerLocalize;
import jakarta.annotation.Nonnull;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableColumn;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
final class BindToNewBeanStep extends StepAdapter
{
	private static final Logger LOG = Logger.getInstance(BindToNewBeanStep.class);

	private JScrollPane myScrollPane;
	private JTable myTable;
	private final WizardData myData;
	private final MyTableModel myTableModel;
	private JCheckBox myChkIsModified;
	private JCheckBox myChkSetData;
	private JCheckBox myChkGetData;
	private JPanel myPanel;

	BindToNewBeanStep(@Nonnull WizardData data)
	{
		myData = data;
		myTableModel = new MyTableModel();
		myTable.setModel(myTableModel);
		myTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
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
			column.setCellEditor(new BeanPropertyTableCellEditor());

			DefaultCellEditor editor = (DefaultCellEditor) myTable.getDefaultEditor(Object.class);
			editor.setClickCountToStart(1);
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
		LOG.assertTrue(myData.myBindToNewBean);
		myTableModel.fireTableDataChanged();
	}

	@Override
    public void _commit(boolean finishChosen) throws CommitStepException
	{
		// Stop editing if any
		TableCellEditor cellEditor = myTable.getCellEditor();
		if(cellEditor != null)
		{
			cellEditor.stopCellEditing();
		}

		// Check that all included fields are bound to valid bean properties
		PsiNameHelper nameHelper = JavaPsiFacade.getInstance(myData.myProject).getNameHelper();
		for(int i = 0; i < myData.myBindings.length; i++)
		{
			FormProperty2BeanProperty binding = myData.myBindings[i];
			if(binding.myBeanProperty == null)
			{
				continue;
			}

			if(!nameHelper.isIdentifier(binding.myBeanProperty.myName))
			{
				throw new CommitStepException(
                    UIDesignerLocalize.errorXIsNotAValidPropertyName(binding.myBeanProperty.myName).get()
				);
			}
		}

		myData.myGenerateIsModified = myChkIsModified.isSelected();
	}

	private final class MyTableModel extends AbstractTableModel
	{
		private final LocalizeValue[] myColumnNames = {
            UIDesignerLocalize.columnFormField(),
            UIDesignerLocalize.columnBeanProperty()
        };
		private final Class[] myColumnClasses = {
            Object.class,
            Object.class
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
        public Class getColumnClass(int column)
		{
			return myColumnClasses[column];
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
			FormProperty2BeanProperty binding = myData.myBindings[row];
			if(column == 0/*Form Property*/)
			{
				return binding.myFormProperty;
			}
			else if(column == 1/*Bean Property*/)
			{
				return binding.myBeanProperty;
			}
			else
			{
				throw new IllegalArgumentException("unknown column: " + column);
			}
		}

		@Override
        public void setValueAt(Object value, int row, int column)
		{
			FormProperty2BeanProperty binding = myData.myBindings[row];
			if(column == 1/*Bean Property*/)
			{
				binding.myBeanProperty = (BeanProperty) value;
			}
			else
			{
				throw new IllegalArgumentException("unknown column: " + column);
			}
		}
	}
}
