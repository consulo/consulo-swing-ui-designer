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
package com.intellij.uiDesigner.impl.designSurface;

import com.intellij.uiDesigner.impl.FormEditingUtil;
import com.intellij.uiDesigner.impl.componentTree.ComponentTree;
import com.intellij.uiDesigner.impl.propertyInspector.DesignerToolWindowManager;
import com.intellij.uiDesigner.impl.radComponents.RadButtonGroup;
import com.intellij.uiDesigner.impl.radComponents.RadComponent;
import com.intellij.uiDesigner.impl.radComponents.RadRootContainer;
import java.util.HashSet;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import com.intellij.uiDesigner.impl.UIDesignerIcons;

import jakarta.annotation.Nonnull;
import javax.swing.*;
import java.awt.*;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

/**
 * Decoration layer is over COMPONENT_LAYER (layer where all components are located).
 * It contains all necessary decorators. Decorators are:
 * - special borders to show component bounds and cell bounds inside grids
 * - special component which marks selected rectangle
 *
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
class PassiveDecorationLayer extends JComponent
{
	@Nonnull
	private final GuiEditor myEditor;

	public PassiveDecorationLayer(@Nonnull GuiEditor editor)
	{
		myEditor = editor;
	}

	/**
	 * Paints all necessary decoration for the specified <code>component</code>
	 */
	protected final void paintPassiveDecoration(RadComponent component, final Graphics g)
	{
		// Paint component bounds and grid markers
		Painter.paintComponentDecoration(myEditor, component, g);

		final Set<RadButtonGroup> paintedGroups = new HashSet<>();
		final RadRootContainer rootContainer = myEditor.getRootContainer();
		ComponentTree componentTree = DesignerToolWindowManager.getInstance(myEditor).getComponentTree();
		final Collection<RadButtonGroup> selectedGroups = componentTree != null ? componentTree.getSelectedElements(RadButtonGroup.class) :
				Collections.<RadButtonGroup>emptyList();

		// Paint selection and dragger
		FormEditingUtil.iterate(component, new FormEditingUtil.ComponentVisitor<RadComponent>()
		{
			@Override
			public boolean visit(RadComponent component)
			{
				Point point = SwingUtilities.convertPoint(component.getDelegee(), 0, 0, rootContainer.getDelegee());
				RadButtonGroup group = (RadButtonGroup) FormEditingUtil.findGroupForComponent(rootContainer, component);
				if(group != null && !paintedGroups.contains(group) && (component.isSelected() || selectedGroups.contains(group)))
				{
					paintedGroups.add(group);
					Painter.paintButtonGroupLines(rootContainer, group, g);
				}
				g.translate(point.x, point.y);
				try
				{
					if(myEditor.isShowComponentTags() && FormEditingUtil.isComponentSwitchedInView(component))
					{
						Painter.paintComponentTag(component, g);
					}
					Painter.paintSelectionDecoration(component, g, myEditor.getGlassLayer().isFocusOwner());
					// Over selection we have to paint dragger
					if(component.hasDragger())
					{
						Icon icon = getDragIcon();
						icon.paintIcon(PassiveDecorationLayer.this, g, -icon.getIconWidth(), -icon.getIconHeight());
					}
				}
				finally
				{
					g.translate(-point.x, -point.y);
				}
				return true;
			}
		});
	}

	private static Icon getDragIcon()
	{
		return TargetAWT.to(UIDesignerIcons.Drag);
	}

	@Override
	public void paint(Graphics g)
	{
		// Passive decoration
		RadRootContainer root = myEditor.getRootContainer();
		for(int i = root.getComponentCount() - 1; i >= 0; i--)
		{
			RadComponent component = root.getComponent(i);
			paintPassiveDecoration(component, g);
		}

		// Paint active decorators
		paintChildren(g);
	}
}
