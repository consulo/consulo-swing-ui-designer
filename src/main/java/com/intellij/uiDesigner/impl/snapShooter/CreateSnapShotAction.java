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

package com.intellij.uiDesigner.impl.snapShooter;

import com.intellij.java.analysis.impl.ide.highlighter.JavaHighlightingColors;
import com.intellij.java.execution.impl.application.ApplicationConfiguration;
import com.intellij.java.execution.impl.application.ApplicationConfigurationType;
import com.intellij.java.execution.impl.util.JreVersionDetector;
import com.intellij.java.language.psi.JavaDirectoryService;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.uiDesigner.impl.GuiFormFileType;
import com.intellij.uiDesigner.impl.designSurface.InsertComponentProcessor;
import com.intellij.uiDesigner.impl.palette.ComponentItem;
import com.intellij.uiDesigner.impl.palette.Palette;
import com.intellij.uiDesigner.impl.radComponents.LayoutManagerRegistry;
import com.intellij.uiDesigner.impl.radComponents.RadComponentFactory;
import com.intellij.uiDesigner.impl.radComponents.RadContainer;
import consulo.application.progress.ProgressManager;
import consulo.colorScheme.EditorColorsManager;
import consulo.colorScheme.EditorColorsScheme;
import consulo.colorScheme.TextAttributes;
import consulo.execution.RunManager;
import consulo.execution.RunnerAndConfigurationSettings;
import consulo.execution.executor.DefaultRunExecutor;
import consulo.execution.executor.Executor;
import consulo.execution.runner.ExecutionEnvironment;
import consulo.execution.runner.ProgramRunner;
import consulo.execution.runner.RunnerRegistry;
import consulo.language.editor.util.IdeView;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiFileFactory;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.module.content.ProjectFileIndex;
import consulo.module.content.ProjectRootManager;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.process.ExecutionException;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.SimpleTextAttributes;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.AnActionWithSyncUpdate;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.ui.ex.awt.Messages;
import consulo.ui.ex.awt.UIUtil;
import consulo.ui.ex.awt.event.DocumentAdapter;
import consulo.ui.ex.awt.tree.ColoredTreeCellRenderer;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import consulo.uiDesigner.impl.localize.UIDesignerLocalize;
import consulo.undoRedo.CommandProcessor;
import consulo.util.io.CharsetToolkit;
import consulo.util.lang.ref.SimpleReference;
import jakarta.annotation.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * @author yole
 */
public class CreateSnapShotAction extends AnAction implements AnActionWithSyncUpdate {
    private static final Logger LOG = Logger.getInstance(CreateSnapShotAction.class);

    @Override
    public void update(AnActionEvent e) {
        Project project = e.getData(Project.KEY);
        IdeView view = e.getData(IdeView.KEY);
        e.getPresentation().setVisible(project != null && view != null && hasDirectoryInPackage(project, view));
    }

    private static boolean hasDirectoryInPackage(Project project, IdeView view) {
        ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
        PsiDirectory[] dirs = view.getDirectories();
        for (PsiDirectory dir : dirs) {
            if (projectFileIndex.isInSourceContent(dir.getVirtualFile()) && JavaDirectoryService.getInstance().getPackage(dir) != null) {
                return true;
            }
        }
        return false;
    }

    @Override
    @RequiredUIAccess
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getData(Project.KEY);
        IdeView view = e.getData(IdeView.KEY);
        if (project == null || view == null) {
            return;
        }

        PsiDirectory dir = view.getOrChooseDirectory();
        if (dir == null) {
            return;
        }

        SnapShotClient client = new SnapShotClient();
        List<RunnerAndConfigurationSettings> appConfigurations = new ArrayList<>();
        RunnerAndConfigurationSettings snapshotConfiguration = null;
        boolean connected = false;

        ApplicationConfigurationType cfgType = ApplicationConfigurationType.getInstance();
        List<RunnerAndConfigurationSettings> racsi = RunManager.getInstance(project).getConfigurationSettingsList(cfgType);

