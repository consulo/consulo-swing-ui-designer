/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.uiDesigner.impl.propertyInspector.editors.string;

import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.uiDesigner.impl.UIDesignerBundle;
import com.intellij.uiDesigner.impl.designSurface.GuiEditor;
import com.intellij.uiDesigner.lw.StringDescriptor;
import consulo.application.ui.DimensionService;
import consulo.dataContext.DataManager;
import consulo.language.editor.CommonDataKeys;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.ui.Size2D;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.ui.ex.awt.ScrollPaneFactory;
import consulo.ui.ex.awt.event.DoubleClickListener;
import consulo.ui.ex.awt.speedSearch.SpeedSearchBase;
import consulo.ui.ex.awt.table.JBTable;
import consulo.util.collection.primitive.objects.ObjectIntMap;
import consulo.util.collection.primitive.objects.ObjectMaps;
import consulo.util.lang.Pair;
import org.jetbrains.annotations.NonNls;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class KeyChooserDialog extends DialogWrapper
{
	private static final Logger LOG = Logger.getInstance(KeyChooserDialog.class);

	private final PropertiesFile myBundle;
	private final String myBundleName;
	/**
	 * List of bundle's pairs
	 */
	private ArrayList<Pair<String, String>> myPairs;
	private final JComponent myCenterPanel;
	/**
	 * Table with key/value pairs
	 */
	private final JTable myTable;
	@NonNls
	private static final String NULL = "null";
	private final MyTableModel myModel;
	private final GuiEditor myEditor;

	private static final String OK_ACTION = "OkAction";

	/**
	 * @param bundle         resource bundle to be shown.
	 * @param bundleName     name of the resource bundle to be shown. We need this
	 *                       name to create StringDescriptor in {@link #getDescriptor()} method.
	 * @param keyToPreselect describes row that should be selected in the
	 * @param parent         the parent component for the dialog.
	 */
	public KeyChooserDialog(
			Component parent,
			@Nonnull PropertiesFile bundle,
			@Nonnull String bundleName,
			String keyToPreselect,
			GuiEditor editor
	)
	{
		super(parent, true);
		myEditor = editor;
		myBundle = bundle;

		myBundleName = bundleName;

		setTitle(UIDesignerBundle.message("title.chooser.value"));

		// Read key/value pairs from resource bundle
		fillPropertyList();

		// Create UI
		myModel = new MyTableModel();
		myTable = new JBTable(myModel);
		myTable.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		new MySpeedSearch(myTable);
		myCenterPanel = ScrollPaneFactory.createScrollPane(myTable);

		myTable.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), OK_ACTION);
		myTable.getActionMap().put(OK_ACTION, new AbstractAction()
		{
			public void actionPerformed(ActionEvent e)
			{
				getOKAction().actionPerformed(e);
			}
		});

		// Calculate width for "Key" columns
		Project projectGuess = DataManager.getInstance().getDataContext(parent).getData(CommonDataKeys.PROJECT);
		Size2D size = DimensionService.getInstance().getSize(getDimensionServiceKey(), projectGuess);
		FontMetrics metrics = myTable.getFontMetrics(myTable.getFont());
		int minWidth = 200;
		int maxWidth = size != null ? size.width() / 2 : Integer.MAX_VALUE;
		if(minWidth > maxWidth)
		{
			minWidth = maxWidth;
		}
		int width = minWidth;
		for(int i = myPairs.size() - 1; i >= 0; i--)
		{
			Pair<String, String> pair = myPairs.get(i);
			width = Math.max(width, metrics.stringWidth(pair.getFirst()));
		}
		width += 20;
		width = Math.max(width, metrics.stringWidth(myModel.getColumnName(0)));
		width = Math.max(width, minWidth);
		width = Math.min(width, maxWidth);
		TableColumnModel columnModel = myTable.getColumnModel();
		TableColumn keyColumn = columnModel.getColumn(0);
		keyColumn.setMaxWidth(width);
		keyColumn.setMinWidth(width);
		TableCellRenderer defaultRenderer = myTable.getDefaultRenderer(String.class);
		if(defaultRenderer instanceof JComponent)
		{
			JComponent component = (JComponent) defaultRenderer;
			component.putClientProperty("html.disable", Boolean.TRUE);
		}
		selectKey(keyToPreselect);

		init();
		new DoubleClickListener()
		{
			@Override
			protected boolean onDoubleClick(MouseEvent e)
			{
				doOKAction();
				return true;
			}
		}.installOn(myTable);
	}

	private void fillPropertyList()
	{
		myPairs = new ArrayList<>();

		List<IProperty> properties = myBundle.getProperties();
		for(IProperty property : properties)
		{
			String key = property.getUnescapedKey();
			String value = property.getValue();
			if(key != null)
			{
				myPairs.add(new Pair<>(key, value != null ? value : NULL));
			}
		}
		Collections.sort(myPairs, new MyPairComparator());
	}

	private void selectKey(String keyToPreselect)
	{
		// Preselect proper row
		int indexToPreselect = -1;
		for(int i = myPairs.size() - 1; i >= 0; i--)
		{
			Pair<String, String> pair = myPairs.get(i);
			if(pair.getFirst().equals(keyToPreselect))
			{
				indexToPreselect = i;
				break;
			}
		}
		if(indexToPreselect != -1)
		{
			selectElementAt(indexToPreselect);
		}
	}

	@Nonnull
	@Override
	protected Action[] createLeftSideActions()
	{
		return new Action[]{new NewKeyValueAction()};
	}

	private void selectElementAt(int index)
	{
		myTable.getSelectionModel().setSelectionInterval(index, index);
		myTable.scrollRectToVisible(myTable.getCellRect(index, 0, true));
	}

	@Nonnull
	protected String getDimensionServiceKey()
	{
		return getClass().getName();
	}

	public JComponent getPreferredFocusedComponent()
	{
		return myTable;
	}

	/**
	 * @return resolved string descriptor. If user chose nothing then the
	 * method returns <code>null</code>.
	 */
	@Nullable
	StringDescriptor getDescriptor()
	{
		int selectedRow = myTable.getSelectedRow();
		if(selectedRow < 0 || selectedRow >= myTable.getRowCount())
		{
			return null;
		}
		else
		{
			Pair<String, String> pair = myPairs.get(selectedRow);
			StringDescriptor descriptor = new StringDescriptor(myBundleName, pair.getFirst());
			descriptor.setResolvedValue(pair.getSecond());
			return descriptor;
		}
	}

	protected JComponent createCenterPanel()
	{
		return myCenterPanel;
	}

	private static final class MyPairComparator implements Comparator<Pair<String, String>>
	{
		public int compare(Pair<String, String> p1, Pair<String, String> p2)
		{
			return p1.getFirst().compareToIgnoreCase(p2.getFirst());
		}
	}

	private final class MyTableModel extends AbstractTableModel
	{
		public int getColumnCount()
		{
			return 2;
		}

		public String getColumnName(int column)
		{
			if(column == 0)
			{
				return UIDesignerBundle.message("column.key");
			}
			else if(column == 1)
			{
				return UIDesignerBundle.message("column.value");
			}
			else
			{
				throw new IllegalArgumentException("unknown column: " + column);
			}
		}

		public Class getColumnClass(int column)
		{
			if(column == 0)
			{
				return String.class;
			}
			else if(column == 1)
			{
				return String.class;
			}
			else
			{
				throw new IllegalArgumentException("unknown column: " + column);
			}
		}

		public Object getValueAt(int row, int column)
		{
			if(column == 0)
			{
				return myPairs.get(row).getFirst();
			}
			else if(column == 1)
			{
				return myPairs.get(row).getSecond();
			}
			else
			{
				throw new IllegalArgumentException("unknown column: " + column);
			}
		}

		public int getRowCount()
		{
			return myPairs.size();
		}

		public void update()
		{
			fireTableDataChanged();
		}
	}

	private class MySpeedSearch extends SpeedSearchBase<JTable>
	{
		private ObjectIntMap<Object> myElements;
		private Object[] myElementsArray;

		public MySpeedSearch(JTable component)
		{
			super(component);
		}

		@Override
		protected int convertIndexToModel(int viewIndex)
		{
			return getComponent().convertRowIndexToModel(viewIndex);
		}

		public int getSelectedIndex()
		{
			return myComponent.getSelectedRow();
		}

		public Object[] getAllElements()
		{
			if(myElements == null)
			{
				myElements = ObjectMaps.newObjectIntHashMap();
				myElementsArray = myPairs.toArray();
				for(int idx = 0; idx < myElementsArray.length; idx++)
				{
					Object element = myElementsArray[idx];
					myElements.putInt(element, idx);
				}
			}
			return myElementsArray;
		}

		public String getElementText(Object element)
		{
			//noinspection unchecked
			return ((Pair<String, String>) element).getFirst();
		}

		public void selectElement(Object element, String selectedText)
		{
			int index = myElements.getInt(element);
			selectElementAt(getComponent().convertRowIndexToView(index));
		}
	}

	private class NewKeyValueAction extends AbstractAction
	{
		public NewKeyValueAction()
		{
			putValue(Action.NAME, UIDesignerBundle.message("key.chooser.new.property"));
		}

		public void actionPerformed(ActionEvent e)
		{
			NewKeyDialog dlg = new NewKeyDialog(getWindow());
			dlg.show();
			if(dlg.isOK())
			{
				if(!StringEditorDialog.saveCreatedProperty(myBundle, dlg.getName(), dlg.getValue(), myEditor.getPsiFile()))
				{
					return;
				}

				fillPropertyList();
				myModel.update();
				selectKey(dlg.getName());
			}
		}
	}
}
