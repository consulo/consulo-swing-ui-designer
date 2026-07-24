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

import com.intellij.java.language.impl.JavaFileType;
import com.intellij.java.language.psi.JavaCodeFragmentFactory;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiJavaPackage;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.intellij.java.language.util.TreeClassChooser;
import com.intellij.java.language.util.TreeClassChooserFactory;
import com.intellij.uiDesigner.compiler.Utils;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.impl.FormEditingUtil;
import com.intellij.uiDesigner.impl.GuiFormFileType;
import com.intellij.uiDesigner.impl.ImageFileFilter;
import com.intellij.uiDesigner.lw.LwRootContainer;
import consulo.annotation.access.RequiredReadAction;
import consulo.application.HelpManager;
import consulo.document.Document;
import consulo.ide.impl.idea.openapi.module.ResourceFileUtil;
import consulo.language.editor.ui.TreeFileChooser;
import consulo.language.editor.ui.awt.EditorTextField;
import consulo.language.psi.PsiCodeFragment;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.platform.base.localize.CommonLocalize;
import consulo.project.Project;
import consulo.project.content.scope.ProjectScopes;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.awt.ComponentWithBrowseButton;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.ui.ex.awt.Messages;
import consulo.ui.ex.awt.TextFieldWithBrowseButton;
import consulo.ui.ex.awt.event.DocumentAdapter;
import consulo.uiDesigner.impl.localize.UIDesignerLocalize;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.function.Predicate;

/**
 * @author Vladimir Kondratyev
 */
public final class ComponentItemDialog extends DialogWrapper
{
	private JPanel myPanel;
	private final ComponentWithBrowseButton<EditorTextField> myTfClassName;
	private final Project myProject;
	private final ComponentItem myItemToBeEdited;
	private final boolean myOneOff;
	private JLabel myLblIcon;
	private TextFieldWithBrowseButton myTfIconPath;
	private JCheckBox myChkHorCanShrink;
	private JCheckBox myChkHorCanGrow;
	private JCheckBox myChkHorWantGrow;
	private JCheckBox myChkVerCanShrink;
	private JCheckBox myChkVerCanGrow;
	private JCheckBox myChkVerWantGrow;
	private JPanel myClassNamePlaceholder;
	private JRadioButton myClassRadioButton;
	private JRadioButton myNestedFormRadioButton;
	private TextFieldWithBrowseButton myTfNestedForm;
	private JCheckBox myAutoCreateBindingCheckbox;
	private JCheckBox myCanAttachLabelCheckbox;
	private JPanel myHSizePolicyPanel;
	private JPanel myVSizePolicyPanel;
	private JComboBox<GroupItem> myGroupComboBox;
	private JLabel myGroupLabel;
	private JCheckBox myIsContainerCheckBox;
	private JLabel myErrorLabel;
	private final EditorTextField myEditorTextField;
	private Document myDocument;

