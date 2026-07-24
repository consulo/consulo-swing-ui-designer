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
package com.intellij.uiDesigner.impl.componentTree;

import com.intellij.java.analysis.impl.ide.highlighter.JavaHighlightingColors;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiField;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.impl.*;
import com.intellij.uiDesigner.impl.actions.StartInplaceEditingAction;
import com.intellij.uiDesigner.impl.designSurface.*;
import com.intellij.uiDesigner.impl.editor.UIFormEditor;
import com.intellij.uiDesigner.impl.palette.ComponentItem;
import com.intellij.uiDesigner.impl.palette.Palette;
import com.intellij.uiDesigner.impl.quickFixes.QuickFixManager;
import com.intellij.uiDesigner.impl.radComponents.*;
import com.intellij.uiDesigner.lw.LwInspectionSuppression;
import consulo.application.AllIcons;
import consulo.application.ApplicationManager;
import consulo.colorScheme.EditorColorsManager;
import consulo.colorScheme.EditorColorsScheme;
import consulo.colorScheme.TextAttributes;
import consulo.colorScheme.TextAttributesKey;
import consulo.dataContext.DataContext;
import consulo.dataContext.DataProvider;
import consulo.language.editor.CommonDataKeys;
import consulo.language.editor.PlatformDataKeys;
import consulo.language.editor.annotation.HighlightSeverity;
import consulo.language.editor.rawHighlight.HighlightDisplayLevel;
import consulo.language.editor.rawHighlight.SeverityRegistrar;
import consulo.language.psi.util.EditSourceUtil;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.ui.ex.DeleteProvider;
import consulo.ui.ex.JBColor;
import consulo.ui.ex.SimpleTextAttributes;
import consulo.ui.ex.action.*;
import consulo.ui.ex.awt.PopupHandler;
import consulo.ui.ex.awt.UIUtil;
import consulo.ui.ex.awt.tree.ColoredTreeCellRenderer;
import consulo.ui.ex.awt.tree.Tree;
import consulo.ui.ex.awt.tree.TreeUtil;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import consulo.ui.ex.tree.NodeDescriptor;
import consulo.ui.ex.util.TextAttributesUtil;
import consulo.ui.image.Image;
import consulo.util.collection.ArrayUtil;
import consulo.util.dataholder.Key;
import org.jetbrains.annotations.NonNls;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import javax.swing.*;
import javax.swing.plaf.TreeUI;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.*;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class ComponentTree extends Tree implements DataProvider
{
	private static final Logger LOG = Logger.getInstance(ComponentTree.class);

	public static final Key<LwInspectionSuppression[]> LW_INSPECTION_SUPPRESSION_ARRAY_DATA_KEY = Key.create(LwInspectionSuppression.class.getName());

	private SimpleTextAttributes myBindingAttributes; // exists only for performance reason
	private SimpleTextAttributes myClassAttributes; // exists only for performance reason
	private SimpleTextAttributes myPackageAttributes; // exists only for performance reason
	private SimpleTextAttributes myUnknownAttributes; // exists only for performance reason
	private SimpleTextAttributes myTitleAttributes; // exists only for performance reason

	private Map<HighlightSeverity, Map<SimpleTextAttributes, SimpleTextAttributes>> myHighlightAttributes;

	private GuiEditor myEditor;
	private UIFormEditor myFormEditor;
	private QuickFixManager myQuickFixManager;
	private RadComponent myDropTargetComponent = null;
	private final StartInplaceEditingAction myStartInplaceEditingAction;
	private final MyDeleteProvider myDeleteProvider = new MyDeleteProvider();

	@NonNls
	private static final String ourHelpID = "guiDesigner.uiTour.compsTree";
	private final Project myProject;

	public ComponentTree(@Nonnull Project project)
	{
		super(new DefaultTreeModel(new DefaultMutableTreeNode()));
		myProject = project;

		setCellRenderer(new MyTreeCellRenderer());
		setRootVisible(false);
		setShowsRootHandles(true);

		// Enable tooltips
		ToolTipManager.sharedInstance().registerComponent(this);

		// Install convenient keyboard navigation
		TreeUtil.installActions(this);

		// Popup menu
		PopupHandler.installPopupHandler(this, (ActionGroup) ActionManager.getInstance().getAction(IdeActions.GROUP_GUI_DESIGNER_COMPONENT_TREE_POPUP), ActionPlaces
				.GUI_DESIGNER_COMPONENT_TREE_POPUP, ActionManager.getInstance());

		// F2 should start inplace editing
		myStartInplaceEditingAction = new StartInplaceEditingAction(null);
		myStartInplaceEditingAction.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0)), this);

		if(!ApplicationManager.getApplication().isHeadlessEnvironment())
		{
			setDragEnabled(true);
			setTransferHandler(new TransferHandler()
			{
				@Override
				public int getSourceActions(JComponent c)
				{
					return DnDConstants.ACTION_COPY_OR_MOVE;
				}

				@Override
				protected Transferable createTransferable(JComponent c)
				{
					return DraggedComponentList.pickupSelection(myEditor, null);
				}
			});
			setDropTarget(new DropTarget(this, new MyDropTargetListener()));
		}
	}

	@Nonnull
	public Project getProject()
	{
		return myProject;
	}

	public void initQuickFixManager(JViewport viewPort)
	{
		myQuickFixManager = new QuickFixManagerImpl(null, this, viewPort);
	}

	public void setEditor(GuiEditor editor)
	{
		myEditor = editor;
		myDeleteProvider.setEditor(editor);
		myQuickFixManager.setEditor(editor);
		myStartInplaceEditingAction.setEditor(editor);
	}

	public void refreshIntentionHint()
	{
		myQuickFixManager.refreshIntentionHint();
	}

	@Override
	@Nullable
	public String getToolTipText(MouseEvent e)
	{
		TreePath path = getPathForLocation(e.getX(), e.getY());
		RadComponent component = getComponentFromPath(path);
		if(component != null)
		{
			ErrorInfo errorInfo = ErrorAnalyzer.getErrorForComponent(component);
			if(errorInfo != null)
			{
				return errorInfo.myDescription;
			}
		}
		return null;
	}

	@Nullable
	private static RadComponent getComponentFromPath(TreePath path)
	{
		if(path != null)
		{
			DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
			LOG.assertTrue(node != null);
			Object userObject = node.getUserObject();
			if(userObject instanceof ComponentPtrDescriptor)
			{
				ComponentPtrDescriptor descriptor = (ComponentPtrDescriptor) userObject;
				ComponentPtr ptr = descriptor.getElement();
				if(ptr != null && ptr.isValid())
				{
					RadComponent component = ptr.getComponent();
					LOG.assertTrue(component != null);
					return component;
				}
			}
		}
		return null;
	}

	/**
	 * TODO[vova] should return pair <RadComponent, TreePath>
	 *
	 * @return first selected component. The method returns <code>null</code>
	 * if there is no selection in the tree.
	 */
	@Nullable
	public RadComponent getSelectedComponent()
	{
		return ArrayUtil.getFirstElement(getSelectedComponents());
	}

	/**
	 * TODO[vova] should return pair <RadComponent, TreePath>
	 *
	 * @return currently selected components.
	 */
	@Nonnull
	public RadComponent[] getSelectedComponents()
	{
		TreePath[] paths = getSelectionPaths();
		if(paths == null)
		{
			return RadComponent.EMPTY_ARRAY;
		}
		ArrayList<RadComponent> result = new ArrayList<>(paths.length);
		for(TreePath path : paths)
		{
			DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
			if(node != null && node.getUserObject() instanceof ComponentPtrDescriptor)
			{
				ComponentPtrDescriptor descriptor = (ComponentPtrDescriptor) node.getUserObject();
				ComponentPtr ptr = descriptor.getElement();
				if(ptr != null && ptr.isValid())
				{
					result.add(ptr.getComponent());
				}
			}
		}
		return result.toArray(new RadComponent[result.size()]);
	}

	/**
	 * Provides {@link PlatformDataKeys#NAVIGATABLE} to navigate to
	 * binding of currently selected component (if any)
	 */
	@Override
	public Object getData(@Nonnull Key<?> dataId)
	{
		if(GuiEditor.DATA_KEY == dataId)
		{
			return myEditor;
		}

		if(PlatformDataKeys.DELETE_ELEMENT_PROVIDER == dataId)
		{
			return myDeleteProvider;
		}

		if(PlatformDataKeys.COPY_PROVIDER == dataId || PlatformDataKeys.CUT_PROVIDER == dataId || PlatformDataKeys.PASTE_PROVIDER == dataId)
		{
			return myEditor == null ? null : myEditor.getData(dataId);
		}

		if(LW_INSPECTION_SUPPRESSION_ARRAY_DATA_KEY == dataId)
		{
			Collection<LwInspectionSuppression> elements = getSelectedElements(LwInspectionSuppression.class);
			return elements.size() == 0 ? null : elements.toArray(new LwInspectionSuppression[elements.size()]);
		}

		if(PlatformDataKeys.HELP_ID == dataId)
		{
			return ourHelpID;
		}

		if(PlatformDataKeys.FILE_EDITOR == dataId)
		{
			return myFormEditor;
		}

		if(CommonDataKeys.NAVIGATABLE != dataId)
		{
			return null;
		}

		RadComponent selectedComponent = getSelectedComponent();
		if(selectedComponent == null)
		{
			return null;
		}

		String classToBind = myEditor.getRootContainer().getClassToBind();
		if(classToBind == null)
		{
			return null;
		}

		PsiClass aClass = FormEditingUtil.findClassToBind(myEditor.getModule(), classToBind);
		if(aClass == null)
		{
			return null;
		}

		if(selectedComponent instanceof RadRootContainer)
		{
			return EditSourceUtil.getDescriptor(aClass);
		}

		String binding = selectedComponent.getBinding();
		if(binding == null)
		{
			return null;
		}

		PsiField[] fields = aClass.getFields();

		for(PsiField field : fields)
		{
			if(binding.equals(field.getName()))
			{
				return EditSourceUtil.getDescriptor(field);
			}
		}

		return null;
	}

	public <T> List<T> getSelectedElements(Class<? extends T> elementClass)
	{
		TreePath[] paths = getSelectionPaths();
		if(paths == null)
		{
			return Collections.emptyList();
		}
		ArrayList<T> result = new ArrayList<>(paths.length);
		for(TreePath path : paths)
		{
			DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
			Object userObject = node.getUserObject();
			if(userObject instanceof NodeDescriptor && elementClass.isInstance(((NodeDescriptor) userObject).getElement()))
			{
				//noinspection unchecked
				result.add((T) ((NodeDescriptor) node.getUserObject()).getElement());
			}
		}
		return result;
	}

	private SimpleTextAttributes getAttribute(@Nonnull SimpleTextAttributes attrs, @Nullable HighlightDisplayLevel level)
	{
		if(level == null)
		{
			return attrs;
		}

		Map<SimpleTextAttributes, SimpleTextAttributes> highlightMap = myHighlightAttributes.get(level.getSeverity());
		if(highlightMap == null)
		{
			highlightMap = new HashMap<>();
			myHighlightAttributes.put(level.getSeverity(), highlightMap);
		}

		SimpleTextAttributes result = highlightMap.get(attrs);
		if(result == null)
		{
			TextAttributesKey attrKey = SeverityRegistrar.getSeverityRegistrar(myProject).getHighlightInfoTypeBySeverity(level.getSeverity()).getAttributesKey();
			TextAttributes textAttrs = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(attrKey);
			textAttrs = TextAttributes.merge(TextAttributesUtil.toTextAttributes(attrs), textAttrs);
			result = TextAttributesUtil.fromTextAttributes(textAttrs);
			highlightMap.put(attrs, result);
		}

		return result;
	}

	@Override
	public void setUI(TreeUI ui)
	{
		super.setUI(ui);

		// [vova] we cannot create this hash in constructor and just clear it here. The
		// problem is that setUI is invoked by constructor of superclass.
		myHighlightAttributes = new HashMap<>();

		EditorColorsScheme globalScheme = EditorColorsManager.getInstance().getGlobalScheme();
		TextAttributes attributes = globalScheme.getAttributes(JavaHighlightingColors.STRING);

		myBindingAttributes = new SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, UIUtil.getTreeForeground());
		myClassAttributes = new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, UIUtil.getTreeForeground());
		myPackageAttributes = new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, Color.GRAY);
		myTitleAttributes = new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, TargetAWT.to(attributes.getForegroundColor()));
		myUnknownAttributes = new SimpleTextAttributes(SimpleTextAttributes.STYLE_WAVED, Color.RED);
	}

	public static Image getComponentIcon(RadComponent component)
	{
		if(!(component instanceof RadErrorComponent))
		{
			Palette palette = Palette.getInstance(component.getProject());
			ComponentItem item = palette.getItem(component.getComponentClassName());
			Image icon;
			if(item != null)
			{
				icon = item.getSmallIcon();
			}
			else
			{
				icon = UIDesignerIcons.Unknown;
			}
			return icon;
		}
		else
		{
			return AllIcons.General.Error;
		}
	}

	public void setDropTargetComponent(@Nullable RadComponent dropTargetComponent)
	{
		if(dropTargetComponent != myDropTargetComponent)
		{
			myDropTargetComponent = dropTargetComponent;
			repaint();
		}
	}

	public void setFormEditor(UIFormEditor formEditor)
	{
		myFormEditor = formEditor;
	}

	private final class MyTreeCellRenderer extends ColoredTreeCellRenderer
	{
		@NonNls
		private static final String SWING_PACKAGE = "javax.swing";

		@Override
		public void customizeCellRenderer(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus)
		{
			DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
			if(node.getUserObject() instanceof ComponentPtrDescriptor)
			{
				ComponentPtrDescriptor descriptor = (ComponentPtrDescriptor) node.getUserObject();
				ComponentPtr ptr = descriptor.getElement();
				if(ptr == null)
				{
					return;
				}
				RadComponent component = ptr.getComponent();
				if(component == null)
				{
					return;
				}

				HighlightDisplayLevel level = ErrorAnalyzer.getHighlightDisplayLevel(myProject, component);

				// Text
				boolean hasText = false;
				String binding = component.getBinding();
				if(binding != null)
				{
					append(binding, getAttribute(myBindingAttributes, level));
					append(" : ", getAttribute(myClassAttributes, level));
					hasText = true;
				}
				else
				{
					String componentTitle = component.getComponentTitle();
					if(componentTitle != null)
					{
						append(componentTitle, getAttribute(myTitleAttributes, level));
						append(" : ", getAttribute(myClassAttributes, level));
						hasText = true;
					}
				}

				String componentClassName = component.getComponentClassName();

				if(component instanceof RadVSpacer)
				{
					append(UIDesignerBundle.message("component.vertical.spacer"), getAttribute(myClassAttributes, level));
				}
				else if(component instanceof RadHSpacer)
				{
					append(UIDesignerBundle.message("component.horizontal.spacer"), getAttribute(myClassAttributes, level));
				}
				else if(component instanceof RadErrorComponent)
				{
					RadErrorComponent c = (RadErrorComponent) component;
					append(c.getErrorDescription(), getAttribute(myUnknownAttributes, level));
				}
				else if(component instanceof RadRootContainer)
				{
					append(UIDesignerBundle.message("component.form"), getAttribute(myClassAttributes, level));
					append(" (", getAttribute(myPackageAttributes, level));
					String classToBind = ((RadRootContainer) component).getClassToBind();
					if(classToBind != null)
					{
						append(classToBind, getAttribute(myPackageAttributes, level));
					}
					else
					{
						append(UIDesignerBundle.message("component.no.binding"), getAttribute(myPackageAttributes, level));
					}
					append(")", getAttribute(myPackageAttributes, level));
				}
				else
				{
					String packageName = null;
					int pos = componentClassName.lastIndexOf('.');
					if(pos >= 0)
					{
						packageName = componentClassName.substring(0, pos);
					}

					SimpleTextAttributes classAttributes = hasText ? myPackageAttributes : myClassAttributes;

					if(packageName != null)
					{
						append(componentClassName.substring(packageName.length() + 1).replace('$', '.'), getAttribute(classAttributes, level));
						if(!packageName.equals(SWING_PACKAGE))
						{
							append(" (", getAttribute(myPackageAttributes, level));
							append(packageName, getAttribute(myPackageAttributes, level));
							append(")", getAttribute(myPackageAttributes, level));
						}
					}
					else
					{
						append(componentClassName.replace('$', '.'), getAttribute(classAttributes, level));
					}
				}

				// Icon
				setIcon(getComponentIcon(component));

				if(component == myDropTargetComponent)
				{
					setBorder(BorderFactory.createLineBorder(JBColor.BLUE, 1));
				}
				else
				{
					setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1));
				}
			}
			else if(node.getUserObject() != null)
			{
				String fragment = node.getUserObject().toString();
				if(fragment != null)
				{
					append(fragment, SimpleTextAttributes.REGULAR_ATTRIBUTES);
				}
				if(node.getUserObject() instanceof SuppressionDescriptor)
				{
					setIcon(UIDesignerIcons.InspectionSuppression);
				}
				else if(node.getUserObject() instanceof ButtonGroupDescriptor)
				{
					setIcon(UIDesignerIcons.ButtonGroup);
				}
			}
		}
	}

	private final class MyDropTargetListener extends DropTargetAdapter
	{
		@Override
		public void dragOver(DropTargetDragEvent dtde)
		{
			try
			{
				RadComponent dropTargetComponent = null;
				ComponentDragObject dragObject = null;

				DraggedComponentList dcl = DraggedComponentList.fromTransferable(dtde.getTransferable());
				if(dcl != null)
				{
					dragObject = dcl;
				}
				else
				{
					ComponentItem componentItem = SimpleTransferable.getData(dtde.getTransferable(), ComponentItem.class);
					if(componentItem != null)
					{
						dragObject = new ComponentItemDragObject(componentItem);
					}
				}

				boolean canDrop = false;
				if(dragObject != null)
				{
					TreePath path = getPathForLocation((int) dtde.getLocation().getX(), (int) dtde.getLocation().getY());
					RadComponent targetComponent = getComponentFromPath(path);
					if(path != null && targetComponent instanceof RadContainer)
					{
						ComponentDropLocation dropLocation = ((RadContainer) targetComponent).getDropLocation(null);
						canDrop = dropLocation.canDrop(dragObject);
						if(dcl != null && FormEditingUtil.isDropOnChild(dcl, dropLocation))
						{
							canDrop = false;
						}
						if(canDrop)
						{
							dropTargetComponent = targetComponent;
							dtde.acceptDrag(dtde.getDropAction());
						}
					}
				}
				if(!canDrop)
				{
					dtde.rejectDrag();
				}
				setDropTargetComponent(dropTargetComponent);
			}
			catch(Exception e)
			{
				LOG.error(e);
			}
		}

		@Override
		public void dragExit(DropTargetEvent dte)
		{
			setDropTargetComponent(null);
		}

		@Override
		public void drop(DropTargetDropEvent dtde)
		{
			try
			{
				DraggedComponentList dcl = DraggedComponentList.fromTransferable(dtde.getTransferable());
				ComponentItem componentItem = SimpleTransferable.getData(dtde.getTransferable(), ComponentItem.class);
				if(dcl != null || componentItem != null)
				{
					TreePath path = getPathForLocation((int) dtde.getLocation().getX(), (int) dtde.getLocation().getY());
					RadComponent targetComponent = getComponentFromPath(path);
					if(!myEditor.ensureEditable())
					{
						return;
					}
					if(targetComponent instanceof RadContainer)
					{
						ComponentDropLocation dropLocation = ((RadContainer) targetComponent).getDropLocation(null);
						if(dcl != null)
						{
							if(!FormEditingUtil.isDropOnChild(dcl, dropLocation))
							{
								RadComponent[] components = dcl.getComponents().toArray(new RadComponent[dcl.getComponents().size()]);
								RadContainer[] originalParents = dcl.getOriginalParents();
								GridConstraints[] originalConstraints = dcl.getOriginalConstraints();
								for(int i = 0; i < components.length; i++)
								{
									originalParents[i].removeComponent(components[i]);
								}
								dropLocation.processDrop(myEditor, components, null, dcl);
								for(int i = 0; i < originalConstraints.length; i++)
								{
									if(originalParents[i].getLayoutManager().isGrid())
									{
										FormEditingUtil.deleteEmptyGridCells(originalParents[i], originalConstraints[i]);
									}
								}
							}
						}
						else
						{
							new InsertComponentProcessor(myEditor).processComponentInsert(componentItem, dropLocation);
						}
					}
					myEditor.refreshAndSave(true);
				}
				setDropTargetComponent(null);
			}
			catch(Exception e)
			{
				LOG.error(e);
			}
		}
	}

	private static class MyDeleteProvider implements DeleteProvider
	{
		private GuiEditor myEditor;

		public void setEditor(GuiEditor editor)
		{
			myEditor = editor;
		}

		@Override
		public void deleteElement(@Nonnull DataContext dataContext)
		{
			if(myEditor != null)
			{
				LwInspectionSuppression[] suppressions = dataContext.getData(LW_INSPECTION_SUPPRESSION_ARRAY_DATA_KEY);
				if(suppressions != null)
				{
					if(!myEditor.ensureEditable())
					{
						return;
					}
					for(LwInspectionSuppression suppression : suppressions)
					{
						myEditor.getRootContainer().removeInspectionSuppression(suppression);
					}
					myEditor.refreshAndSave(true);
				}
				else
				{
					DeleteProvider baseProvider = myEditor.getDataUnchecked(PlatformDataKeys.DELETE_ELEMENT_PROVIDER);
					if(baseProvider != null)
					{
						baseProvider.deleteElement(dataContext);
					}
				}
			}
		}

		@Override
		public boolean canDeleteElement(@Nonnull DataContext dataContext)
		{
			if(myEditor != null)
			{
				LwInspectionSuppression[] suppressions = dataContext.getData(LW_INSPECTION_SUPPRESSION_ARRAY_DATA_KEY);
				if(suppressions != null)
				{
					return true;
				}
				DeleteProvider baseProvider = myEditor.getDataUnchecked(PlatformDataKeys.DELETE_ELEMENT_PROVIDER);
				if(baseProvider != null)
				{
					return baseProvider.canDeleteElement(dataContext);
				}
			}
			return false;
		}
	}
}
