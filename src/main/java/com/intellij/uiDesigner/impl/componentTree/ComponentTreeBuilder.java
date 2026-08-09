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

import com.intellij.uiDesigner.impl.FormEditingUtil;
import com.intellij.uiDesigner.impl.HierarchyChangeListener;
import com.intellij.uiDesigner.impl.SelectionWatcher;
import com.intellij.uiDesigner.impl.designSurface.GuiEditor;
import com.intellij.uiDesigner.impl.propertyInspector.DesignerToolWindowManager;
import com.intellij.uiDesigner.impl.propertyInspector.PropertyInspector;
import com.intellij.uiDesigner.impl.radComponents.RadComponent;
import com.intellij.uiDesigner.impl.radComponents.RadContainer;
import consulo.logging.Logger;
import consulo.ui.ex.awt.tree.AbstractTreeBuilder;
import consulo.ui.ex.tree.NodeDescriptor;
import consulo.util.lang.Comparing;
import jakarta.annotation.Nonnull;

import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultTreeModel;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class ComponentTreeBuilder extends AbstractTreeBuilder
{
	private static final Logger LOG = Logger.getInstance("#com.intellij.componentTree.ComponentTreeBuilder");

	private final GuiEditor myEditor;
	private final MySelectionWatcher mySelectionWatcher;
	/**
	 * More then 0 if we are inside some change. In this case we have not
	 * react on our own events.
	 */
	private int myInsideChange;
	private final MyHierarchyChangeListener myHierarchyChangeListener;
	private MyTreeSelectionListener myTreeSelectionListener;

	public ComponentTreeBuilder(ComponentTree tree, @Nonnull GuiEditor editor)
	{
		super(tree, (DefaultTreeModel) tree.getModel(), new ComponentTreeStructure(editor), MyComparator.ourComparator);

		myEditor = editor;
		mySelectionWatcher = new MySelectionWatcher(editor);

		initRootNode();
		syncSelection();

		myTreeSelectionListener = new MyTreeSelectionListener();
		myHierarchyChangeListener = new MyHierarchyChangeListener();
		getTree().getSelectionModel().addTreeSelectionListener(myTreeSelectionListener);
		editor.addHierarchyChangeListener(myHierarchyChangeListener);
	}

	@Override
    public void dispose()
	{
		myEditor.removeHierarchyChangeListener(myHierarchyChangeListener);
		if(myTreeSelectionListener != null)
		{
			getTree().getSelectionModel().removeTreeSelectionListener(myTreeSelectionListener);
			myTreeSelectionListener = null;
		}
		mySelectionWatcher.dispose();
		super.dispose();
	}

	private ComponentTreeStructure getComponentTreeStructure()
	{
		return (ComponentTreeStructure) getTreeStructure();
	}

	@Override
    protected boolean isAlwaysShowPlus(NodeDescriptor descriptor)
	{
		return false;
	}

	@Override
    protected boolean isAutoExpandNode(NodeDescriptor descriptor)
	{
		return getComponentTreeStructure().isAutoExpandNode(descriptor);
	}

	public void beginUpdateSelection()
	{
		myInsideChange++;
	}

	public void endUpdateSelection()
	{
		myInsideChange--;
		updateSelection();
	}

	/**
	 * This method synchronizes selection in the tree with the selected
	 * RadComponent in the component hierarchy
	 */
	private void syncSelection()
	{
		// Found selected components
		RadContainer rootContainer = myEditor.getRootContainer();
		List<RadComponent> selection = new ArrayList<>();
		FormEditingUtil.iterate(rootContainer, new FormEditingUtil.ComponentVisitor<RadComponent>()
		{
			@Override
            public boolean visit(RadComponent component)
			{
				if(component.isSelected())
				{
					selection.add(component);
				}
				return true;
			}
		});
		if(selection.size() == 0)
		{
			// If there is no selected component in the hierarchy, then
			// we have to select RadRootContainer
			selection.add(rootContainer);
		}

		ComponentPtr[] componentPtrs = new ComponentPtr[selection.size()];
		for(int i = 0; i < selection.size(); i++)
		{
			componentPtrs[i] = new ComponentPtr(myEditor, selection.get(i));
		}

		// Set selection in the tree
		select(componentPtrs, null);

		// Notify the ComponentTree that selected component changed
		myEditor.fireSelectedComponentChanged();
	}

	/**
	 * Compares RadComponent based on their natural order in the container.
	 */
	private static final class MyComparator implements Comparator<NodeDescriptor>
	{
		public static final MyComparator ourComparator = new MyComparator();

		private static int indexOf(RadContainer container, RadComponent component)
		{
			if(container != null)
			{
				for(int i = container.getComponentCount() - 1; i >= 0; i--)
				{
					if(component.equals(container.getComponent(i)))
					{
						return i;
					}
				}
			}
			return -1;
		}

		@Override
        public int compare(NodeDescriptor descriptor1, NodeDescriptor descriptor2)
		{
			if(descriptor1 instanceof ComponentPtrDescriptor && descriptor2 instanceof ComponentPtrDescriptor)
			{
				RadComponent component1 = ((ComponentPtrDescriptor) descriptor1).getComponent();
				RadComponent component2 = ((ComponentPtrDescriptor) descriptor2).getComponent();
				if(component1 == null || component2 == null)
				{
					return 0;
				}
				RadContainer container1 = component1.getParent();
				RadContainer container2 = component2.getParent();
				if(Comparing.equal(container1, container2))
				{
					return indexOf(container1, component1) - indexOf(container2, component2);
				}
				else
				{
					return 0;
				}
			}
			else
			{
				return 0;
			}
		}
	}

	/**
	 * Synchronizes tree with GuiEditor
	 */
	private final class MyHierarchyChangeListener implements HierarchyChangeListener
	{
		@Override
        public void hierarchyChanged()
		{
			if(myInsideChange > 0)
			{
				return;
			}

			myInsideChange++;
			try
			{
                // After updating the tree we have to synchronize the selection in the tree
                // with selected element in the hierarchy
				queueUpdate().doWhenDone(ComponentTreeBuilder.this::syncSelection);
			}
			finally
			{
				myInsideChange--;
			}
		}
	}

	/**
	 * Synchronizes selection in the tree with selection in the editor
	 */
	private final class MySelectionWatcher extends SelectionWatcher
	{
		public MySelectionWatcher(GuiEditor editor)
		{
			super(editor);
		}

		@Override
        protected void selectionChanged(RadComponent component, boolean ignored)
		{
			updateSelection();
		}
	}

	private void updateSelection()
	{
		PropertyInspector propertyInspector = DesignerToolWindowManager.getInstance(myEditor).getPropertyInspector();
		if(propertyInspector.isEditing())
		{
			propertyInspector.stopEditing();
		}

		if(myInsideChange > 0)
		{
			return;
		}
		myInsideChange++;
		try
		{
			updateFromRoot();
			syncSelection();
		}
		finally
		{
			myInsideChange--;
		}
	}

	/**
	 * Synchronizes GuiEditor with the tree
	 */
	private final class MyTreeSelectionListener implements TreeSelectionListener
	{
		@Override
        public void valueChanged(TreeSelectionEvent e)
		{
			if(myInsideChange > 0)
			{
				return;
			}

			Set<ComponentPtr> selectedElements = getSelectedElements(ComponentPtr.class);
			myInsideChange++;
			try
			{
				FormEditingUtil.clearSelection(myEditor.getRootContainer());
				boolean hasComponentInTab = false;
				int count = 0;
				for(ComponentPtr ptr : selectedElements)
				{
					ptr.validate();
					if(ptr.isValid())
					{
						RadComponent component = ptr.getComponent();
						LOG.assertTrue(component != null);
						if(!hasComponentInTab)
						{
							hasComponentInTab = FormEditingUtil.selectComponent(myEditor, component);
						}
						else
						{
							component.setSelected(true);
						}
						if(++count == selectedElements.size())
						{
							myEditor.scrollComponentInView(component);
						}
					}
				}

				// Notify ComponentTree that selected component changed
				myEditor.fireSelectedComponentChanged();
			}
			finally
			{
				myInsideChange--;
			}
		}
	}
}