	/**
	 * @param itemToBeEdited item to be edited. If user closes dialog by "OK" button then
	 * @param oneOff
	 */
	public ComponentItemDialog(Project project, Component parent, @Nonnull ComponentItem itemToBeEdited, boolean oneOff)
	{
		super(parent, false);
		myProject = project;

		myItemToBeEdited = itemToBeEdited;
		myOneOff = oneOff;

		myEditorTextField = new EditorTextField("", project, JavaFileType.INSTANCE);
		myEditorTextField.setFontInheritedFromLAF(true);
		myTfClassName = new ComponentWithBrowseButton<>(myEditorTextField, new MyChooseClassActionListener(project));

		PsiFile boundForm = itemToBeEdited.getBoundForm();
		if(boundForm != null)
		{
			myNestedFormRadioButton.setSelected(true);
			myTfNestedForm.setText(FormEditingUtil.buildResourceName(boundForm));
		}
		else
		{
			myClassRadioButton.setSelected(true);
			setEditorText(myItemToBeEdited.getClassName().replace('$', '.'));
		}
		updateEnabledTextField();

		myTfClassName.getChildComponent().addDocumentListener(new consulo.document.event.DocumentAdapter()
		{
			@Override
            public void documentChanged(consulo.document.event.DocumentEvent e)
			{
				updateOKAction();
			}
		});

		myTfClassName.getButton().setEnabled(!project.isDefault()); // chooser should not work in default project
		myClassNamePlaceholder.setLayout(new BorderLayout());
		myClassNamePlaceholder.add(myTfClassName, BorderLayout.CENTER);

		myTfIconPath.setText(myItemToBeEdited.getIconPath());
		myTfIconPath.addActionListener(new MyChooseFileActionListener(
		    project,
            new ImageFileFilter(null),
            myTfIconPath,
            UIDesignerLocalize.addComponentChooseIcon().get()
        ));

		myTfNestedForm.addActionListener(new MyChooseFileActionListener(
		    project,
            file -> file.getFileType().equals(GuiFormFileType.INSTANCE),
            myTfNestedForm,
            UIDesignerLocalize.addComponentChooseForm().get()
        ));

		myTfNestedForm.getTextField().getDocument().addDocumentListener(new DocumentAdapter()
		{
			@Override
            protected void textChanged(DocumentEvent e)
			{
				updateOKAction();
			}
		});

		GridConstraints defaultConstraints = myItemToBeEdited.getDefaultConstraints();

		// Horizontal size policy
		{
			int hSizePolicy = defaultConstraints.getHSizePolicy();
			myChkHorCanShrink.setSelected((hSizePolicy & GridConstraints.SIZEPOLICY_CAN_SHRINK) != 0);
			myChkHorCanGrow.setSelected((hSizePolicy & GridConstraints.SIZEPOLICY_CAN_GROW) != 0);
			myChkHorWantGrow.setSelected((hSizePolicy & GridConstraints.SIZEPOLICY_WANT_GROW) != 0);
		}

		// Vertical size policy
		{
			int vSizePolicy = defaultConstraints.getVSizePolicy();
			myChkVerCanShrink.setSelected((vSizePolicy & GridConstraints.SIZEPOLICY_CAN_SHRINK) != 0);
			myChkVerCanGrow.setSelected((vSizePolicy & GridConstraints.SIZEPOLICY_CAN_GROW) != 0);
			myChkVerWantGrow.setSelected((vSizePolicy & GridConstraints.SIZEPOLICY_WANT_GROW) != 0);
		}

		myIsContainerCheckBox.setSelected(itemToBeEdited.isContainer());
		myAutoCreateBindingCheckbox.setSelected(itemToBeEdited.isAutoCreateBinding());
		myCanAttachLabelCheckbox.setSelected(itemToBeEdited.isCanAttachLabel());

		myLblIcon.setLabelFor(myTfIconPath);
		myClassRadioButton.addChangeListener(new MyRadioChangeListener());
		myNestedFormRadioButton.addChangeListener(new MyRadioChangeListener());

		if(oneOff)
		{
			myLblIcon.setVisible(false);
			myTfIconPath.setVisible(false);
			myCanAttachLabelCheckbox.setVisible(false);
			myHSizePolicyPanel.setVisible(false);
			myVSizePolicyPanel.setVisible(false);
		}

		updateOKAction();

		init();
	}

	void showGroupChooser(GroupItem defaultGroup)
	{
		myGroupLabel.setVisible(true);
		myGroupComboBox.setVisible(true);
		List<GroupItem> groups = Palette.getInstance(myProject).getGroups();
		myGroupComboBox.setModel(new DefaultComboBoxModel<>(groups.toArray(GroupItem[]::new)));
		myGroupComboBox.setSelectedItem(defaultGroup);
		myGroupComboBox.addItemListener(e -> updateOKAction());
		updateOKAction();
	}

	GroupItem getSelectedGroup()
	{
		return (GroupItem) myGroupComboBox.getSelectedItem();
	}

	private void setEditorText(String className)
	{
		JavaCodeFragmentFactory factory = JavaCodeFragmentFactory.getInstance(myProject);
		PsiJavaPackage defaultPackage = JavaPsiFacade.getInstance(myProject).findPackage("");
		PsiCodeFragment fragment = factory.createReferenceCodeFragment(className, defaultPackage, true, true);
		myDocument = PsiDocumentManager.getInstance(myProject).getDocument(fragment);
		myEditorTextField.setDocument(myDocument);
		updateOKAction();
	}

	@Nonnull
    @Override
	protected Action[] createActions()
	{
		return new Action[]{
				getOKAction(),
				getCancelAction(),
				getHelpAction()
		};
	}

    @Override
    @RequiredUIAccess
    protected void doHelpAction()
	{
		HelpManager.getInstance().invokeHelp("reference.dialogs.addEditPaletteComponent");
	}

