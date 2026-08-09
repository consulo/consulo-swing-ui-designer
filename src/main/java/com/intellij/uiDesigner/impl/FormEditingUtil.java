/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.uiDesigner.impl;

import com.intellij.java.language.psi.*;
import com.intellij.lang.properties.PropertiesReferenceManager;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.uiDesigner.compiler.AsmCodeGenerator;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.impl.componentTree.ComponentTreeBuilder;
import com.intellij.uiDesigner.impl.designSurface.ComponentDropLocation;
import com.intellij.uiDesigner.impl.designSurface.DraggedComponentList;
import com.intellij.uiDesigner.impl.designSurface.GuiEditor;
import com.intellij.uiDesigner.impl.designSurface.Painter;
import com.intellij.uiDesigner.impl.editor.UIFormEditor;
import com.intellij.uiDesigner.impl.palette.ComponentItem;
import com.intellij.uiDesigner.impl.palette.Palette;
import com.intellij.uiDesigner.impl.propertyInspector.DesignerToolWindowManager;
import com.intellij.uiDesigner.impl.propertyInspector.properties.BindingProperty;
import com.intellij.uiDesigner.impl.propertyInspector.properties.IntroComponentProperty;
import com.intellij.uiDesigner.impl.radComponents.RadAbstractGridLayoutManager;
import com.intellij.uiDesigner.impl.radComponents.RadComponent;
import com.intellij.uiDesigner.impl.radComponents.RadContainer;
import com.intellij.uiDesigner.impl.radComponents.RadRootContainer;
import com.intellij.uiDesigner.lw.*;
import consulo.annotation.access.RequiredReadAction;
import consulo.component.ProcessCanceledException;
import consulo.dataContext.DataContext;
import consulo.fileEditor.FileEditor;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiPackage;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.module.Module;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.RelativePoint;
import consulo.ui.ex.awt.Messages;
import consulo.ui.ex.awt.UIUtil;
import consulo.ui.ex.popup.JBPopup;
import consulo.uiDesigner.impl.localize.UIDesignerLocalize;
import consulo.undoRedo.CommandProcessor;
import consulo.util.collection.ArrayUtil;
import consulo.util.lang.ref.SimpleReference;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.List;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class FormEditingUtil
{
	private FormEditingUtil()
	{
	}

	public static boolean canDeleteSelection(GuiEditor editor)
	{
		ArrayList<RadComponent> selection = getSelectedComponents(editor);
		if(selection.isEmpty())
		{
			return false;
		}
		RadRootContainer rootContainer = editor.getRootContainer();
        return rootContainer.getComponentCount() <= 0 || !selection.contains(rootContainer.getComponent(0));
    }

	/**
	 * <b>This method must be executed in command</b>
	 *
	 * @param editor the editor in which the selection is deleted.
	 */
	public static void deleteSelection(GuiEditor editor)
	{
		List<RadComponent> selection = getSelectedComponents(editor);
		deleteComponents(selection, true);
		editor.refreshAndSave(true);
	}

	public static void deleteComponents(Collection<? extends RadComponent> selection, boolean deleteEmptyCells)
	{
		if(selection.size() == 0)
		{
			return;
		}
		RadRootContainer rootContainer = (RadRootContainer) getRoot(selection.iterator().next());
		Set<String> deletedComponentIds = new HashSet<>();
		for(RadComponent component : selection)
		{
			boolean wasSelected = component.isSelected();
			RadContainer parent = component.getParent();

			boolean wasPackedHorz = false;
			boolean wasPackedVert = false;
			if(parent.getParent() != null && parent.getParent().isXY())
			{
				Dimension minSize = parent.getMinimumSize();
				wasPackedHorz = parent.getWidth() == minSize.width;
				wasPackedVert = parent.getHeight() == minSize.height;
			}

			iterate(component, c -> {
                RadComponent rc = (RadComponent) c;
                BindingProperty.checkRemoveUnusedField(rootContainer, rc.getBinding(), null);
                deletedComponentIds.add(rc.getId());
                return true;
            });

			GridConstraints delConstraints = parent.getLayoutManager().isGrid() ? component.getConstraints() : null;

			int index = parent.indexOfComponent(component);
			parent.removeComponent(component);
			if(wasSelected)
			{
				if(parent.getComponentCount() > index)
				{
					parent.getComponent(index).setSelected(true);
				}
				else if(index > 0 && parent.getComponentCount() == index)
				{
					parent.getComponent(index - 1).setSelected(true);
				}
				else
				{
					parent.setSelected(true);
				}
			}
			if(delConstraints != null && deleteEmptyCells)
			{
				deleteEmptyGridCells(parent, delConstraints);
			}

			if(wasPackedHorz || wasPackedVert)
			{
				Dimension minSize = parent.getMinimumSize();
				Dimension newSize = new Dimension(parent.getWidth(), parent.getHeight());
				if(wasPackedHorz)
				{
					newSize.width = minSize.width;
				}
				if(wasPackedVert)
				{
					newSize.height = minSize.height;
				}
				parent.setSize(newSize);
			}
		}

		iterate(rootContainer, component -> {
            RadComponent rc = (RadComponent) component;
            for(IProperty p : component.getModifiedProperties())
            {
                if(p instanceof IntroComponentProperty icp)
                {
                    String value = icp.getValue(rc);
                    if(deletedComponentIds.contains(value))
                    {
                        try
                        {
                            icp.resetValue(rc);
                        }
                        catch(Exception e)
                        {
                            // ignore
                        }
                    }
                }
            }
            return true;
        });
	}

	public static void deleteEmptyGridCells(RadContainer parent, GridConstraints delConstraints)
	{
		deleteEmptyGridCells(parent, delConstraints, true);
		deleteEmptyGridCells(parent, delConstraints, false);
	}

	private static void deleteEmptyGridCells(RadContainer parent, GridConstraints delConstraints, boolean isRow)
	{
		RadAbstractGridLayoutManager layoutManager = parent.getGridLayoutManager();
		for(int cell = delConstraints.getCell(isRow) + delConstraints.getSpan(isRow) - 1; cell >= delConstraints.getCell(isRow); cell--)
		{
			if(cell < parent.getGridCellCount(isRow) && GridChangeUtil.canDeleteCell(parent, cell, isRow) == GridChangeUtil.CellStatus.Empty &&
					!layoutManager.isGapCell(parent, isRow, cell))
			{
				layoutManager.deleteGridCells(parent, cell, isRow);
			}
		}
	}

	public static final int EMPTY_COMPONENT_SIZE = 5;

	private static Component getDeepestEmptyComponentAt(JComponent parent, Point location)
	{
		int size = parent.getComponentCount();

		for(int i = 0; i < size; i++)
		{
			Component child = parent.getComponent(i);

			if(child.isShowing())
			{
				if(child.getWidth() < EMPTY_COMPONENT_SIZE || child.getHeight() < EMPTY_COMPONENT_SIZE)
				{
					Point childLocation = child.getLocationOnScreen();
					Rectangle bounds = new Rectangle();

					bounds.x = childLocation.x;
					bounds.y = childLocation.y;
					bounds.width = child.getWidth();
					bounds.height = child.getHeight();
					bounds.grow(child.getWidth() < EMPTY_COMPONENT_SIZE ? EMPTY_COMPONENT_SIZE : 0, child.getHeight() < EMPTY_COMPONENT_SIZE ?
							EMPTY_COMPONENT_SIZE : 0);

					if(bounds.contains(location))
					{
						return child;
					}
				}

				if(child instanceof JComponent)
				{
					Component result = getDeepestEmptyComponentAt((JComponent) child, location);

					if(result != null)
					{
						return result;
					}
				}
			}
		}

		return null;
	}

	/**
	 * @param x in editor pane coordinates
	 * @param y in editor pane coordinates
	 */
	public static RadComponent getRadComponentAt(RadRootContainer rootContainer, int x, int y)
	{
		Point location = new Point(x, y);
		SwingUtilities.convertPointToScreen(location, rootContainer.getDelegee());
		Component c = getDeepestEmptyComponentAt(rootContainer.getDelegee(), location);

		if(c == null)
		{
			c = SwingUtilities.getDeepestComponentAt(rootContainer.getDelegee(), x, y);
		}

		RadComponent result = null;

		while(c != null)
		{
			if(c instanceof JComponent)
			{
				RadComponent component = (RadComponent) ((JComponent) c).getClientProperty(RadComponent.CLIENT_PROP_RAD_COMPONENT);
				if(component != null)
				{

					if(result == null)
					{
						result = component;
					}
					else
					{
						Point p = SwingUtilities.convertPoint(rootContainer.getDelegee(), x, y, c);
						if(Painter.getResizeMask(component, p.x, p.y) != 0)
						{
							result = component;
						}
					}
				}
			}
			c = c.getParent();
		}

		return result;
	}

	/**
	 * @return component which has dragger. There is only one component with the dragger
	 * at a time.
	 */
	@Nullable
	public static RadComponent getDraggerHost(@Nonnull GuiEditor editor)
	{
		final SimpleReference<RadComponent> result = new SimpleReference<>();
		iterate(editor.getRootContainer(), new ComponentVisitor<RadComponent>()
		{
			@Override
            public boolean visit(RadComponent component)
			{
				if(component.hasDragger())
				{
					result.set(component);
					return false;
				}
				return true;
			}
		});

		return result.get();
	}


	public static Cursor getMoveDropCursor()
	{
		try
		{
			return Cursor.getSystemCustomCursor("MoveDrop.32x32");
		}
		catch(Exception ex)
		{
			return Cursor.getDefaultCursor();
		}
	}

	public static Cursor getMoveNoDropCursor()
	{
		try
		{
			return Cursor.getSystemCustomCursor("MoveNoDrop.32x32");
		}
		catch(Exception ex)
		{
			return Cursor.getDefaultCursor();
		}
	}

	public static Cursor getCopyDropCursor()
	{
		try
		{
			return Cursor.getSystemCustomCursor("CopyDrop.32x32");
		}
		catch(Exception ex)
		{
			return Cursor.getDefaultCursor();
		}
	}

	/**
	 * @return currently selected components. Method returns the minimal amount of
	 * selected component which contains all selected components. It means that if the
	 * selected container contains some selected children then only this container
	 * will be added to the returned array.
	 */
	@Nonnull
	public static ArrayList<RadComponent> getSelectedComponents(@Nonnull GuiEditor editor)
	{
		ArrayList<RadComponent> result = new ArrayList<>();
		calcSelectedComponentsImpl(result, editor.getRootContainer());
		return result;
	}

	private static void calcSelectedComponentsImpl(ArrayList<RadComponent> result, RadContainer container)
	{
		if(container.isSelected())
		{
			if(container.getParent() != null)
			{ // ignore RadRootContainer
				result.add(container);
				return;
			}
		}

		for(int i = 0; i < container.getComponentCount(); i++)
		{
			RadComponent component = container.getComponent(i);
			if(component instanceof RadContainer)
			{
				calcSelectedComponentsImpl(result, (RadContainer) component);
			}
			else
			{
				if(component.isSelected())
				{
					result.add(component);
				}
			}
		}
	}

	/**
	 * @return all selected component inside the <code>editor</code>
	 */
	@Nonnull
	public static ArrayList<RadComponent> getAllSelectedComponents(@Nonnull GuiEditor editor)
	{
		final ArrayList<RadComponent> result = new ArrayList<>();
		iterate(editor.getRootContainer(), new ComponentVisitor<RadComponent>()
		{
			@Override
            public boolean visit(RadComponent component)
			{
				if(component.isSelected())
				{
					result.add(component);
				}
				return true;
			}
		});
		return result;
	}

	public static String getExceptionMessage(Throwable ex)
	{
		if(ex instanceof RuntimeException)
		{
			Throwable cause = ex.getCause();
			if(cause != null && cause != ex)
			{
				return getExceptionMessage(cause);
			}
		}
		else if(ex instanceof InvocationTargetException)
		{
			Throwable target = ((InvocationTargetException) ex).getTargetException();
			if(target != null && target != ex)
			{
				return getExceptionMessage(target);
			}
		}
		String message = ex.getMessage();
		if(ex instanceof ClassNotFoundException)
		{
			message = message != null
                ? UIDesignerLocalize.errorClassNotFoundN(message).get()
                : UIDesignerLocalize.errorClassNotFound().get();
		}
		else if(ex instanceof NoClassDefFoundError)
		{
			message = message != null
                ? UIDesignerLocalize.errorRequiredClassNotFoundN(message).get()
                : UIDesignerLocalize.errorRequiredClassNotFound().get();
		}
		return message;
	}

	public static IComponent findComponentWithBinding(IComponent component, String binding)
	{
		return findComponentWithBinding(component, binding, null);
	}

	public static IComponent findComponentWithBinding(IComponent component, String binding, @Nullable IComponent exceptComponent)
	{
		// Check that binding is unique
		SimpleReference<IComponent> boundComponent = new SimpleReference<>();
		iterate(component, thisComponent -> {
            if(thisComponent != exceptComponent && binding.equals(thisComponent.getBinding()))
            {
                boundComponent.set(thisComponent);
                return false;
            }
            return true;
        });

		return boundComponent.get();
	}

	@Nullable
	public static RadContainer getRadContainerAt(RadRootContainer rootContainer, int x, int y, int epsilon)
	{
		RadComponent component = getRadComponentAt(rootContainer, x, y);
		if(isNullOrRoot(component) && epsilon > 0)
		{
			// try to find component near specified location
			component = getRadComponentAt(rootContainer, x - epsilon, y - epsilon);
			if(isNullOrRoot(component))
			{
				component = getRadComponentAt(rootContainer, x - epsilon, y + epsilon);
			}
			if(isNullOrRoot(component))
			{
				component = getRadComponentAt(rootContainer, x + epsilon, y - epsilon);
			}
			if(isNullOrRoot(component))
			{
				component = getRadComponentAt(rootContainer, x + epsilon, y + epsilon);
			}
		}

		if(component != null)
		{
			return component instanceof RadContainer ? (RadContainer) component : component.getParent();
		}
		return null;
	}

	private static boolean isNullOrRoot(RadComponent component)
	{
		return component == null || component instanceof RadRootContainer;
	}

	public static GridConstraints getDefaultConstraints(RadComponent component)
	{
		Palette palette = Palette.getInstance(component.getProject());
		ComponentItem item = palette.getItem(component.getComponentClassName());
		if(item != null)
		{
			return item.getDefaultConstraints();
		}
		return new GridConstraints();
	}

	public static IRootContainer getRoot(IComponent component)
	{
		while(component != null)
		{
			if(component.getParentContainer() instanceof IRootContainer)
			{
				return (IRootContainer) component.getParentContainer();
			}
			component = component.getParentContainer();
		}
		return null;
	}

	/**
	 * Iterates component and its children (if any)
	 */
	public static void iterate(IComponent component, ComponentVisitor visitor)
	{
		iterateImpl(component, visitor);
	}

	private static boolean iterateImpl(IComponent component, ComponentVisitor visitor)
	{
		boolean shouldContinue;
		try
		{
			shouldContinue = visitor.visit(component);
		}
		catch(ProcessCanceledException ex)
		{
			return false;
		}
		if(!shouldContinue)
		{
			return false;
		}

		if(!(component instanceof IContainer))
		{
			return true;
		}

		IContainer container = (IContainer) component;

		for(int i = 0; i < container.getComponentCount(); i++)
		{
			IComponent c = container.getComponent(i);
			if(!iterateImpl(c, visitor))
			{
				return false;
			}
		}

		return true;
	}

	public static Set<String> collectUsedBundleNames(IRootContainer rootContainer)
	{
		Set<String> bundleNames = new HashSet<>();
		iterateStringDescriptors(rootContainer, (component, descriptor) -> {
            if(descriptor.getBundleName() != null && !bundleNames.contains(descriptor.getBundleName()))
            {
                bundleNames.add(descriptor.getBundleName());
            }
            return true;
        });
		return bundleNames;
	}

	@RequiredReadAction
    public static Locale[] collectUsedLocales(consulo.module.Module module, IRootContainer rootContainer)
	{
		Set<Locale> locales = new HashSet<>();
		PropertiesReferenceManager propManager = PropertiesReferenceManager.getInstance(module.getProject());
		for(String bundleName : collectUsedBundleNames(rootContainer))
		{
			List<PropertiesFile> propFiles = propManager.findPropertiesFiles(module, bundleName.replace('/', '.'));
			for(PropertiesFile propFile : propFiles)
			{
				locales.add(propFile.getLocale());
			}
		}
		return locales.toArray(new Locale[locales.size()]);
	}

	public static void deleteRowOrColumn(GuiEditor editor, RadContainer container, int[] cellsToDelete, boolean isRow)
	{
		Arrays.sort(cellsToDelete);
		int[] cells = ArrayUtil.reverseArray(cellsToDelete);
		if(!editor.ensureEditable())
		{
			return;
		}

        @RequiredUIAccess
		Runnable runnable = () -> {
            if(!GridChangeUtil.canDeleteCells(container, cells, isRow))
            {
                Set<RadComponent> componentsInColumn = new HashSet<>();
                for(RadComponent component : container.getComponents())
                {
                    GridConstraints c = component.getConstraints();
                    for(int cell : cells)
                    {
                        if(c.contains(isRow, cell))
                        {
                            componentsInColumn.add(component);
                            break;
                        }
                    }
                }

                if(componentsInColumn.size() > 0)
                {
                    LocalizeValue message = isRow
                        ? UIDesignerLocalize.deleteRowNonempty(componentsInColumn.size(), cells.length)
                        : UIDesignerLocalize.deleteColumnNonempty(componentsInColumn.size(), cells.length);

                    int rc = Messages.showYesNoDialog(
                        editor,
                        message.get(),
                        isRow ? UIDesignerLocalize.deleteRowTitle().get() : UIDesignerLocalize.deleteColumnTitle().get(),
                        UIUtil.getQuestionIcon()
                    );
                    if(rc != Messages.YES)
                    {
                        return;
                    }

                    deleteComponents(componentsInColumn, false);
                }
            }

            for(int cell : cells)
            {
                container.getGridLayoutManager().deleteGridCells(container, cell, isRow);
            }
            editor.refreshAndSave(true);
        };
        CommandProcessor.getInstance().newCommand()
            .project(editor.getProject())
            .name(isRow ? UIDesignerLocalize.commandDeleteRow() : UIDesignerLocalize.commandDeleteColumn())
            .run(runnable);
    }

	/**
	 * @param rootContainer
	 * @return id
	 */
	public static String generateId(RadRootContainer rootContainer)
	{
		while(true)
		{
			String id = Integer.toString((int) (Math.random() * 1024 * 1024), 16);
			if(findComponent(rootContainer, id) == null)
			{
				return id;
			}
		}
	}

	/**
	 * @return {@link GuiEditor} from the context. Can be <code>null</code>.
	 */
	@Nullable
	public static GuiEditor getEditorFromContext(@Nonnull DataContext context)
	{
		FileEditor editor = context.getData(FileEditor.KEY);
		if(editor instanceof UIFormEditor formEditor)
		{
			return formEditor.getEditor();
		}
		else
		{
			return context.getData(GuiEditor.DATA_KEY);
		}
	}

	@Nullable
	public static GuiEditor getActiveEditor(DataContext context)
	{
		Project project = context.getData(Project.KEY);
		if(project == null)
		{
			return null;
		}
		DesignerToolWindowManager toolWindowManager = DesignerToolWindowManager.getInstance(project);
		if(toolWindowManager == null)
		{
			return null;
		}
		return toolWindowManager.getActiveFormEditor();
	}

	/**
	 * @param componentToAssignBinding
	 * @param binding
	 * @param component                topmost container where to find duplicate binding. In most cases
	 *                                 it should be {@link GuiEditor#getRootContainer()}
	 */
	public static boolean isBindingUnique(IComponent componentToAssignBinding, String binding, IComponent component)
	{
		return findComponentWithBinding(component, binding, componentToAssignBinding) == null;
	}

    @Nullable
    @RequiredReadAction
	public static String buildResourceName(PsiFile file)
	{
		PsiDirectory directory = file.getContainingDirectory();
		if(directory != null)
		{
			PsiPackage pkg = JavaDirectoryService.getInstance().getPackage(directory);
			String packageName = pkg != null ? pkg.getQualifiedName() : "";
			if(packageName.isEmpty())
			{
				return file.getName();
			}
			return packageName.replace('.', '/') + '/' + file.getName();
		}
		return null;
	}

	@Nullable
	public static RadContainer getSelectionParent(List<RadComponent> selection)
	{
		RadContainer parent = null;
		for(RadComponent c : selection)
		{
			if(parent == null)
			{
				parent = c.getParent();
			}
			else if(parent != c.getParent())
			{
				parent = null;
				break;
			}
		}
		return parent;
	}

	public static Rectangle getSelectionBounds(List<RadComponent> selection)
	{
		int minRow = Integer.MAX_VALUE;
		int minCol = Integer.MAX_VALUE;
		int maxRow = 0;
		int maxCol = 0;

		for(RadComponent c : selection)
		{
			minRow = Math.min(minRow, c.getConstraints().getRow());
			minCol = Math.min(minCol, c.getConstraints().getColumn());
			maxRow = Math.max(maxRow, c.getConstraints().getRow() + c.getConstraints().getRowSpan());
			maxCol = Math.max(maxCol, c.getConstraints().getColumn() + c.getConstraints().getColSpan());
		}
		return new Rectangle(minCol, minRow, maxCol - minCol, maxRow - minRow);
	}

	public static boolean isComponentSwitchedInView(@Nonnull RadComponent component)
	{
		while(component.getParent() != null)
		{
			if(!component.getParent().getLayoutManager().isSwitchedToChild(component.getParent(), component))
			{
				return false;
			}
			component = component.getParent();
		}
		return true;
	}

	/**
	 * Selects the component and ensures that the tabbed panes containing the component are
	 * switched to the correct tab.
	 *
	 * @param editor
	 * @param component the component to select. @return true if the component is enclosed in at least one tabbed pane, false otherwise.
	 */
	public static boolean selectComponent(GuiEditor editor, @Nonnull RadComponent component)
	{
		boolean hasTab = false;
		RadComponent parent = component;
		while(parent.getParent() != null)
		{
			if(parent.getParent().getLayoutManager().switchContainerToChild(parent.getParent(), parent))
			{
				hasTab = true;
			}
			parent = parent.getParent();
		}
		component.setSelected(true);
		editor.setSelectionLead(component);
		return hasTab;
	}

	public static void selectSingleComponent(GuiEditor editor, RadComponent component)
	{
		RadContainer root = (RadContainer) getRoot(component);
		if(root == null)
		{
			return;
		}

		ComponentTreeBuilder builder = DesignerToolWindowManager.getInstance(editor).getComponentTreeBuilder();
		// this can return null if the click to select the control also requested to grab the focus -
		// the component tree will be instantiated after the event has been processed completely
		if(builder != null)
		{
			builder.beginUpdateSelection();
		}
		try
		{
			clearSelection(root);
			selectComponent(editor, component);
			editor.setSelectionAnchor(component);
			editor.scrollComponentInView(component);
		}
		finally
		{
			if(builder != null)
			{
				builder.endUpdateSelection();
			}
		}
	}

	public static void selectComponents(GuiEditor editor, List<RadComponent> components)
	{
		if(components.size() > 0)
		{
			RadComponent component = components.get(0);
			ComponentTreeBuilder builder = DesignerToolWindowManager.getInstance(editor).getComponentTreeBuilder();
			if(builder == null)
			{
				// race condition when handling event?
				return;
			}
			builder.beginUpdateSelection();
			try
			{
				clearSelection((RadContainer) getRoot(component));
				for(RadComponent aComponent : components)
				{
					selectComponent(editor, aComponent);
				}
			}
			finally
			{
				builder.endUpdateSelection();
			}
		}
	}

	public static boolean isDropOnChild(DraggedComponentList draggedComponentList, ComponentDropLocation location)
	{
		if(location.getContainer() == null)
		{
			return false;
		}

		for(RadComponent component : draggedComponentList.getComponents())
		{
			if(isChild(location.getContainer(), component))
			{
				return true;
			}
		}
		return false;
	}

	private static boolean isChild(RadContainer maybeChild, RadComponent maybeParent)
	{
		while(maybeChild != null)
		{
			if(maybeParent == maybeChild)
			{
				return true;
			}
			maybeChild = maybeChild.getParent();
		}
		return false;
	}

	public static PsiMethod findCreateComponentsMethod(PsiClass aClass)
	{
		PsiElementFactory factory = JavaPsiFacade.getInstance(aClass.getProject()).getElementFactory();
		PsiMethod method;
		try
		{
			method = factory.createMethodFromText("void " + AsmCodeGenerator.CREATE_COMPONENTS_METHOD_NAME + "() {}", aClass);
		}
		catch(IncorrectOperationException e)
		{
			throw new RuntimeException(e);
		}
		return aClass.findMethodBySignature(method, true);
	}

	public static Object getNextSaveUndoGroupId(Project project)
	{
		GuiEditor guiEditor = DesignerToolWindowManager.getInstance(project).getActiveFormEditor();
		return guiEditor == null ? null : guiEditor.getNextSaveGroupId();
	}

	public static int adjustForGap(RadContainer container, int cellIndex, boolean isRow, int delta)
	{
		if(container.getGridLayoutManager().isGapCell(container, isRow, cellIndex))
		{
			return cellIndex + delta;
		}
		return cellIndex;
	}

	public static int prevRow(RadContainer container, int row)
	{
		return adjustForGap(container, row - 1, true, -1);
	}

	public static int nextRow(RadContainer container, int row)
	{
		return adjustForGap(container, row + 1, true, 1);
	}

	public static int prevCol(RadContainer container, int col)
	{
		return adjustForGap(container, col - 1, false, -1);
	}

	public static int nextCol(RadContainer container, int col)
	{
		return adjustForGap(container, col + 1, false, 1);
	}

	@Nullable
	public static IButtonGroup findGroupForComponent(IRootContainer radRootContainer, @Nonnull IComponent component)
	{
		for(IButtonGroup group : radRootContainer.getButtonGroups())
		{
			for(String id : group.getComponentIds())
			{
				if(component.getId().equals(id))
				{
					return group;
				}
			}
		}
		return null;
	}

	public static void remapToActionTargets(List<RadComponent> selection)
	{
		for(int i = 0; i < selection.size(); i++)
		{
			RadComponent c = selection.get(i);
			if(c.getParent() != null)
			{
				selection.set(i, c.getParent().getActionTargetComponent(c));
			}
		}
	}

	public static void showPopupUnderComponent(JBPopup popup, RadComponent selectedComponent)
	{
		// popup.showUnderneathOf() doesn't work on invisible components
		Rectangle rc = selectedComponent.getBounds();
		Point pnt = new Point(rc.x, rc.y + rc.height);
		popup.show(new RelativePoint(selectedComponent.getDelegee().getParent(), pnt));
	}

	public interface StringDescriptorVisitor<T extends IComponent>
	{
		boolean visit(T component, StringDescriptor descriptor);
	}


	public static void iterateStringDescriptors(IComponent component, StringDescriptorVisitor<IComponent> visitor)
	{
		iterate(component, thisComponent -> {
            for(IProperty prop : thisComponent.getModifiedProperties())
            {
                Object value = prop.getPropertyValue(thisComponent);
                if(value instanceof StringDescriptor)
                {
                    if(!visitor.visit(thisComponent, (StringDescriptor) value))
                    {
                        return false;
                    }
                }
            }
            if(thisComponent.getParentContainer() instanceof ITabbedPane)
            {
                StringDescriptor tabTitle = ((ITabbedPane) thisComponent.getParentContainer()).getTabProperty(
                    thisComponent,
                        ITabbedPane.TAB_TITLE_PROPERTY);
                if(tabTitle != null && !visitor.visit(thisComponent, tabTitle))
                {
                    return false;
                }
                StringDescriptor tabToolTip = ((ITabbedPane) thisComponent.getParentContainer()).getTabProperty(
                    thisComponent,
                        ITabbedPane.TAB_TOOLTIP_PROPERTY);
                if(tabToolTip != null && !visitor.visit(thisComponent, tabToolTip))
                {
                    return false;
                }
            }
            if(thisComponent instanceof IContainer)
            {
                StringDescriptor borderTitle = ((IContainer) thisComponent).getBorderTitle();
                if(borderTitle != null && !visitor.visit(thisComponent, borderTitle))
                {
                    return false;
                }
            }
            return true;
        });
	}

	public static void clearSelection(@Nonnull RadContainer container)
	{
		container.setSelected(false);

		for(int i = 0; i < container.getComponentCount(); i++)
		{
			RadComponent c = container.getComponent(i);
			if(c instanceof RadContainer)
			{
				clearSelection((RadContainer) c);
			}
			else
			{
				c.setSelected(false);
			}
		}
	}

	/**
	 * Finds component with the specified <code>id</code> starting from the
	 * <code>container</code>. The method goes recursively through the hierarchy
	 * of components. Note, that if <code>container</code> itself has <code>id</code>
	 * then the method immediately returns it.
	 *
	 * @return the found component.
	 */
	@Nullable
	public static IComponent findComponent(@Nonnull IComponent component, @Nonnull String id)
	{
		if(id.equals(component.getId()))
		{
			return component;
		}
		if(!(component instanceof IContainer))
		{
			return null;
		}

		IContainer uiContainer = (IContainer) component;
		for(int i = 0; i < uiContainer.getComponentCount(); i++)
		{
			IComponent found = findComponent(uiContainer.getComponent(i), id);
			if(found != null)
			{
				return found;
			}
		}
		return null;
	}

	@Nullable
	public static PsiClass findClassToBind(@Nonnull Module module, @Nonnull String classToBindName)
	{
		return JavaPsiFacade.getInstance(module.getProject()).findClass(classToBindName.replace('$', '.'), GlobalSearchScope.moduleWithDependenciesScope(module));
	}

	public interface ComponentVisitor<Type extends IComponent>
	{
		/**
		 * @return true if iteration should continue
		 */
		boolean visit(Type component);
	}
}
