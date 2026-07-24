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
import com.intellij.uiDesigner.impl.UIDesignerBundle;
import com.intellij.uiDesigner.impl.componentTree.ComponentSelectionListener;
import com.intellij.uiDesigner.impl.propertyInspector.InplaceContext;
import com.intellij.uiDesigner.impl.propertyInspector.Property;
import com.intellij.uiDesigner.impl.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.impl.propertyInspector.PropertyEditorAdapter;
import com.intellij.uiDesigner.impl.radComponents.RadComponent;
import consulo.application.Application;
import consulo.application.ApplicationManager;
import consulo.logging.Logger;
import consulo.ui.ex.awt.IdeFocusTraversalPolicy;
import consulo.ui.ex.awt.util.FocusWatcher;
import consulo.undoRedo.CommandProcessor;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.event.MouseEvent;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class InplaceEditingLayer extends JComponent
{
	private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.InplaceEditingLayer");

	private final GuiEditor myEditor;
	/**
	 * Trackes focus movements inside myInplaceEditorComponent
	 */
	private final MyFocusWatcher myFocusWatcher;
	/**
	 * Commits or cancels editing
	 */
	private final MyPropertyEditorListener myPropertyEditorListener;
	/**
	 * The component which is currently edited with inplace editor.
	 * This component can be null.
	 */
	private RadComponent myInplaceComponent;
	/**
	 * Currently edited inplace property
	 */
	private Property myInplaceProperty;
	/**
	 * Current inplace editor
	 */
	private PropertyEditor myInplaceEditor;
	/**
	 * JComponent which is used as inplace editor
	 */
	private JComponent myInplaceEditorComponent;
	/**
	 * Preferred bounds of the inplace editor component
	 */
	private Rectangle myPreferredBounds;
	/**
	 * If <code>true</code> then we do not have to react on own events
	 */
	private boolean myInsideChange;

	public InplaceEditingLayer(@Nonnull GuiEditor editor)
	{
		myEditor = editor;
		myEditor.addComponentSelectionListener(new MyComponentSelectionListener());
		myFocusWatcher = new MyFocusWatcher();
		myPropertyEditorListener = new MyPropertyEditorListener();
	}

	/**
	 * This is optimization. We do not need to invalidate Swing hierarchy
	 * upper than InplaceEditingLayer.
	 */
	public boolean isValidateRoot()
	{
		return true;
	}

	/**
	 * When there is an inplace editor we "listen" all mouse event
	 * and finish editing by any MOUSE_PRESSED or MOUSE_RELEASED event.
	 * We are acting like yet another glass pane over the standard glass layer.
	 */
	protected void processMouseEvent(MouseEvent e)
	{
		if(
				myInplaceComponent != null &&
						(MouseEvent.MOUSE_PRESSED == e.getID() || MouseEvent.MOUSE_RELEASED == e.getID())
		)
		{
			finishInplaceEditing();
		}
		// [vova] this is very important! Without this code Swing doen't close popup menu on our
		// layered pane. Swing adds MouseListeners to all component to close popup. If we do not
		// invoke super then we lock all mouse listeners.
		super.processMouseEvent(e);
	}

	/**
	 * @return whether the layer is in "editing" state or not
	 */
	public boolean isEditing()
	{
		return myInplaceComponent != null;
	}

	/**
	 * Starts editing of "inplace" property for the component at the
	 * specified point <code>(x, y)</code>.
	 *
	 * @param x x coordinate in the editor coordinate system
	 * @param y y coordinate in the editor coordinate system
	 */
	public void startInplaceEditing(int x, int y)
	{
		RadComponent inplaceComponent = FormEditingUtil.getRadComponentAt(myEditor.getRootContainer(), x, y);
		if(inplaceComponent == null)
		{ // nothing to edit
			return;
		}

		// Try to find property with inplace editor
		Point p = SwingUtilities.convertPoint(this, x, y, inplaceComponent.getDelegee());
		Property inplaceProperty = inplaceComponent.getInplaceProperty(p.x, p.y);
		if(inplaceProperty != null)
		{
			Rectangle bounds = inplaceComponent.getInplaceEditorBounds(inplaceProperty, p.x, p.y);
			startInplaceEditing(inplaceComponent, inplaceProperty, bounds, new InplaceContext(true));
		}
	}

	public void startInplaceEditing(@Nonnull RadComponent inplaceComponent,
									@Nullable Property property,
									@Nullable Rectangle bounds,
									InplaceContext context)
	{
		myInplaceProperty = property;
		if(myInplaceProperty == null)
		{
			return;
		}

		if(!myEditor.ensureEditable())
		{
			myInplaceProperty = null;
			return;
		}

		// Now we have to cancel previous inplace editing (if any)

		// Start new inplace editing
		myInplaceComponent = inplaceComponent;
		myInplaceEditor = myInplaceProperty.getEditor();
		LOG.assertTrue(myInplaceEditor != null);

		// 1. Get editor component
		myInplaceEditorComponent = myInplaceEditor.getComponent(
				myInplaceComponent,
				context.isKeepInitialValue() ? myInplaceProperty.getValue(myInplaceComponent) : null,
				context
		);

		if(context.isModalDialogDisplayed())
		{  // ListModel, for example
			finishInplaceEditing();
			return;
		}

		LOG.assertTrue(myInplaceEditorComponent != null);
		myInplaceEditor.addPropertyEditorListener(myPropertyEditorListener);

		// 2. Set editor component bounds
		Dimension prefSize = myInplaceEditorComponent.getPreferredSize();
		if(bounds != null)
		{ // use bounds provided by the component itself
			Point _p = SwingUtilities.convertPoint(myInplaceComponent.getDelegee(), bounds.x, bounds.y, this);
			myPreferredBounds = new Rectangle(_p.x, _p.y, bounds.width, bounds.height);
		}
		else
		{ // set some default bounds
			Point _p = SwingUtilities.convertPoint(myInplaceComponent.getDelegee(), 0, 0, this);
			myPreferredBounds = new Rectangle(_p.x, _p.y, myInplaceComponent.getWidth(), myInplaceComponent.getHeight());
		}
		myInplaceEditorComponent.setBounds(
				myPreferredBounds.x,
				myPreferredBounds.y + (myPreferredBounds.height - prefSize.height) / 2,
				Math.min(Math.max(prefSize.width, myPreferredBounds.width), getWidth() - myPreferredBounds.x),
				prefSize.height
		);

		// 3. Add it into layer
		add(myInplaceEditorComponent);
		myInplaceEditorComponent.revalidate();

		// 4. Request focus into proper component
		JComponent componentToFocus = myInplaceEditor.getPreferredFocusedComponent(myInplaceEditorComponent);
		if(componentToFocus == null)
		{
			componentToFocus = IdeFocusTraversalPolicy.getPreferredFocusedComponent(myInplaceEditorComponent);
		}
		if(componentToFocus == null)
		{
			componentToFocus = myInplaceEditorComponent;
		}
		if(componentToFocus.requestFocusInWindow())
		{
			myFocusWatcher.install(myInplaceEditorComponent);
		}
		else
		{
			grabFocus();
			final JComponent finalComponentToFocus = componentToFocus;
			ApplicationManager.getApplication().invokeLater(new Runnable()
			{
				public void run()
				{
					finalComponentToFocus.requestFocusInWindow();
					myFocusWatcher.install(myInplaceEditorComponent);
				}
			});
		}

		// 5. Block any mouse event to finish editing by any of them
		enableEvents(MouseEvent.MOUSE_EVENT_MASK);

		repaint();
	}

	private void adjustEditorComponentSize()
	{
		if(myInplaceEditorComponent == null)
		{
			return;
		}
		Dimension preferredSize = myInplaceEditorComponent.getPreferredSize();
		int width = Math.max(preferredSize.width, myPreferredBounds.width);
		// Editor component should not be extended to invisible area
		width = Math.min(width, getWidth() - myInplaceEditorComponent.getX());
		myInplaceEditorComponent.setSize(width, myInplaceEditorComponent.getHeight());
		myInplaceEditorComponent.revalidate();
	}

	/**
	 * Finishes current inplace editing
	 */
	private void finishInplaceEditing()
	{
		if(myInplaceComponent == null || myInsideChange)
		{ // nothing to finish
			return;
		}
		myInsideChange = true;
		try
		{
			// 1. Apply new value to the component
			LOG.assertTrue(myInplaceEditor != null);
			if(!myEditor.isUndoRedoInProgress())
			{
				CommandProcessor.getInstance().executeCommand(
						myInplaceComponent.getProject(),
						new Runnable()
						{
							public void run()
							{
								try
								{
									Object value = myInplaceEditor.getValue();
									myInplaceProperty.setValue(myInplaceComponent, value);
								}
								catch(Exception ignored)
								{
								}
								myEditor.refreshAndSave(true);
							}
						}, UIDesignerBundle.message("command.set.property.value"), null);
			}
			// 2. Remove editor from the layer

			if(myInplaceEditorComponent != null)
			{  // reenterability guard
				removeInplaceEditorComponent();
				myFocusWatcher.deinstall(myInplaceEditorComponent);
			}

			myInplaceEditor.removePropertyEditorListener(myPropertyEditorListener);

			myInplaceComponent = null;
			myInplaceEditorComponent = null;
			myInplaceComponent = null;

			// 3. Let AWT work
			disableEvents(MouseEvent.MOUSE_EVENT_MASK);
		}
		finally
		{
			myInsideChange = false;
		}

		repaint();
	}

	/**
	 * Cancells current inplace editing
	 */
	private void cancelInplaceEditing()
	{
		if(myInplaceComponent == null || myInsideChange)
		{ // nothing to finish
			return;
		}
		myInsideChange = true;
		try
		{
			// 1. Remove editor from the layer
			LOG.assertTrue(myInplaceProperty != null);
			LOG.assertTrue(myInplaceEditor != null);

			removeInplaceEditorComponent();

			myInplaceEditor.removePropertyEditorListener(myPropertyEditorListener);
			myFocusWatcher.deinstall(myInplaceEditorComponent);

			myInplaceComponent = null;
			myInplaceEditorComponent = null;
			myInplaceComponent = null;

			// 2. Let AWT work
			disableEvents(MouseEvent.MOUSE_EVENT_MASK);
		}
		finally
		{
			myInsideChange = false;
		}

		repaint();
	}

	private void removeInplaceEditorComponent()
	{
		remove(myInplaceEditorComponent);
	}

	/**
	 * Finish inplace editing when selection changes
	 */
	private final class MyComponentSelectionListener implements ComponentSelectionListener
	{
		public void selectedComponentChanged(GuiEditor source)
		{
			finishInplaceEditing();
		}
	}

	/**
	 * Finish inplace editing when inplace editor component loses focus
	 */
	private final class MyFocusWatcher extends FocusWatcher
	{
		protected void focusLostImpl(FocusEvent e)
		{
			Component opposite = e.getOppositeComponent();
			if(
					e.isTemporary() ||
							opposite != null && SwingUtilities.isDescendingFrom(opposite, getTopComponent())
			)
			{
				// Do nothing if focus moves inside top component hierarchy
				return;
			}
			// [vova] we need LaterInvocator here to prevent write-access assertions
			ApplicationManager.getApplication().invokeLater(new Runnable()
			{
				public void run()
				{
					finishInplaceEditing();
				}
			}, Application.get().getNoneModalityState());
		}
	}

	/**
	 * Finishes editing by "Enter" and cancels editing by "Esc"
	 */
	private final class MyPropertyEditorListener extends PropertyEditorAdapter
	{
		public void valueCommitted(PropertyEditor source, boolean continueEditing, boolean closeEditorOnError)
		{
			finishInplaceEditing();
		}

		public void editingCanceled(PropertyEditor source)
		{
			cancelInplaceEditing();
		}

		public void preferredSizeChanged(PropertyEditor source)
		{
			adjustEditorComponentSize();
		}
	}
}