        for (RunnerAndConfigurationSettings config : racsi) {
            if (config.getConfiguration() instanceof ApplicationConfiguration) {
                ApplicationConfiguration appConfig = (ApplicationConfiguration) config.getConfiguration();
                appConfigurations.add(config);
                if (appConfig.ENABLE_SWING_INSPECTOR) {
                    SnapShooterConfigurationSettings settings = SnapShooterConfigurationSettings.get(appConfig);
                    snapshotConfiguration = config;
                    if (settings.getLastPort() > 0) {
                        try {
                            client.connect(settings.getLastPort());
                            connected = true;
                        }
                        catch (IOException ex) {
                            connected = false;
                        }
                    }
                }
                if (connected) {
                    break;
                }
            }
        }

        if (snapshotConfiguration == null) {
            snapshotConfiguration = promptForSnapshotConfiguration(project, appConfigurations);
            if (snapshotConfiguration == null) {
                return;
            }
        }

        if (!connected) {
            int rc = Messages.showYesNoDialog(
                project,
                UIDesignerLocalize.snapshotRunPrompt().get(),
                UIDesignerLocalize.snapshotTitle().get(),
                UIUtil.getQuestionIcon()
            );
            if (rc == 1) {
                return;
            }
            ApplicationConfiguration appConfig = (ApplicationConfiguration) snapshotConfiguration.getConfiguration();
            SnapShooterConfigurationSettings settings = SnapShooterConfigurationSettings.get(appConfig);
            settings.setNotifyRunnable(() -> SwingUtilities.invokeLater(() -> {
                Messages.showMessageDialog(
                    project,
                    UIDesignerLocalize.snapshotPrepareNotice().get(),
                    UIDesignerLocalize.snapshotTitle().get(),
                    UIUtil.getInformationIcon()
                );
                try {
                    client.connect(settings.getLastPort());
                }
                catch (IOException ex) {
                    Messages.showMessageDialog(
                        project,
                        UIDesignerLocalize.snapshotConnectionError().get(),
                        UIDesignerLocalize.snapshotTitle().get(),
                        UIUtil.getErrorIcon()
                    );
                    return;
                }
                runSnapShooterSession(client, project, dir, view);
            }));

            try {
                ProgramRunner runner = RunnerRegistry.getInstance().getRunner(DefaultRunExecutor.EXECUTOR_ID, appConfig);
                LOG.assertTrue(runner != null, "Runner MUST not be null!");
                Executor executor = DefaultRunExecutor.getRunExecutorInstance();
                runner.execute(new ExecutionEnvironment(executor, runner, snapshotConfiguration, project));
            }
            catch (ExecutionException ex) {
                Messages.showMessageDialog(
                    project,
                    UIDesignerLocalize.snapshotRunError(ex.getMessage()).get(),
                    UIDesignerLocalize.snapshotTitle().get(),
                    UIUtil.getErrorIcon()
                );
            }
        }
        else {
            runSnapShooterSession(client, project, dir, view);
        }
    }

    @RequiredUIAccess
    private static void runSnapShooterSession(SnapShotClient client, Project project, PsiDirectory dir, IdeView view) {
        try {
            client.suspendSwing();
        }
        catch (IOException e1) {
            Messages.showMessageDialog(
                project,
                UIDesignerLocalize.snapshotConnectionError().get(),
                UIDesignerLocalize.snapshotTitle().get(),
                UIUtil.getInformationIcon()
            );
            return;
        }

        MyDialog dlg = new MyDialog(project, client, dir);
        dlg.show();
        if (dlg.getExitCode() == DialogWrapper.OK_EXIT_CODE) {
            int id = dlg.getSelectedComponentId();
            SimpleReference<Object> result = new SimpleReference<>();
            ProgressManager.getInstance().runProcessWithProgressSynchronously(
                () -> {
                    try {
                        result.set(client.createSnapshot(id));
                    }
                    catch (Exception ex) {
                        result.set(ex);
                    }
                },
                UIDesignerLocalize.progressCreatingSnapshot(),
                false,
                project
            );

            String snapshot = null;
            if (result.get() instanceof String) {
                snapshot = (String) result.get();
            }
            else {
                Exception ex = (Exception) result.get();
                Messages.showMessageDialog(
                    project,
                    UIDesignerLocalize.snapshotCreateError(ex.getMessage()).get(),
                    UIDesignerLocalize.snapshotTitle().get(),
                    UIUtil.getErrorIcon()
                );
            }

            if (snapshot != null) {
                String snapshot1 = snapshot;
                CommandProcessor.getInstance().newCommand()
                    .project(project)
                    .inWriteAction()
                    .run(() -> {
                        try {
                            PsiFile formFile = PsiFileFactory.getInstance(dir.getProject())
                                .createFileFromText(dlg.getFormName() + GuiFormFileType.DOT_DEFAULT_EXTENSION, snapshot1);
                            formFile = (PsiFile) dir.add(formFile);
                            formFile.getVirtualFile().setCharset(CharsetToolkit.UTF8_CHARSET);
                            formFile.getViewProvider().getDocument().setText(snapshot1);
                            view.selectElement(formFile);
                        }
                        catch (IncorrectOperationException ex) {
                            Messages.showMessageDialog(
                                project,
                                UIDesignerLocalize.snapshotSaveError(ex.getMessage()).get(),
                                UIDesignerLocalize.snapshotTitle().get(),
                                UIUtil.getErrorIcon()
                            );
                        }
                    });
            }
        }

        try {
            client.resumeSwing();
        }
        catch (IOException ex) {
            Messages.showErrorDialog(
                project,
                UIDesignerLocalize.snapshotConnectionBroken().get(),
                UIDesignerLocalize.snapshotTitle().get()
            );
        }

        client.dispose();
    }

    @Nullable
    @RequiredUIAccess
    private static RunnerAndConfigurationSettings promptForSnapshotConfiguration(Project project,
                                                                                 List<RunnerAndConfigurationSettings> configurations) {
        if (configurations.isEmpty()) {
            Messages.showMessageDialog(
                project,
                UIDesignerLocalize.snapshotNoConfigurationError().get(),
                UIDesignerLocalize.snapshotTitle().get(),
                UIUtil.getInformationIcon()
            );
            return null;
        }

        for (int i = configurations.size() - 1; i >= 0; i--) {
            JreVersionDetector detector = new JreVersionDetector();
            ApplicationConfiguration configuration = (ApplicationConfiguration) configurations.get(i).getConfiguration();
            if (!detector.isJre50Configured(configuration) && !detector.isModuleJre50Configured(configuration)) {
                configurations.remove(i);
            }
        }

        if (configurations.isEmpty()) {
            Messages.showMessageDialog(
                project,
                UIDesignerLocalize.snapshotNoCompatibleConfigurationError().get(),
                UIDesignerLocalize.snapshotTitle().get(),
                UIUtil.getInformationIcon()
            );
            return null;
        }

        RunnerAndConfigurationSettings snapshotConfiguration;
        if (configurations.size() == 1) {
            int rc = Messages.showYesNoDialog(
                project,
                UIDesignerLocalize.snapshotConfirmConfigurationPrompt(configurations.get(0).getConfiguration().getName()).get(),
                UIDesignerLocalize.snapshotTitle().get(),
                UIUtil.getQuestionIcon()
            );
            if (rc == 1) {
                return null;
            }
            snapshotConfiguration = configurations.get(0);
        }
        else {
            String[] names = new String[configurations.size()];
            for (int i = 0; i < configurations.size(); i++) {
                names[i] = configurations.get(i).getConfiguration().getName();
            }
            int rc = Messages.showChooseDialog(
                project,
                UIDesignerLocalize.snapshotChooseConfigurationPrompt().get(),
                UIDesignerLocalize.snapshotTitle().get(),
                UIUtil.getQuestionIcon(),
                names,
                names[0]
            );
            if (rc < 0) {
                return null;
            }
            snapshotConfiguration = configurations.get(rc);
        }
        ((ApplicationConfiguration) snapshotConfiguration.getConfiguration()).ENABLE_SWING_INSPECTOR = true;
        return snapshotConfiguration;
    }

    private static class MyDialog extends DialogWrapper {
        private JPanel myRootPanel;
        private JTree myComponentTree;
        private JTextField myFormNameTextField;
        private JLabel myErrorLabel;
        private final Project myProject;
        private final SnapShotClient myClient;
        private final PsiDirectory myDirectory;
        private static final String SWING_PACKAGE = "javax.swing.";

        private MyDialog(Project project, SnapShotClient client, PsiDirectory dir) {
            super(project, true);
            myProject = project;
            myClient = client;
            myDirectory = dir;
            init();
            setTitle(UIDesignerLocalize.snapshotTitle());
            setOKButtonText(UIDesignerLocalize.createSnapshotButton());
            SnapShotTreeModel model = new SnapShotTreeModel(client);
            myComponentTree.setModel(model);
            myComponentTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
            myComponentTree.getSelectionModel().addTreeSelectionListener(e -> updateOKAction());
            for (int i = 0; i < 2; i++) {
                for (int row = myComponentTree.getRowCount() - 1; row >= 0; row--) {
                    myComponentTree.expandRow(row);
                }
            }
            myComponentTree.getSelectionModel().setSelectionPath(myComponentTree.getPathForRow(0));
            myFormNameTextField.setText(suggestFormName());

            EditorColorsScheme globalScheme = EditorColorsManager.getInstance().getGlobalScheme();
            TextAttributes attributes = globalScheme.getAttributes(JavaHighlightingColors.STRING);
            final SimpleTextAttributes titleAttributes =
                new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, TargetAWT.to(attributes.getForegroundColor()));

            myComponentTree.setCellRenderer(new ColoredTreeCellRenderer() {
                @Override
                public void customizeCellRenderer(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
                    SnapShotRemoteComponent rc = (SnapShotRemoteComponent) value;

                    String className = rc.getClassName();
                    if (className.startsWith(SWING_PACKAGE)) {
                        append(className.substring(SWING_PACKAGE.length()), SimpleTextAttributes.REGULAR_ATTRIBUTES);
                    }
                    else {
                        append(className, SimpleTextAttributes.REGULAR_ATTRIBUTES);
                    }

                    if (rc.getText().length() > 0) {
                        append(" \"" + rc.getText() + "\"", titleAttributes);
                    }
                    if (rc.getLayoutManager().length() > 0) {
                        append(" (" + rc.getLayoutManager() + ")", SimpleTextAttributes.GRAY_ATTRIBUTES);
                    }

                    if (rc.isTopLevel()) {
                        setIcon(PlatformIconGroup.filetypesUiform());
                    }
                    else {
                        Palette palette = Palette.getInstance(myProject);
                        ComponentItem item = palette.getItem(rc.getClassName());
                        if (item != null) {
                            setIcon(item.getSmallIcon());
                        }
                        else {
                            setIcon(PlatformIconGroup.actionsHelp());
                        }
                    }
                }
            });
            myFormNameTextField.getDocument().addDocumentListener(new DocumentAdapter() {
                @Override
                protected void textChanged(DocumentEvent e) {
                    updateOKAction();
                }
            });
            updateOKAction();
        }

        private String suggestFormName() {
            int count = 0;
            do {
                count++;
            }
            while (myDirectory.findFile("Form" + count + GuiFormFileType.DOT_DEFAULT_EXTENSION) != null);
            return "Form" + count;
        }

        private void updateOKAction() {
            boolean selectedComponentValid = isSelectedComponentValid();
            setOKActionEnabled(isFormNameValid() && selectedComponentValid);
            if (myComponentTree.getSelectionPath() != null && !selectedComponentValid) {
                myErrorLabel.setText(UIDesignerLocalize.snapshooterInvalidContainer().get());
            }
            else {
                myErrorLabel.setText(" ");
            }
        }

        private boolean isSelectedComponentValid() {
            TreePath selectionPath = myComponentTree.getSelectionPath();
            if (selectionPath == null) {
                return false;
            }
            SnapShotRemoteComponent rc = (SnapShotRemoteComponent) selectionPath.getLastPathComponent();
            if (isValidComponent(rc)) {
                return true;
            }
            if (selectionPath.getPathCount() == 2) {
                // capture frame/dialog root pane when a frame or dialog itself is selected
                SnapShotRemoteComponent[] children = rc.getChildren();
                return children != null && children.length > 0 && isValidComponent(children[0]);
            }
            return false;
        }

        private boolean isValidComponent(SnapShotRemoteComponent rc) {
            PsiClass componentClass =
                JavaPsiFacade.getInstance(myProject).findClass(rc.getClassName().replace('$', '.'), GlobalSearchScope.allScope(myProject));
            while (componentClass != null) {
                if (JPanel.class.getName().equals(componentClass.getQualifiedName()) ||
                    JTabbedPane.class.getName().equals(componentClass.getQualifiedName()) ||
                    JScrollPane.class.getName().equals(componentClass.getQualifiedName()) ||
                    JSplitPane.class.getName().equals(componentClass.getQualifiedName())) {
                    return true;
                }
                componentClass = componentClass.getSuperClass();
            }

            return false;
        }

        private boolean isFormNameValid() {
            return myFormNameTextField.getText().length() > 0;
        }

        @Override
        protected String getDimensionServiceKey() {
            return "CreateSnapShotAction.MyDialog";
        }

        @Override
        @RequiredUIAccess
        public JComponent getPreferredFocusedComponent() {
            return myFormNameTextField;
        }

        @Override
        @RequiredUIAccess
        protected void doOKAction() {
            if (getOKAction().isEnabled()) {
                try {
                    myDirectory.checkCreateFile(getFormName() + GuiFormFileType.DOT_DEFAULT_EXTENSION);
                }
                catch (IncorrectOperationException e) {
                    JOptionPane.showMessageDialog(myRootPanel, UIDesignerLocalize.errorFormAlreadyExists(getFormName()).get());
                    return;
                }
                if (!checkUnknownLayoutManagers(myDirectory.getProject())) {
                    return;
                }
                close(OK_EXIT_CODE);
            }
        }

        @RequiredUIAccess
        private boolean checkUnknownLayoutManagers(Project project) {
            Set<String> layoutManagerClasses = new TreeSet<>();
            SnapShotRemoteComponent rc = (SnapShotRemoteComponent) myComponentTree.getSelectionPath().getLastPathComponent();
            assert rc != null;
            SimpleReference<Exception> err = new SimpleReference<>();
            Runnable runnable = () -> {
                try {
                    collectUnknownLayoutManagerClasses(project, rc, layoutManagerClasses);
                }
                catch (IOException e) {
                    err.set(e);
                }
            };
            if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(
                runnable,
                UIDesignerLocalize.progressValidatingLayoutManagers(),
                false,
                project
            )) {
                return false;
            }
            if (!err.isNull()) {
                Messages.showErrorDialog(
                    myRootPanel,
                    UIDesignerLocalize.snapshotConnectionBroken().get(),
                    UIDesignerLocalize.snapshotTitle().get()
                );
                return false;
            }
            if (!layoutManagerClasses.isEmpty()) {
                StringBuilder builder = new StringBuilder(UIDesignerLocalize.snapshotUnknownLayoutPrefix().get());
                for (String layoutManagerClass : layoutManagerClasses) {
                    builder.append(layoutManagerClass).append("\n");
                }
                builder.append(UIDesignerLocalize.snapshotUnknownLayoutPrompt().get());
                return Messages.showYesNoDialog(
                    myProject,
                    builder.toString(),
                    UIDesignerLocalize.snapshotTitle().get(),
                    UIUtil.getQuestionIcon()
                ) == 0;
            }
            return true;
        }

        private void collectUnknownLayoutManagerClasses(Project project, SnapShotRemoteComponent rc,
                                                        Set<String> layoutManagerClasses) throws IOException {
            RadComponentFactory factory = InsertComponentProcessor.getRadComponentFactory(project, rc.getClassName());
            if (factory instanceof RadContainer.Factory && rc.getLayoutManager().length() > 0 &&
                !LayoutManagerRegistry.isKnownLayoutClass(rc.getLayoutManager())) {
                layoutManagerClasses.add(rc.getLayoutManager());
            }

            SnapShotRemoteComponent[] children = rc.getChildren();
            if (children == null) {
                children = myClient.listChildren(rc.getId());
                rc.setChildren(children);
            }
            for (SnapShotRemoteComponent child : children) {
                collectUnknownLayoutManagerClasses(project, child, layoutManagerClasses);
            }
        }

        @Nullable
        @Override
        protected JComponent createCenterPanel() {
            return myRootPanel;
        }

        public int getSelectedComponentId() {
            TreePath selectionPath = myComponentTree.getSelectionPath();
            SnapShotRemoteComponent rc = (SnapShotRemoteComponent) selectionPath.getLastPathComponent();
            if (!isValidComponent(rc) && selectionPath.getPathCount() == 2) {
                // capture frame/dialog root pane when a frame or dialog itself is selected
                SnapShotRemoteComponent[] children = rc.getChildren();
                if (children != null && children.length > 0 && isValidComponent(children[0])) {
                    return children[0].getId();
                }
            }
            return rc.getId();
        }

        public String getFormName() {
            return myFormNameTextField.getText();
        }
    }
}
