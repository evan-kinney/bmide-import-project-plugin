package com.seeq.eclipse;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.QualifiedName;
import org.eclipse.equinox.app.IApplicationContext;
import org.eclipse.ui.IStartup;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.w3c.dom.Document;

import com.teamcenter.bmide.base.core.util.BaseCoreUtil;
import com.teamcenter.bmide.foundation.core.ServerCoreConstants;
import com.teamcenter.bmide.install.dependency.TemplateDependency;
import com.teamcenter.bmide.install.dependency.TemplateDependencyConstants;
import com.teamcenter.bmide.install.dependency.TemplateDependencyReader;
import com.teamcenter.bmide.server.ui.ServerUIConstants;

public class ImportProjects implements IStartup {

	private static final String ARG_IMPORT = "-import";
	private static final String ARG_TEMPLATES = "-templates";
	private static final String ARG_CLOSE = "-close";
	private static final String ARG_DEPTH = "-depth";
	@SuppressWarnings("unused")
	private static final String ARG_DEPTH_VALUE_IMMEDIATES = "immediates";
	private static final String ARG_DEPTH_VALUE_INFINITY = "infinity";

	private String[] getImportPaths() {
		BundleContext context = Activator.getContext();
		ServiceReference<?> ser = context.getServiceReference(IApplicationContext.class.getName());
		IApplicationContext iac = (IApplicationContext) context.getService(ser);
		String[] args = (String[]) iac.getArguments().get(IApplicationContext.APPLICATION_ARGS);
		List<String> importPath = new ArrayList<String>();
		for (int i = 0; i < args.length; i++) {
			String arg = args[i];
			if (arg.compareToIgnoreCase(ARG_IMPORT) == 0) {
				i++;
				if (i < args.length) {
					importPath.add(args[i]);
				}
			}
		}

		return importPath.toArray(new String[importPath.size()]);
	}

	private String getTemplatesPath() {
		BundleContext context = Activator.getContext();
		ServiceReference<?> ser = context.getServiceReference(IApplicationContext.class.getName());
		IApplicationContext iac = (IApplicationContext) context.getService(ser);
		String[] args = (String[]) iac.getArguments().get(IApplicationContext.APPLICATION_ARGS);
		String templatesPath = "";
		for (int i = 0; i < args.length; i++) {
			String arg = args[i];
			if (arg.compareToIgnoreCase(ARG_TEMPLATES) == 0) {
				i++;
				if (i < args.length) {
					templatesPath = args[i];
				}
			}
		}

		return templatesPath;
	}

	private boolean getClose() {
		BundleContext context = Activator.getContext();
		ServiceReference<?> ser = context.getServiceReference(IApplicationContext.class.getName());
		IApplicationContext iac = (IApplicationContext) context.getService(ser);
		String[] args = (String[]) iac.getArguments().get(IApplicationContext.APPLICATION_ARGS);
		for (int i = 0; i < args.length; i++) {
			String arg = args[i];
			if (arg.compareToIgnoreCase(ARG_CLOSE) == 0) {
				return true;
			}
		}

		return false;
	}

	private String getDepth() {
		BundleContext context = Activator.getContext();
		ServiceReference<?> ser = context.getServiceReference(IApplicationContext.class.getName());
		IApplicationContext iac = (IApplicationContext) context.getService(ser);
		String[] args = (String[]) iac.getArguments().get(IApplicationContext.APPLICATION_ARGS);
		String depth = ARG_DEPTH_VALUE_INFINITY;
		for (int i = 0; i < args.length; i++) {
			String arg = args[i];
			if (arg.compareToIgnoreCase(ARG_DEPTH) == 0) {
				i++;
				if (i < args.length) {
					depth = args[i];
				}
			}
		}

		return depth;
	}

	private List<File> findFiles(String path, String pattern, List<File> returnedList) {
		File root = new File(path);
		File[] list = root.listFiles();
		String depth = this.getDepth();

		if (list == null)
			return returnedList;

		for (File f : list) {
			if (f.isDirectory() && depth == ARG_DEPTH_VALUE_INFINITY) {
				this.findFiles(f.getAbsolutePath(), pattern, returnedList);
			} else {
				if (Pattern.matches(pattern, f.getName()) == true) {
					returnedList.add(f);
				}
			}
		}

		return returnedList;
	}

	@SuppressWarnings("unchecked")
	@Override
	public void earlyStartup() {
		String[] importPaths = this.getImportPaths();
		String templatesPath = getTemplatesPath();
		boolean close = getClose();

		for (String importPath : importPaths) {
			LogUtil.info(String.format("Searching for projects in %s", importPath));
			System.out.println(String.format("Searching for projects in %s", importPath));
			List<File> projectFiles = this.findFiles(importPath, "\\" + BaseCoreUtil.PROJECT_FILE, new ArrayList<File>());

			for (File projectFile : projectFiles) {
				System.out.println(String.format("Found project at %s", projectFile.toString()));

				try {
					IWorkspace workspace = ResourcesPlugin.getWorkspace();
					IProjectDescription description = workspace.loadProjectDescription(new Path(projectFile.toString()));
					IProject project = workspace.getRoot().getProject(description.getName());
					if (!project.isOpen() && !project.exists()) {
						LogUtil.info(String.format("Importing project %s %s", description.getName(), description.getLocationURI()));
						System.out.println(String.format("Importing project %s %s", description.getName(), description.getLocationURI()));
						project.create(description, null);
						project.open(null);

						TemplateDependencyReader templateDependencyReader = new TemplateDependencyReader();
						Document dependencyXMLDocument = templateDependencyReader.readXmlFile(project.getFolder(ServerCoreConstants.EXTN_FOLDER_NAME).getFile(ServerCoreConstants.DEPENDENCY_FILE_NAME).getLocation().toOSString());
						TemplateDependency templateDependency = (TemplateDependency) templateDependencyReader.buildObject(dependencyXMLDocument);
						project.setPersistentProperty(new QualifiedName("", ServerUIConstants.SOLUTION_NAME_KEY), templateDependency.getName());
						List<String> prefixes = templateDependency.getPrefixes();

						if (prefixes != null && prefixes.size() > 0) {
							project.setPersistentProperty(new QualifiedName("", TemplateDependencyConstants.PREFIXES), prefixes.get(0));
						}

						project.setPersistentProperty(new QualifiedName("", ServerUIConstants.TEAMCENTER_MODEL_FOLDER_KEY), templatesPath);

						if (close) {
							project.close(null);
						}
					}
				} catch (Exception e) {
					LogUtil.error(e);
					System.err.println(e);
					e.printStackTrace();
				}
			}
		}
	}
}
