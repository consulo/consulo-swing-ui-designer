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

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;

import com.intellij.ide.palette.impl.PaletteToolWindowManager;
import consulo.application.ApplicationManager;
import consulo.logging.Logger;
import com.intellij.uiDesigner.impl.CutCopyPasteSupport;
import com.intellij.uiDesigner.impl.FormEditingUtil;
import com.intellij.uiDesigner.impl.SimpleTransferable;
import com.intellij.uiDesigner.impl.UIDesignerBundle;
import com.intellij.uiDesigner.impl.componentTree.ComponentTree;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.impl.palette.ComponentItem;
import com.intellij.uiDesigner.impl.propertyInspector.DesignerToolWindowManager;
import com.intellij.uiDesigner.impl.radComponents.RadComponent;
import com.intellij.uiDesigner.impl.radComponents.RadContainer;
import consulo.undoRedo.CommandProcessor;

/**
 * @author yole
 */
class DesignDropTargetListener implements DropTargetListener
{
	private static final Logger LOG = Logger.getInstance(DesignDropTargetListener.class);

	private DraggedComponentList myDraggedComponentList;
	private ComponentDragObject myComponentDragObject;
	private List<RadComponent> myDraggedComponentsCopy;
	private Point myLastPoint;
	private final GuiEditor myEditor;
	private final GridInsertProcessor myGridInsertProcessor;
	private boolean myUseDragDelta = false;

	public DesignDropTargetListener(GuiEditor editor)
	{
		myEditor = editor;
		myGridInsertProcessor = new GridInsertProcessor(editor);
	}

	public void dragEnter(DropTargetDragEvent dtde)
	{
		try
		{
			DraggedComponentList dcl = DraggedComponentList.fromTransferable(dtde.getTransferable());
			if(dcl != null)
			{
				myDraggedComponentList = dcl;
				myComponentDragObject = dcl;
				processDragEnter(dcl, dtde.getLocation(), dtde.getDropAction());
				dtde.acceptDrag(dtde.getDropAction());
				myLastPoint = dtde.getLocation();
			}
			else
			{
				ComponentItem componentItem = SimpleTransferable.getData(dtde.getTransferable(), ComponentItem.class);
				if(componentItem != null)
				{
					myComponentDragObject = new ComponentItemDragObject(componentItem);
					dtde.acceptDrag(dtde.getDropAction());
					myLastPoint = dtde.getLocation();
				}
			}
		}
		catch(Exception e)
		{
			LOG.error(e);
		}
	}

	private void processDragEnter(DraggedComponentList draggedComponentList, Point location, int dropAction)
	{
		List<RadComponent> dragComponents = draggedComponentList.getComponents();

		Rectangle allBounds = null;
		if(!draggedComponentList.hasDragDelta() || !myUseDragDelta)
		{
			RadContainer[] originalParents = draggedComponentList.getOriginalParents();
			Rectangle[] originalBounds = draggedComponentList.getOriginalBounds();
			for(int i = 0; i < originalParents.length; i++)
			{
				Rectangle rc = SwingUtilities.convertRectangle(originalParents[i].getDelegee(), originalBounds[i], myEditor.getDragLayer());
				if(allBounds == null)
				{
					allBounds = rc;
				}
				else
				{
					allBounds = allBounds.union(rc);
				}
			}
		}

		// Place selected components to the drag layer.
		myDraggedComponentsCopy = CutCopyPasteSupport.copyComponents(myEditor, dragComponents);
		for(int i = 0; i < dragComponents.size(); i++)
		{
			myDraggedComponentsCopy.get(i).setSelected(true);
			JComponent delegee = myDraggedComponentsCopy.get(i).getDelegee();
			Point point = SwingUtilities.convertPoint(draggedComponentList.getOriginalParents()[i].getDelegee(), delegee.getLocation(),
					myEditor.getDragLayer());
			if(draggedComponentList.hasDragDelta() && myUseDragDelta)
			{
				delegee.setLocation((int) point.getX() + draggedComponentList.getDragDeltaX(), (int) point.getY() + draggedComponentList
						.getDragDeltaY());
			}
			else
			{
				assert allBounds != null;
				delegee.setLocation((int) (point.getX() - allBounds.getX() + location.getX()), (int) (point.getY() - allBounds.getY() + location
						.getY()));
			}
			//myEditor.getDragLayer().add(delegee);
		}

		for(RadComponent c : dragComponents)
		{
			if(dropAction != DnDConstants.ACTION_COPY)
			{
				c.setDragBorder(true);
			}
			c.setSelected(false);
		}
	}

