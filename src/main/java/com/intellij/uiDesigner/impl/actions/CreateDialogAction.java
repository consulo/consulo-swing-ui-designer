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
package com.intellij.uiDesigner.impl.actions;

import com.intellij.java.language.psi.JavaDirectoryService;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiJavaFile;
import com.intellij.java.language.psi.PsiJavaPackage;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.uiDesigner.impl.GuiDesignerConfiguration;
import com.intellij.uiDesigner.impl.UIDesignerBundle;
import consulo.application.AllIcons;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiFileFactory;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.uiDesigner.impl.localize.UIDesignerLocalize;
import jakarta.annotation.Nonnull;

import javax.swing.*;
import java.util.function.Consumer;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class CreateDialogAction extends AbstractCreateFormAction {
    private boolean myRecentGenerateOK;
    private boolean myRecentGenerateCancel;
    private boolean myRecentGenerateMain;

    public CreateDialogAction() {
        super(UIDesignerLocalize.actionCreateDialog(), UIDesignerLocalize.actionDescriptionCreateDialog(), AllIcons.FileTypes.UiForm);
    }

    @Override
    protected void invokeDialog(Project project, PsiDirectory directory, @Nonnull Consumer<PsiElement[]> consumer) {
        final MyInputValidator validator = new MyInputValidator(project, directory);

        final MyContentPane contentPane = new MyContentPane();

        final DialogWrapper dialog = new DialogWrapper(project, true) {
            {
                init();
                setTitle(UIDesignerBundle.message("title.new.dialog"));
            }

            @Override
            protected JComponent createCenterPanel() {
                return contentPane.getPanel();
            }

            @Override
            protected void doOKAction() {
                myRecentGenerateOK = contentPane.myChkGenerateOK.isSelected();
                myRecentGenerateCancel = contentPane.myChkGenerateCancel.isSelected();
                myRecentGenerateMain = contentPane.myChkGenerateMain.isSelected();

                final String inputString = contentPane.myTfClassName.getText().trim();
                if (
                    validator.checkInput(inputString) &&
                        validator.canClose(inputString)
                ) {
                    close(OK_EXIT_CODE);
                }
            }

            @Override
            @RequiredUIAccess
            public JComponent getPreferredFocusedComponent() {
                return contentPane.myTfClassName;
            }
        };

        dialog.showAsync().doWhenDone(() -> consumer.accept(validator.getCreatedElements()));
    }

    @Override
    protected LocalizeValue getCommandName() {
        return UIDesignerLocalize.commandCreateDialog();
    }

    @Override
    protected LocalizeValue getErrorTitle() {
        return UIDesignerLocalize.errorCannotCreateDialog();
    }

    private static String createClassBody(
        final String className,
        final boolean generateOK,
        final boolean generateCancel,
        final boolean generateMain
    ) {
        StringBuilder result = new StringBuilder(1024);

        result.append("public class ").append(className).append(" extends javax.swing.JDialog {\n");
        result.append("private javax.swing.JPanel contentPane;\n");
        result.append("private javax.swing.JButton buttonOK;\n");
        result.append("private javax.swing.JButton buttonCancel;\n");
        result.append("\n");
        result.append("public ").append(className).append("(){\n");
        result.append("setContentPane(contentPane);\n");
        result.append("setModal(true);\n");
        result.append("getRootPane().setDefaultButton(buttonOK);\n");

        if (generateOK) {
            result.append("\n");
            result.append("buttonOK.addActionListener(");
            result.append("new java.awt.event.ActionListener(){");
            result.append("public void actionPerformed(java.awt.event.ActionEvent e){");
            result.append("onOK();");
            result.append("}});\n");
        }

        if (generateCancel) {
            result.append("\n");
            result.append("buttonCancel.addActionListener(");
            result.append("new java.awt.event.ActionListener(){");
            result.append("public void actionPerformed(java.awt.event.ActionEvent e){");
            result.append("onCancel();");
            result.append("}});\n");
            result.append("\n");
            result.append(UIDesignerBundle.message("comment.call.onCancel.cross")).append("\n");
            result.append("setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);\n");
            result.append("addWindowListener(new java.awt.event.WindowAdapter() {\n");
            result.append("  public void windowClosing(java.awt.event.WindowEvent e) {\n");
            result.append("   onCancel();\n");
            result.append("  }\n");
            result.append("});\n");
            result.append("\n");
            result.append(UIDesignerBundle.message("comment.call.onCancel.escape")).append("\n");
            result.append("contentPane.registerKeyboardAction(");
            result.append("  new java.awt.event.ActionListener() {");
            result.append("    public void actionPerformed(java.awt.event.ActionEvent e) {");
            result.append("      onCancel();\n");
            result.append("    }");
            result.append("  },");
            result.append("  javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0),");
            result.append("  javax.swing.JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT");
            result.append(");");
        }

        result.append("}\n");

        if (generateOK) {
            result.append("\n");
            result.append("private void onOK(){\n");
            result.append(UIDesignerBundle.message("comment.onok")).append("\n");
            result.append("dispose();\n");
            result.append("}\n");
        }

        if (generateCancel) {
            result.append("\n");
            result.append("private void onCancel(){\n");
            result.append(UIDesignerBundle.message("comment.oncancel")).append("\n");
            result.append("dispose();\n");
            result.append("}\n");
        }

        if (generateMain) {
            result.append("\n");
            result.append("public static void main(String[] args){\n");
            result.append(className).append(" dialog = new ").append(className).append("();\n");
            result.append("dialog.pack();\n");
            result.append("dialog.setVisible(true);\n");
            result.append("System.exit(0);\n");
            result.append("}\n");
        }

        result.append("}\n");

        return result.toString();
    }


    @Override
    @RequiredUIAccess
    @Nonnull
    protected PsiElement[] create(final String newName, final PsiDirectory directory) throws IncorrectOperationException {
        PsiFile sourceFile = PsiFileFactory.getInstance(directory.getProject())
            .createFileFromText(newName + ".java", createClassBody(newName, myRecentGenerateOK, myRecentGenerateCancel, myRecentGenerateMain));
        sourceFile = (PsiFile) directory.add(sourceFile);

        JavaCodeStyleManager.getInstance(directory.getProject()).shortenClassReferences(sourceFile);
        CodeStyleManager.getInstance(directory.getProject()).reformat(sourceFile);

        final PsiJavaPackage aPackage = JavaDirectoryService.getInstance().getPackage(directory);
        final String packageName = aPackage.getQualifiedName();
        final String fqClassName = packageName.length() == 0 ? newName : packageName + "." + newName;

        final String formBody = createFormBody(fqClassName, "/com/intellij/uiDesigner/impl/NewDialog.xml",
            GuiDesignerConfiguration.getInstance(directory.getProject()).DEFAULT_LAYOUT_MANAGER);
        final PsiFile formFile = PsiFileFactory.getInstance(directory.getProject()).createFileFromText(newName + ".form", formBody);
        PsiElement createdFile = directory.add(formFile);

        PsiClass[] classes = ((PsiJavaFile) sourceFile).getClasses();
        return new PsiElement[]{
            createdFile,
            classes[0]
        };
    }

    private static final class MyContentPane {
        private JPanel myPanel;
        private JCheckBox myChkGenerateCancel;
        private JCheckBox myChkGenerateOK;
        private JCheckBox myChkGenerateMain;
        private JTextField myTfClassName;

        public MyContentPane() {
        }

        public JPanel getPanel() {
            return myPanel;
        }
    }
}
