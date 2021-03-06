package de.unipaderborn.visuflow.builder;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

import de.unipaderborn.visuflow.Logger;
import de.unipaderborn.visuflow.Visuflow;
import de.unipaderborn.visuflow.model.DataModel;
import de.unipaderborn.visuflow.model.VFClass;
import de.unipaderborn.visuflow.model.graph.ICFGStructure;
import de.unipaderborn.visuflow.model.graph.JimpleModelAnalysis;
import de.unipaderborn.visuflow.util.ServiceUtil;

/**
 * @author PAH-Laptop This is the Builder Class for our Project. It triggers on the automatic builds of eclipse and checks which files have changed. It only
 *         triggers a build on Project with the Visuflow Nature that have issued a fullbuild or contain changed java files. Then it gathers all the necessary
 *         information on the target code and runs a soot analysis to compute the structure and jimple necessary for the other views.
 */
public class JimpleBuilder extends IncrementalProjectBuilder {

	private Logger logger = Visuflow.getDefault().getLogger();
	private String classpath;
	private boolean executeBuild;

	class JimpleDeltaVisitor implements IResourceDeltaVisitor {
		public boolean visit(IResourceDelta delta) throws CoreException {
			IResource resource = delta.getResource();
			if (resource.getFileExtension() != null) {
				switch (delta.getKind()) {
				case IResourceDelta.ADDED:
					if (resource.getFileExtension().equals("java")) {
						executeBuild = true;
						return false;
					}
					break;
				case IResourceDelta.REMOVED:
					if (resource.getFileExtension().equals("java")) {
						executeBuild = true;
						return false;
					}
					break;
				case IResourceDelta.CHANGED:
					if (resource.getFileExtension().equals("java")) {
						executeBuild = true;
						return false;
					}
					break;
				}
			}
			return true;
		}
	}

	/**
	 * @param javaProject
	 * @return
	 * @throws JavaModelException
	 */
	protected String getClassFilesLocation(IJavaProject javaProject) throws JavaModelException {
		String path = javaProject.getOutputLocation().toString();
		IResource binFolder = ResourcesPlugin.getWorkspace().getRoot().findMember(path);
		if (binFolder != null)
			return binFolder.getLocation().toString();
		throw new RuntimeException("Could not retrieve Soot classpath for project " + javaProject.getElementName());
	}

	/**
	 * @param javaProject
	 * @return String with the complete Classpath for the soot run Computes the classpath necessary for the soot run
	 */
	private String getSootCP(IJavaProject javaProject) {
		String sootCP = "";
		try {
			for (String resource : getJarFilesLocation(javaProject))
				sootCP = sootCP + File.pathSeparator + resource;
		} catch (JavaModelException e) {
		}
		sootCP = sootCP + File.pathSeparator;
		return sootCP;
	}

	/**
	 * @param javaProject
	 * @return Set of the locations of jars for the soot classpath
	 * @throws JavaModelException
	 * 
	 *             This method computes the locations of the jar files necessary for the soot run
	 */
	protected Set<String> getJarFilesLocation(IJavaProject javaProject) throws JavaModelException {
		Set<String> jars = new HashSet<>();
		IClasspathEntry[] resolvedClasspath = javaProject.getResolvedClasspath(true);
		for (IClasspathEntry classpathEntry : resolvedClasspath) {
			String path = classpathEntry.getPath().toOSString();
			if (path.endsWith(".jar")) {
				IFile file = ResourcesPlugin.getWorkspace().getRoot().getFile(classpathEntry.getPath());
				if (file != null && file.getRawLocation() != null)
					path = file.getRawLocation().toOSString();
				jars.add(path);
			}
		}
		return jars;
	}

	@SuppressWarnings("unused")
	private String getOutputLocation(IJavaProject project) {
		String outputLocation = "";
		IPath path;
		try {
			path = project.getOutputLocation();
			IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
			IFolder folder = root.getFolder(path);
			outputLocation = folder.getLocation().toOSString();
		} catch (JavaModelException e) {
			e.printStackTrace();
		}
		return outputLocation;
	}

	private void fillDataModel(ICFGStructure icfg, List<VFClass> jimpleClasses) {
		DataModel data = ServiceUtil.getService(DataModel.class);
		data.setIcfg(icfg);
		data.setClassList(jimpleClasses);
		// data.setSelectedClass(jimpleClasses.get(0));
	}

	public static final String BUILDER_ID = "JimpleBuilder.JimpleBuilder";

	@Override
	protected IProject[] build(int kind, Map<String, String> args, IProgressMonitor monitor) throws CoreException {
		executeBuild = false;
		if (getProject() != null && getProject().getName().equals(GlobalSettings.get("AnalysisProject"))) {
			if (kind == FULL_BUILD) {
				fullBuild(monitor);
			} else {
				IResourceDelta delta = getDelta(getProject());
				if (delta == null) {
					fullBuild(monitor);
				} else {
					checkForBuild(delta, monitor);
				}
			}
		}
		return null;
	}

	protected void fullBuild(IProgressMonitor monitor) throws CoreException {
		Visuflow.getDefault().getLogger().info("Build Start");
		String targetFolder = "sootOutput";
		IJavaProject project = JavaCore.create(getProject());
		IResourceDelta delta = getDelta(project.getProject());
		if (delta == null || !delta.getAffectedChildren()[0].getProjectRelativePath().toString().equals(targetFolder)) {
			classpath = getSootCP(project);
			String location = GlobalSettings.get("Target_Path");
			IFolder folder = project.getProject().getFolder(targetFolder);

			// at this point, no resources have been created
			if (!folder.exists()) {
				// Changed to force because of bug id vis-119
				folder.create(IResource.FORCE, true, monitor);

			} else {
				for (IResource resource : folder.members()) {
					resource.delete(IResource.FORCE, monitor);
				}
			}
			classpath = location + classpath;
			String[] sootString = new String[] { "-cp", classpath, "-exclude", "javax", "-allow-phantom-refs", "-no-bodies-for-excluded", "-process-dir",
					location, "-src-prec", "only-class", "-w", "-output-format", "J", "-keep-line-number", "-output-dir",
					folder.getLocation().toOSString()/* , "tag.ln","on" */ };
			ICFGStructure icfg = new ICFGStructure();
			JimpleModelAnalysis analysis = new JimpleModelAnalysis();
			analysis.setSootString(sootString);
			List<VFClass> jimpleClasses = new ArrayList<>();
			try {
				analysis.createICFG(icfg, jimpleClasses);
				fillDataModel(icfg, jimpleClasses);
			} catch (Exception e) {
				logger.error("Couldn't execute analysis", e);
			}

			folder.refreshLocal(IResource.DEPTH_INFINITE, monitor);
		}
	}

	protected void checkForBuild(IResourceDelta delta, IProgressMonitor monitor) throws CoreException {
		delta.accept(new JimpleDeltaVisitor());
		if (executeBuild) {
			fullBuild(monitor);
			executeBuild = false;
		}
	}

}