	@Override
    protected void doOKAction()
	{
		// TODO[vova] implement validation
		if(myClassRadioButton.isSelected())
		{
			String className = myDocument.getText().trim();
			PsiClass psiClass = JavaPsiFacade.getInstance(myProject).findClass(className, GlobalSearchScope.allScope(myProject));
			if(psiClass != null)
			{
				myItemToBeEdited.setClassName(getClassOrInnerName(psiClass));
			}
			else
			{
				myItemToBeEdited.setClassName(className);
			}
		}
		else if (!saveNestedForm())
		{
            return;
        }
		myItemToBeEdited.setIconPath(myTfIconPath.getText().trim());

		{
			GridConstraints defaultConstraints = myItemToBeEdited.getDefaultConstraints();
			// Horizontal size policy
			defaultConstraints.setHSizePolicy(
					(myChkHorCanShrink.isSelected() ? GridConstraints.SIZEPOLICY_CAN_SHRINK : 0) |
							(myChkHorCanGrow.isSelected() ? GridConstraints.SIZEPOLICY_CAN_GROW : 0) |
							(myChkHorWantGrow.isSelected() ? GridConstraints.SIZEPOLICY_WANT_GROW : 0)
			);

			// Vertical size policy
			defaultConstraints.setVSizePolicy(
					(myChkVerCanShrink.isSelected() ? GridConstraints.SIZEPOLICY_CAN_SHRINK : 0) |
							(myChkVerCanGrow.isSelected() ? GridConstraints.SIZEPOLICY_CAN_GROW : 0) |
							(myChkVerWantGrow.isSelected() ? GridConstraints.SIZEPOLICY_WANT_GROW : 0)
			);

			defaultConstraints.setFill(
					(myChkHorWantGrow.isSelected() ? GridConstraints.FILL_HORIZONTAL : 0) |
							(myChkVerWantGrow.isSelected() ? GridConstraints.FILL_VERTICAL : 0)
			);
		}

		myItemToBeEdited.setIsContainer(myIsContainerCheckBox.isSelected());
		myItemToBeEdited.setAutoCreateBinding(myAutoCreateBindingCheckbox.isSelected());
		myItemToBeEdited.setCanAttachLabel(myCanAttachLabelCheckbox.isSelected());

		super.doOKAction();
	}

	@RequiredUIAccess
    private boolean saveNestedForm()
	{
		VirtualFile formFile = ResourceFileUtil.findResourceFileInProject(myProject, myTfNestedForm.getText());
		if(formFile == null)
		{
            Messages.showErrorDialog(
			    getWindow(),
                UIDesignerLocalize.addComponentCannotLoadForm(myTfNestedForm.getText()).get(),
                CommonLocalize.titleError().get()
            );
			return false;
		}
		LwRootContainer lwRootContainer;
		try
		{
			lwRootContainer = Utils.getRootContainer(formFile.getInputStream(), null);
		}
		catch(Exception e)
		{
			Messages.showErrorDialog(getWindow(), e.getMessage(), CommonLocalize.titleError().get());
			return false;
		}
		if(lwRootContainer.getClassToBind() == null)
		{
			Messages.showErrorDialog(getWindow(), UIDesignerLocalize.addComponentFormNotBound().get(), CommonLocalize.titleError().get());
			return false;
		}
		if(lwRootContainer.getComponent(0).getBinding() == null)
		{
			Messages.showErrorDialog(getWindow(), UIDesignerLocalize.addComponentRootNotBound().get(), CommonLocalize.titleError().get());
			return false;
		}
		PsiClass psiClass =
				JavaPsiFacade.getInstance(myProject).findClass(lwRootContainer.getClassToBind(), GlobalSearchScope.projectScope(myProject));
		if(psiClass != null)
		{
			myItemToBeEdited.setClassName(getClassOrInnerName(psiClass));
		}
		else
		{
			myItemToBeEdited.setClassName(lwRootContainer.getClassToBind());
		}
		return true;
	}

	@Override
    protected String getDimensionServiceKey()
	{
		if(myOneOff)
		{
			return "#com.intellij.uiDesigner.palette.ComponentItemDialog.OneOff";
		}
		return "#com.intellij.uiDesigner.palette.ComponentItemDialog";
	}

	@Override
    public JComponent getPreferredFocusedComponent()
	{
		return myTfClassName.getChildComponent();
	}

	@Override
    protected JComponent createCenterPanel()
	{
		return myPanel;
	}

