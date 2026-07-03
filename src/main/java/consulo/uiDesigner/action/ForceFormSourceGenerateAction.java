package consulo.uiDesigner.action;

import com.intellij.uiDesigner.impl.GuiFormFileType;
import com.intellij.uiDesigner.impl.make.FormSourceCodeGenerator;
import consulo.annotation.component.ActionImpl;
import consulo.annotation.component.ActionParentRef;
import consulo.annotation.component.ActionRef;
import consulo.application.Application;
import consulo.application.dumb.DumbAware;
import consulo.document.FileDocumentManager;
import consulo.language.psi.PsiDocumentManager;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.AnActionWithSyncUpdate;
import consulo.undoRedo.CommandProcessor;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;

/**
 * @author VISTALL
 * @since 2024-12-11
 */
@ActionImpl(id = "ForceFormSourceGenerateAction", parents = @ActionParentRef(@ActionRef(id = "ProjectViewPopupMenu")))
public class ForceFormSourceGenerateAction extends AnAction implements DumbAware, AnActionWithSyncUpdate {
    public ForceFormSourceGenerateAction() {
        super("Run Form Source Generator");
    }

    @RequiredUIAccess
    @Override
    public void actionPerformed(@Nonnull AnActionEvent e) {
        VirtualFile file = e.getData(VirtualFile.KEY);
        if (file == null) {
            return;
        }
        Project project = e.getRequiredData(Project.KEY);

        final FormSourceCodeGenerator generator = new FormSourceCodeGenerator(project);

        Application application = project.getApplication();
        application.invokeLater(() -> {
            CommandProcessor.getInstance().executeCommand(project, () -> application.runWriteAction(() -> {
                PsiDocumentManager.getInstance(project).commitAllDocuments();
                generator.generate(file);
            }), "", null);
            FileDocumentManager.getInstance().saveAllDocuments();
        }, application.getNoneModalityState());
    }

    @Override
    public void update(@Nonnull AnActionEvent e) {
        VirtualFile file = e.getData(VirtualFile.KEY);
        e.getPresentation().setEnabledAndVisible(file != null && file.getFileType() == GuiFormFileType.INSTANCE);
    }
}