	public void dragOver(DropTargetDragEvent dtde)
	{
		try
		{
			if(myComponentDragObject == null)
			{
				dtde.rejectDrag();
				return;
			}
			int dx = dtde.getLocation().x - myLastPoint.x;
			int dy = dtde.getLocation().y - myLastPoint.y;

			if(myDraggedComponentsCopy != null && myDraggedComponentList != null)
			{
				for(RadComponent aMySelection : myDraggedComponentsCopy)
				{
					aMySelection.shift(dx, dy);
				}
			}

			myLastPoint = dtde.getLocation();
			myEditor.getDragLayer().repaint();

			ComponentDropLocation location = myGridInsertProcessor.processDragEvent(dtde.getLocation(), myComponentDragObject);
			ComponentTree componentTree = DesignerToolWindowManager.getInstance(myEditor).getComponentTree();

			if(!location.canDrop(myComponentDragObject) || (myDraggedComponentList != null && FormEditingUtil.isDropOnChild(myDraggedComponentList,
					location)))
			{
				if(componentTree != null)
				{
					componentTree.setDropTargetComponent(null);
				}
				dtde.rejectDrag();
			}
			else
			{
				if(componentTree != null)
				{
					componentTree.setDropTargetComponent(location.getContainer());
				}
				dtde.acceptDrag(dtde.getDropAction());
			}
		}
		catch(Exception e)
		{
			LOG.error(e);
		}
	}

	public void dropActionChanged(DropTargetDragEvent dtde)
	{
		DraggedComponentList dcl = DraggedComponentList.fromTransferable(dtde.getTransferable());
		if(dcl != null)
		{
			setDraggingState(dcl, dtde.getDropAction() != DnDConstants.ACTION_COPY);
		}
	}

	public void dragExit(DropTargetEvent dte)
	{
		try
		{
			ComponentTree componentTree = DesignerToolWindowManager.getInstance(myEditor).getComponentTree();
			if(componentTree != null)
			{
				componentTree.setDropTargetComponent(null);
			}
			myUseDragDelta = false;
			if(myDraggedComponentList != null)
			{
				cancelDrag();
				setDraggingState(myDraggedComponentList, false);
				myEditor.getActiveDecorationLayer().removeFeedback();
				myDraggedComponentList = null;
				myEditor.setDesignTimeInsets(2);
			}
			myDraggedComponentsCopy = null;
		}
		catch(Exception e)
		{
			LOG.error(e);
		}
	}

	public void drop(final DropTargetDropEvent dtde)
	{
		try
		{
			ComponentTree componentTree = DesignerToolWindowManager.getInstance(myEditor).getComponentTree();
			if(componentTree != null)
			{
				componentTree.setDropTargetComponent(null);
			}


			final DraggedComponentList dcl = DraggedComponentList.fromTransferable(dtde.getTransferable());
			if(dcl != null)
			{
				CommandProcessor.getInstance().executeCommand(myEditor.getProject(), new Runnable()
				{
					public void run()
					{
						if(processDrop(dcl, dtde.getLocation(), dtde.getDropAction()))
						{
							myEditor.refreshAndSave(true);
						}
					}
				}, UIDesignerBundle.message("command.drop.components"), null);
			}
			else
			{
				ComponentItem componentItem = SimpleTransferable.getData(dtde.getTransferable(), ComponentItem.class);
				if(componentItem != null)
				{
					myEditor.getMainProcessor().setInsertFeedbackEnabled(false);
					new InsertComponentProcessor(myEditor).processComponentInsert(dtde.getLocation(), componentItem);
					ApplicationManager.getApplication().invokeLater(new Runnable()
					{
						public void run()
						{
							PaletteToolWindowManager.getInstance(myEditor).clearActiveItem();
							myEditor.getActiveDecorationLayer().removeFeedback();
							myEditor.getLayeredPane().setCursor(null);
							myEditor.getGlassLayer().requestFocus();
							myEditor.getMainProcessor().setInsertFeedbackEnabled(true);
						}
					});
				}
			}
			myDraggedComponentsCopy = null;
			myEditor.repaintLayeredPane();
		}
		catch(Exception e)
		{
			LOG.error(e);
		}
	}

