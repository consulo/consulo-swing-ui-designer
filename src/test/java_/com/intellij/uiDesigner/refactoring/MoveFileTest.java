package com.intellij.uiDesigner.refactoring;

import consulo.document.FileDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.virtualFileSystem.VirtualFile;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiFile;
import com.intellij.refactoring.MultiFileTestCase;
import consulo.language.editor.refactoring.move.fileOrDirectory.MoveFilesOrDirectoriesProcessor;

public abstract class MoveFileTest extends MultiFileTestCase {
  @Override
  protected String getTestDataPath() {
    return "/testData";
  }

  @Override
  protected String getTestRoot() {
    return "/move/";
  }

  public void testMoveIcon() throws Exception {
    doTest("to", "from/addmodulewizard.png");
  }

  //Both names are relative to root directory
  private void doTest(final String targetDirName, final String fileToMove) throws Exception {
    doTest(new PerformAction() {
      @Override
      public void performAction(VirtualFile rootDir, VirtualFile rootAfter) throws Exception {
        VirtualFile child = rootDir.findFileByRelativePath(fileToMove);
        assertNotNull("File " + fileToMove + " not found", child);
        PsiFile file = myPsiManager.findFile(child);

        VirtualFile child1 = rootDir.findChild(targetDirName);
        assertNotNull("File " + targetDirName + " not found", child1);
        PsiDirectory targetDirectory = myPsiManager.findDirectory(child1);

        new MoveFilesOrDirectoriesProcessor(myProject, new PsiElement[] {file}, targetDirectory,
                                            false, false, null, null).run();
        /*assert targetDirectory != null;
        final PsiFile psiFile = targetDirectory.findFile(fileToMove);
        assert psiFile != null;
        final Document document = PsiDocumentManager.getInstance(myProject).getDocument(psiFile);
        assert document != null;
        PsiDocumentManager.getInstance(myProject).doPostponedOperationsAndUnblockDocument(document);*/
        FileDocumentManager.getInstance().saveAllDocuments();
      }
    });
  }
}
