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
import com.intellij.uiDesigner.impl.actions.*;
import com.intellij.uiDesigner.impl.componentTree.ComponentTree;
import com.intellij.uiDesigner.impl.propertyInspector.DesignerToolWindowManager;
import com.intellij.uiDesigner.impl.radComponents.RadComponent;
import consulo.dataContext.DataProvider;
import consulo.ide.impl.idea.ui.popup.PopupOwner;
import consulo.language.editor.CommonDataKeys;
import consulo.language.editor.PlatformDataKeys;
import consulo.logging.Logger;
import consulo.ui.ex.action.ActionManager;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.CustomShortcutSet;
import consulo.ui.ex.action.IdeActions;
import consulo.util.dataholder.Key;
import org.jetbrains.annotations.NonNls;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class GlassLayer extends JComponent implements DataProvider, PopupOwner
{
	private final GuiEditor myEditor;
	private static final Logger LOG = Logger.getInstance(GlassLayer.class);
	private Point myLastMousePosition;

	public GlassLayer(GuiEditor editor)
	{
		myEditor = editor;
		enableEvents(AWTEvent.KEY_EVENT_MASK | AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK);

		registerKeyboardAction(new MoveSelectionToRightAction(myEditor, false, false), IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT);
		registerKeyboardAction(new MoveSelectionToLeftAction(myEditor, false, false), IdeActions.ACTION_EDITOR_MOVE_CARET_LEFT);
		registerKeyboardAction(new MoveSelectionToUpAction(myEditor, false, false), IdeActions.ACTION_EDITOR_MOVE_CARET_UP);
		registerKeyboardAction(new MoveSelectionToDownAction(myEditor, false, false), IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN);

		registerKeyboardAction(new MoveSelectionToRightAction(myEditor, true, false), "EditorRightWithSelection");
		registerKeyboardAction(new MoveSelectionToLeftAction(myEditor, true, false), "EditorLeftWithSelection");
		registerKeyboardAction(new MoveSelectionToUpAction(myEditor, true, false), "EditorUpWithSelection");
		registerKeyboardAction(new MoveSelectionToDownAction(myEditor, true, false), "EditorDownWithSelection");

		registerKeyboardAction(new MoveSelectionToRightAction(myEditor, false, true), "EditorLineEnd");
		registerKeyboardAction(new MoveSelectionToLeftAction(myEditor, false, true), "EditorLineStart");
		registerKeyboardAction(new MoveSelectionToUpAction(myEditor, false, true), "EditorPageUp");
		registerKeyboardAction(new MoveSelectionToDownAction(myEditor, false, true), "EditorPageDown");

		registerKeyboardAction(new MoveSelectionToRightAction(myEditor, true, true), "EditorLineEndWithSelection");
		registerKeyboardAction(new MoveSelectionToLeftAction(myEditor, true, true), "EditorLineStartWithSelection");
		registerKeyboardAction(new MoveSelectionToUpAction(myEditor, true, true), "EditorPageUpWithSelection");
		registerKeyboardAction(new MoveSelectionToDownAction(myEditor, true, true), "EditorPageDownWithSelection");

		registerKeyboardAction(new MoveComponentAction(-1, 0, 0, 0), "EditorScrollUp");
		registerKeyboardAction(new MoveComponentAction(1, 0, 0, 0), "EditorScrollDown");
		registerKeyboardAction(new MoveComponentAction(0, -1, 0, 0), "EditorPreviousWord");
		registerKeyboardAction(new MoveComponentAction(0, 1, 0, 0), "EditorNextWord");

		registerKeyboardAction(new MoveComponentAction(0, 0, -1, 0), IdeActions.ACTION_MOVE_STATEMENT_UP_ACTION);
		registerKeyboardAction(new MoveComponentAction(0, 0, 1, 0), IdeActions.ACTION_MOVE_STATEMENT_DOWN_ACTION);
		registerKeyboardAction(new MoveComponentAction(0, 0, 0, -1), "EditorPreviousWordWithSelection");
		registerKeyboardAction(new MoveComponentAction(0, 0, 0, 1), "EditorNextWordWithSelection");

		registerKeyboardAction(new SelectAllComponentsAction(), "$SelectAll");

		// F2 should start inplace editing
		StartInplaceEditingAction startInplaceEditingAction = new StartInplaceEditingAction(editor);
		startInplaceEditingAction.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0)), this);
	}

	private void registerKeyboardAction(AnAction action, @NonNls String actionId)
	{
		action.registerCustomShortcutSet(ActionManager.getInstance().getAction(actionId).getShortcutSet(), this);
	}

	@Override
	protected void processKeyEvent(KeyEvent e)
	{
		myEditor.myProcessor.processKeyEvent(e);
		if(!e.isConsumed())
		{
			super.processKeyEvent(e);
		}
	}

	@Override
	protected void processMouseEvent(MouseEvent e)
	{
		if(e.getID() == MouseEvent.MOUSE_PRESSED)
		{
			requestFocusInWindow();
		}
		try
		{
			myEditor.myProcessor.processMouseEvent(e);
		}
		catch(Exception ex)
		{
			LOG.error(ex);
		}
	}

	@Override
	protected void processMouseMotionEvent(MouseEvent e)
	{
		myLastMousePosition = e.getPoint();
		try
		{
			myEditor.myProcessor.processMouseEvent(e);
		}
		catch(Exception ex)
		{
			LOG.error(ex);
		}
	}

	@Nonnull
	public Point getLastMousePosition()
	{
		if(myLastMousePosition == null)
		{
			return new Point(10, 10);
		}
		return myLastMousePosition;
	}

	/**
	 * Provides {@link PlatformDataKeys#NAVIGATABLE} to navigate to
	 * binding of currently selected component (if any)
	 */
	@Override
	public Object getData(@Nonnull Key<?> dataId)
	{
		if(CommonDataKeys.NAVIGATABLE == dataId)
		{
			ComponentTree componentTree = DesignerToolWindowManager.getInstance(myEditor).getComponentTree();
			if(componentTree != null)
			{
				return componentTree.getData(dataId);
			}
		}
		return null;
	}

	@Override
	@Nullable
	public Point getBestPopupPosition()
	{
		ArrayList<RadComponent> selection = FormEditingUtil.getSelectedComponents(myEditor);
		if(selection.size() > 0)
		{
			RadComponent component = selection.get(0);
			Rectangle bounds = component.getBounds();
			int bottom = bounds.height > 4 ? bounds.y + bounds.height - 4 : bounds.y;
			int left = bounds.width > 4 ? bounds.x + 4 : bounds.x;
			Point pnt = new Point(left, bottom);  // the location needs to be within the component
			return SwingUtilities.convertPoint(component.getParent().getDelegee(), pnt, this);
		}
		return null;
	}
}