	private boolean processDrop(DraggedComponentList dcl, Point dropPoint, int dropAction)
	{
		myEditor.getActiveDecorationLayer().removeFeedback();
		int dropX = dropPoint.x;
		int dropY = dropPoint.y;
		ArrayList<RadComponent> dclComponents = dcl.getComponents();
		int componentCount = dclComponents.size();
		ComponentDropLocation location = GridInsertProcessor.getDropLocation(myEditor.getRootContainer(), dropPoint);
		if(FormEditingUtil.isDropOnChild(dcl, location))
		{
			setDraggingState(dcl, false);
			return false;
		}
		if(!location.canDrop(dcl))
		{
			setDraggingState(dcl, false);
			return false;
		}

		if(!myEditor.ensureEditable())
		{
			setDraggingState(dcl, false);
			return false;
		}

		List<RadComponent> droppedComponents;

		RadContainer[] originalParents = dcl.getOriginalParents();

		cancelDrag();
		if(dropAction == DnDConstants.ACTION_COPY)
		{
			setDraggingState(dcl, false);
			droppedComponents = myDraggedComponentsCopy;
			if(droppedComponents == null)
			{
				return false;
			}
		}
		else
		{
			for(int i = 0; i < dclComponents.size(); i++)
			{
				LOG.info("Removing component " + dclComponents.get(i).getId() + " with constraints " + dcl.getOriginalConstraints()[i]);
				originalParents[i].removeComponent(dclComponents.get(i));
			}
			droppedComponents = dclComponents;
		}

		int[] dx = new int[componentCount];
		int[] dy = new int[componentCount];
		for(int i = 0; i < componentCount; i++)
		{
			RadComponent component = myDraggedComponentsCopy.get(i);
			dx[i] = component.getX() - dropX;
			dy[i] = component.getY() - dropY;
		}

		RadComponent[] components = droppedComponents.toArray(new RadComponent[componentCount]);
		GridConstraints[] originalConstraints = dcl.getOriginalConstraints();

		location.processDrop(myEditor, components, originalConstraints, dcl);

		if(dropAction == DnDConstants.ACTION_COPY)
		{
			for(RadComponent component : droppedComponents)
			{
				InsertComponentProcessor.createBindingWhenDrop(myEditor, component, false);
			}
			FormEditingUtil.selectComponents(myEditor, droppedComponents);
		}
		else
		{
			setDraggingState(dcl, false);
		}

		for(int i = 0; i < originalConstraints.length; i++)
		{
			if(originalParents[i].getLayoutManager().isGrid())
			{
				FormEditingUtil.deleteEmptyGridCells(originalParents[i], originalConstraints[i]);
			}
		}
		return true;
	}

	private void cancelDrag()
	{
		if(myDraggedComponentsCopy != null)
		{
			for(RadComponent c : myDraggedComponentsCopy)
			{
				myEditor.getDragLayer().remove(c.getDelegee());
			}
		}
		myEditor.refresh();
	}

	private static void setDraggingState(DraggedComponentList draggedComponentList, boolean dragging)
	{
		for(RadComponent c : draggedComponentList.getComponents())
		{
			c.setDragBorder(dragging);
		}
	}

	public void setUseDragDelta(boolean useDragDelta)
	{
		myUseDragDelta = useDragDelta;
	}
}
