package de.unipaderborn.visuflow.wizard;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.ui.INewWizard;
import org.eclipse.ui.IWorkbench;

import de.unipaderborn.visuflow.ProjectPreferences;
import de.unipaderborn.visuflow.builder.AddRemoveVisuFlowNatureHandler;
import de.unipaderborn.visuflow.builder.GlobalSettings;
import de.unipaderborn.visuflow.model.DataModel;
import de.unipaderborn.visuflow.util.ServiceUtil;

/**
 * @author Zafar Habeeb
 *
 */
public class WizardHandler extends Wizard implements INewWizard {

	private WizardPageHandler page;
	private WizardHandlerPageTwo pageTwo;
	private ISelection selection;
	private WizardInput wizardInput;

	public WizardHandler() {
		super();
		setNeedsProgressMonitor(true);
	}

	@Override
	public void addPages() {
		page = new WizardPageHandler(selection);
		addPage(page);
		pageTwo = new WizardHandlerPageTwo(selection);
		addPage(pageTwo);
	}

	@Override
	public boolean canFinish() {
		if(getContainer().getCurrentPage() == page)
			return false;
		else
			return true;
	}

	/**
	 * This method is used to handle post processing after the user clicks on finish button in the wizard.
	 **/
	@Override
	public boolean performFinish() {
		HashMap<String, String> pageOneValues = page.getContainerName();
		HashMap<String, String> pageTwoValues = pageTwo.getContainerName();
		final String targetProjectPath = pageOneValues.get("TargetPath");
		final String analysisProjectName = pageOneValues.get("ProjectName");
		wizardInput = new WizardInput();
		wizardInput.setTargetPath(targetProjectPath);
		wizardInput.setProjectName(analysisProjectName);
		wizardInput.setPackageName(pageOneValues.get("PackageName"));
		wizardInput.setClassName(pageOneValues.get("ClassName"));
		wizardInput.setAnalysisType(pageTwoValues.get("AnalysisType"));
		wizardInput.setAnalysisFramework(pageTwoValues.get("AnalysisFramework"));
		wizardInput.setAnalysisDirection(pageTwoValues.get("AnalysisDirection"));
		wizardInput.setFlowType(pageTwoValues.get("FlowSet"));
		wizardInput.setFlowType1(pageTwoValues.get("Type1"));
		wizardInput.setFlowtype2(pageTwoValues.get("Type2"));
		wizardInput.setCustomClassFirst(pageTwoValues.get("CustomType1"));
		wizardInput.setCustomClassSecond(pageTwoValues.get("CustomType2"));
		String sootLocation = pageTwoValues.get("sootLocation");
		Path sootPath = new Path(sootLocation);
		wizardInput.setSootPath(sootPath);
		IRunnableWithProgress op = new IRunnableWithProgress() {
			@Override
			public void run(IProgressMonitor monitor) throws InvocationTargetException {
				try {
					doFinish(targetProjectPath, analysisProjectName, monitor);
				} catch (CoreException e) {
					throw new InvocationTargetException(e);
				} catch (FileNotFoundException e) {

					e.printStackTrace();
				} catch (IOException e) {

					e.printStackTrace();
				} finally {
					monitor.done();
				}
			}
		};
		try {
			getContainer().run(true, false, op);
		} catch (InterruptedException e) {
			return false;
		} catch (InvocationTargetException e) {
			Throwable realException = e.getTargetException();
			MessageDialog.openError(getShell(), "Error", realException.getMessage());
			return false;
		}
		return true;
	}

	private void doFinish(
			String targetProjectPath,
			String analysisProjectName,
			IProgressMonitor monitor)
					throws CoreException, IOException {
		monitor.beginTask("Creating " +analysisProjectName, 2);
		IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
		ProjectGenerator projectGen = new ProjectGenerator();
		IJavaProject sourceProject = projectGen.createProject(wizardInput);
		IResource resourceTarget = root.findMember(new Path(targetProjectPath));
		IJavaProject targetProject = JavaCore.create(resourceTarget.getProject());
		GlobalSettings.put("Target_Path",resourceTarget.getLocation().toOSString()+ File.separator +  targetProject.getOutputLocation().lastSegment());
		GlobalSettings.put("AnalysisProject", sourceProject.getProject().getName());
		GlobalSettings.put("TargetProject", targetProject.getProject().getName());
		ProjectPreferences projPref = new ProjectPreferences();
		projPref.createPreferences();
		AddRemoveVisuFlowNatureHandler addNature = new AddRemoveVisuFlowNatureHandler();
		try {
			if(!sourceProject.getProject().isNatureEnabled("JimpleBuilder.VisuFlowNature"))
				addNature.toggleNature(sourceProject.getProject());
		} catch (CoreException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		ServiceUtil.getService(DataModel.class).triggerProjectRebuild();
		monitor.worked(1);
		monitor.setTaskName("Opening file for editing...");
		getShell().getDisplay().asyncExec(new Runnable() {
			@Override
			public void run() {

			}
		});
		monitor.worked(1);
	}

	@Override
	public void init(IWorkbench workbench, IStructuredSelection selection) {
		this.selection = selection;
	}

}
