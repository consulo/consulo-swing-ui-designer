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
package com.intellij.uiDesigner.impl.palette;

import com.intellij.ide.palette.PaletteItem;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.impl.HSpacer;
import com.intellij.uiDesigner.impl.UIDesignerBundle;
import com.intellij.uiDesigner.impl.UIDesignerIcons;
import com.intellij.uiDesigner.impl.VSpacer;
import com.intellij.uiDesigner.impl.binding.FormClassIndex;
import com.intellij.uiDesigner.impl.propertyInspector.IntrospectedProperty;
import com.intellij.uiDesigner.impl.radComponents.RadAtomicComponent;
import com.intellij.uiDesigner.lw.StringDescriptor;
import consulo.ide.impl.idea.openapi.module.ResourceFileUtil;
import consulo.language.editor.LangDataKeys;
import consulo.language.psi.PsiFile;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.ui.ex.SimpleTextAttributes;
import consulo.ui.ex.action.ActionGroup;
import consulo.ui.ex.action.ActionManager;
import consulo.ui.ex.awt.ColoredListCellRenderer;
import consulo.ui.ex.awt.dnd.DnDDragStartBean;
import consulo.ui.image.Image;
import consulo.ui.image.ImageKey;
import consulo.util.dataholder.Key;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.annotations.NonNls;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class ComponentItem implements Cloneable, PaletteItem
{
	private static final Logger LOG = Logger.getInstance(ComponentItem.class);

	public static final Key<ComponentItem> DATA_KEY = Key.create(ComponentItem.class.getName());

	private String myClassName;
	private final GridConstraints myDefaultConstraints;
	/**
	 * Do not use this member directly. Use {@link #getIcon()} instead.
	 */
	private consulo.ui.image.Image myIcon;
	/**
	 * Do not use this member directly. Use {@link #getSmallIcon()} instead.
	 */
	private consulo.ui.image.Image mySmallIcon;
	/**
	 * @see #getIconPath()
	 * @see #setIconPath(String)
	 */
	private String myIconPath;
	/**
	 * Do not access this field directly. Use {@link #getToolTipText()} instead.
	 */
	final String myToolTipText;
	private final Map<String, StringDescriptor> myPropertyName2initialValue;
	/**
	 * Whether item is removable or not
	 */
	private final boolean myRemovable;

	private boolean myAutoCreateBinding;
	private boolean myCanAttachLabel;
	private boolean myIsContainer;
	private boolean myAnyComponent;
	private Dimension myInitialSize;

	@Nonnull
	private final Project myProject;

	public ComponentItem(@Nonnull Project project,
						 @Nonnull String className,
						 @Nullable String iconPath,
						 @Nullable String toolTipText,
						 @Nonnull GridConstraints defaultConstraints,
						 @Nonnull Map<String, StringDescriptor> propertyName2initialValue,
						 boolean removable,
						 boolean autoCreateBinding,
						 boolean canAttachLabel)
	{
		myAutoCreateBinding = autoCreateBinding;
		myCanAttachLabel = canAttachLabel;
		myProject = project;
		setClassName(className);
		setIconPath(iconPath);

		myToolTipText = toolTipText;
		myDefaultConstraints = defaultConstraints;
		myPropertyName2initialValue = propertyName2initialValue;

		myRemovable = removable;
	}

	/**
	 * @return whether the item is removable from palette or not.
	 */
	public boolean isRemovable()
	{
		return myRemovable;
	}

	private static String calcToolTipText(@Nonnull String className)
	{
		int lastDotIndex = className.lastIndexOf('.');
		if(lastDotIndex != -1 && lastDotIndex != className.length() - 1/*not the last char in class name*/)
		{
			return className.substring(lastDotIndex + 1) + " (" + className.substring(0, lastDotIndex) + ")";
		}
		else
		{
			return className;
		}
	}

	/**
	 * Creates deep copy of the object. You can edit any properties of the returned object.
	 */
	@Override
    public ComponentItem clone()
	{
		ComponentItem result = new ComponentItem(
		    myProject,
            myClassName,
            myIconPath,
            myToolTipText,
            (GridConstraints) myDefaultConstraints.clone(),
            new HashMap<>(myPropertyName2initialValue),
            myRemovable,
            myAutoCreateBinding,
            myCanAttachLabel
        );
		result.setIsContainer(myIsContainer);
		return result;
	}

	/**
	 * @return string that represents path in the JAR file system that was used to load
	 * icon returned by {@link #getIcon()} method. This method can returns <code>null</code>.
	 * It means that palette item has some "unknown" item.
	 */
	@Nullable
	String getIconPath()
	{
		return myIconPath;
	}

	/**
	 * @param iconPath new path inside JAR file system. <code>null</code> means that
	 *                 <code>iconPath</code> is not specified and some "unknown" icon should be used
	 *                 to represent the {@link ComponentItem} in UI.
	 */
	void setIconPath(@Nullable String iconPath)
	{
		myIcon = null; // reset cached icon
		mySmallIcon = null; // reset cached icon

		myIconPath = iconPath;
	}

	/**
	 * @return item's icon. This icon is used to represent item at the toolbar.
	 * Note, that the method never returns <code>null</code>. It returns some
	 * default "unknown" icon for the items that has no specified icon in the XML.
	 */
	@Nonnull
	public consulo.ui.image.Image getIcon()
	{
		// Check cached value first
		if(myIcon != null)
		{
			return myIcon;
		}

		// Create new icon
		if(myIconPath != null && myIconPath.length() > 0)
		{
			VirtualFile iconFile = ResourceFileUtil.findResourceFileInScope(myIconPath, myProject, GlobalSearchScope.allScope(myProject));
			if(iconFile != null)
			{
				try
				{
					myIcon = consulo.ui.image.Image.fromBytes(Image.ImageType.PNG, iconFile.contentsToByteArray(), Image.DEFAULT_ICON_SIZE, Image.DEFAULT_ICON_SIZE);
				}
				catch(Exception e)
				{
					myIcon = null;
				}
			}
			else
			{
				myIconPath = myIconPath.replace("consulo.uiDesigner.UIDesignerIconGroup@", "consulo.platform.base.PlatformIconGroup@uiDesigner.");
				myIconPath = myIconPath.replace("/com/intellij/uiDesigner/icons/", "consulo.platform.base.PlatformIconGroup@uiDesigner.");
				myIconPath = StringUtil.trimEnd(myIconPath, ".png");

				ImageKey imageKey = ImageKey.fromString(myIconPath, Image.DEFAULT_ICON_SIZE, Image.DEFAULT_ICON_SIZE);
				if(imageKey != null)
				{
					myIcon = imageKey;
				}
			}
		}
		if(myIcon == null)
		{
			myIcon = UIDesignerIcons.Unknown;
		}
		LOG.assertTrue(myIcon != null);
		return myIcon;
	}

	/**
	 * @return small item's icon. This icon represents component in the
	 * component tree. The method never returns <code>null</code>. It returns some
	 * default "unknown" icon for the items that has no specified icon in the XML.
	 */
	@Nonnull
	public consulo.ui.image.Image getSmallIcon()
	{
		// Check cached value first
		if(mySmallIcon != null)
		{
			return myIcon;
		}

		mySmallIcon = getIcon();
		return mySmallIcon;
	}

	/**
	 * @return name of component's class which is represented by the item.
	 */
	@Nonnull
	public String getClassName()
	{
		return myClassName;
	}

	public String getClassShortName()
	{
		int lastDotIndex = myClassName.lastIndexOf('.');
		if(lastDotIndex != -1 && lastDotIndex != myClassName.length() - 1/*not the last char in class name*/)
		{
			return myClassName.substring(lastDotIndex + 1).replace('$', '.');
		}
		else
		{
			return myClassName.replace('$', '.');
		}
	}

	/**
	 * @param className name of the class that will be instanteated when user drop
	 *                  item on the form. Cannot be <code>null</code>. If the class does not exist or
	 *                  could not be instanteated (for example, class has no default constructor,
	 *                  it's not a subclass of JComponent, etc) then placeholder component will be
	 *                  added to the form.
	 */
	public void setClassName(@Nonnull String className)
	{
		myClassName = className;
	}

	public String getToolTipText()
	{
		return myToolTipText != null ? myToolTipText : calcToolTipText(myClassName);
	}

	@Nonnull
	public GridConstraints getDefaultConstraints()
	{
		return myDefaultConstraints;
	}

	/**
	 * The method returns initial value of the property. Term
	 * "initial" means that just after creation of RadComponent
	 * all its properties are set into initial values.
	 * The method returns <code>null</code> if the
	 * initial property is not defined. Unfortunately we cannot
	 * put this method into the constuctor of <code>RadComponent</code>.
	 * The problem is that <code>RadComponent</code> is used in the
	 * code genaration and code generation doesn't depend on any
	 * <code>ComponentItem</code>, so we need to initialize <code>RadComponent</code>
	 * in all places where it's needed explicitly.
	 */
	public Object getInitialValue(IntrospectedProperty property)
	{
		return myPropertyName2initialValue.get(property.getName());
	}

	/**
	 * Internal method. It should be used only to externalize initial item's values.
	 * This method never returns <code>null</code>.
	 */
	Map<String, StringDescriptor> getInitialValues()
	{
		return myPropertyName2initialValue;
	}

	public boolean isAutoCreateBinding()
	{
		return myAutoCreateBinding;
	}

	public void setAutoCreateBinding(boolean autoCreateBinding)
	{
		myAutoCreateBinding = autoCreateBinding;
	}

	public boolean isCanAttachLabel()
	{
		return myCanAttachLabel;
	}

	public void setCanAttachLabel(boolean canAttachLabel)
	{
		myCanAttachLabel = canAttachLabel;
	}

	public boolean isContainer()
	{
		return myIsContainer;
	}

	public void setIsContainer(boolean isContainer)
	{
		myIsContainer = isContainer;
	}

	public boolean equals(Object o)
	{
		if(this == o)
		{
			return true;
		}
		if(!(o instanceof ComponentItem))
		{
			return false;
		}

		ComponentItem componentItem = (ComponentItem) o;

		if(myClassName != null ? !myClassName.equals(componentItem.myClassName) : componentItem.myClassName != null)
		{
			return false;
		}
		if(myDefaultConstraints != null ? !myDefaultConstraints.equals(componentItem.myDefaultConstraints) : componentItem.myDefaultConstraints != null)
		{
			return false;
		}
		if(myIconPath != null ? !myIconPath.equals(componentItem.myIconPath) : componentItem.myIconPath != null)
		{
			return false;
		}
		if(myPropertyName2initialValue != null ? !myPropertyName2initialValue.equals(componentItem.myPropertyName2initialValue) : componentItem.myPropertyName2initialValue != null)
		{
			return false;
		}
		if(myToolTipText != null ? !myToolTipText.equals(componentItem.myToolTipText) : componentItem.myToolTipText != null)
		{
			return false;
		}

		return true;
	}

	public int hashCode()
	{
		int result;
		result = (myClassName != null ? myClassName.hashCode() : 0);
		result = 29 * result + (myDefaultConstraints != null ? myDefaultConstraints.hashCode() : 0);
		result = 29 * result + (myIconPath != null ? myIconPath.hashCode() : 0);
		result = 29 * result + (myToolTipText != null ? myToolTipText.hashCode() : 0);
		result = 29 * result + (myPropertyName2initialValue != null ? myPropertyName2initialValue.hashCode() : 0);
		return result;
	}

	public void customizeCellRenderer(ColoredListCellRenderer cellRenderer, boolean selected, boolean hasFocus)
	{
		cellRenderer.setIcon(getSmallIcon());
		if(myAnyComponent)
		{
			cellRenderer.append(UIDesignerBundle.message("palette.non.palette.component"), SimpleTextAttributes.REGULAR_ATTRIBUTES);
			cellRenderer.setToolTipText(UIDesignerBundle.message("palette.non.palette.component.tooltip"));
		}
		else
		{
			cellRenderer.append(getClassShortName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
			cellRenderer.setToolTipText(getToolTipText());
		}
	}

	@Nullable
	public DnDDragStartBean startDragging()
	{
		if(isAnyComponent())
		{
			return null;
		}
		return new DnDDragStartBean(this);
	}

	@Nullable
	public ActionGroup getPopupActionGroup()
	{
		return (ActionGroup) ActionManager.getInstance().getAction("GuiDesigner.PaletteComponentPopupMenu");
	}

	@Nullable
	public Object getData(Project project, Key<?> dataId)
	{
		if(LangDataKeys.PSI_ELEMENT == dataId)
		{
			return JavaPsiFacade.getInstance(project).findClass(myClassName, GlobalSearchScope.allScope(project));
		}
		if(DATA_KEY == dataId)
		{
			return this;
		}
		if(GroupItem.DATA_KEY == dataId)
		{
			return Palette.getInstance(project).findGroup(this);
		}
		return null;
	}

	@Nullable
	public PsiFile getBoundForm()
	{
		if(myClassName.length() == 0 || myClassName.startsWith("javax.swing"))
		{
			return null;
		}
		List<PsiFile> boundForms = FormClassIndex.findFormsBoundToClass(myProject, myClassName.replace('$', '.'));
		if(boundForms.size() > 0)
		{
			return boundForms.get(0);
		}
		return null;
	}

	@Nonnull
	public Dimension getInitialSize(JComponent parent, ClassLoader loader)
	{
		if(myInitialSize != null)
		{
			return myInitialSize;
		}
		myInitialSize = new Dimension(myDefaultConstraints.myPreferredSize);
		if(myInitialSize.width <= 0 || myInitialSize.height <= 0)
		{
			try
			{
				Class aClass = Class.forName(getClassName(), true, loader);
				RadAtomicComponent component = new RadAtomicComponent(aClass, "", Palette.getInstance(myProject));
				component.initDefaultProperties(this);
				JComponent delegee = component.getDelegee();
				if(parent != null)
				{
					Font font = parent.getFont();
					delegee.setFont(font);
				}
				Dimension prefSize = delegee.getPreferredSize();
				Dimension minSize = delegee.getMinimumSize();
				if(myInitialSize.width <= 0)
				{
					myInitialSize.width = prefSize.width;
				}
				if(myInitialSize.height <= 0)
				{
					myInitialSize.height = prefSize.height;
				}
				myInitialSize.width = Math.max(myInitialSize.width, minSize.width);
				myInitialSize.height = Math.max(myInitialSize.height, minSize.height);
			}
			catch(Exception e)
			{
				LOG.debug(e);
			}
		}
		return myInitialSize;
	}

	public static ComponentItem createAnyComponentItem(Project project)
	{
		ComponentItem result = new ComponentItem(project, "", null, null, new GridConstraints(), new HashMap<>(), false, false, false);
		result.myAnyComponent = true;
		return result;
	}

	public boolean isAnyComponent()
	{
		return myAnyComponent;
	}

	public boolean isSpacer()
	{
		return myClassName.equals(HSpacer.class.getName()) || myClassName.equals(VSpacer.class.getName());
	}
}