	private void updateEnabledTextField()
	{
		myEditorTextField.setEnabled(myClassRadioButton.isSelected());
		myTfNestedForm.setEnabled(myNestedFormRadioButton.isSelected());
		updateOKAction();
	}

	private void updateOKAction()
	{
		setOKActionEnabled(isOKEnabled());
	}

	private boolean isOKEnabled()
	{
		myErrorLabel.setText(" ");
		if(myClassRadioButton.isSelected())
		{
			if(myDocument == null)
			{  // why?
				return false;
			}
			JavaPsiFacade javaPsiFacade = JavaPsiFacade.getInstance(myProject);
			if(!javaPsiFacade.getNameHelper().isQualifiedName(myDocument.getText()))
			{
				if(myDocument.getTextLength() > 0)
				{
					myErrorLabel.setText(UIDesignerLocalize.addComponentErrorQualifiedNameRequired().get());
				}
				return false;
			}
			PsiClass psiClass = javaPsiFacade.findClass(myDocument.getText(), (GlobalSearchScope) ProjectScopes.getAllScope(myProject));
			PsiClass componentClass = javaPsiFacade.findClass(JComponent.class.getName(), (GlobalSearchScope) ProjectScopes.getAllScope(myProject));
			if(psiClass != null && componentClass != null && !InheritanceUtil.isInheritorOrSelf(psiClass, componentClass, true))
			{
				myErrorLabel.setText(UIDesignerLocalize.addComponentErrorComponentRequired().get());
				return false;
			}
		}
		else
		{
			if(myTfNestedForm.getText().length() == 0)
			{
				return false;
			}
		}
        return !myGroupComboBox.isVisible() || myGroupComboBox.getSelectedItem() != null;
    }

	private static String getClassOrInnerName(PsiClass aClass)
	{
		PsiClass parentClass = PsiTreeUtil.getParentOfType(aClass, PsiClass.class, true);
		if(parentClass != null)
		{
			return getClassOrInnerName(parentClass) + "$" + aClass.getName();
		}
		return aClass.getQualifiedName();
	}

	private class MyChooseClassActionListener implements ActionListener
	{
		private final Project myProject;

		public MyChooseClassActionListener(Project project)
		{
			myProject = project;
		}

        @Override
        @RequiredUIAccess
        public void actionPerformed(ActionEvent e)
		{
			TreeClassChooserFactory factory = TreeClassChooserFactory.getInstance(myProject);
			TreeClassChooser chooser = factory.createInheritanceClassChooser(
                UIDesignerLocalize.titleChooseComponentClass().get(),
				GlobalSearchScope.allScope(myProject),
                JavaPsiFacade.getInstance(myProject).findClass(JComponent.class.getName(), GlobalSearchScope.allScope(myProject)),
                true,
                true,
                null
            );
			chooser.showDialog();
			PsiClass result = chooser.getSelected();
			if(result != null)
			{
				setEditorText(result.getQualifiedName());
			}
		}
	}

	private static class MyChooseFileActionListener implements ActionListener
	{
		private final Project myProject;
		private final Predicate<PsiFile> myFilter;
		private final TextFieldWithBrowseButton myTextField;
		private final String myTitle;

		public MyChooseFileActionListener(Project project,
                                          Predicate<PsiFile> filter,
                                          TextFieldWithBrowseButton textField,
                                          String title)
		{
			myProject = project;
			myFilter = filter;
			myTextField = textField;
			myTitle = title;
		}

		@Override
        @RequiredReadAction
        public void actionPerformed(ActionEvent e)
		{
			TreeClassChooserFactory factory = TreeClassChooserFactory.getInstance(myProject);
			PsiFile formFile = null;
			if(myTextField.getText().length() > 0)
			{
				VirtualFile formVFile = ResourceFileUtil.findResourceFileInScope(myTextField.getText(), myProject, (GlobalSearchScope) ProjectScopes.getAllScope(myProject));
				if(formVFile != null)
				{
					formFile = PsiManager.getInstance(myProject).findFile(formVFile);
				}
			}
			TreeFileChooser fileChooser = factory.createFileChooser(myTitle, formFile, null, myFilter, true, true);
			fileChooser.showDialog();
			PsiFile file = fileChooser.getSelectedFile();
			if(file != null)
			{
				myTextField.setText(FormEditingUtil.buildResourceName(file));
			}
		}
	}

	private class MyRadioChangeListener implements ChangeListener
	{
		@Override
        public void stateChanged(ChangeEvent e)
		{
			updateEnabledTextField();
		}
	}
}
