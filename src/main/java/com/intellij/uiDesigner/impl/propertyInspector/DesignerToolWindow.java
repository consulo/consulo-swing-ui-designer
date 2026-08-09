/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.uiDesigner.impl.propertyInspector;

import com.intellij.uiDesigner.impl.componentTree.ComponentTree;
import com.intellij.uiDesigner.impl.componentTree.ComponentTreeBuilder;
import com.intellij.uiDesigner.impl.designSurface.GuiEditor;
import consulo.dataContext.DataProvider;
import consulo.disposer.Disposer;
import consulo.ide.impl.idea.designer.LightToolWindowContent;
import consulo.project.Project;
import consulo.ui.ex.awt.IdeBorderFactory;
import consulo.ui.ex.awt.ScrollPaneFactory;
import consulo.ui.ex.awt.SideBorder;
import consulo.ui.ex.awt.Splitter;
import consulo.util.dataholder.Key;
import jakarta.annotation.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;

/**
 * @author Alexander Lobas
 */
public class DesignerToolWindow implements LightToolWindowContent
{
	private final MyToolWindowPanel myToolWindowPanel = new MyToolWindowPanel();
	private ComponentTree myComponentTree;
	private ComponentTreeBuilder myComponentTreeBuilder;
	private PropertyInspector myPropertyInspector;

	public DesignerToolWindow(Project project)
	{
		myComponentTree = new ComponentTree(project);

		JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myComponentTree);
		scrollPane.setBorder(IdeBorderFactory.createBorder(SideBorder.BOTTOM));
		scrollPane.setPreferredSize(new Dimension(250, -1));
		myComponentTree.initQuickFixManager(scrollPane.getViewport());

		myPropertyInspector = new PropertyInspector(project, myComponentTree);

		myToolWindowPanel.setFirstComponent(scrollPane);
		myToolWindowPanel.setSecondComponent(myPropertyInspector);
	}

	@Override
	public void dispose()
	{
		clearTreeBuilder();
		myToolWindowPanel.dispose();
		myComponentTree = null;
		myPropertyInspector = null;
	}

	private void clearTreeBuilder()
	{
		if(myComponentTreeBuilder != null)
		{
			Disposer.dispose(myComponentTreeBuilder);
			myComponentTreeBuilder = null;
		}
	}

	public void update(GuiEditor designer)
	{
		clearTreeBuilder();

		myComponentTree.setModel(new DefaultTreeModel(new DefaultMutableTreeNode()));
		myComponentTree.setEditor(designer);
		myPropertyInspector.setEditor(designer);

		if(designer == null)
		{
			myComponentTree.setFormEditor(null);
		}
		else
		{
			myComponentTree.setFormEditor(designer.getEditor());
			myComponentTreeBuilder = new ComponentTreeBuilder(myComponentTree, designer);
		}
	}

	public JComponent getToolWindowPanel()
	{
		return myToolWindowPanel;
	}

	public ComponentTree getComponentTree()
	{
		return myComponentTree;
	}

	public ComponentTreeBuilder getComponentTreeBuilder()
	{
		return myComponentTreeBuilder;
	}

	public void updateComponentTree()
	{
		if(myComponentTreeBuilder != null)
		{
			myComponentTreeBuilder.queueUpdate();
		}
	}

	public PropertyInspector getPropertyInspector()
	{
		return myPropertyInspector;
	}

	public void refreshErrors()
	{
		if(myComponentTree != null)
		{
			myComponentTree.refreshIntentionHint();
			myComponentTree.repaint(myComponentTree.getVisibleRect());
		}

		// PropertyInspector
		if(myPropertyInspector != null)
		{
			myPropertyInspector.refreshIntentionHint();
			myPropertyInspector.repaint(myPropertyInspector.getVisibleRect());
		}
	}

	private class MyToolWindowPanel extends Splitter implements DataProvider
	{
		MyToolWindowPanel()
		{
			super(true, 0.33f);
		}

		@Nullable
        @Override
		public Object getData(Key dataId)
		{
			if(GuiEditor.DATA_KEY == dataId && myComponentTree != null)
			{
				return myComponentTree.getData(dataId);
			}
			return null;
		}
	}
}